# 도감 메달 시안: blender -b -P medal.py -- <font.ttf> <out.png> <hex색> <글자> [locked]
import bpy, sys, math
argv = sys.argv[sys.argv.index("--") + 1:]
font_path, out, hexcol, glyph = argv[:4]
locked = len(argv) > 4 and argv[4] == "locked"

def srgb(h):
    h = h.lstrip("#"); c = [int(h[i:i+2], 16) / 255 for i in (0, 2, 4)]
    return tuple(((x + 0.055) / 1.055) ** 2.4 if x > 0.04045 else x / 12.92 for x in c) + (1,)

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

base = srgb("#8E959E") if locked else srgb(hexcol)
rimc = srgb("#C9CED4") if locked else srgb("#E9D9A6")   # 잠긴 메달은 은회색, 모은 메달은 연금빛 테두리
face = mat("face", base, 0.05, 0.42, clearcoat=0.25)
rim = mat("rim", rimc, 0.9, 0.22)
ink = mat("ink", srgb("#F7F8FA") if not locked else srgb("#E6E8EB"), 0.0, 0.4)

root = bpy.data.objects.new("medal", None); sc.collection.objects.link(root)

def parent(o): o.parent = root; return o

# 몸통: 두꺼운 원판 + 둥근 모서리
bpy.ops.mesh.primitive_cylinder_add(vertices=160, radius=1.0, depth=0.24)
body = parent(bpy.context.object); body.data.materials.append(rim)
bv = body.modifiers.new("bevel", "BEVEL"); bv.width = 0.07; bv.segments = 8
bpy.ops.object.shade_smooth()

# 앞면: 살짝 솟은 사업자 색 원판(에나멜)
bpy.ops.mesh.primitive_cylinder_add(vertices=160, radius=0.8, depth=0.06, location=(0, 0, 0.13))
fc = parent(bpy.context.object); fc.data.materials.append(face)
bv = fc.modifiers.new("bevel", "BEVEL"); bv.width = 0.02; bv.segments = 4
bpy.ops.object.shade_smooth()

# 테두리 안쪽 고리
bpy.ops.mesh.primitive_torus_add(major_radius=0.86, minor_radius=0.035, major_segments=160, minor_segments=24, location=(0, 0, 0.13))
ring = parent(bpy.context.object); ring.data.materials.append(rim)
bpy.ops.object.shade_smooth()

# 글자: 앞면 위로 돋을새김(잠긴 메달은 자물쇠 대신 글자를 흐리게)
font = bpy.data.fonts.load(font_path)
cu = bpy.data.curves.new("glyph", "FONT"); cu.body = glyph; cu.font = font
cu.size = 1.05; cu.extrude = 0.05; cu.offset = 0.006; cu.bevel_depth = 0.012; cu.align_x = "CENTER"; cu.align_y = "CENTER"
txt = parent(bpy.data.objects.new("glyph", cu)); sc.collection.objects.link(txt)
txt.location = (0, 0.02, 0.2); txt.data.materials.append(ink)

# 보는 각도: 살짝 위에서 내려다보고 오른쪽으로 돌림
root.rotation_euler = (math.radians(68), 0, math.radians(-14))

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
area("key", (-4, -5, 5), (50, 0, -38), 700, 9)
area("rim", (3, 3, 3), (-50, 0, 140), 600, 3, (0.9, 0.95, 1))
area("fill", (5, -5, 0.5), (85, 0, 45), 150, 8)
sc.world = bpy.data.worlds.new("w"); sc.world.use_nodes = True
sc.world.node_tree.nodes["Background"].inputs["Color"].default_value = (0.8, 0.82, 0.85, 1)
sc.world.node_tree.nodes["Background"].inputs["Strength"].default_value = 0.35

# 바닥 그림자는 넣지 않는다: 투명 배경 이미지 테두리에 그림자가 잘려 사각형이 보이므로(앱에서 크기만 맞춰 쓴다)

r = sc.render
r.engine = "CYCLES"
sc.cycles.samples = 96
sc.cycles.use_denoising = True
try:
    prefs = bpy.context.preferences.addons["cycles"].preferences
    prefs.compute_device_type = "METAL"; prefs.get_devices()
    for d in prefs.devices: d.use = True
    sc.cycles.device = "GPU"
except Exception as e:
    print("GPU 설정 실패, CPU로:", e)
r.film_transparent = True
r.resolution_x = r.resolution_y = 512
r.image_settings.file_format = "PNG"; r.image_settings.color_mode = "RGBA"
sc.view_settings.view_transform = "Standard"  # 브랜드 색이 바래지 않게
r.filepath = out
bpy.ops.render.render(write_still=True)
print("RENDERED", out)
