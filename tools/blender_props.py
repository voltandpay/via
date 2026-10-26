# VIA 3D 소품: blender -b -P blender_props.py -- <font.ttf> <out.png> <종류> [옵션...] [--size N]
#   coin                       포인트 코인(P)
#   chest closed|open [t]      미션 상자. t(0~1)는 열린 정도(애니메이션 장면용, 기본 1)
#   emblem <글자|@문양> locked|gold  지역 엠블럼(방패)
#   symbol                     VIA 심볼(경로 + 번개 경유지)
#   moon                       경부하 부스트(달 + 번개)
#   card <hex색> [글자]          충전 멤버십 카드(사업자 색). 글자를 빼면 칩·번호 줄만
#   ticket <hex색> [금액]        제휴 할인 쿠폰 티켓(두 겹 유리). 금액은 화면 글자로 따로 보여 주므로 그림에는 번개만 넣는다
#   shelf [dark]               메달 진열장 선반(가로로 긴 그림, --aspect로 세로 비율). dark는 다크 모드용 짙은 유리
# 재질·조명·렌더 설정은 메달과 같은 tools/blender_style.py를 쓴다.
import bpy, bmesh, sys, os, math
from mathutils import Vector
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import blender_style as st

argv = sys.argv[sys.argv.index("--") + 1:]
size = 512
aspect = 1.0
if "--size" in argv:
    i = argv.index("--size"); size = int(argv[i + 1]); argv = argv[:i] + argv[i + 2:]
if "--aspect" in argv:
    i = argv.index("--aspect"); aspect = float(argv[i + 1]); argv = argv[:i] + argv[i + 2:]
font_path, out, kind, *opts = argv

srgb = st.srgb

def mat(name, color, metal=0.0, rough=0.3, coat=0.5, emit=0.0, grad=0.28):
    """기본은 반광 플라스틱(그라데이션). metal>0.5면 금속."""
    if metal > 0.5: return st.metal(name, color, rough)
    return st.plastic(name, color, grad=grad, rough=rough, coat=coat, emit=emit)

bpy.ops.wm.read_factory_settings(use_empty=True)
sc = bpy.context.scene
root = bpy.data.objects.new("root", None); sc.collection.objects.link(root)

def own(o, m=None, smooth=True, bevel=0.0, segs=4):
    o.parent = root
    if m: o.data.materials.append(m)
    if bevel:
        b = o.modifiers.new("bevel", "BEVEL"); b.width = bevel; b.segments = segs; b.limit_method = "ANGLE"
    if smooth and o.type == "MESH":
        for poly in o.data.polygons: poly.use_smooth = True
    return o

def cyl(r, depth, loc=(0, 0, 0), rot=(0, 0, 0), m=None, bevel=0.0, verts=128):
    bpy.ops.mesh.primitive_cylinder_add(vertices=verts, radius=r, depth=depth, location=loc, rotation=rot)
    return own(bpy.context.object, m, bevel=bevel)

def box(sx, sy, sz, loc=(0, 0, 0), m=None, bevel=0.0):
    bpy.ops.mesh.primitive_cube_add(size=1, location=loc)
    o = bpy.context.object; o.scale = (sx, sy, sz)
    bpy.ops.object.transform_apply(scale=True)
    return own(o, m, smooth=False, bevel=bevel)

def prism(points, depth, z=0.0, m=None, bevel=0.0, name="prism"):
    """XY 평면 다각형을 Z로 [depth]만큼 밀어 올린다."""
    me = bpy.data.meshes.new(name); bm = bmesh.new()
    vs = [bm.verts.new((x, y, z)) for x, y in points]
    f = bm.faces.new(vs)
    # 한 면 그대로 밀어 올린다(삼각형으로 나누면 오목한 모양에서 베벨이 깨진다). 윤곽은 반시계·자기 교차 없이 넘길 것
    ext = bmesh.ops.extrude_face_region(bm, geom=[f])
    for v in [g for g in ext["geom"] if isinstance(g, bmesh.types.BMVert)]: v.co.z += depth
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    bm.to_mesh(me); bm.free()
    o = bpy.data.objects.new(name, me); sc.collection.objects.link(o)
    return own(o, m, smooth=False, bevel=bevel)

