# 도감 메달: blender -b -P blender_medal.py -- <font.ttf> <out.png> <hex색> <글자> [locked|bronze|silver|gold|event] [--spin 도] [--size N]
#   --spin: 세로축으로 돌린 각도(새 스탬프 획득 연출 장면용), --size: 출력 크기(기본 512)
import bpy, sys, math
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

def srgb(h):
    h = h.lstrip("#"); c = [int(h[i:i+2], 16) / 255 for i in (0, 2, 4)]
    return tuple(((x + 0.055) / 1.055) ** 2.4 if x > 0.04045 else x / 12.92 for x in c) + (1,)

def pastel(c, k=0.08):
    """토스풍: 브랜드 색을 흰색 쪽으로 살짝 밝힌 파스텔(선형 색공간에서 섞는다)."""
    return tuple(x + (1 - x) * k for x in c[:3]) + (1,)

def mat(name, color, metal, rough, clearcoat=0.0):
    m = bpy.data.materials.new(name); m.use_nodes = True
    p = m.node_tree.nodes["Principled BSDF"]
    p.inputs["Base Color"].default_value = color
    p.inputs["Metallic"].default_value = metal
    p.inputs["Roughness"].default_value = rough
    if "Coat Weight" in p.inputs: p.inputs["Coat Weight"].default_value = clearcoat
    return m

bpy.ops.wm.read_factory_settings(use_empty=True)
sc = bpy.context.scene

# 토스풍 3D: 무광 점토 질감, 파스텔, 두툼하고 둥근 형태, 정면에 가까운 시점
base = srgb("#B4BBC4") if locked else pastel(srgb(hexcol))
# 테두리: 잠긴 메달은 연회색, 단계별로 동·은·금(파스텔), event = 사업자 데이 한정(연보라)
rimc = srgb({"locked": "#CDD3DB", "bronze": "#E99A62", "silver": "#D3DAE3", "gold": "#FFC21A", "event": "#8F7BFF"}.get(variant, "#FFE08A"))
face = mat("face", base, 0.0, 0.55)
rim = mat("rim", rimc, 0.15, 0.38)
ink = mat("ink", srgb("#FFFFFF") if not locked else srgb("#F1F3F5"), 0.0, 0.5)

root = bpy.data.objects.new("medal", None); sc.collection.objects.link(root)

def parent(o): o.parent = root; return o

# 몸통: 두꺼운 원판 + 둥근 모서리
bpy.ops.mesh.primitive_cylinder_add(vertices=160, radius=1.0, depth=0.34)
body = parent(bpy.context.object); body.data.materials.append(rim)
bv = body.modifiers.new("bevel", "BEVEL"); bv.width = 0.15; bv.segments = 12
bpy.ops.object.shade_smooth()

# 앞면: 살짝 솟은 사업자 색 원판(에나멜)
bpy.ops.mesh.primitive_cylinder_add(vertices=160, radius=0.78, depth=0.1, location=(0, 0, 0.16))
fc = parent(bpy.context.object); fc.data.materials.append(face)
bv = fc.modifiers.new("bevel", "BEVEL"); bv.width = 0.045; bv.segments = 8
bpy.ops.object.shade_smooth()


# 글자: 앞면 위로 돋을새김(잠긴 메달은 자물쇠 대신 글자를 흐리게)
font = bpy.data.fonts.load(font_path)
cu = bpy.data.curves.new("glyph", "FONT"); cu.body = glyph; cu.font = font
cu.size = 1.0; cu.extrude = 0.06; cu.offset = 0.0; cu.bevel_depth = 0.03; cu.bevel_resolution = 4; cu.align_x = "CENTER"; cu.align_y = "CENTER"
txt = parent(bpy.data.objects.new("glyph", cu)); sc.collection.objects.link(txt)
txt.location = (0, 0.02, 0.24); txt.data.materials.append(ink)

# 보는 각도: 살짝 위에서 내려다보고 오른쪽으로 돌림
root.rotation_euler = (math.radians(78), 0, math.radians(-10 + spin))

# 카메라
cam = bpy.data.objects.new("cam", bpy.data.cameras.new("cam")); sc.collection.objects.link(cam)
cam.data.lens = 85
cam.location = (0, -7.6, 1.35)
cam.rotation_euler = (math.radians(80), 0, 0)
sc.camera = cam

# 조명: 큰 키 라이트(왼쪽 위) + 뒤쪽 테두리 라이트 + 약한 채움
def area(name, loc, rot, energy, size, color=(1, 1, 1)):
    l = bpy.data.lights.new(name, "AREA"); l.energy = energy; l.size = size; l.color = color
    o = bpy.data.objects.new(name, l); o.location = loc; o.rotation_euler = [math.radians(a) for a in rot]
    sc.collection.objects.link(o)
# 토스풍: 크고 부드러운 빛, 그림자를 밝게(점토 느낌)
area("key", (-4, -5, 5), (50, 0, -38), 550, 12)
area("rim", (3, 3, 3), (-50, 0, 140), 300, 6, (0.95, 0.97, 1))
area("fill", (5, -5, 0.5), (85, 0, 45), 280, 10)
sc.world = bpy.data.worlds.new("w"); sc.world.use_nodes = True
sc.world.node_tree.nodes["Background"].inputs["Color"].default_value = (0.95, 0.96, 1.0, 1)
sc.world.node_tree.nodes["Background"].inputs["Strength"].default_value = 0.5

# 바닥 그림자는 넣지 않는다: 투명 배경 이미지 테두리에 그림자가 잘려 사각형이 보이므로(앱에서 크기만 맞춰 쓴다)

r = sc.render
r.engine = "CYCLES"
sc.cycles.samples = 64
sc.cycles.use_denoising = True
try:
    prefs = bpy.context.preferences.addons["cycles"].preferences
    prefs.compute_device_type = "METAL"; prefs.get_devices()
    for d in prefs.devices: d.use = True
    sc.cycles.device = "GPU"
except Exception as e:
    print("GPU 설정 실패, CPU로:", e)
r.film_transparent = True
r.resolution_x = r.resolution_y = size
r.image_settings.file_format = "PNG"; r.image_settings.color_mode = "RGBA"
sc.view_settings.view_transform = "Standard"  # 브랜드 색이 바래지 않게
r.filepath = out
bpy.ops.render.render(write_still=True)
print("RENDERED", out)
