#!/usr/bin/env python3
"""직업별 랭킹에서 후보를 뽑아 기준일 스냅샷을 수집한다.

사용: candidates.py <날짜> <출력경로> <직업:필요수> ...
예:   candidates.py 2026-08-24 /tmp/staging 아델:2 팬텀:1
"""
import gzip, json, os, sys, time, threading, urllib.request, urllib.parse, urllib.error
from concurrent.futures import ThreadPoolExecutor

ROOT = "/Users/nubuli/Desktop/project/maple-web-proj-v2"
FIXTURES = os.path.join(ROOT, "src/test/resources/fixtures")
KEY = next(l.split("=", 1)[1].strip().strip('"').strip("'")
           for l in open(os.path.join(ROOT, ".env")) if l.startswith("MAPLE_OPEN_API_KEY"))
BASE = "https://open.api.nexon.com/maplestory/v1/"

# 랭킹 class 필터. 단일 직업은 "{직업}-전체 전직", 전직이 갈리는 직업은 "{직업군}-{전직}".
RANK_CLASS = {
    "데몬슬레이어": "레지스탕스-데몬슬레이어", "데몬어벤져": "레지스탕스-데몬어벤져",
    "메카닉": "레지스탕스-메카닉", "배틀메이지": "레지스탕스-배틀메이지",
    "블래스터": "레지스탕스-블래스터", "와일드헌터": "레지스탕스-와일드헌터",
    "제논": "레지스탕스-제논",
    "나이트워커": "기사단-나이트워커", "미하일": "기사단-미하일", "소울마스터": "기사단-소울마스터",
    "스트라이커": "기사단-스트라이커", "윈드브레이커": "기사단-윈드브레이커",
    "플레임위자드": "기사단-플레임위자드",
    "보우마스터": "궁수-보우마스터", "신궁": "궁수-신궁", "패스파인더": "궁수-패스파인더",
    "나이트로드": "도적-나이트로드", "듀얼블레이더": "도적-듀얼블레이더", "섀도어": "도적-섀도어",
    "비숍": "마법사-비숍", "아크메이지(불,독)": "마법사-아크메이지(불,독)",
    "아크메이지(썬,콜)": "마법사-아크메이지(썬,콜)",
    "다크나이트": "전사-다크나이트", "팔라딘": "전사-팔라딘", "히어로": "전사-히어로",
    "바이퍼": "해적-바이퍼", "캐논마스터": "해적-캐논마스터", "캡틴": "해적-캡틴",
    "키네시스": "프렌즈 월드-키네시스", "제로": "초월자-제로",
}
ENDPOINTS = {
    "basic": "character/basic", "stat": "character/stat",
    "item-equipment": "character/item-equipment", "cashitem-equipment": "character/cashitem-equipment",
    "set-effect": "character/set-effect", "symbol-equipment": "character/symbol-equipment",
    "pet-equipment": "character/pet-equipment", "hyper-stat": "character/hyper-stat",
    "ability": "character/ability", "skill": "character/skill",
    "hexamatrix-stat": "character/hexamatrix-stat", "other-stat": "character/other-stat",
    "union-raider": "user/union-raider", "union-champion": "user/union-champion",
    "union-artifact": "user/union-artifact",
}
lock = threading.Lock()


def call(path, params, tries=4):
    q = urllib.parse.urlencode(params)
    req = urllib.request.Request(BASE + path + "?" + q, headers={"x-nxopen-api-key": KEY})
    for i in range(tries):
        try:
            with urllib.request.urlopen(req, timeout=30) as r:
                return json.loads(r.read().decode("utf-8"))
        except urllib.error.HTTPError as e:
            if e.code in (429, 500, 502, 503):
                time.sleep(0.4 * (i + 1)); continue
            return None
        except Exception:
            time.sleep(0.4 * (i + 1))
    return None


def rank_filter(job):
    return RANK_CLASS.get(job, f"{job}-전체 전직")


def used_names():
    names = set()
    for f in os.listdir(FIXTURES):
        if f.endswith(".json.gz"):
            with gzip.open(os.path.join(FIXTURES, f), "rt", encoding="utf-8") as fh:
                names.add(json.load(fh)["characterName"])
    return names


def collect(meta, date, out):
    docs, errs = {}, []
    for key, path in ENDPOINTS.items():
        params = {"ocid": meta["ocid"], "date": date}
        if key == "skill":
            params["character_skill_grade"] = "0"
        d = call(path, params)
        if d is None:
            errs.append(key)
        docs[key] = d
    payload = {k: v for k, v in meta.items() if k != "__file__"}
    payload["documents"] = docs
    payload["collectedAt"] = date
    with gzip.open(os.path.join(out, meta["__file__"]), "wt", encoding="utf-8") as f:
        json.dump(payload, f, ensure_ascii=False)
    with lock:
        print(f"  {meta['job']:14} {meta['characterName']:14} rank={meta['rank']:<5}"
              f"{'  실패:' + ','.join(errs) if errs else ''}", flush=True)


def main():
    date, out = sys.argv[1], sys.argv[2]
    want = dict(a.split(":") for a in sys.argv[3:])
    os.makedirs(out, exist_ok=True)
    used = used_names()
    metas = []
    for job, n in want.items():
        n = int(n)
        picked = 0
        for page in range(1, 4):
            if picked >= n:
                break
            r = call("ranking/overall", {"date": date, "class": rank_filter(job), "page": page})
            rows = (r or {}).get("ranking", [])
            if not rows:
                print(f"!! {job}: 랭킹 없음 (필터 {rank_filter(job)})"); break
            for e in rows:
                if picked >= n:
                    break
                if e["character_name"] in used:
                    continue
                ocid = call("id", {"character_name": e["character_name"]})
                if not ocid or not ocid.get("ocid"):
                    continue
                metas.append({
                    "__file__": f"{job}-cand-{e['ranking']}.json.gz",
                    "job": job, "characterName": e["character_name"], "rank": e["ranking"],
                    "level": e["character_level"], "world": e["world_name"], "ocid": ocid["ocid"],
                })
                used.add(e["character_name"]); picked += 1
    print(f"후보 {len(metas)}명 수집 -> {out}", flush=True)
    with ThreadPoolExecutor(16) as ex:
        list(ex.map(lambda m: collect(m, date, out), metas))


main()
