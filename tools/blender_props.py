# VIA 3D 소품: blender -b -P blender_props.py -- <font.ttf> <out.png> <종류> [옵션...] [--size N]
#   coin                       포인트 코인(P)
#   chest closed|open          리서치 돌파 상자
#   emblem <글자> locked|gold   지역 엠블럼(방패)
#   symbol                     VIA 심볼(경로 + 번개 경유지)
#   moon                       경부하 부스트(달 + 번개)
# 메달(tools/blender_medal.py)과 같은 조명·색 처리(Standard)로 맞춘다.
import bpy, bmesh, sys, math
from mathutils import Vector

argv = sys.argv[sys.argv.index("--") + 1:]
size = 512
if "--size" in argv:
    i = argv.index("--size"); size = int(argv[i + 1]); argv = argv[:i] + argv[i + 2:]
font_path, out, kind, *opts = argv

def srgb(h):
    h = h.lstrip("#"); c = [int(h[i:i+2], 16) / 255 for i in (0, 2, 4)]
    return tuple(((x + 0.055) / 1.055) ** 2.4 if x > 0.04045 else x / 12.92 for x in c) + (1,)

def mat(name, color, metal=0.0, rough=0.4, coat=0.0, emit=0.0):
    m = bpy.data.materials.new(name); m.use_nodes = True
    p = m.node_tree.nodes["Principled BSDF"]
    p.inputs["Base Color"].default_value = srgb(color)
    p.inputs["Metallic"].default_value = metal
    p.inputs["Roughness"].default_value = rough
    if "Coat Weight" in p.inputs: p.inputs["Coat Weight"].default_value = coat
    if emit:
        p.inputs["Emission Color"].default_value = srgb(color)
        p.inputs["Emission Strength"].default_value = emit
    return m

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
    ext = bmesh.ops.extrude_face_region(bm, geom=[f])
    for v in [g for g in ext["geom"] if isinstance(g, bmesh.types.BMVert)]: v.co.z += depth
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    bm.to_mesh(me); bm.free()
    o = bpy.data.objects.new(name, me); sc.collection.objects.link(o)
    return own(o, m, smooth=False, bevel=bevel)

def text(body, size_, z, m, extrude=0.05, y=0.0):
    font = bpy.data.fonts.load(font_path)
    cu = bpy.data.curves.new("t", "FONT"); cu.body = body; cu.font = font
    cu.size = size_; cu.extrude = extrude; cu.offset = 0.006; cu.bevel_depth = 0.012
    cu.align_x = "CENTER"; cu.align_y = "CENTER"
    o = bpy.data.objects.new("t", cu); sc.collection.objects.link(o)
    o.location = (0, y, z); o.data.materials.append(m); o.parent = root
    return o

BOLT = [(2, -10), (-7, 2), (-1, 2), (-2, 10), (7, -2), (1, -2)]  # 위가 +y가 되도록 아래에서 뒤집는다
def bolt(scale, z, m, depth, cx=0.0, cy=0.0):
    return prism([(cx + x * scale, cy - y * scale) for x, y in BOLT], depth, z, m, bevel=depth * 0.25, name="bolt")

gold = mat("gold", "#F2C24A", 0.95, 0.22)
goldDark = mat("goldDark", "#D9A520", 0.95, 0.3)
white = mat("white", "#F7F8FA", 0.0, 0.35)
blue = mat("blue", "#0064FF", 0.05, 0.35, coat=0.4)

flat = True   # 원판·방패처럼 XY 평면에 만들어 앞(+Z)을 카메라 쪽으로 세우는 것
if kind == "coin":
    cyl(1.0, 0.2, m=gold, bevel=0.06)
    cyl(0.78, 0.04, loc=(0, 0, 0.1), m=goldDark, bevel=0.015)
    bpy.ops.mesh.primitive_torus_add(major_radius=0.86, minor_radius=0.035, major_segments=128, minor_segments=24, location=(0, 0, 0.1))
    own(bpy.context.object, gold)
    text("P", 1.05, 0.13, gold, extrude=0.06, y=0.03)

elif kind == "emblem":
    glyph, state = opts[0], opts[1]
    done = state == "gold"
    def shield(sx, sy):
        right = [(0.68, 1.05), (0.82, 0.93)] + [(0.85, y / 10) for y in range(8, -2, -2)]
        for i in range(1, 13):   # 오른쪽 아래 곡선 → 아래 꼭짓점(0, -1.15)
            t = i / 12
            right.append(((1 - t) ** 2 * 0.85 + 2 * (1 - t) * t * 0.85, (1 - t) ** 2 * -0.2 + 2 * (1 - t) * t * -0.85 + t * t * -1.15))
        left = [(-x, y) for x, y in reversed(right[:-1])]
        return [(x * sx, y * sy) for x, y in right + left]
    body = mat("body", "#F2C24A" if done else "#B9BFC7", 0.9 if done else 0.6, 0.25)
    face = mat("face", "#0064FF" if done else "#9AA1AA", 0.05, 0.4, coat=0.3)
    prism(shield(1, 1), 0.22, -0.11, body, bevel=0.05, name="shield")
    prism(shield(0.8, 0.8), 0.06, 0.11, face, bevel=0.02, name="face")
    text(glyph, 0.95, 0.19, white if done else mat("ink", "#E6E8EB"), y=0.05)

