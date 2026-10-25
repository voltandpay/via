"""영어 리뷰용 분류 체계. 카테고리 이름(키)은 국내 taxonomy.py와 같게 맞춰 국내·해외를 바로 비교한다.

패턴은 대소문자 무시 정규식. Gemini 판정(해외 표본 3,000건)을 기준값으로 정밀도·재현율을 재며 다듬었다.
"""

PAIN = {
    "앱 안정성·오류": r"crash|bug\b|buggy|bugs\b|glitch|freez|frozen|\blag\b|laggy|slow to (load|open|respond)|\bslow\b|loading|won'?t (open|load|launch)|doesn'?t (open|load)|keeps? (closing|crashing|logging)|stopped working|not working|doesn'?t work|does not work|never works|broken|error|after (the )?(latest |recent |last )?update|since (the )?(latest |recent |last )?update|useless|unusable",
    "로그인·인증": r"log ?in|login|sign ?in|signin|logs? me out|logged out|password|verification|verify|2fa|two.?factor|authenticat|sign ?up|register|registration|\baccount\b",
    "결제·청구 오류": r"payment|pay\b|paid|charged (me )?(twice|double|again)|double charg|refund|billing|billed|credit card|debit card|card (declin|not accepted|issue|on file)|add(ing)? (a )?card|overcharg|pre.?auth|\bhold\b.{0,20}card",
    "요금·가격": r"price|pricing|expensive|\bcost|per kwh|/kwh|kwh rate|\$\s?\d|£\s?\d|€\s?\d|\bfees?\b|tariff|\brates?\b|cheap|overpriced|pricey|subscription|afford",
    "충전기 고장·충전 실패": r"(charger|station|stall|unit|plug)s? (is |are |was |were )?(broken|down|out of (order|service)|not working|offline|faulty|dead)|(won'?t|wouldn'?t|didn'?t|couldn'?t|could not|did not|fail(ed|s)? to|unable to|never|will not|doesn'?t) (start|initiate|begin|authori[sz]e|enable|allow|stop|finish)\b.{0,12}(charg|session)|charg(ing|e)\b.{0,10}(won'?t|doesn'?t|didn'?t|never|wouldn'?t) (start|work|finish|stop)|charg(ing|e) (failed|stopped|stops|interrupted|ended)|stops? charging|session (ended|failed|stopped|didn'?t|doesn'?t)|initiating charg|stuck (on|at) |broken charger|fail(ed|s)? to charge|won'?t charge|didn'?t charge|not charging|can'?t charge|cannot charge|\binterrupted\b|\bretr(y|ies)\b",
    "충전기 정보 부정확": r"(status|availability|map|location|info(rmation)?|data|distance|figures|details|percentage)\w*\b.{0,25}(wrong|incorrect|inaccurate|out of date|outdated|not (accurate|updated|correct|real|up to date)|isn'?t (accurate|correct))|shows? (as )?(available|in use|occupied)|(wasn'?t|isn'?t|not) (there|accurate)|doesn'?t (show|register|find|list) (the )?(charger|station)|nowhere near|real.?time|availability|\baccurate\b|up to date",
    "대기·혼잡·점유": r"(wait|waited|waiting|queue|line)\b.{0,15}(charg|stall|station)|(chargers?|stalls?|stations?) (were |are |was )?(all )?(taken|occupied|in use|full|busy)|\bqueue|\biced\b|blocked by|hogg",
    "원격제어·차량연결": r"remote|precondition|climate|\bhvac\b|\block|unlock|\bdoors?\b|\bhorn\b|connect(ion|ed|ing)? (to|with) (the |my )?(car|vehicle|charger)|(car|vehicle) (is |was )?(offline|asleep|not (responding|connected|found))|wak(e|ing) (up )?(the |my )?(car|vehicle)|disconnect|command|timed? ?out|time ?out|connected (services|drive|car)|start (the |my )?car|engine start|heat(ed|er|ing)|\bvehicle status|\bstatus\b",
    "디지털키": r"digital key|phone (as )?(a )?key|key ?card|keyless|phone key|\bnfc\b|key fob",
    "경로·내비 (일반)": r"\broute|routing|navigation|\bnav\b|directions|trip plan|destination|waypoint|road ?trip|\bmaps?\b",
    "배터리·전비": r"battery (level|percent|%|health|status|capacity|charge|is)|\bbattery\b(?!.{0,20}(phone|power usage|life of my phone))|remaining range|range (left|remaining|estimate|is|shows)|efficien|kwh/100|mi/kwh|miles per kwh|state of charge|\bsoc\b|\b12v\b",
    "포인트·리워드·멤버십": r"reward|cash ?back|gift ?card|incentive|rebate|membership|loyalty|\bperks?\b|voucher|\bbonus|reward points|earn(ed|ing)? (points|credit)|points (balance|earned)",
    "UI·UX 불편": r"interface|\bui\b|\bux\b|layout|design|\bmenus?\b|intuitive|confusing|cluttered|hard to (find|use|navigate)|easy to use|easy to navigate|user.?friendly|redesign|dark mode|widget|\bfont|simple|clunky|look(s)? (great|good|nice)|\bclean\b",
    "고객센터·응대": r"customer (service|support|care)|\bsupport\b|contacted|call(ed)? (them|the|customer|their)|help ?desk|\bchat\b|no response|never (heard|responded|replied)|representative|\bagent\b|helpline|hotline",
    "알림": r"notification|notif|\balerts?\b|push\b|notify",
    "아파트·완속 요금 격차": r"(apartment|condo|flat|workplace)\b.{0,30}(price|expensive|cost|rate)|level ?2.{0,20}(price|expensive|cost)",
    "미출차·점유 수수료": r"idle fee|idle (charge|fees)|overstay|parking (fee|charge)|blocking fee",
    "리워드 축소·소멸 불만": r"(reward|points?|incentive|credit|cash ?back|bonus|gift ?card)s?.{0,30}(reduc|cut\b|removed|gone|expired|expire|never (received|got|paid|arrived)|not (received|paid)|haven'?t (received|got))",
    "충전 인프라(충전소 수·위치·속도)": r"(lots of|plenty of|many|more|few|not enough|lack of|number of|no|nearly no|a lot of)\b.{0,10}(charg|station|location)|coverage|\bnetwork\b|fast charg|rapid charg|slow charg|charging speed|charg\w* (is |are |was |were )?(ridiculously |very |so |really )?(slow|fast)|\bkw\b|\d+\s?kw\b|chargers? (everywhere|near|nearby|close)|infrastructure|easy to find|finding (a )?charg|underpowered",
}

