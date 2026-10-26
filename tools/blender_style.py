"""VIA 3D 공통 스타일(blender_medal.py·blender_props.py가 함께 쓴다).

토스 3D 그래픽에서 빌린 원칙(자산은 쓰지 않고 느낌만):
- 채도 높은 또렷한 색, 위는 밝고 아래는 짙은 그라데이션(회색 그림자 대신 색이 남는 그림자)
- 반광 플라스틱 질감(살짝 코팅), 포인트로 반투명 유리와 푸른빛 은색
- 두툼하고 둥근 형태, 흰 글자·기호, 살짝 비튼 3/4 시점, 흰 배경에 맞춘 밝은 조명
"""
import bpy, math


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


def glass(name, tint="#DCEBFF", rough=0.12):
    """반투명 유리(잠긴 메달 등). 투명 배경에서도 비치도록 film_transparent_glass를 켠다."""
    m = bpy.data.materials.new(name); m.use_nodes = True
    p = _principled(m)
    p.inputs["Base Color"].default_value = srgb(tint)
    p.inputs["Roughness"].default_value = rough
    p.inputs["IOR"].default_value = 1.45
    if "Transmission Weight" in p.inputs: p.inputs["Transmission Weight"].default_value = 1.0
    if "Coat Weight" in p.inputs: p.inputs["Coat Weight"].default_value = 0.4
    return m


WHITE = "#FFFFFF"
# 메달 단계 테두리(채도 있는 금·갈색 동·푸른 은·보라 한정). 동은 작게 봐도 금과 갈리게 짙은 구릿빛
TIER = {"gold": "#FFBE0B", "bronze": "#A8572A", "silver": "#B9C6D8", "event": "#7C5CFF"}

# 보는 각도: 원판·방패 같은 납작한 물체를 살짝 뒤로 눕히고 오른쪽으로 비튼다(3/4 시점)
FLAT_TILT = (math.radians(66), math.radians(-6), math.radians(-24))
# 글자가 큰 카드·쿠폰은 덜 비튼다(많이 비틀면 한글 획이 뭉개져 '채'가 '체'처럼 읽힌다)
CARD_TILT = (math.radians(78), 0, math.radians(-10))


def lights_and_world(sc):
    def area(name, loc, rot, energy, size, color=(1, 1, 1)):
        l = bpy.data.lights.new(name, "AREA"); l.energy = energy; l.size = size; l.color = color
        o = bpy.data.objects.new(name, l); o.location = loc; o.rotation_euler = [math.radians(a) for a in rot]
        sc.collection.objects.link(o)
    area("key", (-4, -5, 6), (45, 0, -38), 520, 7)            # 왼쪽 위 주광: 반광 하이라이트
    area("fill", (5, -6, 1), (80, 0, 40), 180, 10)            # 오른쪽 앞 채움
    area("rim", (2, 4, 4), (-45, 0, 160), 420, 4, (0.85, 0.92, 1))   # 뒤쪽 푸른 윤곽광
    sc.world = bpy.data.worlds.new("w"); sc.world.use_nodes = True
    bg = sc.world.node_tree.nodes["Background"]
    bg.inputs["Color"].default_value = (1, 1, 1, 1)           # 흰 배경 반사 → 그림자도 밝고 색이 남는다
    bg.inputs["Strength"].default_value = 0.6


def render_setup(sc, out, width, height, samples=64):
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
    r.resolution_x = width; r.resolution_y = height
    r.image_settings.file_format = "PNG"; r.image_settings.color_mode = "RGBA"
    sc.view_settings.view_transform = "Standard"   # 색이 바래지 않게
    r.filepath = out
