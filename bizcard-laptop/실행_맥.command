#!/bin/bash
# 영업도우미 노트북용 - 맥 실행 (python3 필요, 문자 자동 발송 포함)
cd "$(dirname "$0")"
exec python3 server.py 8765
