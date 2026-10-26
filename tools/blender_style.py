"""VIA 3D 공통 스타일(blender_medal.py·blender_props.py가 함께 쓴다).

유리 스타일(서리 유리 3D 아이콘 레퍼런스에서 느낌만 빌림):
- 반투명 서리 유리 몸통 + 매끈한 코팅(가장자리에 또렷한 하이라이트)
- 유리 속에서 은은하게 빛나는 진한 색 덩어리(사업자 색), 위는 밝고 아래는 짙게
- 진한 색 판 앞에 유리 판을 살짝 비켜 겹친 두 겹 구성(카드·엠블럼·쿠폰)
- 단계 색은 유리 속 테두리 고리가 빛나는 것으로(동전 옆면의 주황빛처럼)
- 크고 부드러운 주광 + 뒤쪽 따뜻한·푸른 윤곽광, 두께가 보이게 조금 더 비튼 3/4 시점
"""
import bpy, bmesh, math


def srgb(h):
    h = h.lstrip("#"); c = [int(h[i:i + 2], 16) / 255 for i in (0, 2, 4)]
    return tuple(((x + 0.055) / 1.055) ** 2.4 if x > 0.04045 else x / 12.92 for x in c) + (1,)


def _mix(c, target, k):
    return tuple(x + (t - x) * k for x, t in zip(c[:3], target)) + (1,)


def _principled(m):
    return m.node_tree.nodes["Principled BSDF"]


def plastic(name, color, grad=0.28, rough=0.3, coat=0.5, emit=0.0):
    """반광 플라스틱. grad>0이면 물체 위쪽(생성 좌표 Y)은 밝게, 아래는 짙게."""
    m = bpy.data.materials.new(name); m.use_nodes = True
    nt = m.node_tree; p = _principled(m)
    c = srgb(color)
    p.inputs["Roughness"].default_value = rough
    p.inputs["Metallic"].default_value = 0.0
    if "Coat Weight" in p.inputs:
        p.inputs["Coat Weight"].default_value = coat
        p.inputs["Coat Roughness"].default_value = 0.12
    if grad > 0:
        tc = nt.nodes.new("ShaderNodeTexCoord")
        sep = nt.nodes.new("ShaderNodeSeparateXYZ")
        ramp = nt.nodes.new("ShaderNodeValToRGB")
        # 밝은 쪽은 살짝만(바래지 않게), 짙은 쪽은 조금 더 — 표면 대부분이 또렷한 원래 색으로 남는다
        ramp.color_ramp.elements[0].color = _mix(c, (0, 0, 0), grad * 0.8)
        ramp.color_ramp.elements[1].color = _mix(c, (1, 1, 1), grad * 0.45)
        nt.links.new(tc.outputs["Generated"], sep.inputs["Vector"])
        nt.links.new(sep.outputs["Y"], ramp.inputs["Fac"])
        nt.links.new(ramp.outputs["Color"], p.inputs["Base Color"])
    else:
        p.inputs["Base Color"].default_value = c
    if emit:
        p.inputs["Emission Color"].default_value = c
        p.inputs["Emission Strength"].default_value = emit
    return m


def metal(name, color, rough=0.2):
    """푸른빛이 도는 은색·금속(토스의 은색 동전 느낌)."""
    m = bpy.data.materials.new(name); m.use_nodes = True
    p = _principled(m)
    p.inputs["Base Color"].default_value = srgb(color)
    p.inputs["Metallic"].default_value = 1.0
    p.inputs["Roughness"].default_value = rough
    return m


WHITE = "#FFFFFF"
# 메달 단계 테두리(채도 있는 금·갈색 동·푸른 은·보라 한정). 동은 작게 봐도 금과 갈리게 짙은 구릿빛
TIER = {"gold": "#FFBE0B", "bronze": "#A8572A", "silver": "#B9C6D8", "event": "#7C5CFF"}

# 보는 각도: 원판·방패 같은 납작한 물체를 뒤로 눕히고 오른쪽으로 비튼다(두께가 보이는 3/4 시점)
FLAT_TILT = (math.radians(58), math.radians(-10), math.radians(-30))
# 글자가 큰 카드·쿠폰은 덜 비튼다(많이 비틀면 한글 획이 뭉개져 '채'가 '체'처럼 읽힌다)
CARD_TILT = (math.radians(66), math.radians(-6), math.radians(-20))


def frosted(name, tint="#EAF2FF", rough=0.24, ior=1.45, tint_k=1.0):
    """서리 유리: 빛이 통과하되 뒤가 흐리게 보인다. tint_k는 유리 색의 진하기(0이면 흰 유리)."""
    m = bpy.data.materials.new(name); m.use_nodes = True
    p = _principled(m)
    p.inputs["Base Color"].default_value = _mix(srgb(tint), (1, 1, 1), 1 - tint_k)
    p.inputs["Roughness"].default_value = rough
    p.inputs["IOR"].default_value = ior
    if "Transmission Weight" in p.inputs: p.inputs["Transmission Weight"].default_value = 1.0
    if "Coat Weight" in p.inputs:   # 서리 몸통 위 매끈한 코팅 → 가장자리 하이라이트
        p.inputs["Coat Weight"].default_value = 1.0; p.inputs["Coat Roughness"].default_value = 0.03
    return m