elif kind == "symbol":
    tile = box(2.0, 2.0, 0.34, m=blue, bevel=0.32)
    tile.modifiers["bevel"].segments = 10
    # 경로: 왼쪽 아래 출발점 → 가운데 경유지 → 오른쪽 위 목적지
    cu = bpy.data.curves.new("road", "CURVE"); cu.dimensions = "3D"; cu.bevel_depth = 0.07; cu.bevel_resolution = 6
    sp = cu.splines.new("BEZIER"); sp.bezier_points.add(2)
    for bp, (co, h1, h2) in zip(sp.bezier_points, [
        ((-0.55, -0.6, 0.2), (-0.55, -0.9, 0.2), (-0.55, -0.25, 0.2)),
        ((0, 0, 0.2), (-0.3, -0.05, 0.2), (0.3, 0.05, 0.2)),
        ((0.55, 0.6, 0.2), (0.55, 0.25, 0.2), (0.55, 0.9, 0.2)),
    ]):
        bp.co, bp.handle_left, bp.handle_right = co, h1, h2
    road = bpy.data.objects.new("road", cu); sc.collection.objects.link(road); road.parent = root; cu.materials.append(white)
    bpy.ops.mesh.primitive_uv_sphere_add(radius=0.13, location=(-0.55, -0.6, 0.2)); own(bpy.context.object, white)
    box(0.24, 0.24, 0.16, loc=(0.55, 0.62, 0.22), m=white, bevel=0.03)
    cyl(0.36, 0.14, loc=(0, 0, 0.24), m=white, bevel=0.04)
    bolt(0.026, 0.3, blue, 0.07)

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
    prism(pts[::4], 0.26, -0.13, mat("moon", "#FFD45A", 0.3, 0.35), bevel=0.05, name="moon")
    bolt(0.045, 0.0, blue, 0.18, cx=0.45, cy=-0.35)

elif kind == "chest":
    flat = False
    opened = opts and opts[0] == "open"
    wood = mat("wood", "#6A57FF", 0.1, 0.35, coat=0.5)   # VIA 이벤트 보라
    box(1.6, 1.0, 0.8, m=wood, bevel=0.06)
    for x in (-0.55, 0.55):
        box(0.16, 1.04, 0.84, loc=(x, 0, 0), m=gold, bevel=0.02)
    lid = bpy.data.objects.new("lid", None); sc.collection.objects.link(lid); lid.parent = root
    lid.location = (0, 0.5, 0.4)   # 뒤쪽 경첩
    def lidpart(o): o.parent = lid; o.location = o.location - Vector((0, 0.5, 0.4)); return o
    lidpart(cyl(0.5, 1.6, loc=(0, 0, 0.4), rot=(0, math.pi / 2, 0), m=wood, bevel=0.04))
    for x in (-0.55, 0.55):
        lidpart(cyl(0.52, 0.16, loc=(x, 0, 0.4), rot=(0, math.pi / 2, 0), m=gold, bevel=0.01))
    box(0.3, 0.08, 0.34, loc=(0, -0.53, 0.32), m=gold, bevel=0.03)   # 자물쇠판
    box(0.06, 0.02, 0.12, loc=(0, -0.575, 0.3), m=mat("hole", "#2A2340", 0, 0.6))
    if opened:
        lid.rotation_euler = (math.radians(-108), 0, 0)
        glow = mat("glow", "#FFE08A", 0, 0.5, emit=2.2)
        box(1.44, 0.86, 0.02, loc=(0, 0, 0.405), m=glow)
        for i, (x, y, z, rx) in enumerate([(-0.3, -0.1, 0.48, 20), (0.15, 0.12, 0.5, -15), (0.4, -0.15, 0.47, 35), (-0.05, -0.2, 0.55, -30)]):
            cyl(0.17, 0.05, loc=(x, y, z), rot=(math.radians(rx), math.radians(rx / 2), 0), m=gold, bevel=0.012, verts=64)
        pl = bpy.data.lights.new("spark", "POINT"); pl.energy = 35; pl.color = (1, 0.85, 0.5)
        po = bpy.data.objects.new("spark", pl); po.location = (0, -0.1, 1.1); sc.collection.objects.link(po)
    root.rotation_euler = (0, 0, math.radians(-24))

if flat:
    root.rotation_euler = (math.radians(72), 0, math.radians(-16))

# 조명: 메달과 같은 배치
def area(name, loc, rot, energy, size_, color=(1, 1, 1)):
    l = bpy.data.lights.new(name, "AREA"); l.energy = energy; l.size = size_; l.color = color
    o = bpy.data.objects.new(name, l); o.location = loc; o.rotation_euler = [math.radians(a) for a in rot]
    sc.collection.objects.link(o)
area("key", (-4, -5, 5), (50, 0, -38), 700, 9)
area("rim", (3, 3, 3), (-50, 0, 140), 600, 3, (0.9, 0.95, 1))
area("fill", (5, -5, 0.5), (85, 0, 45), 150, 8)
sc.world = bpy.data.worlds.new("w"); sc.world.use_nodes = True
sc.world.node_tree.nodes["Background"].inputs["Color"].default_value = (0.8, 0.82, 0.85, 1)
sc.world.node_tree.nodes["Background"].inputs["Strength"].default_value = 0.35

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
elev = math.radians(18 if kind == "chest" else 8)
cam.location = center + Vector((0, -12 * math.cos(elev), 12 * math.sin(elev)))
cam.rotation_euler = (math.pi / 2 - elev, 0, 0)
cam.data.clip_end = 100
sc.camera = cam

r = sc.render
r.engine = "CYCLES"; sc.cycles.samples = 96; sc.cycles.use_denoising = True
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
sc.view_settings.view_transform = "Standard"
r.filepath = out
bpy.ops.render.render(write_still=True)
print("RENDERED", out)
