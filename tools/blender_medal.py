# 도감 메달: blender -b -P blender_medal.py -- <font.ttf> <out.png> <hex색> <글자> [locked|bronze|silver|gold|event] [--spin 도] [--size N]
#   --spin: 세로축으로 돌린 각도(새 스탬프 획득 연출 장면용), --size: 출력 크기(기본 512)
# 모양: 두툼한 동전. 바깥 테두리(단계 색)가 솟아 있고 안쪽 면(사업자 색)이 오목하게 들어가 있으며 흰 글자가 돋아 있다.
# 잠긴 메달은 반투명 유리 동전. 스타일은 tools/blender_style.py.
import bpy, sys, os, math
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import blender_style as st

argv = sys.argv[sys.argv.index("--") + 1:]
def flag(name, default):
    global argv
    if name not in argv: return default
    i = argv.index(name); v = argv[i + 1]; argv = argv[:i] + argv[i + 2:]; return float(v)
spin = flag("--spin", 0.0)
size = int(flag("--size", 512))
font_path, out, hexcol, glyph = argv[:4]
variant = argv[4] if len(argv) > 4 else ""
locked = variant == "locked"

bpy.ops.wm.read_factory_settings(use_empty=True)
sc = bpy.context.scene

if locked:
    # 유리 동전은 흰 카드 위에서 거의 안 보였다 → 은 테두리 + 회청색 면으로 '빈 칸'이 또렷하게
    rim = st.metal("rim", st.TIER["silver"], rough=0.22)
    face = st.plastic("face", "#9AA7BE", grad=0.25)
    ink = st.plastic("ink", "#FFFFFF", grad=0, rough=0.4, coat=0)
elif variant == "silver":
    rim = st.metal("rim", st.TIER["silver"], rough=0.18)
    face = st.plastic("face", hexcol)
    ink = st.plastic("ink", st.WHITE, grad=0, rough=0.35)
else:
    rim = st.plastic("rim", st.TIER.get(variant, hexcol), grad=0.3)
    face = st.plastic("face", hexcol)
    ink = st.plastic("ink", st.WHITE, grad=0, rough=0.35)

root = bpy.data.objects.new("medal", None); sc.collection.objects.link(root)
def parent(o): o.parent = root; return o
def smooth(o, bevel=0.0, segs=8):
    if bevel:
        b = o.modifiers.new("bevel", "BEVEL"); b.width = bevel; b.segments = segs
    for p in o.data.polygons: p.use_smooth = True
    return o

# 몸통: 두꺼운 원판
bpy.ops.mesh.primitive_cylinder_add(vertices=160, radius=1.0, depth=0.26)
smooth(parent(bpy.context.object), bevel=0.1, segs=10).data.materials.append(rim)
# 솟은 테두리: 원판 윗면 가장자리를 도는 고리
bpy.ops.mesh.primitive_torus_add(major_radius=0.88, minor_radius=0.12, major_segments=160, minor_segments=32, location=(0, 0, 0.12))
smooth(parent(bpy.context.object)).data.materials.append(rim)
# 오목한 안쪽 면(테두리보다 낮다)
bpy.ops.mesh.primitive_cylinder_add(vertices=160, radius=0.8, depth=0.04, location=(0, 0, 0.13))
smooth(parent(bpy.context.object), bevel=0.015, segs=4).data.materials.append(face)

# 글자: 굵고 둥근 흰 돋을새김
font = bpy.data.fonts.load(font_path)
cu = bpy.data.curves.new("glyph", "FONT"); cu.body = glyph; cu.font = font
cu.size = 0.92; cu.extrude = 0.05; cu.bevel_depth = 0.022; cu.bevel_resolution = 4
cu.align_x = "CENTER"; cu.align_y = "CENTER"
txt = parent(bpy.data.objects.new("glyph", cu)); sc.collection.objects.link(txt)
txt.location = (0, 0.02, 0.2); txt.data.materials.append(ink)
if locked: txt.scale = (0.9, 0.9, 1)

rx, ry, rz = st.FLAT_TILT
root.rotation_euler = (rx, ry, rz + math.radians(spin))

# 카메라: 회전해도 잘리지 않게 넉넉히
cam = bpy.data.objects.new("cam", bpy.data.cameras.new("cam")); sc.collection.objects.link(cam)
cam.data.lens = 85
cam.location = (0, -8.4, 1.5)
cam.rotation_euler = (math.radians(80), 0, 0)
sc.camera = cam

st.lights_and_world(sc)
st.render_setup(sc, out, size, size)
bpy.ops.render.render(write_still=True)
print("RENDERED", out)