def glow(name, color, strength=1.2, grad=0.35):
    """유리 속에서 은은하게 빛나는 진한 색 덩어리. 위는 밝고 아래는 짙다."""
    m = plastic(name, color, grad=grad, rough=0.5, coat=0.0)
    p = _principled(m)
    p.inputs["Emission Color"].default_value = srgb(color)
    p.inputs["Emission Strength"].default_value = strength * 0.25
    return m


def lights_and_world(sc):
    """크고 부드러운 주광 + 차가운 채움 + 뒤쪽 따뜻한·푸른 윤곽광(유리 가장자리가 빛나게)."""
    def area(name, loc, rot, energy, size, color=(1, 1, 1)):
        l = bpy.data.lights.new(name, "AREA"); l.energy = energy; l.size = size; l.color = color
        o = bpy.data.objects.new(name, l); o.location = loc; o.rotation_euler = [math.radians(a) for a in rot]
        sc.collection.objects.link(o)
    area("key", (-4, -6, 6), (50, 0, -35), 420, 12)
    area("fill", (6, -5, 0), (85, 0, 50), 160, 14, (0.85, 0.9, 1))
    area("rimW", (5, 3, 2), (-60, 0, 125), 700, 3, (1, 0.55, 0.25))
    area("rimC", (-5, 3, 3), (-55, 0, -130), 380, 4, (0.6, 0.75, 1))
    sc.world = bpy.data.worlds.new("w"); sc.world.use_nodes = True
    bg = sc.world.node_tree.nodes["Background"]
    bg.inputs["Color"].default_value = (0.93, 0.95, 1, 1)   # 푸른빛 도는 흰 스튜디오
    bg.inputs["Strength"].default_value = 0.7


def render_setup(sc, out, width, height, samples=128):
    r = sc.render
    r.engine = "CYCLES"; sc.cycles.samples = samples; sc.cycles.use_denoising = True
    try:
        prefs = bpy.context.preferences.addons["cycles"].preferences
        prefs.compute_device_type = "METAL"; prefs.get_devices()
        for d in prefs.devices: d.use = True
        sc.cycles.device = "GPU"
    except Exception as e:
        print("GPU 설정 실패, CPU로:", e)
    r.film_transparent = True
    sc.cycles.film_transparent_glass = True
    sc.cycles.transmission_bounces = 12; sc.cycles.max_bounces = 16   # 유리가 겹쳐도 검게 막히지 않게
    r.resolution_x = width; r.resolution_y = height
    r.image_settings.file_format = "PNG"; r.image_settings.color_mode = "RGBA"
    sc.view_settings.view_transform = "Standard"   # 색이 바래지 않게
    r.filepath = out


# 글자 대신 쓰는 문양. 글자 인자가 "@이름"이면 이 윤곽을 밀어 올려 세운다. 좌표는 대략 -1~1, 위가 +y.
# 사업자 로고를 흉내 내지 않는 단순한 도형(같은 계열 색이 많아 색만으로는 갈리지 않으니 모양으로 가른다)
def _star(n, r1, r2, a0=90):
    return [((r1 if i % 2 == 0 else r2) * math.cos(math.radians(a0 + 180 * i / n)),
             (r1 if i % 2 == 0 else r2) * math.sin(math.radians(a0 + 180 * i / n))) for i in range(2 * n)]

def _drop():
    a = math.degrees(math.acos(0.62 / 1.3))   # 꼭짓점(0,1)에서 원(중심 0,-0.3 · 반지름 0.62)에 그은 접선
    return [(0, 1.0)] + [(0.62 * math.cos(math.radians(t)), -0.3 + 0.62 * math.sin(math.radians(t)))
                         for t in range(int(90 + a), int(450 - a) + 1, 6)]

def _leaf():
    """잎(끝이 뾰족한 렌즈) + 아래 꼭지. 꼭지가 없으면 작게 볼 때 타원처럼 읽힌다."""
    top = [(-0.8 + 1.7 * i / 24, 0.46 * math.sin(math.pi * i / 24) ** 0.8) for i in range(25)]
    leaf = [(x, -y) for x, y in top] + [(x, y) for x, y in reversed(top[1:-1])]
    stem = [(-1.05, -0.05), (-0.6, -0.05), (-0.6, 0.05), (-1.05, 0.05)]
    c = math.cos(math.radians(45)); s_ = math.sin(math.radians(45))
    return [[(x * c - y * s_, x * s_ + y * c) for x, y in pts] for pts in (leaf, stem)]


