#!/bin/sh
# Pleos Virtual Console HTTP 조작 (시연·테스트용). 연결이 잠깐 끊기면(emulator_inactive) 다시 시도한다.
# 콘솔 웹 페이지(http://127.0.0.1:8088)가 브라우저에 열려 있어야 동작한다 — 닫히면 계속 emulator_inactive.
# 사용: tools/vc.sh gear Drive | soc 15 | charge on|off | port open|closed | odo 123
V=${VC_URL:-http://127.0.0.1:8088/vehicle}
case "$1" in
  gear)   url="$V/gear?gear=$2" ;;
  soc)    url="$V/ev-battery?field=soc&value=$2" ;;
  charge) [ "$2" = on ] && v=true || v=false; url="$V/ev-battery?field=charge_switch&value=$v" ;;
  port)   [ "$2" = open ] && v=true || v=false; url="$V/ev-battery?field=charge_port_open&value=$v" ;;
  odo)    url="$V/odometer?traveled_distance_km=$2" ;;
  *) echo "usage: $0 gear|soc|charge|port|odo VALUE" >&2; exit 2 ;;
esac
i=0
while [ $i -lt 40 ]; do
  code=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$url")
  [ "$code" = 200 ] && { echo "ok $1 $2"; exit 0; }
  i=$((i + 1)); sleep 3
done
echo "fail $1 $2 (http $code)" >&2; exit 1