HYPO = {
    "H1 요금 비교 어려움·손해": r"compar\w*.{0,15}(price|rate|cost)|cheapest (charger|station|rate|price)|price (difference|comparison)|member(ship)? (price|rate)|non.?member|roaming|hidden (fee|cost)|price (is |was )?(not )?(shown|displayed|listed)|didn'?t (show|know|see) the (price|cost)|doesn'?t show (the )?(price|cost)",
    "H2 장거리 충전 계획 부담": r"road ?trip|long (trip|drive|distance|journey)|plan(ning)? (my |a |the )?(trip|route|journey)|trip plan|route plan|along (the|my) (route|way)|highway|motorway|cross.?country",
    "H3 통행료·충전비 묶어 판단": r"\btolls?\b",
    "H4 충전 대기 지루함": r"(wait(ing)?|while charging).{0,30}(bored|boring|nothing to do|waste of time)|\bbored\b|\bboring\b",
    "H5 전비 저하 원인 모름": r"(efficien\w*|range|consumption).{0,30}(drop|worse|why|low|poor|terrible)|(winter|cold).{0,30}(range|efficien|battery)",
    "H6 리워드 반응": r"reward|cash ?back|gift ?card|incentive|rebate|\bbonus|\bperks?\b|voucher|loyalty|reward points|earn(ed|ing)? (points|credit)",
    "H6a 주행 연동 리워드(캐시백 드라이빙 등)": r"(miles|mileage|driv(ing|e))\b.{0,20}(reward|points|cash ?back)|reward.{0,20}(per mile|driving|drive)",
}

EV_FILTER = r""  # 해외는 내비 그룹이 없어 필터 없음
