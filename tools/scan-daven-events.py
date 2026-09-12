#!/usr/bin/env python3
"""데벤 자연 실험 1단계: 캐릭터별로 날짜 범위의 stat 문서만 받아 전투력이 바뀐 날을 찾는다.

  python3 tools/scan-daven-events.py <픽스처dir> <시작일> <끝일> <출력 json>
출력: {name: {ocid, days: {date: 전투력}}}
"""
import gzip, json, os, sys, time, datetime as dt, threading
from concurrent.futures import ThreadPoolExecutor
import urllib.request, urllib.parse, urllib.error

ROOT = "/Users/nubuli/Desktop/project/maple-web-proj-v2"
SRC, D0, D1, OUT = sys.argv[1], dt.date.fromisoformat(sys.argv[2]), dt.date.fromisoformat(sys.argv[3]), sys.argv[4]
KEY = None
for line in open(os.path.join(ROOT, ".env")):
    if line.startswith("MAPLE_OPEN_API_KEY"): KEY = line.split("=", 1)[1].strip().strip('"').strip("'")
BASE = "https://open.api.nexon.com/maplestory/v1/"
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
metas = []
for name in sorted(os.listdir(SRC)):
    if name.endswith(".json.gz"):
        d = json.load(gzip.open(os.path.join(SRC, name), "rt", encoding="utf-8"))
        metas.append((d["characterName"], d["ocid"]))
dates = [(D0 + dt.timedelta(days=i)).isoformat() for i in range((D1 - D0).days + 1)]
print(f"{len(metas)}명 × {len(dates)}일", flush=True)
out = {}; lock = threading.Lock(); done = 0
def one(meta):
    global done
    name, ocid = meta; days = {}
    for date in dates:
        s = fetch("character/stat", {"ocid": ocid, "date": date})
        if s and s.get("final_stat"):
            v = next((x["stat_value"] for x in s["final_stat"] if x["stat_name"] == "전투력"), None)
            if v: days[date] = int(float(v))
    with lock:
        out[name] = {"ocid": ocid, "days": days}; done += 1
        if done % 20 == 0: print(f"  .. {done}", flush=True)
with ThreadPoolExecutor(max_workers=16) as ex: list(ex.map(one, metas))
json.dump(out, open(OUT, "w"), ensure_ascii=False)
changes = sum(1 for v in out.values() for a, b in zip(sorted(v["days"]), sorted(v["days"])[1:]) if v["days"][a] != v["days"][b])
print(f"done: 전투력이 바뀐 날 {changes}건", flush=True)