def text(body, size_, z, m, extrude=0.05, y=0.0, bevel=0.025):
    font = bpy.data.fonts.load(font_path)
    cu = bpy.data.curves.new("t", "FONT"); cu.body = body; cu.font = font
    cu.size = size_; cu.extrude = extrude; cu.offset = 0.0; cu.bevel_depth = bevel; cu.bevel_resolution = 4
    cu.align_x = "CENTER"; cu.align_y = "CENTER"
    o = bpy.data.objects.new("t", cu); sc.collection.objects.link(o)
    o.location = (0, y, z); o.data.materials.append(m); o.parent = root
    return o

BOLT = [(2, -10), (-7, 2), (-1, 2), (-2, 10), (7, -2), (1, -2)]  # 위가 +y가 되도록 아래에서 뒤집는다
def bolt(scale, z, m, depth, cx=0.0, cy=0.0):
    return prism([(cx + x * scale, cy - y * scale) for x, y in BOLT], depth, z, m, bevel=depth * 0.25, name="bolt")

gold = mat("gold", "#FFBE0B")
goldDark = mat("goldDark", "#F59E0B", grad=0.15)
white = mat("white", st.WHITE, grad=0, rough=0.25, coat=0.8)
blue = mat("blue", "#1E6BFF", grad=0.35)
def glass(tint="#F7FAFF", k=0.2, name="glass"): return st.frosted(name, tint, rough=0.24, tint_k=k)

def rounded_rect(w, h, r, notch=0.0):
    """둥근 사각형 윤곽(반시계). notch가 있으면 왼쪽·오른쪽 가운데에 반원 홈(쿠폰 모양)."""
    def arc(cx, cy, a0): return [(cx + r * math.cos(math.radians(a0 + k)), cy + r * math.sin(math.radians(a0 + k))) for k in range(0, 91, 10)]
    def dent(side, sign): return [(side + sign * notch * math.cos(math.radians(t)), notch * math.sin(math.radians(t * -sign))) for t in range(-90, 91, 10)]
    pts = arc(w / 2 - r, h / 2 - r, 0) + arc(-w / 2 + r, h / 2 - r, 90)
    if notch: pts += dent(-w / 2, 1)
    pts += arc(-w / 2 + r, -h / 2 + r, 180) + arc(w / 2 - r, -h / 2 + r, 270)
    if notch: pts += dent(w / 2, -1)
    return pts

def shield(sx, sy):
    right = [(0.68, 1.05), (0.82, 0.93)] + [(0.85, y / 10) for y in range(8, -2, -2)]
    for i in range(1, 13):   # 오른쪽 아래 곡선 → 아래 꼭짓점(0, -1.15)
        t = i / 12
        right.append(((1 - t) ** 2 * 0.85 + 2 * (1 - t) * t * 0.85, (1 - t) ** 2 * -0.2 + 2 * (1 - t) * t * -0.85 + t * t * -1.15))
    left = [(-x, y) for x, y in reversed(right[:-1])]
    return [(x * sx, y * sy) for x, y in right + left]

flat = True   # 원판·방패처럼 XY 평면에 만들어 앞(+Z)을 카메라 쪽으로 세우는 것
if kind == "coin":
    # 메달과 같은 유리 동전: 속에 금빛 원판, 둘레에 금 고리, 앞에 흰 P
    cyl(1.0, 0.44, m=glass(), bevel=0.08)
    bpy.ops.mesh.primitive_torus_add(major_radius=0.86, minor_radius=0.1, major_segments=128, minor_segments=20)
    o = own(bpy.context.object, st.glow("ring", "#FFBE0B", 3.0, grad=0.2)); o.scale = (1, 1, 1.25)
    cyl(0.8, 0.16, loc=(0, 0, -0.03), m=st.glow("core", "#FFB020", 2.2, grad=0.5), bevel=0.06)
    text("P", 1.0, 0.22, white, extrude=0.04, y=0.03, bevel=0.012)

