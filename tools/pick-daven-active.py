#!/usr/bin/env python3
"""데벤 랭킹 상위에서 '최근에 실제로 바뀌는' 캐릭터를 고른다 (자연 실험 표본 확장).
  python3 tools/pick-daven-active.py <페이지수> <출력 fixture dir>
두 날짜(30일 전, 어제) stat 의 전투력이 다르고 둘 다 1e7 이상이면 활동 중으로 보고, 어제 문서를 픽스처로 받는다."""
import gzip, json, os, sys, time, datetime as dt, threading
from concurrent.futures import ThreadPoolExecutor
import urllib.request, urllib.parse, urllib.error
ROOT = "/Users/nubuli/Desktop/project/maple-web-proj-v2"; PAGES = int(sys.argv[1]); OUT = sys.argv[2]
KEY = None
for line in open(os.path.join(ROOT, ".env")):
    if line.startswith("MAPLE_OPEN_API_KEY"): KEY = line.split("=", 1)[1].strip().strip('"').strip("'")
BASE = "https://open.api.nexon.com/maplestory/v1/"
ENDPOINTS = {"basic": "character/basic", "stat": "character/stat", "item-equipment": "character/item-equipment", "cashitem-equipment": "character/cashitem-equipment", "set-effect": "character/set-effect", "symbol-equipment": "character/symbol-equipment", "pet-equipment": "character/pet-equipment", "hyper-stat": "character/hyper-stat", "ability": "character/ability", "skill": "character/skill", "hexamatrix-stat": "character/hexamatrix-stat", "other-stat": "character/other-stat", "union-raider": "user/union-raider", "union-champion": "user/union-champion", "union-artifact": "user/union-artifact"}
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
Y = (dt.date.today() - dt.timedelta(days=1)).isoformat(); M = (dt.date.today() - dt.timedelta(days=31)).isoformat()
existing = set()
for d in ("build/dev-daven",):
    for f in os.listdir(os.path.join(ROOT, d)):
        if f.endswith(".json.gz"): existing.add(json.load(gzip.open(os.path.join(ROOT, d, f)))["characterName"])
entries = []
for p in range(1, PAGES + 1):
    r = fetch("ranking/overall", {"date": Y, "class": "레지스탕스-데몬어벤져", "page": p}) or {}
    entries += [e for e in r.get("ranking") or [] if e["character_level"] >= 260 and e["character_name"] not in existing]
print(f"후보 {len(entries)}명 (기존 209 제외)", flush=True)
os.makedirs(OUT, exist_ok=True); lock = threading.Lock(); n_ok = 0
def cp(ocid, date):
    s = fetch("character/stat", {"ocid": ocid, "date": date})
    if not s or not s.get("final_stat"): return 0
    return int(float(next((x["stat_value"] for x in s["final_stat"] if x["stat_name"] == "전투력"), 0) or 0))
def one(e):
    global n_ok
    idr = fetch("id", {"character_name": e["character_name"]})
    if not idr: return
    ocid = idr["ocid"]; a, b = cp(ocid, M), cp(ocid, Y)
    if a < 1e7 or b < 1e7 or a == b: return
    docs = {}
    for k, path in ENDPOINTS.items():
        params = {"ocid": ocid, "date": Y}
        if k == "skill": params["character_skill_grade"] = "0"
        docs[k] = fetch(path, params)
    basic = docs.get("basic") or {}
    out = {"job": "데몬어벤져", "characterName": e["character_name"], "rank": e["ranking"], "level": e["character_level"], "world": basic.get("world_name"), "ocid": ocid, "documents": docs, "collectedAt": Y}
    with gzip.open(os.path.join(OUT, f"데몬어벤져-{e['ranking']}-{abs(hash(e['character_name'])) % 100000}.json.gz"), "wt", encoding="utf-8") as f: json.dump(out, f, ensure_ascii=False)
    with lock:
        n_ok += 1
        if n_ok % 50 == 0: print(f"  .. {n_ok}", flush=True)
with ThreadPoolExecutor(max_workers=16) as ex: list(ex.map(one, entries))
print(f"done: 활동 중 {n_ok}명", flush=True)
