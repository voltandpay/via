"""PT 표지·영상 썸네일용 히어로 컷(1920x1080). Blender 렌더(투명 PNG)들을 배치하고 글자를 얹는다.
python3 tools/compose_hero.py <렌더 폴더> <symbol.png> <asta_sans.ttf> <out.png>
렌더 폴더: hero_medal_chaevi / hero_medal_water / hero_medal_skel / hero_coin / hero_chest / hero_emblem .png"""
import os, sys
from PIL import Image, ImageDraw, ImageFilter, ImageFont

src, symbol_path, font_path, out = sys.argv[1:]
W, H = 1920, 1080

# 배경: 위는 옅은 파랑, 아래는 Pleos 밝은 배경(#F7F8FA)
bg = Image.new("RGBA", (W, H))
top, bottom = (232, 240, 255), (247, 248, 250)
for y in range(H):
    t = y / (H - 1)
    ImageDraw.Draw(bg).line([(0, y), (W, y)], fill=tuple(int(top[i] + (bottom[i] - top[i]) * t) for i in range(3)) + (255,))

def load(path, width):
    im = Image.open(path).convert("RGBA")
    im = im.crop(im.split()[3].point(lambda v: 255 if v > 2 else 0).getbbox())
    return im.resize((width, round(im.height * width / im.width)), Image.LANCZOS)

def place(im, cx, cy, shadow=0.22, blur=28, drop=24):
    """가운데 좌표에 놓고, 아래로 번진 그림자를 먼저 깐다."""
    x, y = cx - im.width // 2, cy - im.height // 2
    sh = Image.new("RGBA", im.size, (20, 30, 60, 0))
    sh.putalpha(im.split()[3].point(lambda v: int(v * shadow)))
    pad = blur * 3
    canvas = Image.new("RGBA", (im.width + pad * 2, im.height + pad * 2))
    canvas.alpha_composite(sh, (pad, pad))
    canvas = canvas.filter(ImageFilter.GaussianBlur(blur))
    bg.alpha_composite(canvas, (x - pad, y - pad + drop))
    bg.alpha_composite(im, (x, y))

# 오른쪽: 심볼을 가운데 두고 보상 소품을 둘레에(뒤에서 앞 순서)
cx, cy = 1360, 540
place(load(os.path.join(src, "hero_medal_skel.png"), 210), cx + 330, cy - 300, shadow=0.15)
place(load(os.path.join(src, "hero_emblem.png"), 190), cx + 400, cy + 40, shadow=0.15)
place(load(os.path.join(src, "hero_medal_water.png"), 190), cx - 290, cy + 330, shadow=0.15)
place(load(symbol_path, 560), cx, cy - 10, shadow=0.28, blur=36, drop=36)
place(load(os.path.join(src, "hero_medal_chaevi.png"), 250), cx - 330, cy - 230)
place(load(os.path.join(src, "hero_chest.png"), 300), cx + 230, cy + 300)
place(load(os.path.join(src, "hero_coin.png"), 130), cx - 40, cy + 400)

# 왼쪽: 이름과 한 줄 소개(Asta Sans 가변 굵기)
def font(px, weight):
    f = ImageFont.truetype(font_path, px)
    try: f.set_variation_by_axes([weight])
    except Exception: pass
    return f
d = ImageDraw.Draw(bg)
x0 = 140
d.text((x0, 300), "VIA", font=font(230, 900), fill=(19, 20, 23))
d.text((x0 + 8, 560), "가는 길에, 가장 싸게,", font=font(64, 700), fill=(19, 20, 23))
d.text((x0 + 8, 642), "차가 확인한 보상", font=font(64, 700), fill=(0, 100, 255))
d.text((x0 + 8, 760), "경로 위 최저가 충전소 추천 · 음성으로 경유지 추가 · 충전소 도감", font=font(30, 500), fill=(84, 88, 96))
d.text((x0 + 8, 940), "볼트앤페이 · Pleos Connect 차량용 앱", font=font(28, 600), fill=(120, 124, 132))

bg.convert("RGB").save(out, quality=95)
print("saved", out)
