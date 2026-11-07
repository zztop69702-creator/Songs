"""src/ 의 install.ps1, app.html, app.ico 를 base64 로 묶어 설치용 .bat 과 .zip 을 만든다.

사용법: python build.py [출력폴더]   (기본: dist)
원본 v1.7 설치 파일과 같은 형식(CRLF, 한 줄 base64 블록)으로 만든다.
"""
import base64
import pathlib
import sys
import zipfile

NAME, VER = 'AI다음행보', 'v1.7'
HERE = pathlib.Path(__file__).resolve().parent
SRC = HERE / 'src'

HEADER = [
    '@echo off',
    'set "SELF=%~f0"',
    'powershell -NoProfile -ExecutionPolicy Bypass -Command "$t=[IO.File]::ReadAllText($env:SELF);'
    "$m=[regex]::Match($t,'__SCRIPT__\\r?\\n([A-Za-z0-9+/=]+)');"
    'iex ([Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($m.Groups[1].Value)))"',
    'if errorlevel 1 pause',
    'exit /b',
]


def b64(name):
    return base64.b64encode((SRC / name).read_bytes()).decode('ascii')


def build_bat():
    lines = HEADER + ['__SCRIPT__', b64('install.ps1'), '__HTML__', b64('app.html'), '__ICO__', b64('app.ico')]
    return ('\r\n'.join(lines) + '\r\n').encode('ascii')


def main():
    out = pathlib.Path(sys.argv[1]) if len(sys.argv) > 1 else HERE / 'dist'
    out.mkdir(parents=True, exist_ok=True)
    bat_name = f'{NAME}_{VER}_설치.bat'
    data = build_bat()
    (out / bat_name).write_bytes(data)
    with zipfile.ZipFile(out / f'{NAME}_{VER}_설치.zip', 'w', zipfile.ZIP_DEFLATED) as z:
        z.writestr(zipfile.ZipInfo(bat_name, date_time=(2026, 11, 7, 0, 0, 0)), data, zipfile.ZIP_DEFLATED)
    print(out / bat_name, len(data), 'bytes')


if __name__ == '__main__':
    main()