elif kind == "emblem":
    glyph, state = opts[0], opts[1]
    done = state == "gold"
    # 두 겹: 뒤에 진한 색 방패(완성 금빛 · 잠김 회청색), 앞에 살짝 비켜 겹친 맑은 서리 유리 방패
    back = prism(shield(1, 1), 0.16, -0.26, st.glow("back", "#FFB020" if done else "#8D9BB4", 2.2 if done else 0.8), bevel=0.04, name="back")
    back.location = (0.3, 0.1, 0)
    prism(shield(1, 1), 0.14, -0.04, glass("#F4F8FF", 0.12, "front"), bevel=0.05, name="front")
    if glyph.startswith("@"): st.shape(sc, glyph[1:], 0.5, 0.07, 0.09, white, root, offset=(0, -0.05))
    else: text(glyph, 0.95, 0.11, white, extrude=0.04, y=0.03, bevel=0.012)

elif kind == "symbol":
    # 두 겹: 뒤에 진한 파랑 타일, 앞에 서리 유리 타일. 흰 경로·번개는 유리 앞에
    back = box(2.0, 2.0, 0.24, loc=(0.18, 0.14, -0.3), m=st.glow("back", "#1E6BFF", 1.6, grad=0.4), bevel=0.3)
    back.modifiers["bevel"].segments = 10
    tile = box(2.0, 2.0, 0.24, m=glass("#DCE9FF", 0.2, "tile"), bevel=0.3)
    tile.modifiers["bevel"].segments = 10
    cu = bpy.data.curves.new("road", "CURVE"); cu.dimensions = "3D"; cu.bevel_depth = 0.07; cu.bevel_resolution = 6
    sp = cu.splines.new("BEZIER"); sp.bezier_points.add(2)
    for bp, (co, h1, h2) in zip(sp.bezier_points, [
        ((-0.55, -0.6, 0.16), (-0.55, -0.9, 0.16), (-0.55, -0.25, 0.16)),
        ((0, 0, 0.16), (-0.3, -0.05, 0.16), (0.3, 0.05, 0.16)),
        ((0.55, 0.6, 0.16), (0.55, 0.25, 0.16), (0.55, 0.9, 0.16)),
    ]):
        bp.co, bp.handle_left, bp.handle_right = co, h1, h2
    road = bpy.data.objects.new("road", cu); sc.collection.objects.link(road); road.parent = root; cu.materials.append(white)
    bpy.ops.mesh.primitive_uv_sphere_add(radius=0.13, location=(-0.55, -0.6, 0.16)); own(bpy.context.object, white)
    box(0.24, 0.24, 0.16, loc=(0.55, 0.62, 0.18), m=white, bevel=0.03)
    cyl(0.36, 0.14, loc=(0, 0, 0.2), m=white, bevel=0.04)
    bolt(0.026, 0.26, blue, 0.07)

elif kind == "moon":
    # 초승달: 바깥 원(반지름 1)에서 오른쪽 위 원(중심 0.5,0.36 · 반지름 0.86)을 뺀 윤곽을 다각형으로
    cx, cy, cr = 0.5, 0.36, 0.86
    n = 720
    ts = [2 * math.pi * i / n for i in range(n)]
    out_ok = [math.hypot(math.cos(t) - cx, math.sin(t) - cy) > cr for t in ts]
    k = next(i for i in range(n) if out_ok[i] and not out_ok[i - 1])   # 잘린 곳을 벗어나는 지점부터
    outer = [(math.cos(ts[(k + i) % n]), math.sin(ts[(k + i) % n])) for i in range(n) if out_ok[(k + i) % n]]
    inner = [(cx + cr * math.cos(t), cy + cr * math.sin(t)) for t in ts]
    inner = [q for q in inner if math.hypot(*q) < 1]
    j = min(range(len(inner)), key=lambda i: math.dist(inner[i], outer[-1]))
    inner = inner[j:] + inner[:j]
    if math.dist(inner[1], outer[0]) < math.dist(inner[-1], outer[0]): inner.reverse(); inner = inner[-1:] + inner[:-1]
    pts = outer + inner
    # 서리 유리 달 속에 노란 빛 덩어리(조금 작게)
    prism(pts[::4], 0.34, -0.17, glass("#FFE9A8", 0.4, "moon"), bevel=0.06, name="moon")
    prism([(x * 0.86 - 0.05, y * 0.86) for x, y in pts[::4]], 0.16, -0.08, st.glow("core", "#FFC83D", 2.2, grad=0.4), bevel=0.04, name="core")
    bolt(0.045, 0.12, blue, 0.16, cx=0.45, cy=-0.35)

