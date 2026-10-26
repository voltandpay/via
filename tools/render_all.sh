#!/usr/bin/env bash
# VIA 3D 이미지 전체를 다시 렌더링하고 앱 리소스·문서 그림으로 바꾼다.
# 사용: BLENDER=/path/to/Blender bash tools/render_all.sh [작업 폴더]   (렌더 수십 장, 병렬 3개)
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
WORK="${1:-$ROOT/build/render}"
B="${BLENDER:-blender}"
F="$ROOT/app/src/main/res/font/asta_sans.ttf"
RES="$ROOT/app/src/main/res/drawable-nodpi"
mkdir -p "$WORK"; cd "$WORK"

# 사업자: id 색 문양(tools/blender_style.py SHAPES). 글자 대신 모양으로 가른다(같은 계열 색이 많다)
OPS="me #12A5C9 @leaf
kepco #13408C @plug
skel #E2231A @hexagon
chaevi #00A06A @bolt
evsis #6A3FD8 @sparkle
gscv #00857C @triangle
everon #6DB33F @plus
water #2D7FF9 @drop
pluglink #F07B1E @chevron"

jobs="$WORK/jobs.txt"; : > "$jobs"
m() { echo "medal|$*" >> "$jobs"; }
p() { echo "props|$*" >> "$jobs"; }
while read -r id col g; do
  for t in bronze silver gold; do m "medal_${id}_$t.png|$col|$g|$t"; done
  p "card_$id.png|card|$col"
done <<< "$OPS"
p "card_epit.png|card|#1E2A5A"
m "medal_locked.png|#00A06A|?|locked"
m "medal_chaevi_event.png|#00A06A|@bolt|event"
m "medal_skel_event.png|#E2231A|@hexagon|event"
m "medal_season.png|#1E2A5A|@star|gold"
for i in 0 1 2 3 4 5 6 7; do m "spin_$i.png|#00A06A|@bolt|bronze|--spin|$((i * 45))"; done
p "prop_coin.png|coin"; p "prop_chest_closed.png|chest|closed"; p "prop_symbol.png|symbol"; p "prop_moon.png|moon"
i=0; for t in 0 0.15 0.35 0.6 0.85 1; do p "chest_f$i.png|chest|open|$t"; i=$((i + 1)); done
for r in "downtown @tower" "gangnam @city" "gyeonggi @mountain"; do set -- $r; p "emblem_$1_gold.png|emblem|$2|gold"; p "emblem_$1_locked.png|emblem|$2|locked"; done
p "ticket_kepco.png|ticket|#13408C|1,000원"; p "ticket_chaevi.png|ticket|#00A06A|2,000원"; p "ticket_water.png|ticket|#2D7FF9|3,000원"
p "shelf.png|shelf|--size|1600|--aspect|0.3"; p "shelf_dark.png|shelf|dark|--size|1600|--aspect|0.3"
m "hero_medal_chaevi.png|#00A06A|@bolt|gold|--size|1024"; m "hero_medal_water.png|#2D7FF9|@drop|silver|--size|1024"; m "hero_medal_skel.png|#E2231A|@hexagon|event|--size|1024"
p "hero_coin.png|coin|--size|1024"; p "hero_chest.png|chest|open|1|--size|1024"; p "hero_emblem.png|emblem|@city|gold|--size|1024"
p "symbol_4096.png|symbol|--size|4096"

render() {
  IFS='|' read -r kind out rest <<< "$1"
  IFS='|' read -r -a args <<< "$rest"
  script="$ROOT/tools/blender_$kind.py"
  "$B" -b -P "$script" -- "$F" "$WORK/$out" "${args[@]}" > "$WORK/$out.log" 2>&1 && echo "OK $out" || echo "FAIL $out"
}
export -f render; export B F ROOT WORK
xargs -P 3 -I{} bash -c 'render "$@"' _ {} < "$jobs"
grep -l Traceback "$WORK"/*.log && { echo "렌더 실패가 있다"; exit 1; } || true

# 앱 리소스로 변환
python3 "$ROOT/tools/medal_to_webp.py" medal_*.png card_*.png prop_coin.png prop_chest_closed.png prop_symbol.png prop_moon.png emblem_*.png "$RES"
python3 "$ROOT/tools/frames_to_webp.py" prop_chest_f chest_f0.png chest_f1.png chest_f2.png chest_f3.png chest_f4.png chest_f5.png "$RES"
python3 "$ROOT/tools/frames_to_webp.py" stamp_spin_chaevi_ spin_0.png spin_1.png spin_2.png spin_3.png spin_4.png spin_5.png spin_6.png spin_7.png "$RES"
python3 - "$WORK" "$RES" "$ROOT" <<'PY'
import sys
from PIL import Image
work, res, root = sys.argv[1:]
def tight(path, pad=0):
    im = Image.open(path); b = im.split()[3].point(lambda v: 255 if v > 2 else 0).getbbox()
    return im.crop((b[0] - pad, b[1] - pad, b[2] + pad, b[3] + pad))
for n in ["kepco", "chaevi", "water"]:   # 티켓: 가로로 긴 비율 그대로
    im = tight(f"{work}/ticket_{n}.png", 6); im = im.resize((360, round(im.height * 360 / im.width)), Image.LANCZOS)
    im.save(f"{res}/ticket_{n}.webp", "WEBP", quality=90, method=6)
for src, dst in [("shelf", "prop_shelf"), ("shelf_dark", "prop_shelf_dark")]:   # 선반: 판 윗면이 그림 맨 위
    im = Image.open(f"{work}/{src}.png"); b = im.split()[3].point(lambda v: 255 if v > 2 else 0).getbbox()
    im = im.crop((b[0], b[1], b[2], b[3] + 4)); im = im.resize((1400, round(im.height * 1400 / im.width)), Image.LANCZOS)
    im.save(f"{res}/{dst}.webp", "WEBP", quality=90, method=6)
im = tight(f"{work}/symbol_4096.png"); s = int(max(im.size) * 1.08)   # PT 표지용 심볼
c = Image.new("RGBA", (s, s), (0, 0, 0, 0)); c.paste(im, ((s - im.width) // 2, (s - im.height) // 2))
c.resize((2048, 2048), Image.LANCZOS).save(f"{root}/doc/assets/via_symbol_2048.png", optimize=True)
PY
python3 "$ROOT/tools/compose_hero.py" "$WORK" "$WORK/symbol_4096.png" "$F" "$ROOT/doc/assets/via_hero_1920x1080.png"
echo "done"
