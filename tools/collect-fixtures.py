#!/usr/bin/env python3
"""기준일 고정 재수집.

기존 픽스처의 (job, characterName, rank, level, world, ocid)를 그대로 쓰고,
15개 문서만 date=고정일로 다시 받아 새 디렉터리에 쓴다.
"""
import gzip, json, os, sys, time, threading
from concurrent.futures import ThreadPoolExecutor
import urllib.request, urllib.parse, urllib.error

ROOT = "/Users/nubuli/Desktop/project/maple-web-proj-v2"
SRC = os.path.join(ROOT, "src/test/resources/fixtures")
DATE = sys.argv[1] if len(sys.argv) > 1 else "2026-08-25"
OUT = sys.argv[2] if len(sys.argv) > 2 else f"/private/tmp/claude-501/-Users-nubuli-Desktop-project-maple-web-proj-v2/b898ba90-34b4-4024-b5a0-efa92f8e5741/scratchpad/fixtures-{DATE}"

KEY = None
for line in open(os.path.join(ROOT, ".env")):
    if line.startswith("MAPLE_OPEN_API_KEY"):
        KEY = line.split("=", 1)[1].strip().strip('"').strip("'")
assert KEY

BASE = "https://open.api.nexon.com/maplestory/v1/"
ENDPOINTS = {
    "basic": "character/basic",
    "stat": "character/stat",
    "item-equipment": "character/item-equipment",
    "cashitem-equipment": "character/cashitem-equipment",
    "set-effect": "character/set-effect",
    "symbol-equipment": "character/symbol-equipment",
    "pet-equipment": "character/pet-equipment",
    "hyper-stat": "character/hyper-stat",
    "ability": "character/ability",
    "skill": "character/skill",
    "hexamatrix-stat": "character/hexamatrix-stat",
    "other-stat": "character/other-stat",
    "union-raider": "user/union-raider",
    "union-champion": "user/union-champion",
    "union-artifact": "user/union-artifact",
}

lock = threading.Lock()
stats = {"ok": 0, "fail": 0}


def fetch(path, params, tries=4):
    q = urllib.parse.urlencode(params)
    req = urllib.request.Request(BASE + path + "?" + q, headers={"x-nxopen-api-key": KEY})
    last = None
    for i in range(tries):
        try:
            with urllib.request.urlopen(req, timeout=30) as r:
                return json.loads(r.read().decode("utf-8"))
        except urllib.error.HTTPError as e:
            body = e.read().decode("utf-8", "replace")
            last = f"HTTP {e.code} {body[:160]}"
            if e.code in (429, 500, 502, 503):
                time.sleep(0.5 * (i + 1))
                continue
            return {"__error__": last}
        except Exception as e:  # noqa
            last = repr(e)
            time.sleep(0.5 * (i + 1))
    return {"__error__": last}


def collect(meta):
    ocid = meta["ocid"]
    docs = {}
    errs = []
    for key, path in ENDPOINTS.items():
        params = {"ocid": ocid, "date": DATE}
        if key == "skill":
            params["character_skill_grade"] = "0"
        d = fetch(path, params)
        if "__error__" in d:
            errs.append(f"{key}: {d['__error__']}")
            d = None
        docs[key] = d
    out = dict(meta)
    out["documents"] = docs
    out["collectedAt"] = DATE
    # 랭킹의 world_name 은 시즌 당시 월드다. 챌린저스 시즌이 끝나 일반 월드로
    # 이관된 캐릭터는 랭킹과 현재 월드가 다르므로 basic 을 현재 월드로 삼는다.
    basic = docs.get("basic") or {}
    if basic.get("world_name"):
        out["world"] = basic["world_name"]
    with gzip.open(os.path.join(OUT, meta["__file__"]), "wt", encoding="utf-8") as f:
        json.dump({k: v for k, v in out.items() if k != "__file__"}, f, ensure_ascii=False)
    with lock:
        if errs:
            stats["fail"] += 1
            print(f"  ! {meta['job']} {meta['characterName']}: {'; '.join(errs[:3])}", flush=True)
        else:
            stats["ok"] += 1
            if stats["ok"] % 40 == 0:
                print(f"  .. {stats['ok']} done", flush=True)


def main():
    os.makedirs(OUT, exist_ok=True)
    metas = []
    for name in sorted(os.listdir(SRC)):
        if not name.endswith(".json.gz"):
            continue
        with gzip.open(os.path.join(SRC, name), "rt", encoding="utf-8") as f:
            d = json.load(f)
        metas.append({
            "__file__": name,
            "job": d["job"], "characterName": d["characterName"], "rank": d["rank"],
            "level": d["level"], "world": d["world"], "ocid": d["ocid"],
        })
    print(f"{len(metas)} characters -> {OUT} (date={DATE})", flush=True)
    t0 = time.time()
    with ThreadPoolExecutor(max_workers=24) as ex:
        list(ex.map(collect, metas))
    print(f"done ok={stats['ok']} fail={stats['fail']} in {time.time()-t0:.0f}s", flush=True)
    # index.json / expected.json 복사(선택 목록 유지)
    for extra in ("index.json",):
        src = os.path.join(SRC, extra)
        if os.path.exists(src):
            open(os.path.join(OUT, extra), "w", encoding="utf-8").write(open(src, encoding="utf-8").read())


main()
