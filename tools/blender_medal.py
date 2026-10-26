# 도감 메달: blender -b -P blender_medal.py -- <font.ttf> <out.png> <hex색> <글자|@문양> [locked|bronze|silver|gold|event] [--spin 도] [--size N]
#   --spin: 세로축으로 돌린 각도(새 스탬프 획득 연출 장면용), --size: 출력 크기(기본 512)
# 모양: 두꺼운 서리 유리 동전. 유리 속에 사업자 색 원판이 은은하게 빛나고, 그 둘레의 고리가 단계 색으로 빛나며,
# 앞면에 흰 문양(st.SHAPES, 사업자마다 다른 모양)이 돋아 있다. 잠긴 메달은 회청색 원판·은 고리. 스타일은 tools/blender_style.py.
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

glass = st.frosted("glass", "#F7FAFF", rough=0.24, tint_k=0.2)
core = st.glow("core", "#9AA7BE" if locked else hexcol, 1.2 if locked else 2.2, grad=0.5)
ring = st.glow("ring", st.TIER["silver" if locked else (variant or "gold")], 3.0, grad=0.2)
ink = st.plastic("ink", st.WHITE, grad=0, rough=0.25, coat=0.8)

root = bpy.data.objects.new("medal", None); sc.collection.objects.link(root)
def parent(o): o.parent = root; return o
def smooth(o, bevel=0.0, segs=8):
    if bevel:
        b = o.modifiers.new("bevel", "BEVEL"); b.width = bevel; b.segments = segs
    for p in o.data.polygons: p.use_smooth = True
    return o

# 두꺼운 유리 몸통(옆면이 보이게)
bpy.ops.mesh.primitive_cylinder_add(vertices=160, radius=1.0, depth=0.44)
smooth(parent(bpy.context.object), bevel=0.08, segs=10).data.materials.append(glass)
# 유리 속 단계 색 고리(동전 옆면이 빛나는 것처럼)
bpy.ops.mesh.primitive_torus_add(major_radius=0.86, minor_radius=0.1, major_segments=160, minor_segments=24)
o = parent(bpy.context.object); o.scale = (1, 1, 1.25); smooth(o).data.materials.append(ring)
# 유리 속 사업자 색 원판
bpy.ops.mesh.primitive_cylinder_add(vertices=160, radius=0.8, depth=0.16, location=(0, 0, -0.03))
smooth(parent(bpy.context.object), bevel=0.06, segs=6).data.materials.append(core)

# 글자: 유리 앞면 위에 흰 돋을새김(얇은 베벨로 획을 또렷하게)
if glyph.startswith("@"):   # 문양(st.SHAPES)
    st.shape(sc, glyph[1:], 0.48 * (0.9 if locked else 1), 0.17, 0.09, ink, root, offset=(0, 0.02))
else:
    font = bpy.data.fonts.load(font_path)
    cu = bpy.data.curves.new("glyph", "FONT"); cu.body = glyph; cu.font = font
    cu.size = 0.95; cu.extrude = 0.04; cu.bevel_depth = 0.012; cu.bevel_resolution = 4
    cu.align_x = "CENTER"; cu.align_y = "CENTER"
    txt = parent(bpy.data.objects.new("glyph", cu)); sc.collection.objects.link(txt)
    txt.location = (0, 0.03, 0.22); txt.data.materials.append(ink)
    if locked: txt.scale = (0.9, 0.9, 1)

rx, ry, rz = st.FLAT_TILT
root.rotation_euler = (rx, ry, rz + math.radians(spin))

# 카메라: 회전해도 잘리지 않게 넉넉히
cam = bpy.data.objects.new("cam", bpy.data.cameras.new("cam")); sc.collection.objects.link(cam)
cam.data.lens = 120
cam.location = (0, -8.4, 1.5)
cam.rotation_euler = (math.radians(80), 0, 0)
sc.camera = cam

st.lights_and_world(sc)
st.render_setup(sc, out, size, size)
bpy.ops.render.render(write_still=True)
print("RENDERED", out)
