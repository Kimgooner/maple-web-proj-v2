#!/usr/bin/env python3
"""기존 픽스처에 propensity 문서를 덧붙인다 (기준일 고정, 나머지 문서는 손대지 않는다).

데몬어벤져의 의지 HP 때문에 성향 엔드포인트를 스냅샷에 넣으면서, 이미 모아 둔
482건을 통째로 다시 받지 않으려고 만들었다. 이미 있는 픽스처는 건너뛴다.
"""
import gzip, json, os, sys, time
from concurrent.futures import ThreadPoolExecutor
import urllib.request, urllib.parse, urllib.error

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.environ.get("FIXTURE_SRC", os.path.join(ROOT, "src/test/resources/fixtures"))
KEY = None
for line in open(os.path.join(ROOT, ".env")):
    if line.startswith("MAPLE_OPEN_API_KEY"):
        KEY = line.split("=", 1)[1].strip().strip('"').strip("'")
assert KEY


def fetch(ocid, date):
    q = urllib.parse.urlencode({"ocid": ocid, "date": date})
    req = urllib.request.Request("https://open.api.nexon.com/maplestory/v1/character/propensity?" + q,
                                 headers={"x-nxopen-api-key": KEY})
    for i in range(4):
        try:
            with urllib.request.urlopen(req, timeout=30) as r:
                return json.loads(r.read().decode("utf-8"))
        except Exception:  # noqa
            time.sleep(0.5 * (i + 1))
    return None


def one(name):
    path = os.path.join(SRC, name)
    with gzip.open(path, "rt", encoding="utf-8") as f:
        d = json.load(f)
    if d.get("documents", {}).get("propensity"):
        return "skip"
    doc = fetch(d["ocid"], d.get("collectedAt") or sys.argv[1])
    if doc is None:
        return "fail"
    d["documents"]["propensity"] = doc
    with gzip.open(path, "wt", encoding="utf-8") as f:
        json.dump(d, f, ensure_ascii=False)
    return "ok"


names = sorted(n for n in os.listdir(SRC) if n.endswith(".json.gz"))
with ThreadPoolExecutor(max_workers=12) as ex:
    results = list(ex.map(one, names))
print({k: results.count(k) for k in ("ok", "skip", "fail")})