elif kind in ("card", "ticket"):
    color = opts[0]
    label = opts[1] if len(opts) > 1 else ""
    if kind == "card":
        # 두 겹: 뒤에 사업자 색 카드, 앞에 사업자 색이 옅게 도는 서리 유리 카드
        prism(rounded_rect(1.7, 1.08, 0.13), 0.06, -0.16, st.glow("back", color, 2.4), bevel=0.02, name="back").location = (0.12, 0.1, 0)
        prism(rounded_rect(1.7, 1.08, 0.13), 0.12, -0.06, glass(color, 0.15, "card"), bevel=0.04, name="card")
        prism(rounded_rect(0.32, 0.24, 0.05), 0.03, 0.06, gold, bevel=0.01, name="chip").location = (-0.5, 0.1, 0)
        for i, w in enumerate((0.9, 0.55)):   # 카드 번호 자리(흰 줄)
            prism(rounded_rect(w, 0.06, 0.03), 0.015, 0.06, white, name="line").location = (-0.78 + w / 2, -0.25 - i * 0.13, 0)
        # 카드 윗변(0.54) 안에 들어오게: 글자 높이 0.5, 가운데 y=0.12. 얇은 베벨로 획을 또렷하게
        if label: text(label, 0.5, 0.07, white, extrude=0.03, y=0.12, bevel=0.01).location.x = 0.42
    else:
        # 두 겹 쿠폰: 뒤에 진한 색, 앞에 서리 유리. 금액은 화면 글자로 — 그림에는 번개와 절취선만
        prism(rounded_rect(1.8, 0.9, 0.12, notch=0.16), 0.08, -0.2, st.glow("back", color, 2.4), bevel=0.03, name="back").location = (0.1, 0.08, 0)
        prism(rounded_rect(1.8, 0.9, 0.12, notch=0.16), 0.12, -0.06, glass(color, 0.15, "ticket"), bevel=0.04, name="ticket")
        for i in range(5):   # 점선 절취선
            cyl(0.035, 0.03, loc=(0.55, -0.3 + i * 0.15, 0.07), m=white, verts=32)
        bolt(0.03, 0.06, white, 0.05, cx=-0.15)

elif kind == "shelf":
    flat = False
    # 진열장 선반: 서리 유리 판 + 은은한 받침. dark는 다크 모드용(밝은 유리가 눈부시지 않게 짙게)
    dark = bool(opts) and opts[0] == "dark"
    board = glass("#4A5672", 0.8, "shelf") if dark else glass("#E3EEFF", 0.35, "shelf")
    trim = st.glow("trim", "#3B4A6B" if dark else "#9DB4E0", 0.8 if dark else 1.2, grad=0.2)
    box(9.4, 0.9, 0.22, loc=(0, 0, -0.11), m=board, bevel=0.06)         # 선반 판
    box(9.2, 0.05, 0.08, loc=(0, -0.3, -0.12), m=trim, bevel=0.02)      # 판 속 빛 띠
    for x in (-4.2, 0, 4.2):                                           # 받침
        box(0.22, 0.5, 0.5, loc=(x, 0.15, -0.45), m=board, bevel=0.06)