def _rect(x0, y0, x1, y1): return [(x0, y0), (x1, y0), (x1, y1), (x0, y1)]

SHAPES = {
    "bolt": [[(x / 10, -y / 10) for x, y in [(2, -10), (-7, 2), (-1, 2), (-2, 10), (7, -2), (1, -2)]]],
    "plug": [[(-0.1, -0.95), (0.1, -0.95), (0.1, -0.5), (0.35, -0.42), (0.55, -0.15), (0.55, 0.5), (0.4, 0.5), (0.4, 0.95),
              (0.22, 0.95), (0.22, 0.5), (-0.22, 0.5), (-0.22, 0.95), (-0.4, 0.95), (-0.4, 0.5), (-0.55, 0.5),
              (-0.55, -0.15), (-0.35, -0.42), (-0.1, -0.5)]],
    "hexagon": [[(0.92 * math.cos(math.radians(60 * i)), 0.92 * math.sin(math.radians(60 * i))) for i in range(6)]],
    "sparkle": [_star(4, 1.0, 0.3)],
    "star": [_star(5, 1.0, 0.45)],
    "triangle": [[(0.95 * math.cos(math.radians(90 + 120 * i)), -0.1 + 0.95 * math.sin(math.radians(90 + 120 * i))) for i in range(3)]],
    "plus": [[(-0.2, -0.85), (0.2, -0.85), (0.2, -0.2), (0.85, -0.2), (0.85, 0.2), (0.2, 0.2), (0.2, 0.85),
              (-0.2, 0.85), (-0.2, 0.2), (-0.85, 0.2), (-0.85, -0.2), (-0.2, -0.2)]],
    "drop": [_drop()],
    "leaf": _leaf(),
    "chevron": [[(-0.55, 0.9), (-0.1, 0.9), (0.6, 0), (-0.1, -0.9), (-0.55, -0.9), (0.15, 0)]],
    # 지역 엠블럼: 서울 도심(남산 타워) · 강남·서초(빌딩 숲) · 경기 남부(산줄기)
    "tower": [[(-0.55, -0.95), (0.55, -0.95), (0.4, -0.72), (0.11, -0.72), (0.11, 0.05), (0.34, 0.12), (0.34, 0.32),
               (0.12, 0.4), (0.06, 0.4), (0.035, 0.98), (-0.035, 0.98), (-0.06, 0.4), (-0.12, 0.4), (-0.34, 0.32),
               (-0.34, 0.12), (-0.11, 0.05), (-0.11, -0.72), (-0.4, -0.72)]],
    # 한 덩어리 스카이라인(떨어진 막대 셋이면 막대그래프로 읽힌다): 옥탑 있는 낮은 빌딩 · 단 지고 첨탑 선 높은 빌딩 · 지붕 기운 빌딩
    "city": [[(-0.9, -0.9), (0.9, -0.9), (0.9, 0.2), (0.6, 0.38), (0.34, 0.38), (0.34, 0.62), (0.2, 0.62), (0.2, 0.78),
              (0.08, 0.78), (0.08, 1.0), (-0.02, 1.0), (-0.02, 0.78), (-0.14, 0.78), (-0.14, 0.62), (-0.28, 0.62),
              (-0.28, 0.05), (-0.5, 0.05), (-0.5, 0.2), (-0.68, 0.2), (-0.68, 0.05), (-0.9, 0.05)]],
    "mountain": [[(-0.95, -0.7), (0.95, -0.7), (0.5, 0.25), (0.25, -0.05), (-0.25, 0.85)]],
}


def shape(sc, name, scale, z, depth, m, parent, offset=(0.0, 0.0)):
    """SHAPES[name]의 윤곽들을 XY 평면에 그려 Z로 [depth]만큼 밀어 올린다(베벨로 모서리를 둥글게)."""
    objs = []
    for k, pts in enumerate(SHAPES[name]):
        pts = [(offset[0] + x * scale, offset[1] + y * scale) for x, y in pts]
        if sum(x0 * y1 - x1 * y0 for (x0, y0), (x1, y1) in zip(pts, pts[1:] + pts[:1])) < 0: pts.reverse()   # 반시계로
        me = bpy.data.meshes.new(f"{name}{k}"); bm = bmesh.new()
        f = bm.faces.new([bm.verts.new((x, y, z)) for x, y in pts])
        ext = bmesh.ops.extrude_face_region(bm, geom=[f])
        for v in [g for g in ext["geom"] if isinstance(g, bmesh.types.BMVert)]: v.co.z += depth
        bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
        bm.to_mesh(me); bm.free()
        o = bpy.data.objects.new(f"{name}{k}", me); sc.collection.objects.link(o); o.parent = parent
        o.data.materials.append(m)
        b = o.modifiers.new("bevel", "BEVEL"); b.width = depth * 0.3; b.segments = 4; b.limit_method = "ANGLE"
        objs.append(o)
    return objs
