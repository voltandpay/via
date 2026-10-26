"""애니메이션 장면 PNG들을 같은 영역으로 잘라(장면마다 흔들리지 않게) 320px WebP로 저장한다.
python3 tools/frames_to_webp.py <out_prefix> <frame0.png> <frame1.png> ... <out_dir>
→ <out_dir>/<out_prefix>0.webp, <out_prefix>1.webp, ..."""
import os, sys
from PIL import Image

prefix, *frames, out_dir = sys.argv[1:]
ims = [Image.open(f) for f in frames]
boxes = [im.split()[3].point(lambda v: 255 if v > 2 else 0).getbbox() for im in ims]
x0, y0 = min(b[0] for b in boxes), min(b[1] for b in boxes)
x1, y1 = max(b[2] for b in boxes), max(b[3] for b in boxes)
s = max(x1 - x0, y1 - y0) + 12
for i, im in enumerate(ims):
    c = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    c.paste(im.crop((x0, y0, x1, y1)), ((s - (x1 - x0)) // 2, (s - (y1 - y0)) // 2))
    c.resize((320, 320), Image.LANCZOS).save(os.path.join(out_dir, f"{prefix}{i}.webp"), "WEBP", quality=90, method=6)
    print(f"{prefix}{i}")
