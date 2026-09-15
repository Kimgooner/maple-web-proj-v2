#!/usr/bin/env python3
"""한 캐릭터를 날짜 구간으로 하루씩 통째로 받는다 (전투력 식을 한 캐릭터로 조이는 앵커 표본).

  python3 tools/fetch-daily.py <캐릭터명> <시작일> <종료일> <출력 디렉터리>
파일명: <이름>__<날짜>.json.gz. 픽스처 형식이라 AdHocCharacterCheckTest 로 바로 돌린다.
"""
import gzip, json, os, sys, time, datetime as dt
from concurrent.futures import ThreadPoolExecutor
import urllib.request, urllib.parse, urllib.error
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
NAME, START, END, OUT = sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4]
KEY = [l.split("=", 1)[1].strip().strip('"').strip("'") for l in open(os.path.join(ROOT, ".env")) if l.startswith("MAPLE_OPEN_API_KEY")][0]
BASE = "https://open.api.nexon.com/maplestory/v1/"
ENDPOINTS = {"basic": "character/basic", "stat": "character/stat", "item-equipment": "character/item-equipment",
    "cashitem-equipment": "character/cashitem-equipment", "set-effect": "character/set-effect", "symbol-equipment": "character/symbol-equipment",
    "pet-equipment": "character/pet-equipment", "hyper-stat": "character/hyper-stat", "ability": "character/ability", "skill": "character/skill",
    "hexamatrix-stat": "character/hexamatrix-stat", "other-stat": "character/other-stat", "union-raider": "user/union-raider",
    "union-champion": "user/union-champion", "union-artifact": "user/union-artifact", "propensity": "character/propensity"}
def fetch(path, params, tries=4):
    req = urllib.request.Request(BASE + path + "?" + urllib.parse.urlencode(params), headers={"x-nxopen-api-key": KEY})
    for i in range(tries):
        try:
            with urllib.request.urlopen(req, timeout=30) as r: return json.loads(r.read().decode("utf-8"))
        except urllib.error.HTTPError as e:
            if e.code in (429, 500, 502, 503): time.sleep(0.5*(i+1)); continue
            return None
        except Exception: time.sleep(0.5*(i+1))
    return None
ocid = fetch("id", {"character_name": NAME})["ocid"]
def one(date):
    docs = {}
    for k, p in ENDPOINTS.items():
        params = {"ocid": ocid, "date": date}
        if k == "skill": params["character_skill_grade"] = "0"
        docs[k] = fetch(p, params)
    basic = docs.get("basic") or {}
    if not basic.get("character_class"): return date, "empty"
    fx = {"job": basic["character_class"], "characterName": NAME, "rank": 0, "level": basic.get("character_level") or 0, "world": basic.get("world_name"), "ocid": ocid, "documents": docs, "collectedAt": date}
    with gzip.open(os.path.join(OUT, f"{NAME}__{date}.json.gz"), "wt", encoding="utf-8") as f: json.dump(fx, f, ensure_ascii=False)
    return date, "ok"
os.makedirs(OUT, exist_ok=True)
d0, d1 = dt.date.fromisoformat(START), dt.date.fromisoformat(END)
dates = [(d0 + dt.timedelta(days=i)).isoformat() for i in range((d1 - d0).days + 1)]
with ThreadPoolExecutor(8) as ex:
    res = list(ex.map(one, dates))
print({s: sum(1 for _, r in res if r == s) for s in ("ok", "empty")})
