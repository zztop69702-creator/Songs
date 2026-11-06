/* 영업도우미 노트북용 VER3.0
 * 원본(VER2.0 휴대폰 PWA)의 명함 그리기·소개글·명함 인식 로직을 그대로 옮기고,
 * 노트북 환경(웹캠·끌어놓기·붙여넣기·PC 카톡·휴대폰과 연결·QR)에 맞게 보내기 방식을 바꿨다. */
(function () {
  'use strict';
  const KEY = 'yeongil-card-laptop-v3';
  const LOGKEY = 'yeongil-card-laptop-log';
  const F = 'Pretendard, "Noto Sans KR", sans-serif';
  const VOLATILE = ['rName', 'rTitle', 'rCompany', 'rPhone', 'rTel', 'rEmail', 'rKakao', 'rLines', 'editing'];
  const R_FIELDS = [
    ['rName', '이름', '홍길동', 'text'], ['rTitle', '직책', '부장', 'text'], ['rCompany', '회사', '(주)OO건설', 'text'],
    ['rPhone', '휴대폰', '010-0000-0000', 'tel'], ['rKakao', '카카오톡 ID', 'kakao_id', 'text'],
    ['rEmail', '이메일', 'name@company.com', 'email'], ['rTel', '사무실', '02-000-0000', 'tel']
  ];
  const $ = id => document.getElementById(id);
  const isFile = location.protocol === 'file:';

  // ---------- 상태 ----------
  let S = (() => {
    let saved = {};
    try { saved = JSON.parse(localStorage.getItem(KEY) || '{}'); } catch (e) {}
    return { ...DEF, incl: null, sendMode: 'separate', cardOnly: false, ...saved };
  })();
  if (!S.incl) S.incl = { [S.cat || 'cabinet']: true };
  function persist() {
    const o = { ...S }; VOLATILE.forEach(k => delete o[k]);
    try { localStorage.setItem(KEY, JSON.stringify(o)); } catch (e) {}
  }
  function set(patch, opts) { Object.assign(S, patch); persist(); render(opts); }

  // ---------- 상태 메시지 ----------
  let stT;
  function status(msg, kind) {
    const el = $('status'); el.textContent = msg; el.className = kind || ''; el.style.display = msg ? 'block' : 'none';
    clearTimeout(stT); if (msg) stT = setTimeout(() => { el.style.display = 'none'; }, kind === 'err' ? 9000 : 6500);
  }

  // ---------- 이미지 ----------
  const IMG = {};
  function loadImg(k) { return new Promise(ok => { const i = new Image(); i.onload = () => ok(i); i.onerror = () => ok(null); i.src = window.CARD_ASSETS[k]; IMG[k] = i; }); }

  // ---------- 명함 그리기 (원본 draw()를 캔버스·명함종류를 인자로 받도록만 바꿈) ----------
  function justify(ctx, parts, x, w, y) {
    const ws = parts.map(p => ctx.measureText(p).width);
    const gap = (w - ws.reduce((a, b) => a + b, 0)) / (parts.length - 1);
    let cx = x; parts.forEach((p, i) => { ctx.fillText(p, cx, y); cx += ws[i] + gap; });
  }
  const geo = cat => ({ T: cat === 'cabinet' ? 1080 : cat === 'tab' ? 1045 : 0, L: cat === 'general' ? 733 : 0 });
  function draw(c, cat) {
    const img = IMG.card; if (!img || !img.naturalWidth) return false;
    const ctx = c.getContext('2d'), v = THEMES.find(t => t.id === S.theme) || THEMES[0], s = img.naturalWidth / 1024;
    const reset = () => { ctx.filter = 'none'; ctx.globalCompositeOperation = 'source-over'; ctx.letterSpacing = '0px'; };
    const { T, L } = geo(cat), tab = IMG.tab, face = IMG.face;
    if (c.height !== T + 541) c.height = T + 541;
    if (c.width !== L + 1024) c.width = L + 1024;
    reset(); ctx.setTransform(1, 0, 0, 1, 0, 0); ctx.textBaseline = 'middle';
    ctx.fillStyle = v.body; ctx.fillRect(0, 0, c.width, c.height);
    if (cat === 'cabinet') {
      ctx.filter = v.img; ctx.drawImage(img, 0, 0, 1024 * s, 980 * s, 0, 0, 1024, 980); reset();
      ctx.fillStyle = v.banner; ctx.fillRect(0, 980, 1024, 100);
      ctx.font = `800 44px ${F}`; ctx.letterSpacing = '-0.4px';
      const parts = [['근접건물 소음차단에 강한 ', v.bannerInk], ['소음캐비닛', v.hi], ['을 시공하세요', v.bannerInk]];
      let bx = (1024 - parts.reduce((a, p) => a + ctx.measureText(p[0]).width, 0)) / 2;
      parts.forEach(([t, col]) => { ctx.fillStyle = col; ctx.fillText(t, bx, 1031); bx += ctx.measureText(t).width; });
      reset();
    } else if (cat === 'tab') {
      if (tab && tab.naturalWidth) { const k = tab.naturalWidth / 1024; ctx.drawImage(tab, 0, 0, 1024 * k, 1045 * k, 0, 0, 1024, 1045); }
    } else if (face && face.naturalWidth) {
      const k = face.naturalWidth / 2138; ctx.drawImage(face, 24 * k, 22 * k, 963 * k, 711 * k, 0, 0, 733, 541);
    }
    ctx.setTransform(1, 0, 0, 1, L, T - 1080);
    ctx.fillStyle = v.body; ctx.fillRect(0, 1080, 1024, 315);
    ctx.filter = v.logo; ctx.globalCompositeOperation = v.blend;
    ctx.drawImage(img, 70 * s, 1088 * s, 260 * s, 282 * s, 72, 1096, 260, 282); reset();
    const tx = 432;
    ctx.fillStyle = v.sub; ctx.font = `500 32px ${F}`; ctx.fillText(S.title, tx, 1135);
    ctx.fillStyle = v.ink; ctx.font = `800 66px ${F}`; ctx.letterSpacing = '49px'; ctx.fillText(S.name, tx, 1202); reset();
    ctx.fillStyle = v.phone; ctx.font = `600 44px ${F}`; ctx.fillText(S.phone, tx, 1277);
    ctx.fillStyle = v.sub; ctx.font = `500 38px ${F}`; ctx.fillText(S.email, tx, 1336);
    ctx.fillStyle = v.rule; ctx.fillRect(0, 1395, 1024, 6);
    ctx.fillStyle = v.foot; ctx.fillRect(0, 1401, 1024, 220);
    const ys = [1441, 1487, 1533, 1579];
    ctx.fillStyle = v.ink; ctx.font = `700 32px ${F}`;
    justify(ctx, [...'기계설비공사업'], 64, 310, ys[0]);
    justify(ctx, [...'가스시설시공업'], 64, 310, ys[1]);
    justify(ctx, [...'전기공사업'], 64, 310, ys[2]);
    ctx.font = `700 30px ${F}`; ctx.letterSpacing = '-0.9px';
    justify(ctx, ['금속', '구조및', '창호', '공사업'], 64, 310, ys[3]); reset();
    ctx.fillStyle = v.divider; ctx.fillRect(410, 1421, 2, 180);
    const rx = 448;
    ctx.font = `700 32px ${F}`; ctx.fillStyle = v.label; ctx.fillText('회사메일 :', rx, ys[0]);
    const w = ctx.measureText('회사메일 : ').width;
    ctx.font = `500 32px ${F}`; ctx.fillStyle = v.ink; ctx.fillText('y8975101@hanmail.net', rx + w, ys[0]);
    ctx.font = `500 26px ${F}`; ctx.letterSpacing = '-0.8px'; ctx.fillStyle = v.sub; ctx.fillText('경기도 광명시 오리로 643, 603호(하안동, 석산빌딩)', rx, ys[1]); reset();
    ctx.font = `600 32px ${F}`; ctx.letterSpacing = '5.8px';
    [['TEL', ' : 02-897-5101', ys[2]], ['FAX', ' : 02-897-5100', ys[3]]].forEach(([l, t, y]) => {
      ctx.fillStyle = v.label; ctx.fillText(l, rx, y);
      const lw = ctx.measureText(l).width; ctx.fillStyle = v.ink; ctx.fillText(t, rx + lw, y);
    });
    reset(); ctx.setTransform(1, 0, 0, 1, 0, 0);
    return true;
  }
  const ORDER = ['tab', 'cabinet', 'general'];
  function selectedCats() { const a = ORDER.filter(k => S.incl[k]); return a.length ? a : [cat()]; }
  // 보낼 이미지 1장(명함종류별) — 홍보사진 포함 또는 명함만
  function catCanvas(k) {
    const full = document.createElement('canvas'); draw(full, k);
    if (!S.cardOnly) return full;
    const { T, L } = geo(k), out = document.createElement('canvas'); out.width = 1024; out.height = 541;
    out.getContext('2d').drawImage(full, L, T, 1024, 541, 0, 0, 1024, 541); return out;
  }
  // 여러 장을 폭 1024로 맞춰 세로로 합친 1장
  function mergedCanvas(cats) {
    const parts = cats.map(catCanvas), W = 1024, gap = 24;
    const hs = parts.map(p => Math.round(p.height * W / p.width));
    const out = document.createElement('canvas'); out.width = W; out.height = hs.reduce((a, b) => a + b, 0) + gap * (parts.length - 1);
    const x = out.getContext('2d'); x.fillStyle = '#ffffff'; x.fillRect(0, 0, W, out.height); x.imageSmoothingQuality = 'high';
    let y = 0; parts.forEach((p, i) => { x.drawImage(p, 0, y, W, hs[i]); y += hs[i] + gap; });
    return out;
  }
  // 보낼 순서: 소개글을 고른 명함이 맨 앞, 나머지는 TAB → 소음캐비닛 → 얼굴명함 순
  function sendOrder() { const c = selectedCats(), m = cat(); return c.includes(m) ? [m, ...c.filter(k => k !== m)] : c; }
  const caption = (k, i, n) => `[(주)영일 ${S.name}] ${CATS[k]} 명함 (${i + 1}/${n})`;
  // 받는 사람이 받을 문자 목록. 기본은 명함별로 따로(선명), 선택 시 한 장으로 합쳐 1건
  function items() {
    const c = sendOrder();
    if (c.length === 1 || S.sendMode === 'merged') return [{ cats: c, c: c.length > 1 ? mergedCanvas(c) : catCanvas(c[0]), name: c.map(k => CATS[k]).join('+'), text: fullText() }];
    return c.map((k, i) => ({ cats: [k], c: catCanvas(k), name: CATS[k], text: i === 0 ? fullText() : caption(k, i, c.length) }));
  }
  const outputCanvases = items;
  const toBlob = (c, type, q) => new Promise(r => c.toBlob(r, type || 'image/png', q));
  const safe = t => String(t || '').replace(/[\\/:*?"<>|\s]+/g, '_');
  function fileName(part, ext) { return `${safe(part)}_명함_${safe(S.name || '담당자')}${S.rName ? '_to_' + safe(S.rName) : ''}.${ext}`; }

  // ---------- 글 ----------
  const cat = () => S.cat || 'cabinet';
  const sel = () => (S.msgSel || {})[cat()] ?? 0;
  const fill = t => t.replace(/\{(\w+)\}/g, (m, k) => S[k] || '');
  function msgText(c, i) { const ed = ((S.msgEdit || {})[c] || {})[i]; return ed != null ? ed : fill(MSGS[c][i]); }
  function greeting() { if (!S.rName) return ''; return `${S.rName}${S.rTitle ? ' ' + S.rTitle : ''}님, 안녕하세요.\n\n`; }
  function message() { const c = cat(), i = sel(); if (i === 99) return (S.msgCustom || {})[c] || ''; return greeting() + msgText(c, i); }
  function cardInfo() { return `\n\n─────────\n(주)영일 ${S.title}\n${S.name}\n휴대폰 ${S.phone}\n이메일 ${S.email}\nTEL 02-897-5101 · FAX 02-897-5100\n경기도 광명시 오리로 643, 603호(하안동, 석산빌딩)`; }
  const fullText = () => (message() + cardInfo()).replace(/^\s+/, '');
  const digits = v => String(v || '').replace(/\D/g, '');
  function fmtPhone(v) {
    let d = digits(v); if (d.startsWith('82') && d.length >= 11) d = '0' + d.slice(2);
    d = d.slice(0, 11);
    if (d.startsWith('02')) { if (d.length < 3) return d; if (d.length < 6) return d.slice(0, 2) + '-' + d.slice(2); return d.slice(0, 2) + '-' + d.slice(2, d.length - 4) + '-' + d.slice(-4); }
    if (d.length < 4) return d; if (d.length < 8) return d.slice(0, 3) + '-' + d.slice(3);
    return d.slice(0, 3) + '-' + d.slice(3, d.length - 4) + '-' + d.slice(-4);
  }

  // ---------- 명함 인식(파싱) : 원본 parseCard + 카톡ID·직책·사무실번호·+82 추가 ----------
  function parseCard(data) {
    const joinKo = s => s.replace(/([가-힣])\s+(?=[가-힣](\s|$))/g, '$1').replace(/\s+/g, ' ').trim();
    let lines = (data.lines || []).map(l => ({ t: joinKo(l.text || ''), h: l.bbox ? l.bbox.y1 - l.bbox.y0 : 0 })).filter(l => l.t);
    if (!lines.length) lines = (data.text || '').split(/\n+/).map(t => ({ t: joinKo(t), h: 0 })).filter(l => l.t);
    const flat = lines.map(l => l.t).join('\n');
    // "E. abc@..", "E-mail : abc@.." 같은 머리표를 먼저 떼어야 'E.abc@..'로 붙지 않음
    const em = flat.replace(/(^|[\s|/])(?:e-?mail|mail|e)\s*[.:：]\s*(?=[A-Za-z0-9._%+-]+\s*@)/gim, '$1 ').replace(/\s*@\s*/g, '@').replace(/\s*\.\s*(?=[a-z]{2,})/gi, '.').match(/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/);
    const numFlat = flat.replace(/(?<=\d)[Oo]|[Oo](?=\d)/g, '0').replace(/(?<=\d)[Il|]|[Il|](?=\d)/g, '1');
    const ph = numFlat.match(/(?:\+?\s*82[\s.\-)]*)?\(?(0?1[016789])\)?[\s.\-)]*(\d{3,4})[\s.\-]*(\d{4})(?!\d)/);
    let tel = '';
    numFlat.split('\n').forEach(l => {
      if (tel) return;
      // "Tel 031.527.0799  Fax 031.527.1799"처럼 한 줄에 있으면 팩스 앞부분만 봄 (줄 전체를 버리지 않음)
      const seg = l.split(/fax|팩스|\bF\s*[.:)]/i)[0];
      const m = seg.match(/(?<!\d)(0(?:2|[3-6][1-5]|70|50\d?|80))[\s.\-)]*(\d{3,4})[\s.\-]*(\d{4})(?!\d)/);
      if (m) tel = `${m[1]}-${m[2]}-${m[3]}`;
    });
    // 카카오톡 ID: "카카오톡 ID : abc", "카톡 abc", "KakaoTalk: abc", "kakao id abc"
    const kk = flat.match(/(?:카카오\s*톡|카톡|kakao\s*talk|kakao)\s*(?:id|아이디)?\s*[:：.\-]?\s*([A-Za-z0-9][A-Za-z0-9._\-]{2,19})/i);
    let kakao = kk ? kk[1] : '';
    if (/^(id|talk|com|kakao)$/i.test(kakao)) kakao = '';
    const SUR = '김이박최정강조윤장임한오서신권황안송류유전홍고문양손배백허남심노하곽성차주우구민진나지엄채원천방공현함변염여추도소석선설마길연위표명기반왕금옥육인맹제모탁국어은편용예봉경사부';
    const TITLE_S = '대표이사|대표|부사장|사장|전무|상무|이사|본부장|부장|차장|과장|대리|주임|사원|팀장|실장|소장|책임|선임|수석|연구원|매니저|기사|감독|원장|회장|교장|교감|행정실장|주무관|계장|팀원';
    const TITLE = new RegExp('(' + TITLE_S + ')', 'g');
    const CO = /(\(\s*[주유]\s*\)|㈜|주식회사|유한회사|건설|산업|엔지니어링|기술|설비|테크|코리아|그룹|홀딩스|개발|종합|전기|기계|공사|ENG|E&C|CO\.|학교|교육청|교육지원청|시청|구청|군청)/i;
    const ADDR = /(특별시|광역시|[가-힣]+(시|도|군|구)\s+[가-힣]+(시|군|구|읍|면|동|로|길)|[가-힣0-9]+(로|길)\s*\d|\d+\s*(호|층|번지)|번길|빌딩|타워|센터)/;
    const EN_TITLE = /\b(CEO|대표|President|Director|Manager)\b/i;
    // 지명(경기도·서울시·남양주시 등)이 이름으로 잡히지 않게
    const PLACE = /^(서울|부산|대구|인천|광주|대전|울산|세종|경기|강원|충청|충북|충남|전라|전북|전남|경상|경북|경남|제주)|(특별시|광역시|자치시|[시군읍면로길층])$/;
    let best = null;
    lines.forEach(l => {
      if (/^\s*(서울|부산|대구|인천|광주|대전|울산|세종|경기|강원|충청|충북|충남|전라|전북|전남|경상|경북|경남|제주)/.test(l.t)) return;
      // 회사줄·주소줄·전화/팩스/메일 머리줄은 이름 후보에서 제외. 숫자가 있어도 'CEO 지철연 010-...'처럼 이름이 같이 있을 수 있어 줄을 버리지 않고 숫자만 지움
      if (CO.test(l.t) || ADDR.test(l.t) || /@/.test(l.t.replace(/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+/g, '')) ) return;
      const clean = l.t.replace(/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+/g, ' ').replace(/(tel|fax|e-?mail|mobile|phone|hp|h\.p|팩스|전화|휴대폰|핸드폰)\s*[.:]?/gi, ' ');
      const hasTitle = new RegExp(TITLE_S).test(clean) || EN_TITLE.test(clean);
      const ko = clean.replace(TITLE, ' ').replace(/[^가-힣\s]/g, ' ').split(/\s+/).filter(Boolean);
      ko.forEach(w => {
        if (w.length < 2 || w.length > 4 || SUR.indexOf(w[0]) < 0 || PLACE.test(w)) return;
        const score = l.h + (w.length === 3 ? 20 : 0) + (ko.length === 1 ? 10 : 0) + (hasTitle ? 25 : 0) - (/\d/.test(l.t) ? 5 : 0);
        if (!best || score > best.score) best = { w, score, line: clean };
      });
    });
    let title = '';
    if (best) { const tm = best.line.match(TITLE); if (tm) title = tm[tm.length - 1]; else if (/\bCEO\b|\bPresident\b/i.test(best.line)) title = '대표'; }
    if (!title) { const tl = lines.find(l => !/\d|@/.test(l.t) && l.t.length <= 20 && new RegExp('(' + TITLE_S + ')$').test(l.t)); if (tl) title = tl.t.match(new RegExp('(' + TITLE_S + ')$'))[1]; }
    const coLine = lines.filter(l => CO.test(l.t) && !/@/.test(l.t) && (l.t.match(/\d/g) || []).length < 4)
      .sort((a, b) => (/\(\s*주\s*\)|㈜|주식회사/.test(b.t) - /\(\s*주\s*\)|㈜|주식회사/.test(a.t)) || b.h - a.h)[0];
    // 로고 잡티('Fy (주선…') 제거: 한글·(주)·㈜ 앞의 영문/기호를 떼고, 닫는 괄호가 빠진 '(주' 도 '(주)'로
    let company = coLine ? coLine.t.replace(/^[^가-힣(㈜]*(?=[가-힣(㈜])/, '').replace(/\(\s*주\s*\)?/g, '(주)').replace(/\(\s*유\s*\)?/g, '(유)').replace(/\s+(대표|CEO).*$/i, '').trim() : '';
    if (!company && em && !/(naver|daum|hanmail|gmail|nate|kakao|hotmail|yahoo)\./i.test(em[0])) company = em[0].split('@')[1].split('.')[0];
    return {
      rName: best ? best.w : '', rTitle: title, rCompany: company, rPhone: ph ? `${ph[1].replace(/^1/, '01')}-${ph[2]}-${ph[3]}` : '',
      rTel: tel, rEmail: em ? em[0] : '', rKakao: kakao,
      rLines: lines.map(l => l.t).filter(t => /[가-힣A-Za-z]/.test(t)).slice(0, 16)
    };
  }
  const scoreOf = g => ['rName', 'rCompany', 'rPhone', 'rEmail', 'rKakao'].filter(k => g[k]).length;
  const PARSE_KEYS = ['rName', 'rTitle', 'rCompany', 'rPhone', 'rTel', 'rEmail', 'rKakao'];

  // ---------- OCR (Tesseract.js, 폴더 안 파일 사용 → 인터넷 없이 동작) ----------
  let worker = null, workerP = null, ocrPass = 0;
  function loadScript(src) { return new Promise((ok, no) => { const s = document.createElement('script'); s.src = src; s.onload = ok; s.onerror = no; document.head.appendChild(s); }); }
  async function getWorker() {
    if (worker) return worker;
    if (!workerP) workerP = (async () => {
      if (!window.Tesseract) await loadScript('vendor/tesseract/tesseract.min.js');
      const base = new URL('vendor/', location.href).href;
      const w = await Tesseract.createWorker('kor+eng', 1, {
        workerPath: base + 'tesseract/worker.min.js', corePath: base + 'tesseract-core', langPath: base + 'lang', workerBlobURL: false,
        logger: m => {
          if (m.status === 'recognizing text') progress(Math.round((ocrPass * 100 + m.progress * 100) / 2), '글자 읽는 중');
          else if (/loading|initializ/.test(m.status)) progress(null, '인식 엔진 준비 중 (처음 한 번 5~20초)');
        }
      });
      worker = w; return w;
    })().catch(e => { workerP = null; throw e; });
    return workerP;
  }
  function progress(p, label) {
    const bar = $('ocrBar'); bar.style.display = 'block';
    if (p != null) bar.firstElementChild.style.width = p + '%';
    $('ocrText').textContent = `${label || '명함 읽는 중'}… ${p != null ? p + '%' : ''}`;
  }
  // 원본 prep(): 확대 + 흑백 + 오츠 이진화(어두운 바탕이면 반전)
  function prep(src) {
    const nw = src.naturalWidth || src.width, nh = src.naturalHeight || src.height;
    const k = Math.min(3, 2400 / Math.max(nw, nh));
    const W = Math.round(nw * k), H = Math.round(nh * k);
    const g = document.createElement('canvas'); g.width = W; g.height = H;
    const gx = g.getContext('2d'); gx.filter = 'grayscale(1) contrast(1.3)'; gx.drawImage(src, 0, 0, W, H);
    const b = document.createElement('canvas'); b.width = W; b.height = H;
    const bx = b.getContext('2d'); bx.drawImage(g, 0, 0);
    const id = bx.getImageData(0, 0, W, H), p = id.data, hist = new Array(256).fill(0);
    for (let i = 0; i < p.length; i += 4) hist[p[i]]++;
    const tot = W * H; let sum = 0; for (let i = 0; i < 256; i++) sum += i * hist[i];
    let sB = 0, wB = 0, best = 0, th = 128;
    for (let t = 0; t < 256; t++) { wB += hist[t]; if (!wB) continue; const wF = tot - wB; if (!wF) break; sB += t * hist[t]; const mB = sB / wB, mF = (sum - sB) / wF, v = wB * wF * (mB - mF) * (mB - mF); if (v > best) { best = v; th = t; } }
    let dark = 0; for (let i = 0; i < p.length; i += 4) if (p[i] < th) dark++;
    const inv = dark > tot / 2;
    for (let i = 0; i < p.length; i += 4) { let v = p[i] < th ? 0 : 255; if (inv) v = 255 - v; p[i] = p[i + 1] = p[i + 2] = v; }
    bx.putImageData(id, 0, 0);
    return { gray: g, bin: b };
  }
  let scanSrc = null, busy = false; // 마지막 스캔 원본(canvas)
  function blobToCanvas(blob) {
    return new Promise((ok, no) => {
      const im = new Image(), url = URL.createObjectURL(blob);
      im.onload = () => { const c = document.createElement('canvas'); c.width = im.naturalWidth; c.height = im.naturalHeight; c.getContext('2d').drawImage(im, 0, 0); URL.revokeObjectURL(url); ok(c); };
      im.onerror = () => { URL.revokeObjectURL(url); no(new Error('이미지를 열 수 없습니다')); };
      im.src = url;
    });
  }
  async function scanBlob(blob) { try { scanCanvas(await blobToCanvas(blob)); } catch (e) { status('이미지 파일을 열 수 없습니다. JPG·PNG 사진인지 확인하세요.', 'err'); } }
  async function scanCanvas(c) {
    $('scanMore').open = true;
    if (busy) { status('지금 인식 중입니다. 잠시 기다리세요.'); return; }
    scanSrc = c;
    const th = $('scanThumb'); th.src = c.toDataURL('image/jpeg', 0.85); th.style.display = 'block'; $('thumbTools').style.display = 'flex';
    if (isFile) { status('더블클릭 실행에서는 사진 인식이 안 됩니다. 실행_윈도우.bat로 여세요. (또는 캡처 도구 텍스트 복사 → [복사한 글로 채우기])', 'err'); return; }
    busy = true; ocrPass = 0; progress(0, '명함 읽는 중');
    try {
      const w = await getWorker(), img = prep(c);
      await w.setParameters({ tessedit_pageseg_mode: '6', preserve_interword_spaces: '1' });
      const a = parseCard((await w.recognize(img.bin)).data);
      ocrPass = 1;
      await w.setParameters({ tessedit_pageseg_mode: '11' });
      const b = parseCard((await w.recognize(img.gray)).data);
      const got = {}; PARSE_KEYS.forEach(k => { got[k] = a[k] || b[k] || ''; });
      if (scoreOf(b) > scoreOf(a) && b.rName) { got.rName = b.rName; got.rTitle = b.rTitle || got.rTitle; }
      const lines = [...new Set([...(a.rLines || []), ...(b.rLines || [])])].slice(0, 20);
      const n = scoreOf(got);
      set({ ...got, rLines: lines });
      progress(100, '완료');
      status(n >= 2 ? `${n}개 항목을 읽었습니다. 틀린 곳만 고치세요. 아래 '인식된 글자'를 눌러 채울 수도 있습니다.` : '인식이 부족합니다. 더 밝고 가깝게 다시 찍거나, 아래 인식된 글자를 눌러 채우세요.', n >= 2 ? 'ok' : 'err');
    } catch (e) {
      console.error(e);
      $('ocrBar').style.display = 'none'; $('ocrText').textContent = '';
      status('사진 인식 엔진을 불러오지 못했습니다. 실행_윈도우.bat로 열었는지, vendor 폴더가 있는지 확인하세요.', 'err');
    } finally { busy = false; }
  }
  function rotateScan() {
    if (!scanSrc) return; const c = document.createElement('canvas'); c.width = scanSrc.height; c.height = scanSrc.width;
    const x = c.getContext('2d'); x.translate(c.width, 0); x.rotate(Math.PI / 2); x.drawImage(scanSrc, 0, 0); scanCanvas(c);
  }
  function fillFromText(raw) {
    const got = parseCard({ text: raw }), upd = {};
    PARSE_KEYS.forEach(k => { if (got[k]) upd[k] = got[k]; });
    upd.rLines = got.rLines;
    const n = scoreOf(got);
    set(upd);
    status(n ? `${n}개 항목을 채웠습니다. 확인 후 틀린 곳만 고치세요.` : '이름·번호를 찾지 못했습니다. 아래 인식된 글자를 눌러 채우세요.', n ? 'ok' : 'err');
    return n;
  }

  // ---------- 웹캠 ----------
  let stream = null;
  async function openCam(deviceId) {
    if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) { status('이 브라우저는 웹캠을 쓸 수 없습니다. Chrome 또는 Edge로 여세요.', 'err'); return; }
    stopCam();
    try {
      stream = await navigator.mediaDevices.getUserMedia({ video: deviceId ? { deviceId: { exact: deviceId }, width: { ideal: 1920 }, height: { ideal: 1080 } } : { width: { ideal: 1920 }, height: { ideal: 1080 }, facingMode: 'environment' }, audio: false });
    } catch (e) {
      status(e && e.name === 'NotAllowedError' ? '카메라 권한이 거부되었습니다. 주소창 왼쪽 자물쇠(또는 카메라) 아이콘 → 카메라 허용 후 다시 누르세요.' : '웹캠을 찾지 못했습니다. 카메라가 연결되어 있는지, 다른 프로그램(줌·팀즈 등)이 쓰고 있지 않은지 확인하세요.', 'err');
      return;
    }
    const v = $('camVideo'); v.srcObject = stream; $('camModal').classList.add('open');
    v.onloadedmetadata = () => placeGuide();
    try {
      const devs = (await navigator.mediaDevices.enumerateDevices()).filter(d => d.kind === 'videoinput');
      const cur = stream.getVideoTracks()[0].getSettings().deviceId;
      $('camSel').innerHTML = devs.map((d, i) => `<option value="${d.deviceId}" ${d.deviceId === cur ? 'selected' : ''}>${esc(d.label || '카메라 ' + (i + 1))}</option>`).join('');
      $('camSel').style.display = devs.length > 1 ? '' : 'none';
    } catch (e) {}
  }
  function guideRect(vw, vh) { // 명함 비율(90x50) 안내 칸: 화면 폭의 78%
    let w = vw * 0.78, h = w / 1.8; if (h > vh * 0.85) { h = vh * 0.85; w = h * 1.8; }
    return { x: (vw - w) / 2, y: (vh - h) / 2, w, h };
  }
  function placeGuide() {
    const v = $('camVideo'), g = $('camGuide'); if (!v.videoWidth) return;
    const r = guideRect(v.clientWidth, v.clientHeight);
    Object.assign(g.style, { left: r.x + 'px', top: r.y + 'px', width: r.w + 'px', height: r.h + 'px' });
  }
  function stopCam() { if (stream) { stream.getTracks().forEach(t => t.stop()); stream = null; } $('camModal').classList.remove('open'); }
  function shoot() {
    const v = $('camVideo'); if (!v.videoWidth) return;
    const r = guideRect(v.videoWidth, v.videoHeight), m = 0.04; // 안내 칸보다 조금 넓게 자름
    const x = Math.max(0, r.x - r.w * m), y = Math.max(0, r.y - r.h * m), w = Math.min(v.videoWidth - x, r.w * (1 + 2 * m)), h = Math.min(v.videoHeight - y, r.h * (1 + 2 * m));
    const c = document.createElement('canvas'); c.width = Math.round(w); c.height = Math.round(h);
    c.getContext('2d').drawImage(v, x, y, w, h, 0, 0, c.width, c.height);
    stopCam(); scanCanvas(c);
  }

  // ---------- 복사 / 저장 ----------
  async function copyText(t) {
    try { await navigator.clipboard.writeText(t); return true; } catch (e) {}
    const ta = document.createElement('textarea'); ta.value = t; ta.style.position = 'fixed'; ta.style.opacity = '0'; document.body.appendChild(ta); ta.select();
    let ok = false; try { ok = document.execCommand('copy'); } catch (e) {} ta.remove(); return ok;
  }
  async function copyImage(idx) {
    const outs = outputCanvases();
    try {
      const blob = await toBlob(outs[idx || 0].c, 'image/png');
      await navigator.clipboard.write([new ClipboardItem({ 'image/png': blob })]);
      return outs.length;
    } catch (e) { console.error(e); return 0; }
  }
  function saveBlob(blob, name) {
    const a = document.createElement('a'); a.href = URL.createObjectURL(blob); a.download = name;
    document.body.appendChild(a); a.click(); a.remove(); setTimeout(() => URL.revokeObjectURL(a.href), 5000);
  }
  async function saveImages() {
    const outs = outputCanvases();
    for (const o of outs) { saveBlob(await toBlob(o.c, 'image/jpeg', 0.9), fileName(o.name, 'jpg')); await new Promise(r => setTimeout(r, 350)); }
    return outs.length;
  }
  async function imgCopyMsg(prefix) {
    const n = await copyImage();
    if (!n) return prefix + ' 이미지 복사가 막혀 있어 [이미지 파일 저장] 후 첨부하세요.';
    return prefix + (n > 1 ? ` (명함 이미지 ${n}장 중 첫 장만 복사됨 — 나머지는 [이미지 파일 저장] 후 첨부)` : '');
  }

  // ---------- 보낸 기록 ----------
  function getLog() { try { return JSON.parse(localStorage.getItem(LOGKEY) || '[]'); } catch (e) { return []; } }
  function addLog(channel) {
    const L = getLog(), now = new Date(), key = [S.rName, digits(S.rPhone), channel].join('|');
    if (L[0] && L[0].key === key && now - new Date(L[0].at) < 10 * 60 * 1000) return; // 10분 내 같은 건 중복 기록 안 함
    L.unshift({ key, at: now.toISOString(), name: S.rName || '', title: S.rTitle || '', company: S.rCompany || '', phone: S.rPhone || '', email: S.rEmail || '', kakao: S.rKakao || '', cards: selectedCats().map(k => CATS[k]).join('+'), msg: sel() === 99 ? '직접작성' : `${CATS[cat()]} ${sel() + 1}번`, channel });
    try { localStorage.setItem(LOGKEY, JSON.stringify(L.slice(0, 500))); } catch (e) {}
    renderLog();
  }
  function renderLog() {
    const L = getLog();
    $('logBox').innerHTML = L.length ? `<table class="log"><tr><th>일시</th><th>받는 사람</th><th>연락처</th><th>보낸 것</th><th>방법</th></tr>${L.slice(0, 30).map(r => `<tr><td>${esc(r.at.slice(5, 16).replace('T', ' '))}</td><td>${esc(r.name)} ${esc(r.title)}<br><span class="hint">${esc(r.company)}</span></td><td>${esc(r.phone || r.email || r.kakao)}</td><td>${esc(r.cards)}<br><span class="hint">${esc(r.msg)}</span></td><td>${esc(r.channel)}</td></tr>`).join('')}</table>` : '<div class="hint">아직 기록이 없습니다. 보내기 버튼을 누르면 자동으로 남습니다.</div>';
  }
  function logCsv() {
    const L = getLog(); if (!L.length) { status('기록이 없습니다.'); return; }
    const head = ['일시', '이름', '직책', '회사', '휴대폰', '이메일', '카톡ID', '보낸 명함', '소개글', '방법'];
    const q = v => `"${String(v || '').replace(/"/g, '""')}"`;
    const rows = L.map(r => [new Date(r.at).toLocaleString('ko-KR'), r.name, r.title, r.company, r.phone, r.email, r.kakao, r.cards, r.msg, r.channel].map(q).join(','));
    saveBlob(new Blob(['﻿' + [head.map(q).join(','), ...rows].join('\r\n')], { type: 'text/csv' }), `영업도우미_보낸기록_${stamp()}.csv`);
  }
  const stamp = () => { const d = new Date(); return `${d.getFullYear()}${String(d.getMonth() + 1).padStart(2, '0')}${String(d.getDate()).padStart(2, '0')}`; };

  // ---------- 보내기 동작 ----------
  function needRecipient(kind) {
    if (kind === 'phone' && digits(S.rPhone).length < 10) { status('받는 사람 휴대폰 번호를 먼저 넣으세요 (왼쪽 ① 인식 결과).', 'err'); $('r_rPhone').focus(); return false; }
    if (kind === 'email' && !/@/.test(S.rEmail || '')) { status('받는 사람 이메일을 먼저 넣으세요.', 'err'); $('r_rEmail').focus(); return false; }
    return true;
  }
  const actions = {
    copyTxt: async () => { const ok = await copyText(fullText()); status(ok ? '보낼 글을 복사했습니다. Ctrl+V 하세요.' : '복사하지 못했습니다.', ok ? 'ok' : 'err'); },
    save: async () => { const n = await saveImages(); status(`이미지 ${n}개를 '다운로드' 폴더에 저장했습니다.`, 'ok'); },
    share: async () => {
      const outs = outputCanvases(), files = [];
      for (const o of outs) files.push(new File([await toBlob(o.c, 'image/jpeg', 0.9)], fileName(o.name, 'jpg'), { type: 'image/jpeg' }));
      const text = fullText();
      try {
        if (navigator.canShare && navigator.canShare({ files, text })) { await navigator.share({ files, text }); addLog('Windows 공유'); status('공유를 보냈습니다.', 'ok'); return; }
        if (navigator.canShare && navigator.canShare({ files })) { await copyText(text); await navigator.share({ files }); addLog('Windows 공유'); status('이미지를 공유했습니다. 글은 복사되어 있으니 붙여넣기 하세요.', 'ok'); return; }
      } catch (e) { if (e && e.name === 'AbortError') { status('공유를 취소했습니다.'); return; } }
      status('이 브라우저는 공유창을 지원하지 않습니다. 복사·저장 버튼을 쓰세요.', 'err');
    },
    kId: async () => { if (!S.rKakao) { status('카카오톡 ID가 비어 있습니다. 명함에 ID가 없으면 휴대폰 번호로 찾으세요.', 'err'); return; } await copyText(S.rKakao); status(`카톡 ID '${S.rKakao}'를 복사했습니다. PC 카톡 → 친구 추가 → ID로 추가 칸에 Ctrl+V.`, 'ok'); },
    kPhone: async () => { if (!needRecipient('phone')) return; await copyText(S.rPhone); status(`${S.rPhone} 를 복사했습니다.`, 'ok'); },
    kImg: async i => { const n = await copyImage(i); addLog('카카오톡 PC'); status(n ? `${i + 1}번째 명함 이미지(${items()[i].name})를 복사했습니다. 카톡 대화창에 Ctrl+V → 전송.` + (i + 1 < n ? ` 다음은 ${i + 2}번째 이미지.` : ' 다음은 소개글 복사.') : '이미지 복사가 막혀 있습니다. [이미지 파일 저장]을 쓰세요.', n ? 'ok' : 'err'); },
    kText: async () => { const ok = await copyText(fullText()); addLog('카카오톡 PC'); status(ok ? '소개글+명함정보를 복사했습니다. 카톡 대화창에 Ctrl+V → Enter.' : '복사하지 못했습니다.', ok ? 'ok' : 'err'); },
    phoneLink: async () => {
      if (!needRecipient('phone')) return;
      const m = await imgCopyMsg('문자창이 열리면 이미지를 Ctrl+V(또는 첨부 버튼)로 넣고 전송하세요.');
      addLog('문자(휴대폰과 연결)'); status(m + ' 문자창이 안 열리면 Windows [휴대폰과 연결] 앱 설정을 확인하거나 QR 방식을 쓰세요.', 'ok');
      setTimeout(() => { location.href = `sms:${digits(S.rPhone)}?body=${encodeURIComponent(fullText())}`; }, 80);
    },
    qr: () => {
      if (!needRecipient('phone')) return;
      let text = fullText(), note = '갤럭시 기본 카메라로 QR을 비추고 뜨는 알림을 누르면 문자창이 열립니다. 명함 이미지는 문자창의 + → 갤러리에서 첨부하세요.';
      let qr = makeQr(`SMSTO:${digits(S.rPhone)}:${text}`);
      if (!qr) { text = message().replace(/^\s+/, '') + `\n\n(주)영일 ${S.name} ${S.phone}`; qr = makeQr(`SMSTO:${digits(S.rPhone)}:${text}`); note = '글이 길어 명함정보를 줄였습니다. ' + note; }
      if (!qr) { qr = makeQr(`SMSTO:${digits(S.rPhone)}:`); note = '글이 너무 길어 번호만 넣었습니다. [글 복사] 후 폰으로 보내세요. ' + note; }
      $('qrBox').innerHTML = qr; $('qrNote').textContent = `받는 사람 ${S.rName || ''} ${S.rPhone} · ` + note;
      $('qrModal').classList.add('open'); addLog('문자(QR→폰)');
    },
    gmail: async () => {
      if (!needRecipient('email')) return;
      const m = await imgCopyMsg('Gmail 작성창이 열리면 본문에 Ctrl+V로 명함 이미지를 넣으세요.');
      window.open(`https://mail.google.com/mail/?view=cm&fs=1&to=${encodeURIComponent(S.rEmail)}&su=${encodeURIComponent(mailSubject())}&body=${encodeURIComponent(fullText())}`, '_blank');
      addLog('메일(Gmail)'); status(m, 'ok');
    },
    mailto: async () => {
      if (!needRecipient('email')) return;
      const m = await imgCopyMsg('메일 작성창이 열리면 본문에 Ctrl+V로 명함 이미지를 넣거나 파일을 첨부하세요.');
      addLog('메일'); status(m, 'ok');
      setTimeout(() => { location.href = `mailto:${S.rEmail}?subject=${encodeURIComponent(mailSubject())}&body=${encodeURIComponent(fullText())}`; }, 80);
    },
    vcf: () => {
      if (!S.rName && !S.rPhone && !S.rEmail) { status('저장할 연락처가 없습니다. 먼저 명함을 스캔하세요.', 'err'); return; }
      const e = v => String(v || '').replace(/([,;\\])/g, '\\$1').replace(/\n/g, '\\n');
      const v = ['BEGIN:VCARD', 'VERSION:3.0', `N;CHARSET=UTF-8:${e((S.rName || '').slice(0, 1))};${e((S.rName || '').slice(1))};;;`, `FN;CHARSET=UTF-8:${e(S.rName || S.rCompany || S.rPhone)}`,
        S.rCompany && `ORG;CHARSET=UTF-8:${e(S.rCompany)}`, S.rTitle && `TITLE;CHARSET=UTF-8:${e(S.rTitle)}`,
        S.rPhone && `TEL;TYPE=CELL:${S.rPhone}`, S.rTel && `TEL;TYPE=WORK:${S.rTel}`, S.rEmail && `EMAIL;TYPE=WORK:${S.rEmail}`,
        `NOTE;CHARSET=UTF-8:${e(`영업도우미 명함 스캔 ${new Date().toLocaleDateString('ko-KR')}${S.rKakao ? ' / 카톡ID ' + S.rKakao : ''}`)}`, 'END:VCARD'].filter(Boolean).join('\r\n');
      saveBlob(new Blob([v + '\r\n'], { type: 'text/vcard' }), `${safe(S.rName || S.rPhone || '연락처')}.vcf`);
      status('연락처 파일(.vcf)을 저장했습니다. 구글 주소록(contacts.google.com) → 가져오기, 또는 카톡 나와의 채팅으로 폰에 보내 열면 연락처에 추가됩니다.', 'ok');
    }
  };
  const mailSubject = () => `[(주)영일] ${S.name} ${CATS[cat()]} 명함`;
  function makeQr(text) {
    try {
      qrcode.stringToBytes = qrcode.stringToBytesFuncs['UTF-8'];
      const q = qrcode(0, 'L'); q.addData(text, 'Byte'); q.make();
      if (q.getModuleCount() > 117) return null; // 버전 25 초과는 화면에서 잘 안 읽혀 줄임
      return q.createSvgTag({ cellSize: 4, margin: 4, scalable: true });
    } catch (e) { return null; }
  }

  // ---------- 솔라피 문자 자동 발송 (노트북 로컬 서버가 대신 호출, 키는 브라우저에 두지 않음) ----------
  const SOL = { configured: false, from: '' };
  async function api(path, body) {
    let r;
    try { r = await fetch(path, body ? { method: 'POST', headers: { 'Content-Type': 'application/json', 'X-Yeongil': '1' }, body: JSON.stringify(body) } : { headers: { 'X-Yeongil': '1' } }); }
    catch (e) { throw new Error('노트북 실행 프로그램(검은 창)과 연결이 끊겼습니다. 검은 창이 열려 있는지, 빨간 글씨가 있는지 확인하세요. (검은 창 안을 클릭했다면 Enter를 한 번 누르세요) 창이 닫혔다면 실행_윈도우.bat을 다시 실행하세요.'); }
    let j = null; try { j = await r.json(); } catch (e) {}
    if (!j) throw new Error(r.status === 404 ? '실행_윈도우.bat(최신 버전)로 열어야 문자 자동 발송을 쓸 수 있습니다.' : 'HTTP ' + r.status);
    return j;
  }
  const won = n => (n == null ? '-' : Number(n).toLocaleString('ko-KR') + '원');
  async function solRefresh(showBalance) {
    const st = $('solState'), go = $('solSend');
    if (isFile) { st.textContent = '파일 더블클릭 실행에서는 쓸 수 없습니다. 실행_윈도우.bat로 여세요.'; go.disabled = true; return; }
    try {
      const j = await api('api/sms/status');
      SOL.configured = !!j.configured; SOL.from = j.from || '';
      go.disabled = !SOL.configured;
      if (!SOL.configured) { st.innerHTML = '아직 설정 전입니다. <b>[설정]</b>에서 솔라피 API Key를 넣으세요.'; return; }
      st.textContent = `연결됨 · 발신번호 ${fmtPhone(SOL.from)} · 키 ${j.apiKey}`;
      if (showBalance) { const b = await api('api/sms/balance'); if (b.ok) st.textContent += ` · 잔액 ${won(b.balance)}${b.point ? ' / 포인트 ' + won(b.point) : ''}`; }
    } catch (e) { st.textContent = e.message; go.disabled = true; }
  }
  // MMS 이미지: JPG 200KB 이하 (솔라피 제한) → 여유 있게 190KB 이하로 줄임
  // 솔라피 MMS 이미지 제한: JPG, 200KB 이하, 가로 1500px · 세로 1440px 이하 (실제 오류 'TooHighResolution'로 확인)
  const MMS_MAX_W = 1500, MMS_MAX_H = 1440;
  async function mmsJpeg(src) {
    const k0 = Math.min(1, MMS_MAX_W / src.width, MMS_MAX_H / src.height);
    for (const f of [1, 0.85, 0.72, 0.6, 0.5, 0.42, 0.35]) {
      const k = k0 * f;
      const c = document.createElement('canvas'); c.width = Math.min(MMS_MAX_W, Math.floor(src.width * k)); c.height = Math.min(MMS_MAX_H, Math.floor(src.height * k));
      const x = c.getContext('2d'); x.fillStyle = '#fff'; x.fillRect(0, 0, c.width, c.height); x.imageSmoothingQuality = 'high'; x.drawImage(src, 0, 0, c.width, c.height);
      for (const q of [0.82, 0.7, 0.6]) { const b = await toBlob(c, 'image/jpeg', q); if (b.size <= 190 * 1024) return { blob: b, w: c.width, h: c.height }; }
    }
    throw new Error('이미지를 200KB 이하로 줄이지 못했습니다.');
  }
  const bytesOf = t => { let n = 0; for (const ch of t) n += ch.charCodeAt(0) > 127 ? 2 : 1; return n; };
  const MMS_WON = 110;
  let solPending = null;
  async function solOpenSend() {
    if (!SOL.configured) { openSolCfg(); return; }
    if (!needRecipient('phone')) return;
    status('문자용 이미지 준비 중…');
    const its = items(), list = [];
    for (const it of its) list.push({ ...it, img: await mmsJpeg(it.c) });
    solPending = list;
    $('solItems').innerHTML = `<div class="lbl">첫 번째 문자 글 (인사말·소개글·회사정보·내 연락처 — 여기서 마지막으로 고칠 수 있음)</div>
      <textarea class="edit" id="solText" rows="10"></textarea><div class="hint" id="solBytes"></div>
      <div class="lbl">보낼 명함 — <b>체크한 것만</b> 보냅니다</div>
      <div style="display:grid;grid-template-columns:repeat(${Math.min(3, list.length)},minmax(0,1fr));gap:10px">` +
      list.map((it, i) => `<label class="solck" style="display:flex;flex-direction:column;gap:6px;border:3px solid var(--accent);padding:6px;cursor:pointer;background:#fff1ee">
        <span class="row" style="gap:6px;font-size:13px;font-weight:800"><input type="checkbox" data-solck="${i}" checked style="accent-color:#ec3013;width:18px;height:18px">${esc(it.name)}</span>
        <img src="${URL.createObjectURL(it.img.blob)}" alt="" style="width:100%;max-height:34vh;object-fit:contain;background:var(--n200)">
        <span class="hint" data-solcap="${i}"></span></label>`).join('') + '</div>';
    $('solText').value = list[0].text; $('solText').oninput = solBytes;
    $('solItems').querySelectorAll('[data-solck]').forEach(cb => { cb.onchange = solBytes; });
    solBytes();
    $('solSendModal').classList.add('open'); status('');
  }
  function solChecked() { return (solPending || []).filter((it, i) => { const cb = document.querySelector(`[data-solck="${i}"]`); return !cb || cb.checked; }); }
  function solBytes() {
    const n = bytesOf($('solText').value), over = n > 2000, ck = solChecked(), cnt = ck.length;
    $('solBytes').innerHTML = `${n} / 2000 byte${over ? ' · <b style="color:#c9260e">너무 깁니다. 줄여야 보낼 수 있습니다.</b>' : ''}`;
    (solPending || []).forEach((it, i) => {
      const cb = document.querySelector(`[data-solck="${i}"]`), lab = cb && cb.closest('label'), cap = document.querySelector(`[data-solcap="${i}"]`);
      const pos = ck.indexOf(it);
      if (lab) { lab.style.borderColor = cb.checked ? 'var(--accent)' : 'var(--n300)'; lab.style.background = cb.checked ? '#fff1ee' : '#fff'; lab.querySelector('img').style.opacity = cb.checked ? '1' : '.35'; }
      if (cap) cap.textContent = pos < 0 ? '보내지 않음' : `문자 ${pos + 1}/${cnt}` + (pos === 0 ? ' · 위의 글과 함께' : ' · 짧은 설명만') + ` · ${Math.round(it.img.blob.size / 1024)}KB`;
    });
    $('solTo').textContent = `받는 사람: ${S.rName || ''}${S.rTitle ? ' ' + S.rTitle : ''} ${S.rPhone}${S.rCompany ? ' (' + S.rCompany + ')' : ''} · 문자 ${cnt}건`;
    $('solCost').textContent = cnt ? `사진 MMS ${cnt}건 × 약 ${MMS_WON}원 = 약 ${(cnt * MMS_WON).toLocaleString()}원 (VAT 별도, 솔라피 가격표 기준)` : '보낼 명함을 1개 이상 체크하세요.';
    $('solGo').disabled = over || !$('solText').value.trim() || !cnt;
    $('solGo').textContent = cnt ? `${cnt}건 보내기` : '보내기';
  }
  const b64Of = blob => new Promise(ok => { const r = new FileReader(); r.onload = () => ok(String(r.result).split(',')[1]); r.readAsDataURL(blob); });
  async function solGo() {
    if (!solPending) return;
    const go = $('solGo'); go.disabled = true; go.textContent = '보내는 중…';
    try {
      const subject = `[(주)영일] ${S.name} 명함`.slice(0, 20), list = [];
      const ck = solChecked(); if (!ck.length) { status('보낼 명함을 체크하세요.', 'err'); return; }
      for (const [i, it] of ck.entries()) list.push({ image: await b64Of(it.img.blob), text: i === 0 ? $('solText').value : caption(it.cats[0], i, ck.length), subject });
      const j = await api('api/sms/send', { to: digits(S.rPhone), items: list });
      if (j.ok) {
        $('solSendModal').classList.remove('open'); addLog(`문자 자동(솔라피) ${list.length}건`);
        status(`${S.rName || S.rPhone}님께 문자 ${j.count || list.length}건 접수 완료. 실제 도착은 솔라피 콘솔 > 메시지 내역에서 확인할 수 있습니다.`, 'ok');
        solRefresh(true);
      } else status('발송 실패: ' + j.error, 'err');
    } catch (e) { status('발송 실패: ' + e.message, 'err'); }
    finally { solBytes(); }
  }
  function openSolCfg() { $('solFrom').value = fmtPhone(SOL.from || S.phone); $('solKey').value = ''; $('solSecret').value = ''; $('solCfgModal').classList.add('open'); setTimeout(() => $('solKey').focus(), 50); }
  async function solSaveCfg() {
    const btn = $('solCfgSave'); btn.disabled = true; btn.textContent = '확인 중…';
    try {
      const j = await api('api/sms/config', { apiKey: $('solKey').value, apiSecret: $('solSecret').value, from: $('solFrom').value });
      if (j.ok) { $('solCfgModal').classList.remove('open'); $('solSecret').value = ''; status(`솔라피 연결 성공 · 잔액 ${won(j.balance)}`, 'ok'); solRefresh(true); }
      else status('연결 실패: ' + j.error + ' (Key/Secret을 다시 복사해 보세요)', 'err');
    } catch (e) { status(e.message, 'err'); }
    finally { btn.disabled = false; btn.textContent = '저장하고 연결 확인'; }
  }

  // ---------- 화면 그리기 ----------
  const esc = s => String(s == null ? '' : s).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  function inputsHtml(list, prefix) {
    return list.map(([k, label, ph, mode]) => `<label class="fld"><span>${label}</span><input id="${prefix}${k}" data-k="${k}" placeholder="${esc(ph)}" inputmode="${mode || 'text'}" autocomplete="off"></label>`).join('');
  }
  function syncInputs(root) { root.querySelectorAll('input[data-k]').forEach(i => { if (document.activeElement !== i) i.value = S[i.dataset.k] || ''; }); }
  function render() {
    if (!S.incl[S.cat || 'cabinet']) { const f = ORDER.find(x => S.incl[x]); if (f) S.cat = f; }
    const c = cat(), i = sel(), edits = (S.msgEdit || {})[c] || {};
    const selc = selectedCats();
    $('picks').innerHTML = ORDER.map(k => `<button data-pick="${k}" class="${S.incl[k] ? 'on' : ''}"><span class="ck">${S.incl[k] ? '✓' : ''}</span><span class="only" data-only="${k}" title="이 명함 하나만 보내기">이것만</span><img alt="" data-thumb="${k}" src="${thumbs[k] || ''}">${CATS[k]}</button>`).join('');
    $('pickSum').innerHTML = `보낼 명함 <b>${selc.length}개</b>: ${selc.map(k => CATS[k]).join(', ')}`;
    $('cats').innerHTML = selc.map(k => `<button data-cat="${k}" class="${k === c ? 'on' : ''}">${CATS[k]}</button>`).join('');
    $('cats').style.gridTemplateColumns = `repeat(${selc.length},minmax(0,1fr))`;
    $('optCardOnly').checked = !!S.cardOnly;
    const nIt = selc.length === 1 || S.sendMode === 'merged' ? 1 : selc.length;
    $('modeBox').innerHTML = selc.length > 1 ? `<span class="lbl">여러 장 보내는 방식</span><button class="mode ${S.sendMode !== 'merged' ? 'on' : ''}" data-mode="separate">명함별로 따로 ${selc.length}건 (선명, 권장)</button><button class="mode ${S.sendMode === 'merged' ? 'on' : ''}" data-mode="merged">한 장으로 합쳐 1건 (문자 크기 제한 때문에 글씨가 매우 작아짐, 비권장)</button>` : '';
    $('solSend').textContent = `📨 문자 자동 발송 · ${nIt}건 (약 ${(nIt * MMS_WON).toLocaleString()}원 + VAT)`;
    $('kImgs').innerHTML = Array.from({ length: nIt }, (_, i) => `<button class="btn red" data-kimg="${i}" style="min-height:32px">이미지 ${i + 1} 복사</button>`).join('');
    $('themes').innerHTML = THEMES.map(t => `<button data-theme="${t.id}" class="${t.id === S.theme ? 'on' : ''}" title="${esc(t.name)}"><div class="sw"><div style="flex:2;background:${t.banner}"></div><div style="height:4px;background:${t.hi}"></div><div style="flex:1;background:${t.body}"></div></div><b>${t.id}</b></button>`).join('');
    $('msgTitle').textContent = `소개글 · ${CATS[c]} (10개 + 직접 작성)`;
    $('msgs').innerHTML = MSGS[c].map((t, n) => `<button class="msg ${i === n ? 'on' : ''}" data-msg="${n}"><span class="dot"></span><span style="display:flex;flex-direction:column;gap:3px;min-width:0"><span class="tag">${n + 1}번${edits[n] != null ? ' · 수정됨' : ''}</span><span class="tx">${esc(msgText(c, n))}</span></span></button>`).join('') +
      `<button class="msg ${i === 99 ? 'on' : ''}" data-msg="99"><span class="dot"></span><span class="tag" style="font-size:13px;color:var(--ink)">직접 작성</span></button>`;
    $('btnEdit').style.display = i === 99 ? 'none' : '';
    $('btnEdit').textContent = S.editing ? '닫기' : `${i + 1}번 수정`;
    $('editBox').style.display = S.editing && i !== 99 ? 'flex' : 'none';
    $('editTitle').textContent = `${i + 1}번 소개글 수정 (이 노트북에 저장)`;
    if (document.activeElement !== $('editText')) $('editText').value = i === 99 ? '' : msgText(c, i);
    $('customText').style.display = i === 99 ? 'block' : 'none';
    if (document.activeElement !== $('customText')) $('customText').value = (S.msgCustom || {})[c] || '';
    syncInputs($('rMain')); syncInputs($('rFields')); syncInputs($('myFields'));
    const L = S.rLines || [];
    $('lines').style.display = L.length ? 'flex' : 'none';
    $('lines').innerHTML = L.length ? `<div class="lbl">인식된 글자 · 눌러서 칸에 채우기</div>` + L.map((t, n) => `<div class="ln"><span title="${esc(t)}">${esc(t)}</span><button data-line="${n}" data-to="rName">이름</button><button data-line="${n}" data-to="rCompany">회사</button><button data-line="${n}" data-to="rPhone">휴대폰</button><button data-line="${n}" data-to="rKakao">카톡</button></div>`).join('') : '';
    drawPreview();
  }
  let drawT; const thumbs = {}; let thumbKey = '';
  function drawPreview() {
    clearTimeout(drawT);
    drawT = setTimeout(() => {
      const box = $('preview'), its = items();
      box.innerHTML = '';
      its.forEach((it, i) => {
        const d = document.createElement('div'); d.className = 'bubble';
        d.innerHTML = `<div class="bt">문자 ${i + 1}${its.length > 1 ? '/' + its.length : ''} · ${esc(it.name)}</div>`;
        d.appendChild(it.c);
        const t = document.createElement('div'); t.className = 'tx'; t.textContent = it.text; d.appendChild(t);
        box.appendChild(d);
      });
      const cap = document.createElement('div'); cap.className = 'hint';
      cap.textContent = `${S.cardOnly ? '명함 부분만' : '홍보 사진 + 명함'} · 첫 문자에 인사말·소개글·회사정보·내 연락처가 들어갑니다.`;
      box.appendChild(cap);
      const key = S.theme + '|' + S.name + S.title + S.phone + S.email;
      if (key !== thumbKey) { // 고르기 칸 썸네일(명함 부분)
        thumbKey = key;
        ORDER.forEach(k => {
          const full = document.createElement('canvas'); draw(full, k);
          const { T, L } = geo(k), t = document.createElement('canvas'); t.width = 360; t.height = Math.round(360 * (T + 541) / (L + 1024));
          t.getContext('2d').drawImage(full, 0, 0, t.width, t.height); thumbs[k] = t.toDataURL('image/jpeg', 0.8);
          const im = document.querySelector(`[data-thumb="${k}"]`); if (im) im.src = thumbs[k];
        });
      }
    }, 60);
  }

  // ---------- 이벤트 ----------
  function bind() {
    $('rFields').innerHTML = inputsHtml(R_FIELDS.filter(f => f[0] !== 'rName' && f[0] !== 'rPhone'), 'r_');
    $('myFields').innerHTML = inputsHtml([['name', '이름', '홍길동'], ['title', '직책/부서', '이사 / 덕트진단 1팀'], ['phone', '휴대폰', '010-0000-0000', 'tel'], ['email', '이메일', 'name@hanmail.net', 'email']], 'm_');
    const onField = e => {
      const k = e.target.dataset.k; if (!k) return;
      let v = e.target.value;
      if (e.type === 'change' && ['rPhone', 'rTel', 'phone'].includes(k)) { v = fmtPhone(v); e.target.value = v; }
      S[k] = v; persist(); drawPreview();
    };
    ['rMain', 'rFields', 'myFields'].forEach(id => { $(id).addEventListener('input', onField); $(id).addEventListener('change', onField); });
    document.body.addEventListener('click', e => {
      const t = e.target.closest('[data-cat],[data-theme],[data-msg],[data-line]'); if (!t) return;
      if (t.dataset.cat) set({ cat: t.dataset.cat, editing: false });
      else if (t.dataset.theme) set({ theme: t.dataset.theme });
      else if (t.dataset.msg != null && t.classList.contains('msg')) set({ msgSel: { ...(S.msgSel || {}), [cat()]: +t.dataset.msg }, editing: false });
      else if (t.dataset.line != null) {
        const line = (S.rLines || [])[+t.dataset.line] || '', to = t.dataset.to;
        let v = line;
        if (to === 'rName') v = line.replace(/[^가-힣]/g, '').slice(0, 4);
        if (to === 'rPhone') v = fmtPhone(line);
        if (to === 'rKakao') v = (line.match(/[A-Za-z0-9][A-Za-z0-9._\-]{2,19}/g) || []).filter(x => !/^(id|kakao|talk)$/i.test(x)).pop() || line;
        set({ [to]: v });
      }
    });
    $('picks').addEventListener('click', e => {
      const o = e.target.closest('[data-only]');
      if (o) { const k = o.dataset.only; set({ incl: { [k]: true }, cat: k }); status(`${CATS[k]} 명함 하나만 보냅니다.`, 'ok'); return; }
      const b = e.target.closest('[data-pick]'); if (!b) return; const k = b.dataset.pick;
      const incl = { ...S.incl, [k]: !S.incl[k] };
      if (!ORDER.some(x => incl[x])) { status('명함은 최소 1개는 골라야 합니다.'); return; }
      const patch = { incl };
      if (incl[k] && !S.incl[k] && !ORDER.some(x => S.incl[x] && x === cat())) patch.cat = k;
      if (!incl[cat()]) patch.cat = ORDER.find(x => incl[x]);
      set(patch);
    });
    $('modeBox').addEventListener('click', e => { const b = e.target.closest('[data-mode]'); if (b) set({ sendMode: b.dataset.mode }); });
    $('kImgs').addEventListener('click', e => { const b = e.target.closest('[data-kimg]'); if (b) actions.kImg(+b.dataset.kimg); });
    $('optCardOnly').onchange = e => set({ cardOnly: e.target.checked });
    $('btnEdit').onclick = () => set({ editing: !S.editing });
    $('btnEditDone').onclick = () => set({ editing: false });
    $('editText').oninput = e => { const all = { ...(S.msgEdit || {}) }, mine = { ...(all[cat()] || {}) }; mine[sel()] = e.target.value; all[cat()] = mine; S.msgEdit = all; persist(); updateMsgsOnly(); };
    $('btnEditRevert').onclick = () => { const all = { ...(S.msgEdit || {}) }, mine = { ...(all[cat()] || {}) }; delete mine[sel()]; all[cat()] = mine; set({ msgEdit: all }); status('원래 문구로 되돌렸습니다.'); };
    $('customText').oninput = e => { S.msgCustom = { ...(S.msgCustom || {}), [cat()]: e.target.value }; persist(); drawPreview(); };
    // 스캔 입력
    $('btnCam').onclick = () => openCam();
    $('camSel').onchange = e => openCam(e.target.value);
    $('camClose').onclick = stopCam; $('camShot').onclick = shoot;
    window.addEventListener('resize', placeGuide);
    $('fileIn').onchange = e => { const f = e.target.files && e.target.files[0]; e.target.value = ''; if (f) scanBlob(f); };
    const drop = $('drop');
    ['dragenter', 'dragover'].forEach(ev => document.addEventListener(ev, e => { e.preventDefault(); drop.classList.add('over'); }));
    ['dragleave', 'drop'].forEach(ev => document.addEventListener(ev, e => { e.preventDefault(); if (ev === 'drop' || e.target === document.documentElement) drop.classList.remove('over'); }));
    document.addEventListener('drop', e => {
      const f = [...(e.dataTransfer.files || [])].find(x => /^image\//.test(x.type));
      if (f) { scanBlob(f); return; }
      const t = e.dataTransfer.getData('text'); if (t) fillFromText(t);
    });
    document.addEventListener('paste', e => {
      const items = [...((e.clipboardData && e.clipboardData.items) || [])];
      const img = items.find(it => it.kind === 'file' && /^image\//.test(it.type));
      if (img) { e.preventDefault(); scanBlob(img.getAsFile()); return; }
      const a = document.activeElement;
      if (a && (a.tagName === 'INPUT' || a.tagName === 'TEXTAREA')) return; // 입력칸에는 그대로 붙여넣기
      const t = e.clipboardData && e.clipboardData.getData('text'); if (t && t.trim()) { e.preventDefault(); $('pasteBox').value = t; fillFromText(t); }
    });
    document.addEventListener('keydown', e => {
      if ($('camModal').classList.contains('open')) { if (e.code === 'Space') { e.preventDefault(); shoot(); } if (e.key === 'Escape') stopCam(); }
      else if (e.key === 'Escape') ['qrModal', 'solCfgModal', 'solSendModal'].forEach(id => $(id).classList.remove('open'));
    });
    $('btnRotate').onclick = rotateScan; $('btnRescan').onclick = () => scanSrc && scanCanvas(scanSrc);
    $('btnPaste').onclick = async () => {
      try { const t = await navigator.clipboard.readText(); if (t && t.trim()) { $('pasteBox').value = t; if (fillFromText(t)) return; } } catch (e) {}
      $('pasteBox').style.display = 'block'; $('pasteBox').focus(); status('아래 칸에 Ctrl+V로 붙여넣으세요.');
    };
    $('pasteBox').oninput = e => { if (e.target.value.trim().length > 6) fillFromText(e.target.value); };
    $('btnClearR').onclick = () => { const o = {}; PARSE_KEYS.forEach(k => { o[k] = ''; }); o.rLines = []; set(o); $('scanThumb').style.display = 'none'; $('thumbTools').style.display = 'none'; $('ocrBar').style.display = 'none'; $('ocrText').textContent = ''; $('pasteBox').value = ''; scanSrc = null; };
    // 보내기
    const A = { btnCopyText: 'copyTxt', btnSave: 'save', btnShare: 'share', kCopyId: 'kId', kCopyPhone: 'kPhone', kText: 'kText', sPhoneLink: 'phoneLink', sQr: 'qr', mGmail: 'gmail', mMailto: 'mailto', btnVcf: 'vcf' };
    Object.entries(A).forEach(([id, a]) => {
      $(id).onclick = () => {
        try { const p = actions[a](); if (p && p.catch) p.catch(err => { console.error(err); status('오류: ' + err.message, 'err'); }); }
        catch (err) { console.error(err); status('오류: ' + err.message, 'err'); }
      };
    });
    $('qrClose').onclick = () => $('qrModal').classList.remove('open');
    $('solSetBtn').onclick = openSolCfg; $('solCfgClose').onclick = () => $('solCfgModal').classList.remove('open');
    $('solCfgSave').onclick = solSaveCfg;
    $('solCfgReset').onclick = async () => { if (!confirm('이 노트북에 저장된 솔라피 키를 삭제할까요?')) return; try { await api('api/sms/reset', {}); } catch (e) {} $('solCfgModal').classList.remove('open'); solRefresh(); status('솔라피 설정을 삭제했습니다.'); };
    $('solSend').onclick = () => solOpenSend().catch(e => status(e.message, 'err'));
    $('solSendClose').onclick = () => $('solSendModal').classList.remove('open');
    $('solGo').onclick = solGo;
    $('btnLogCsv').onclick = logCsv;
    $('btnLogClear').onclick = () => { if (confirm('보낸 기록을 모두 지울까요? (먼저 CSV로 저장해 두세요)')) { localStorage.removeItem(LOGKEY); renderLog(); } };
    // 백업 (휴대폰 VER2.0 백업 파일과 호환)
    $('btnBackup').onclick = () => { const o = { ...S }; VOLATILE.forEach(k => delete o[k]); saveBlob(new Blob([JSON.stringify({ app: '영업도우미', version: '3.0-laptop', exportedAt: new Date().toISOString(), data: o, log: getLog() }, null, 2)], { type: 'application/json' }), `영업도우미_노트북백업_${stamp()}.json`); status('백업 파일을 저장했습니다.', 'ok'); };
    $('backupIn').onchange = e => {
      const f = e.target.files && e.target.files[0]; e.target.value = ''; if (!f) return;
      const r = new FileReader();
      r.onload = () => {
        try {
          const j = JSON.parse(r.result); if (!j || j.app !== '영업도우미' || typeof j.data !== 'object') throw new Error('bad');
          const d = { ...j.data }; [...VOLATILE, 'status', 'ocrBusy', 'ocrP', 'canInstall', 'standalone', 'pasteOpen', 'pasteText', 'schema'].forEach(k => delete d[k]);
          if (Array.isArray(j.log)) { try { localStorage.setItem(LOGKEY, JSON.stringify(j.log)); } catch (e) {} renderLog(); }
          set({ ...DEF, incl: { [d.cat || 'cabinet']: true }, sendMode: 'separate', cardOnly: false, ...d });
          status(`백업을 복원했습니다 (${(j.exportedAt || '').slice(0, 10)}).`, 'ok');
        } catch (err) { status('영업도우미 백업 파일이 아닙니다.', 'err'); }
      };
      r.readAsText(f);
    };
    $('btnReset').onclick = () => { if (confirm('내 정보(이름·직책·휴대폰·이메일)를 기본값으로 되돌릴까요?')) set({ name: DEF.name, title: DEF.title, phone: DEF.phone, email: DEF.email }); };
  }
  function updateMsgsOnly() {
    const c = cat(), i = sel(), el = $('msgs').querySelector(`[data-msg="${i}"] .tx`), tg = $('msgs').querySelector(`[data-msg="${i}"] .tag`);
    if (el) el.textContent = msgText(c, i); if (tg) tg.textContent = `${i + 1}번 · 수정됨`;
    drawPreview();
  }

  // ---------- 시작 ----------
  async function start() {
    if (isFile) $('fileWarn').style.display = 'block';
    $('modeText').innerHTML = isFile ? '실행: 파일 직접 열기<br>(사진 인식 불가)' : '실행: 노트북 로컬 서버<br>사진 인식 가능 · 인터넷 불필요';
    bind(); renderLog(); solRefresh(true);
    await Promise.all([loadImg('card'), loadImg('tab'), loadImg('face')]);
    try { await Promise.all(['500', '600', '700', '800'].map(w => document.fonts.load(`${w} 40px Pretendard`, '가나다'))); } catch (e) {}
    render();
    window.__app = { S: () => S, parseCard, fullText, outputCanvases, items, mmsJpeg, scanCanvas, scanBlob, set }; // 점검용
  }
  start();
})();
