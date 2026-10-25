"""해외(미국·영국) 비교용 앱 목록. (그룹, 이름, 국가, Play ID, EV 필터 여부)

국내 앱 목록(apps.py)과 같은 분류 체계로 비교하기 위해 그룹 이름을 맞췄다.
- g_hmg: 해외 현대차그룹 / g_oem: 해외 경쟁 완성차 / g_charge: 해외 충전 앱 / g_reward: 해외 리워드·DR 앱
"""

APPS_GLOBAL = [
    # 해외 현대차그룹 — 영국판(*.oneapp.eu)은 국내 통합 앱과 같은 계열
    ("g_hmg", "MyHyundai with Bluelink (US)", "us", "com.stationdm.bluelink", False),
    ("g_hmg", "Kia Access (US)", "us", "com.myuvo.link", False),
    ("g_hmg", "Genesis Intelligent Assistant (US)", "us", "com.stationdm.genesis", False),
    ("g_hmg", "myHyundai (EU)", "gb", "com.hyundai.oneapp.eu", False),
    ("g_hmg", "Kia App (EU)", "gb", "com.kia.oneapp.eu", False),
    ("g_hmg", "MY GENESIS (EU)", "gb", "com.genesis.oneapp.eu", False),
    ("g_hmg", "Charge myHyundai (EU)", "gb", "com.digitalchargingsolutions.hyundai", False),
    # 해외 경쟁 완성차 (국내 경쟁 완성차 앱의 해외판)
    ("g_oem", "Tesla", "us", "com.teslamotors.tesla", False),
    ("g_oem", "Tesla", "gb", "com.teslamotors.tesla", False),
    ("g_oem", "My BMW", "us", "de.bmw.connected.mobile20.na", False),
    ("g_oem", "My BMW", "gb", "de.bmw.connected.mobile20.row", False),
    ("g_oem", "MINI", "us", "de.mini.connected.mobile20.na", False),
    ("g_oem", "MINI", "gb", "de.mini.connected.mobile20.row", False),
    ("g_oem", "Mercedes-Benz", "us", "com.mbusa.mmusa.android", False),
    ("g_oem", "Mercedes-Benz", "gb", "com.daimler.ris.mercedesme.ece.android", False),
    ("g_oem", "Volvo Cars", "us", "se.volvo.vcc", False),
    ("g_oem", "Volvo Cars", "gb", "se.volvo.vcc", False),
    ("g_oem", "Polestar", "us", "com.polestar.explore", False),
    ("g_oem", "Polestar", "gb", "com.polestar.explore", False),
    ("g_oem", "myChevrolet", "us", "com.gm.chevrolet.nomad.ownership", False),
    ("g_oem", "Volkswagen", "us", "com.vw.carnet.release", False),
    ("g_oem", "Volkswagen", "gb", "com.volkswagen.weconnect", False),
    ("g_oem", "myAudi", "us", "de.myaudi.mobile.assistant", False),
    ("g_oem", "myAudi", "gb", "de.myaudi.mobile.assistant", False),
    ("g_oem", "My Porsche", "us", "com.porsche.one", False),
    ("g_oem", "My Porsche", "gb", "de.porsche.one", False),
    ("g_oem", "BYD AUTO", "gb", "com.byd.bydautolink", False),
    # 해외 충전 앱
    ("g_charge", "ChargePoint", "us", "com.coulombtech", False),
    ("g_charge", "ChargePoint", "gb", "com.coulombtech", False),
    ("g_charge", "Electrify America", "us", "com.ea.evowner", False),
    ("g_charge", "EVgo", "us", "com.driivz.mobile.android.evgo.driver", False),
    ("g_charge", "PlugShare", "us", "com.xatori.Plugshare", False),
    ("g_charge", "PlugShare", "gb", "com.xatori.Plugshare", False),
    ("g_charge", "Octopus Electroverse", "gb", "energy.octopus.electricjuice.android", False),
    ("g_charge", "Octopus Electroverse", "us", "energy.octopus.electricjuice.android", False),
    ("g_charge", "IONITY", "gb", "com.cleevio.ionity.android.app", False),
    ("g_charge", "Chargemap", "gb", "com.chargemap_beta.android", False),
    # 해외 리워드·DR 앱 — 우리 '수요 분산 보상' 구조의 선례
    ("g_reward", "Optiwatt", "us", "com.getoptiwatt.optiwatt", False),
    ("g_reward", "ev.energy", "us", "energy.ev.app", False),
    ("g_reward", "ev.energy", "gb", "energy.ev.app", False),
    ("g_reward", "Octopus Energy (EV 관련만)", "gb", "energy.octopus.octopusenergy.android", True),
]

CAP_GLOBAL = 3000          # 앱·국가당 최신 리뷰 상한
CAP_GLOBAL_FILTERED = 30000  # EV 필터 앱은 많이 받아 걸러낸다
EV_FILTER_EN = r"\bev\b|electric vehicle|charg|intelligent octopus|smart charg|off.?peak|tesla|car\b"