elif kind == "chest":
    flat = False
    opened = bool(opts) and opts[0] == "open"
    chest_t = (float(opts[1]) if len(opts) > 1 else 1.0) if opened else 0.0
    # 서리 유리 상자 속에 보라 빛 덩어리, 금 띠는 그대로(VIA 이벤트 보라)
    shell = glass("#B9A8FF", 0.45, "shell")
    core = st.glow("core", "#6E55FF", 2.0, grad=0.35)
    box(1.6, 1.0, 0.8, m=shell, bevel=0.08)
    box(1.36, 0.78, 0.6, m=core, bevel=0.06)
    for x in (-0.55, 0.55):
        box(0.16, 1.04, 0.84, loc=(x, 0, 0), m=gold, bevel=0.02)
    lid = bpy.data.objects.new("lid", None); sc.collection.objects.link(lid); lid.parent = root
    lid.location = (0, 0.5, 0.4)   # 뒤쪽 경첩
    def lidpart(o): o.parent = lid; o.location = o.location - Vector((0, 0.5, 0.4)); return o
    lidpart(cyl(0.5, 1.6, loc=(0, 0, 0.4), rot=(0, math.pi / 2, 0), m=shell, bevel=0.05))
    lidpart(cyl(0.36, 1.4, loc=(0, 0, 0.4), rot=(0, math.pi / 2, 0), m=core, bevel=0.04))
    for x in (-0.55, 0.55):
        lidpart(cyl(0.52, 0.16, loc=(x, 0, 0.4), rot=(0, math.pi / 2, 0), m=gold, bevel=0.01))
    box(0.3, 0.08, 0.34, loc=(0, -0.53, 0.32), m=gold, bevel=0.03)   # 자물쇠판
    box(0.06, 0.02, 0.12, loc=(0, -0.575, 0.3), m=mat("hole", "#2A2340", grad=0, rough=0.6, coat=0))
    if chest_t > 0:
        # 열리는 정도에 따라 빛이 세지고, 반쯤 열리면 동전이 보인다
        glowm = mat("glow", "#FFE08A", grad=0, rough=0.5, coat=0, emit=1.3 * chest_t)
        box(1.44, 0.86, 0.02, loc=(0, 0, 0.405), m=glowm)
        if chest_t >= 0.45:
            for i, (x, y, z, rx) in enumerate([(-0.3, -0.1, 0.48, 20), (0.15, 0.12, 0.5, -15), (0.4, -0.15, 0.47, 35), (-0.05, -0.2, 0.55, -30)]):
                cyl(0.17, 0.05, loc=(x, y, z + 0.12 * (1 - chest_t)), rot=(math.radians(rx), math.radians(rx / 2), 0), m=gold, bevel=0.012, verts=64)
        pl = bpy.data.lights.new("spark", "POINT"); pl.energy = 20 * chest_t; pl.color = (1, 0.85, 0.5)
        po = bpy.data.objects.new("spark", pl); po.location = (0, -0.1, 1.1); sc.collection.objects.link(po)
    # 카메라는 다 열린 모양에 맞춘다(장면마다 같은 구도라야 애니메이션이 흔들리지 않는다)
    lid.rotation_euler = (math.radians(-108), 0, 0)
    root.rotation_euler = (0, 0, math.radians(-24))

if flat:
    root.rotation_euler = st.CARD_TILT if kind in ("card", "ticket") else st.FLAT_TILT

st.lights_and_world(sc)

# 카메라: 보이는 물체의 경계 상자에 맞춘 정사영(살짝 위에서)
bpy.context.view_layer.update()
dg = bpy.context.evaluated_depsgraph_get()
pts = []
for o in sc.objects:
    if o.type in {"MESH", "CURVE", "FONT"} and not o.hide_render:
        ev = o.evaluated_get(dg)
        pts += [o.matrix_world @ Vector(c) for c in ev.bound_box]
lo = Vector([min(p[i] for p in pts) for i in range(3)]); hi = Vector([max(p[i] for p in pts) for i in range(3)])
center = (lo + hi) / 2; extent = max(hi - lo)
cam = bpy.data.objects.new("cam", bpy.data.cameras.new("cam")); sc.collection.objects.link(cam)
cam.data.type = "ORTHO"; cam.data.ortho_scale = extent * 1.15
elev = math.radians({"chest": 18, "shelf": 14}.get(kind, 8))
cam.location = center + Vector((0, -12 * math.cos(elev), 12 * math.sin(elev)))
cam.rotation_euler = (math.pi / 2 - elev, 0, 0)
cam.data.clip_end = 100
if kind == "chest":
    lid.rotation_euler = (math.radians(-108 * chest_t), 0, 0)
sc.camera = cam

st.render_setup(sc, out, size, int(size * aspect))

bpy.ops.render.render(write_still=True)
print("RENDERED", out)
