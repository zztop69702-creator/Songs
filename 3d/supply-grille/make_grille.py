"""이중 가이드베인형 급기 그릴 파라메트릭 CAD 모델 (CadQuery).

웹 뷰어(supply-grille-3d.html)와 같은 좌표·치수 규칙을 쓴다.
  - 단위 mm, 기류 방향 +Z (실내 쪽), 전면 프레임 실내측 면 z=+FACE_T
  - 전면 수평 날개: X축 회전 (상하 풍향), 후면 수직 날개: Y축 회전 (좌우 풍향)

사용 예:
  python make_grille.py --W 400 --H 200 --a1 15 --a2 0 --obd
출력: STEP(조립체) + STL(단일 메시)
치수는 일반적인 대표값이며 특정 제조사 제품 치수가 아니다.
"""
import argparse
import cadquery as cq

T = 1.2       # 날개 두께
SHEET = 1.5   # 넥 판두께
FACE_T = 6.0  # 전면 프레임 두께


def ring(ow, oh, iw, ih, depth, zc):
    outer = cq.Workplane("XY").box(ow, oh, depth)
    inner = cq.Workplane("XY").box(iw, ih, depth + 2)
    return outer.cut(inner).translate((0, 0, zc))


def positions(length, pitch):
    n = max(1, round(length / pitch) - 1)
    s = length / (n + 1)
    return [-length / 2 + s * (i + 1) for i in range(n)]


def build(W, H, F, D, a1, a2, p, d, obd, ob):
    D = max(D, 2 * d + 12)
    parts = {}
    parts["face_frame"] = ring(W + 2 * F, H + 2 * F, W, H, FACE_T, FACE_T / 2)
    parts["neck"] = ring(W + 2 * SHEET, H + 2 * SHEET, W, H, D, -D / 2)

    zF = -d / 2 - 2
    front = None
    for y in positions(H, p):
        v = (cq.Workplane("XY").box(W - 1, T, d)
             .rotate((0, 0, 0), (1, 0, 0), -a1).translate((0, y, zF)))
        front = v if front is None else front.union(v)
    parts["front_vanes"] = front

    zR = -d - 8 - d / 2
    rear = None
    for x in positions(W, p):
        v = (cq.Workplane("XY").box(T, H - 1, d)
             .rotate((0, 0, 0), (0, 1, 0), a2).translate((x, 0, zR)))
        rear = v if rear is None else rear.union(v)
    parts["rear_vanes"] = rear

    if obd:
        depth = 30.0
        zO = -D - depth / 2
        body = ring(W + 2 * SHEET, H + 2 * SHEET, W, H, depth, zO)
        flange = ring(W + 16, H + 16, W, H, 1.5, -D)
        parts["obd_frame"] = body.union(flange)
        ys = positions(H, 28)
        bw = min((H / (len(ys) + 1)) * 1.05, 27)  # 전개 시에도 프레임(30mm) 안에 들어가는 폭
        blades = None
        for i, y in enumerate(ys):
            ang = ob if i % 2 else -ob
            b = (cq.Workplane("XY").box(W - 2, bw, 1)
                 .rotate((0, 0, 0), (1, 0, 0), ang).translate((0, y, zO)))
            blades = b if blades is None else blades.union(b)
        parts["obd_blades"] = blades
    return parts, D


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--W", type=float, default=400)
    ap.add_argument("--H", type=float, default=200)
    ap.add_argument("--F", type=float, default=30, help="플랜지 폭")
    ap.add_argument("--D", type=float, default=55, help="넥 깊이")
    ap.add_argument("--a1", type=float, default=15, help="전면 날개 각도(+위)")
    ap.add_argument("--a2", type=float, default=0, help="후면 날개 각도(+우)")
    ap.add_argument("--p", type=float, default=20, help="날개 피치")
    ap.add_argument("--d", type=float, default=18, help="날개 깊이")
    ap.add_argument("--obd", action="store_true")
    ap.add_argument("--ob", type=float, default=90, help="댐퍼 개도(90=전개)")
    ap.add_argument("--out", default=None)
    a = ap.parse_args()

    parts, D = build(a.W, a.H, a.F, a.D, a.a1, a.a2, a.p, a.d, a.obd, a.ob)
    name = a.out or f"supply_grille_{int(a.W)}x{int(a.H)}" + ("_obd" if a.obd else "")

    colors = {"face_frame": (0.95, 0.95, 0.94), "neck": (0.9, 0.9, 0.9),
              "front_vanes": (0.95, 0.95, 0.94), "rear_vanes": (0.85, 0.86, 0.86),
              "obd_frame": (0.6, 0.64, 0.66), "obd_blades": (0.6, 0.64, 0.66)}
    asm = cq.Assembly(name=name)
    for k, v in parts.items():
        asm.add(v, name=k, color=cq.Color(*colors[k]))
    asm.save(f"{name}.step")

    merged = None
    for v in parts.values():
        merged = v if merged is None else merged.union(v)
    cq.exporters.export(merged, f"{name}.stl", tolerance=0.05, angularTolerance=0.2)

    bb = merged.val().BoundingBox()
    print(f"{name}: neck depth {D:.0f} mm, bbox {bb.xlen:.1f} x {bb.ylen:.1f} x {bb.zlen:.1f} mm, "
          f"volume {merged.val().Volume()/1000:.1f} cm3")


if __name__ == "__main__":
    main()
