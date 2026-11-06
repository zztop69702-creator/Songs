#!/usr/bin/env python3
"""영업도우미 노트북용 - 로컬 서버 (맥/리눅스용, server.ps1과 같은 기능)
이 노트북(127.0.0.1)에서만 접속됩니다. 솔라피 키는 solapi.local.json(권한 600)에 저장됩니다."""
import hashlib, hmac, http.server, json, os, re, secrets, socketserver, string, sys, urllib.error, urllib.request, webbrowser
from datetime import datetime

ROOT = os.path.dirname(os.path.abspath(__file__))
CFG = os.path.join(ROOT, 'solapi.local.json')
BASE = os.environ.get('SOLAPI_BASE', 'https://api.solapi.com')  # 시험용으로만 바꿈
CHARS = string.digits + string.ascii_lowercase + string.ascii_uppercase


def load_cfg():
    try:
        with open(CFG, encoding='utf-8') as f:
            c = json.load(f)
        return c if c.get('apiKey') and c.get('apiSecret') else None
    except Exception:
        return None


def save_cfg(c):
    fd = os.open(CFG, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
    with os.fdopen(fd, 'w', encoding='utf-8') as f:
        json.dump(c, f, ensure_ascii=False)


def auth(c):
    date = datetime.now().astimezone().isoformat(timespec='seconds')
    salt = ''.join(secrets.choice(CHARS) for _ in range(32))
    sig = hmac.new(c['apiSecret'].encode(), (date + salt).encode(), hashlib.sha256).hexdigest()
    return f"HMAC-SHA256 apiKey={c['apiKey']}, date={date}, salt={salt}, signature={sig}"


def sol(c, method, path, body=None):
    data = json.dumps(body, ensure_ascii=False).encode('utf-8') if body is not None else None
    req = urllib.request.Request(f'{BASE}/{path}', data=data, method=method, headers={'Authorization': auth(c), 'Content-Type': 'application/json'})
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            return r.status, json.loads(r.read().decode('utf-8') or 'null')
    except urllib.error.HTTPError as e:
        t = e.read().decode('utf-8', 'replace')
        try:
            return e.code, json.loads(t)
        except Exception:
            return e.code, {'errorMessage': t}
    except Exception as e:
        return 0, {'errorMessage': f'솔라피 서버에 연결하지 못했습니다 (인터넷 확인): {e}'}


def err(st, b):
    b = b or {}
    return f"{b.get('errorCode', 'HTTP ' + str(st))}: {b.get('errorMessage', '')}"


def api(path, body):
    if path == 'api/sms/status':
        c = load_cfg()
        if not c:
            return {'ok': True, 'configured': False, 'server': 'python'}
        return {'ok': True, 'configured': True, 'server': 'python', 'from': c['from'], 'apiKey': c['apiKey'][:4] + '****'}
    if path == 'api/sms/config':
        c = {'apiKey': str(body.get('apiKey', '')).strip(), 'apiSecret': str(body.get('apiSecret', '')).strip(), 'from': re.sub(r'\D', '', str(body.get('from', '')))}
        if not all(c.values()):
            return {'ok': False, 'error': 'API Key, API Secret, 발신번호를 모두 넣으세요.'}
        st, b = sol(c, 'GET', 'cash/v1/balance')
        if st != 200:
            return {'ok': False, 'error': err(st, b)}
        save_cfg(c)
        return {'ok': True, 'balance': b.get('balance'), 'point': b.get('point')}
    if path == 'api/sms/balance':
        c = load_cfg()
        if not c:
            return {'ok': False, 'error': 'not configured'}
        st, b = sol(c, 'GET', 'cash/v1/balance')
        return {'ok': True, 'balance': b.get('balance'), 'point': b.get('point')} if st == 200 else {'ok': False, 'error': err(st, b)}
    if path == 'api/sms/send':
        c = load_cfg()
        if not c:
            return {'ok': False, 'error': '문자 설정이 없습니다.'}
        to = re.sub(r'\D', '', str(body.get('to', '')))
        if len(to) < 10:
            return {'ok': False, 'error': '받는 번호가 올바르지 않습니다.'}
        its = body.get('items') or [{'text': body.get('text', ''), 'image': body.get('image'), 'subject': body.get('subject')}]
        if not 1 <= len(its) <= 5:
            return {'ok': False, 'error': '한 번에 1~5건만 보낼 수 있습니다.'}
        msgs = []
        for it in its:
            msg = {'to': to, 'from': c['from'], 'text': str(it.get('text') or '')}
            if it.get('image'):
                st, b = sol(c, 'POST', 'storage/v1/files', {'file': it['image'], 'type': 'MMS', 'name': 'card.jpg'})
                if st != 200 or not (b or {}).get('fileId'):
                    return {'ok': False, 'error': '이미지 업로드 실패 - ' + err(st, b)}
                msg.update(imageId=b['fileId'], type='MMS')
                if it.get('subject'):
                    msg['subject'] = str(it['subject'])
            msgs.append(msg)
        st, b = sol(c, 'POST', 'messages/v4/send-many/fast', {'messages': msgs, 'allowDuplicates': True})
        if st != 200:
            return {'ok': False, 'error': err(st, b)}
        failed = (b or {}).get('failedMessageList') or []
        if failed:
            return {'ok': False, 'error': f"{len(msgs)}건 중 {len(failed)}건 접수 실패: {failed[0].get('statusCode')} {failed[0].get('statusMessage')}"}
        return {'ok': True, 'count': len(msgs), 'groupId': ((b or {}).get('groupInfo') or {}).get('groupId')}
    if path == 'api/sms/reset':
        if os.path.exists(CFG):
            os.remove(CFG)
        return {'ok': True}
    return {'ok': False, 'error': 'unknown api'}


class H(http.server.SimpleHTTPRequestHandler):
    def __init__(self, *a, **k):
        super().__init__(*a, directory=ROOT, **k)

    def log_message(self, *a):
        pass

    def _json(self, code, o):
        d = json.dumps(o, ensure_ascii=False).encode('utf-8')
        self.send_response(code)
        self.send_header('Content-Type', 'application/json; charset=utf-8')
        self.send_header('Content-Length', str(len(d)))
        self.end_headers()
        self.wfile.write(d)

    def _api(self, method):
        path = self.path.split('?')[0].lstrip('/')
        if method != 'GET':
            o = self.headers.get('Origin')
            # 다른 웹사이트가 이 서버로 몰래 문자 발송을 요청하지 못하게 막음
            if self.headers.get('X-Yeongil') != '1' or (o and not re.match(r'^http://(localhost|127\.0\.0\.1):\d+$', o)):
                return self._json(403, {'ok': False, 'error': 'forbidden'})
        n = int(self.headers.get('Content-Length') or 0)
        body = json.loads(self.rfile.read(n).decode('utf-8')) if n else {}
        try:
            out = api(path, body or {})
        except Exception as e:
            out = {'ok': False, 'error': f'서버 오류: {e}'}
        if path == 'api/sms/send':
            print(f"  [{datetime.now():%H:%M:%S}] 문자 발송 {'접수 성공' if out.get('ok') else '실패: ' + str(out.get('error'))}", flush=True)
        self._json(200, out)

    def do_GET(self):
        p = self.path.split('?')[0]
        if p.startswith('/api/'):
            return self._api('GET')
        if p.endswith('.local.json'):
            return self.send_error(404)
        return super().do_GET()

    def do_POST(self):
        if self.path.startswith('/api/'):
            return self._api('POST')
        self.send_error(404)

    def end_headers(self):
        self.send_header('Cache-Control', 'no-cache')
        super().end_headers()


H.extensions_map.update({'.wasm': 'application/wasm', '.js': 'text/javascript', '.woff2': 'font/woff2', '.gz': 'application/octet-stream'})


socketserver.ThreadingTCPServer.allow_reuse_address = True
socketserver.ThreadingTCPServer.daemon_threads = True


def main():
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8765
    for p in range(port, port + 20):
        try:
            srv = socketserver.ThreadingTCPServer(('127.0.0.1', p), H)
            break
        except OSError:
            continue
    else:
        sys.exit('서버를 시작하지 못했습니다.')
    url = f'http://localhost:{p}/'
    print(f'\n  영업도우미 노트북용 실행 중\n  주소: {url}\n  ※ 이 창을 닫으면 앱이 멈춥니다.\n', flush=True)
    if os.environ.get('NO_BROWSER') != '1':
        webbrowser.open(url)
    srv.serve_forever()


if __name__ == '__main__':
    main()
