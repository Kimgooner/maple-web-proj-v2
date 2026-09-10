# MapleDelta

**https://mapledelta.kr**

메이플스토리 캐릭터의 **실전 전투력**이 언제, 무엇 때문에 변했는지 보여주는 서비스입니다.
넥슨 OPEN API의 원본 데이터를 받아 보스 프리셋 기준으로 전투력을 다시 계산하고,
두 시점 사이에 바뀐 장비·스킬·심볼·세트·유니온을 이름 단위로 풀어 줍니다.

## 왜 다시 계산하나

OPEN API의 `character/stat`이 주는 전투력은 **조회 시점에 끼고 있던 프리셋** 기준입니다.
사냥 프리셋을 낀 채로 조회하면 실력과 무관한 낮은 값이 잡히고, 전투력 수치만 높이는
세팅은 실전 딜과 무관합니다. 그래서 프리셋별 원본을 전부 받아 규칙으로 보스 프리셋을
골라내고, 그 조합으로 전투력을 다시 계산합니다. 넥슨이 준 값은 버리지 않고 옆에 같이
보여 줍니다.

계산식은 랭킹 표본 9,000여 명으로 검증했고, 표본의 99% 이상에서 넥슨 값과 정수까지
일치합니다. 다른 경우는 대부분 넥슨의 stat 문서가 마지막 접속 시점에 멈춰 있어서입니다.

## 화면

1. **메인** — 닉네임을 넣으면 최근 30일과 12개월을 한 번에 조회합니다. 진행률은 팝업으로
   보이고, 끝나면 상세 화면으로 넘어갑니다.
2. **캐릭터 화면**
   - 헤더: 캐릭터 정보, 계산 전투력, 넥슨 전투력
   - 추이 차트: 전투력이 변한 지점마다 변화 핀. 핀을 누르면 직전 지점과 비교합니다.
     두 점을 골라 임의 구간을 비교하는 모드도 있습니다.
   - 변경 내역: 장비(아이콘 · 변경 유형 · 스탯 증감), 핵심(스킬 · 심볼 · 하이퍼스탯 · 어빌리티 ·
     세트 · 유니온 · 헥사를 항목 이름과 이전 → 이후 값으로), 캐시, 펫

## 구조

```
frontend/   React + Vite + TypeScript. 빌드하면 src/main/resources/static/app 으로 나간다
src/        Spring Boot 4 / WebFlux (전 구간 리액티브)
  external/nexon   OPEN API 경계 (엔드포인트 16종, 레이트 리미터)
  analysis         조회 유스케이스: 스냅샷 수집 → 프리셋 선택 → 시트 조립 → 계산 → 비교
  domain           원본 JSON 을 StatSheet 으로 바꾸는 파서들
  validation       랭킹 표본으로 계산식 오차를 재는 도구
deploy/     docker compose + Caddy. GitHub Actions 가 main 푸시마다 서버를 갱신한다
```

조회 한 건은 시점마다 넥슨 문서 15개를 받습니다. 추이는 SSE 로 최신 지점부터 흘려보내고,
데이터시트는 6시간 메모리 캐시에 둡니다.

### 주요 API

| 경로 | 설명 |
|---|---|
| `GET /api/analysis/combat-power/history?characterName&range=daily\|monthly` | 추이 (일간 30 / 월간 12 지점) |
| `GET /api/analysis/combat-power/history/stream` | 같은 조회를 SSE 로 (`meta` → `point`… → `done`) |
| `GET /api/analysis/combat-power/detail?ocid&previousDate&currentDate` | 두 시점 사이 변화 |
| `GET /api/validation/current-debug?characterName` | 현재 계산 내역 (디버그, `maple.validation.enabled=true` 일 때만) |

## 로컬 실행

```bash
# 백엔드 (Java 21). 저장소 루트 .env 의 MAPLE_OPEN_API_KEY 를 읽는다
./gradlew bootRun

# 프런트 (Node 22). /api 는 8080 으로 프록시된다
cd frontend && npm install && npm run dev   # http://localhost:5173
```

테스트는 `./gradlew test` 와 `cd frontend && npm test`. 골든 테스트는 로컬 픽스처가 필요해
CI 에서는 제외됩니다.

## 배포

`main` 에 푸시하면 [deploy 워크플로](.github/workflows/deploy.yml)가 프런트·백엔드를 빌드하고
arm64 이미지를 GHCR 에 올린 뒤 서버의 `docker compose` 를 갱신합니다. 자세한 건
[deploy/README.md](deploy/README.md).

## 아직 안 끝난 것

- 데몬어벤져는 HP 기반 계산식이라 아직 미완성입니다. 값이 넥슨과 크게 다를 수 있고
  화면에 "계산 정확도 낮음" 배지가 붙습니다. 그 밖의 직업(제논 · 제로 포함)은 완성됐습니다.
- 변화별 전투력 기여도(이 장비 교체로 몇 % 올랐나)는 아직 없습니다. 스탯 증감까지만 보입니다.

---

Data based on NEXON Open API
