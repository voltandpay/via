"""Blender 렌더(PNG, 투명 배경)를 메달에 맞춰 정사각형으로 자르고 320px WebP로 저장한다.
python3 tools/medal_to_webp.py <in.png>... <out_dir>"""
import os, sys
from PIL import Image

*inputs, out_dir = sys.argv[1:]
for path in inputs:
    im = Image.open(path)
    x0, y0, x1, y1 = im.split()[3].point(lambda v: 255 if v > 2 else 0).getbbox()
    s = max(x1 - x0, y1 - y0) + 12
    c = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    c.paste(im.crop((x0, y0, x1, y1)), ((s - (x1 - x0)) // 2, (s - (y1 - y0)) // 2))
    name = os.path.splitext(os.path.basename(path))[0]
    c.resize((320, 320), Image.LANCZOS).save(os.path.join(out_dir, name + ".webp"), "WEBP", quality=90, method=6)
    print(name)
