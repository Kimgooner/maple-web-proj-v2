#!/usr/bin/env python3
"""HP 상한(50만) 아래의 데몬어벤져를 랭킹에서 찾아 픽스처로 받는다.

상위 랭킹 데벤은 전원 HP 50만에 걸려 있어 스탯창으로 HP 를 볼 수 없다. 전투력 식을
맞추려면 HP 가 보이는 표본이 필요하고, 그건 랭킹 40쪽(Lv.281) 아래에서부터 나온다.

  python3 tools/collect-daven-subcap.py <날짜> <출력 dir> [시작쪽] [끝쪽]
"""
import gzip, json, os, sys, time, threading, datetime as dt
from concurrent.futures import ThreadPoolExecutor
import urllib.request, urllib.parse, urllib.error

ROOT = "/Users/nubuli/Desktop/project/maple-web-proj-v2"
DATE = sys.argv[1]
OUT = sys.argv[2]
P0 = int(sys.argv[3]) if len(sys.argv) > 3 else 30
P1 = int(sys.argv[4]) if len(sys.argv) > 4 else 220
KEY = None
for line in open(os.path.join(ROOT, ".env")):
    if line.startswith("MAPLE_OPEN_API_KEY"):
        KEY = line.split("=", 1)[1].strip().strip('"').strip("'")
BASE = "https://open.api.nexon.com/maplestory/v1/"
ENDPOINTS = {
    "basic": "character/basic", "stat": "character/stat", "item-equipment": "character/item-equipment",
    "cashitem-equipment": "character/cashitem-equipment", "set-effect": "character/set-effect",
    "symbol-equipment": "character/symbol-equipment", "pet-equipment": "character/pet-equipment",
    "hyper-stat": "character/hyper-stat", "ability": "character/ability", "skill": "character/skill",
    "hexamatrix-stat": "character/hexamatrix-stat", "other-stat": "character/other-stat",
    "union-raider": "user/union-raider", "union-champion": "user/union-champion", "union-artifact": "user/union-artifact",
}

def fetch(path, params, tries=4):
    req = urllib.request.Request(BASE + path + "?" + urllib.parse.urlencode(params), headers={"x-nxopen-api-key": KEY})
    for i in range(tries):
        try:
            with urllib.request.urlopen(req, timeout=30) as r:
                return json.loads(r.read().decode("utf-8"))
        except urllib.error.HTTPError as e:
            if e.code in (429, 500, 502, 503): time.sleep(0.5 * (i + 1)); continue
            return {"__error__": f"HTTP {e.code}"}
        except Exception as e:
            time.sleep(0.5 * (i + 1))
    return {"__error__": "retry"}

lock = threading.Lock(); n_ok = 0

def probe_and_collect(entry):
    global n_ok
    name = entry["character_name"]
    idr = fetch("id", {"character_name": name})
    if "__error__" in idr: return None
    ocid = idr["ocid"]
    stat = fetch("character/stat", {"ocid": ocid, "date": DATE})
    if "__error__" in stat or not stat.get("final_stat"): return None
    st = {s["stat_name"]: s["stat_value"] for s in stat["final_stat"]}
    hp = int(st.get("HP") or 0); cp = int(float(st.get("전투력") or 0))
    if hp >= 500000 or cp <= 0 or hp == 0: return None
    docs = {"stat": stat}
    for key, path in ENDPOINTS.items():
        if key == "stat": continue
        params = {"ocid": ocid, "date": DATE}
        if key == "skill": params["character_skill_grade"] = "0"
        d = fetch(path, params)
        docs[key] = None if "__error__" in d else d
    basic = docs.get("basic") or {}
    out = {"job": "데몬어벤져", "characterName": name, "rank": entry["ranking"], "level": entry["character_level"],
           "world": basic.get("world_name") or entry.get("world_name"), "ocid": ocid, "documents": docs, "collectedAt": DATE}
    fn = f"데몬어벤져-{entry['ranking']}-{abs(hash(name)) % 100000}.json.gz"
    with gzip.open(os.path.join(OUT, fn), "wt", encoding="utf-8") as f:
        json.dump(out, f, ensure_ascii=False)
    with lock:
        n_ok += 1
        print(f"  + {name} Lv{entry['character_level']} HP {hp:,} 전투력 {cp:,}  ({n_ok})", flush=True)
    return name

os.makedirs(OUT, exist_ok=True)
entries = []
for page in range(P0, P1 + 1):
    r = fetch("ranking/overall", {"date": DATE, "class": "레지스탕스-데몬어벤져", "page": page}).get("ranking") or []
    r = [e for e in r if e.get("character_level", 0) >= 260]
    if not r: break
    entries += r
print(f"랭킹 {P0}~{P1}쪽 Lv260+ {len(entries)}명 조사 → {OUT}", flush=True)
t0 = time.time()
with ThreadPoolExecutor(max_workers=12) as ex:
    list(ex.map(probe_and_collect, entries))
print(f"done: HP<50만 {n_ok}명 in {time.time()-t0:.0f}s", flush=True)
