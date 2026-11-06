#!/bin/bash
# 영업도우미 노트북용 - 맥 실행 (python3 필요, 문자 자동 발송 포함)
cd "$(dirname "$0")"
PORT=8765
while lsof -iTCP:$PORT -sTCP:LISTEN >/dev/null 2>&1; do PORT=$((PORT+1)); done
echo "영업도우미 노트북용 실행 중: http://localhost:$PORT/  (이 창을 닫으면 멈춥니다)"
(sleep 1; open "http://localhost:$PORT/") &
NO_BROWSER=1 exec python3 server.py $PORT
