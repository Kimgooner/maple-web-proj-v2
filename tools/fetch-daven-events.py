#!/usr/bin/env python3
"""데벤 자연 실험 2단계: 전투력이 바뀐 날의 전날/당일 문서를 통째로 받는다.

  python3 tools/fetch-daven-events.py build/daven/cp-days.json build/daven-events [최대변화율]
파일명: <이름>__<날짜>__a.json.gz (전날) / __b.json.gz (당일)
"""
import gzip, json, os, sys, time, datetime as dt, threading, math
from concurrent.futures import ThreadPoolExecutor
import urllib.request, urllib.parse, urllib.error
ROOT = "/Users/nubuli/Desktop/project/maple-web-proj-v2"
DAYS, OUT = sys.argv[1], sys.argv[2]; MAXCHG = float(sys.argv[3]) if len(sys.argv) > 3 else 0.03
KEY = None
for line in open(os.path.join(ROOT, ".env")):
    if line.startswith("MAPLE_OPEN_API_KEY"): KEY = line.split("=", 1)[1].strip().strip('"').strip("'")
BASE = "https://open.api.nexon.com/maplestory/v1/"
ENDPOINTS = {"basic": "character/basic", "stat": "character/stat", "item-equipment": "character/item-equipment",
    "cashitem-equipment": "character/cashitem-equipment", "set-effect": "character/set-effect", "symbol-equipment": "character/symbol-equipment",
    "pet-equipment": "character/pet-equipment", "hyper-stat": "character/hyper-stat", "ability": "character/ability", "skill": "character/skill",
    "hexamatrix-stat": "character/hexamatrix-stat", "other-stat": "character/other-stat", "union-raider": "user/union-raider",
    "union-champion": "user/union-champion", "union-artifact": "user/union-artifact"}
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
def snapshot(name, ocid, date):
    docs = {}
    for k, p in ENDPOINTS.items():
        params = {"ocid": ocid, "date": date}
        if k == "skill": params["character_skill_grade"] = "0"
        docs[k] = fetch(p, params)
    basic = docs.get("basic") or {}
    return {"job": "데몬어벤져", "characterName": name, "rank": 0, "level": basic.get("character_level") or 0, "world": basic.get("world_name"), "ocid": ocid, "documents": docs, "collectedAt": date}
data = json.load(open(DAYS)); os.makedirs(OUT, exist_ok=True)
jobs = []
for name, v in data.items():
    days = sorted(v["days"])
    for a, b in zip(days, days[1:]):
        if (dt.date.fromisoformat(b) - dt.date.fromisoformat(a)).days != 1: continue
        x, y = v["days"][a], v["days"][b]
        if x > 0 and y > 0 and x != y and abs(math.log(y/x)) <= MAXCHG:
            jobs.append((name, v["ocid"], a, b))
print(f"작은 변화(≤{MAXCHG*100:.0f}%) 사건 {len(jobs)}건 → 문서 {len(jobs)*30}회", flush=True)
lock = threading.Lock(); done = 0
def one(job):
    global done
    name, ocid, a, b = job
    for tag, date in (("a", a), ("b", b)):
        fn = os.path.join(OUT, f"{name}__{b}__{tag}.json.gz")
        if os.path.exists(fn): continue
        with gzip.open(fn, "wt", encoding="utf-8") as f: json.dump(snapshot(name, ocid, date), f, ensure_ascii=False)
    with lock:
        done += 1
        if done % 25 == 0: print(f"  .. {done}", flush=True)
with ThreadPoolExecutor(max_workers=16) as ex: list(ex.map(one, jobs))
print("done", flush=True)
