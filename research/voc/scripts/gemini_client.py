"""Gemini REST 호출 공용 모듈. 키는 research/voc/.env 의 GEMINI_API_KEY 에서만 읽고 출력하지 않는다."""
import json
import os
import time
import urllib.error
import urllib.request

ENV = os.path.join(os.path.dirname(__file__), "..", ".env")
BASE = "https://generativelanguage.googleapis.com/v1beta/models"


def _key():
    with open(ENV, encoding="utf-8") as f:
        for line in f:
            if line.startswith("GEMINI_API_KEY="):
                return line.split("=", 1)[1].strip()
    raise RuntimeError("GEMINI_API_KEY 없음")


def generate(model, prompt, schema=None, temperature=0.0, retries=6):
    body = {"contents": [{"parts": [{"text": prompt}]}],
            "generationConfig": {"temperature": temperature}}
    if schema:
        body["generationConfig"].update({"responseMimeType": "application/json", "responseSchema": schema})
    req = urllib.request.Request(f"{BASE}/{model}:generateContent", data=json.dumps(body).encode(),
                                 headers={"Content-Type": "application/json", "x-goog-api-key": _key()})
    wait = 10
    for attempt in range(retries):
        try:
            with urllib.request.urlopen(req, timeout=180) as r:
                d = json.load(r)
            return d["candidates"][0]["content"]["parts"][0]["text"], d.get("usageMetadata", {})
        except urllib.error.HTTPError as e:
            msg = e.read().decode("utf-8", "ignore")[:300]
            if e.code in (429, 500, 503) and attempt < retries - 1:
                time.sleep(wait)
                wait = min(wait * 2, 120)
                continue
            raise RuntimeError(f"HTTP {e.code}: {msg}") from None
        except (urllib.error.URLError, TimeoutError, KeyError) as e:
            if attempt < retries - 1:
                time.sleep(wait)
                continue
            raise RuntimeError(str(e)) from None
