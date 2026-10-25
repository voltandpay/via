"""수집 대상 앱 목록. group: hmg(현대차그룹) / oem(경쟁 완성차) / charge(충전 앱) / navi(내비, EV 리뷰만 필터)."""

APPS = [
    # 현대차그룹
    ("hmg", "마이현대", "com.hyundai.oneapp.kr"),
    ("hmg", "Kia App(기아)", "com.kia.oneapp.kr"),
    ("hmg", "MY GENESIS", "com.genesis.oneapp"),
    ("hmg", "E-pit(현대 충전)", "com.hyundai.acteev"),
    # 경쟁 완성차
    ("oem", "Tesla", "com.teslamotors.tesla"),
    ("oem", "My BMW", "de.bmw.connected.mobile20.kr"),
    ("oem", "MINI", "de.mini.connected.mobile20.kr"),
    ("oem", "Mercedes-Benz", "com.daimler.ris.mercedesme.kr.android"),
    ("oem", "Volvo Cars", "se.volvo.vcc"),
    ("oem", "Polestar", "com.polestar.explore"),
    ("oem", "My Renault", "com.myrsm.android"),
    ("oem", "myChevrolet", "com.gm.chevrolet.nomad.ownership"),
    ("oem", "KGM LINK", "com.kgm.link"),
    ("oem", "My Volkswagen", "kr.co.volkswagen.my"),
    ("oem", "myAudi", "de.myaudi.mobile.assistant"),
    ("oem", "My Porsche", "de.porsche.one"),
    ("oem", "BYD AUTO", "com.byd.bydautolink"),
    ("oem", "렉서스 커넥트", "com.lguplus.tmccssapp"),
    # 충전 앱
    ("charge", "EV Infra", "com.client.ev.activities"),
    ("charge", "차지비(GS)", "com.lgntel.ngcharger"),
    ("charge", "일렉베리", "com.tbu.elecvery"),
    ("charge", "채비", "com.dym.chaevi.main"),
    ("charge", "SK일렉링크", "kr.co.sscharger"),
    ("charge", "에버온", "com.chasygo"),
    ("charge", "파워큐브", "com.cubechargerapp.cubecharger"),
    ("charge", "플러그링크", "com.pling.app"),
    ("charge", "볼트업(LGU+)", "com.lgvoltup.app"),
    ("charge", "EV이음(환경부)", "com.parkingthru.evcard"),
    ("charge", "KEPCO PLUG(한전)", "co.kr.kepco.plug"),
    ("charge", "이지차저", "com.easychargerapp.client"),
    ("charge", "스타코프", "kr.co.starkoff.app"),
    ("charge", "워터", "kr.bep.water"),
    ("charge", "모두의충전", "org.ScalarData.EveryCharge"),
    ("charge", "투루차저(휴맥스)", "com.humax.client"),
    ("charge", "EVSIS", "com.ldcc.android.evsis"),
    ("charge", "해피차저", "com.kevcs.happecharger.app"),
    # 내비 — 리뷰가 매우 많으므로 최신 일부만 받고 EV 관련만 남긴다
    ("navi", "티맵", "com.skt.tmap.ku"),
    ("navi", "카카오내비", "com.locnall.KimGiSa"),
]

# 앱당 최대 수집 건수 (내비는 EV 필터 전 기준)
CAP = {"hmg": 60000, "oem": 30000, "charge": 30000, "navi": 80000}
