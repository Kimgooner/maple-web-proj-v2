# 배포

`main` 에 푸시하면 `.github/workflows/deploy.yml` 이 돈다.

1. 프런트 테스트·빌드 → gradle 단위 테스트 → `bootJar` (골든 테스트는 픽스처가 로컬 전용이라 제외)
2. arm64 이미지 두 개를 만들어 `{latest, sha-xxxxxxx}` 로 푸시
   - `Dockerfile` → `ghcr.io/kimgooner/maple-web-proj-v2` (앱)
   - `frontend/Dockerfile` → `ghcr.io/kimgooner/maple-web-proj-v2-web` (nginx + 프런트)
3. SSH 로 OCI 서버(`/opt/mapledelta`)에 compose 파일을 복사하고 `docker compose pull && up -d`

서버에는 이 디렉터리의 `docker-compose.yml`, `Caddyfile` 과 서버에만 있는 `.env`
(`.env.example` 참고) 가 있다. 넥슨 키는 `.env` 에만 둔다.

- GitHub 시크릿: `OCI_HOST`, `OCI_USER`, `OCI_SSH_KEY` (배포 전용 ed25519 키)
- 서버 GHCR 로그인은 배포 순간에만 워크플로 토큰으로 한다. 상시 로그인 없음.
- 도메인: `mapledelta.kr` (www 포함). Caddy 가 Let's Encrypt 인증서를 받아 자동 갱신하고
  http 는 https 로 넘긴다. IP 직접 접속은 HTTP 로 남겨 둔다 (점검용).
- 로그: `docker compose logs -f app`

## 되돌리기

배포한 것이 잘못됐을 때. **다시 배포해서 고치는 것보다 이쪽이 빠르다** — CI 가 이미지를
다시 만드는 데 몇 분이 걸리지만, 이미 올라가 있는 이미지로 돌아가는 데는 몇 초면 된다.

이미지는 커밋마다 `sha-xxxxxxx`(커밋 앞 7자)로 남아 있다. `latest` 와 별개라 지워지지 않는다.

```bash
# 1. 돌아갈 커밋을 고른다. 마지막으로 멀쩡했던 배포의 커밋이다.
gh run list --workflow deploy --limit 10

# 2. 서버에서 그 태그로 고정하고 올린다. 앱·web 이 같은 태그로 함께 움직인다.
ssh oci
cd /opt/mapledelta
echo 'APP_TAG=sha-646889c' >> .env
docker compose up -d

# 3. 확인
curl -sf -o /dev/null -w '%{http_code}\n' http://127.0.0.1/
docker compose logs --tail 50 app
```

**되돌린 뒤 다시 배포하기 전에 `.env` 의 `APP_TAG` 줄을 지워야 한다.** 남아 있으면 새 배포가
`docker compose up -d` 를 돌려도 고정된 옛 이미지가 그대로 뜬다 — 배포는 성공했다고 나오는데
화면은 안 바뀌는, 원인 찾기 제일 나쁜 상태가 된다.

```bash
sed -i '/^APP_TAG=/d' /opt/mapledelta/.env
```

되돌려도 **Redis 는 그대로 남는다.** 캐시 키에 버전이 붙어 있어서(`maple:datasheet:v11:`)
옛 버전이 v10 을 쓰면 v11 값은 안 읽히고 그냥 자리만 차지하다 TTL 로 사라진다. 레벨 구간
표본은 만료가 없어 그대로 살아 있고, 되돌린 버전에서도 그대로 읽힌다.

### 잠시 닫기

원인을 찾는 동안 화면을 내려 두려면:

```bash
cd /opt/mapledelta
docker compose stop web app     # Caddy 가 502 를 준다
docker compose start app web    # 다시 연다
```

## 레벨 구간 표본 배치

매주 **화요일 00:00 KST** 에 랭킹에서 레벨 구간(260~300, 5레벨 단위)마다 1,000명을 뽑아
전투력을 다시 재고, 상위 1/10/30% · 중앙값 · 평균을 주 단위로 쌓는다. 한 번에 약 25분,
넥슨 API 14만 회(일일 한도의 0.7%)를 쓴다.

- `MAPLE_LEVEL_BAND_ENABLED=true` 일 때만 돈다. 로컬·테스트 기본값은 꺼짐이다.
- 저장 키 `maple:levelband:v1:weeks` 에 **만료 없이** 넣는다. 지나간 주는 다시 잴 수 없다
  (랭킹은 그날의 것이다). Redis 정책이 `volatile-lru` 라 만료 없는 키는 안 밀려난다.
- 부팅 채우기는 **쌓인 주가 하나도 없을 때만** 돈다. 그래서 배포해도 다시 재지 않는다.
  `LevelBandStatsServiceTest`, `RedisMapleCacheNoExpiryTest` 가 이 둘을 지킨다.
- 월요일이 아닌 이유: 전일 데이터가 다음날 02시부터 열려서, 월요일 00시에는 토요일 것까지밖에
  못 본다. 하루 미루면 일요일까지 들어온다.
- 지금 쌓인 것 보기: `curl -s localhost/api/analysis/level-band-stats | jq '.weeks[-1]'`
- 강제로 다시 재려면 키를 지우고 앱을 재시작한다 (25분간 API 를 쓴다):
  `docker compose exec redis redis-cli DEL maple:levelband:v1:weeks && docker compose restart app`

## 방문 집계

캐릭터 조회 한 번마다 Redis 카운터를 올린다. **브라우저로 나가는 것이 없다** — 스크립트도,
쿠키도, 외부 업체도 붙이지 않았다. 그래서 동의 배너가 필요 없고 숫자는 서버 밖으로 안 나간다.

세는 것은 추이 조회(`/api/analysis/combat-power/history`)뿐이다. 화면이 캐릭터 하나를 열 때
반드시 지나는 길이고, 상세 조회는 그 뒤에 여러 번 따라붙어 같이 세면 한 사람이 여러 번으로 잡힌다.

```bash
ssh oci
cd /opt/mapledelta
D=$(date +%F)
docker compose exec -T redis redis-cli GET     "maple:visit:v1:$D:lookups"     # 조회 횟수
docker compose exec -T redis redis-cli PFCOUNT "maple:visit:v1:$D:visitors"    # 고유 방문자
docker compose exec -T redis redis-cli PFCOUNT "maple:visit:v1:$D:characters"  # 고유 캐릭터
```

최근 2주를 한 번에:

```bash
for i in $(seq 0 13); do
  D=$(date -d "-$i day" +%F 2>/dev/null || date -v-${i}d +%F)
  printf '%s  조회 %-6s 방문자 %-5s 캐릭터 %s\n' "$D" \
    "$(docker compose exec -T redis redis-cli GET     maple:visit:v1:$D:lookups)" \
    "$(docker compose exec -T redis redis-cli PFCOUNT maple:visit:v1:$D:visitors)" \
    "$(docker compose exec -T redis redis-cli PFCOUNT maple:visit:v1:$D:characters)"
done
```

- 고유 수는 **HyperLogLog** 다. 값을 통째로 담지 않고 스케치만 남겨서 하루치가 수 KB 를
  넘지 않고, **넣은 값을 되꺼낼 수 없다** — 방문자 목록이 남지 않는다. 대신 근사치다(오차 약 0.8%).
- 방문자 구분은 **IP 에 날짜를 섞어 해시한 앞 16자**다. 날이 바뀌면 같은 사람도 다른 값이 되어,
  날짜를 가로질러 한 사람을 따라갈 수 없다. 하루치 고유 수를 세는 데는 충분하고 그 이상은 못 한다.
- **만료를 걸지 않는다.** 지나간 날의 방문 수는 다시 만들 수 없고, Redis 가 `volatile-lru` 라
  TTL 없는 키는 안 밀려난다. 하루 세 키라 1년을 쌓아도 수 MB 다.
- 집계 실패는 삼킨다. 통계를 못 세는 것이 조회를 막을 이유가 되지 않는다.
- 로컬(`MAPLE_CACHE_TYPE=memory`)에서는 아무것도 세지 않는다.

## 요청이 지나는 길

```
브라우저 → Caddy(TLS) → web(nginx) ─┬─ /       정적 파일, /c/이름 은 index.html
                                    └─ /api/   app(Spring, 8080)
```

Caddy 는 TLS 만 맡는다. 정적 파일 서빙과 경로 라우팅 fallback 은 `frontend/nginx.conf`
에 있고, Spring 은 화면을 서빙하지 않는다 — `/app/` 을 아는 것은 이제 아무도 없어서,
옛 주소로 들어오면 nginx 가 `index.html` 을 주고 화면이 홈으로 넘긴다.
SSE 가 실시간으로 흐르려면 Caddy 의 `flush_interval -1` 과 nginx 의
`proxy_buffering off` 가 둘 다 있어야 한다. 하나라도 빠지면 진행률이 0% 에서
멈췄다가 한 번에 끝난다.

## Redis 캐시

캐시가 `redis` 컨테이너에 있고, 쓰임이 다른 두 종류가 산다.

| 키 | 무엇 | TTL | 크기 | 쓰는 곳 |
|---|---|---|---|---|
| `maple:history:v5:<ocid>:<date>` | 추이 지점 (레벨·전투력·솔 에르다 조각) | 아래 참고 | 약 200B | 차트 (SSE) |
| `maple:datasheet:v11:<ocid>:<date>` | 데이터시트 통째 | 아래 참고 | 약 43KB | 구간 상세 팝업, monthly·yearly |
| `maple:ocid:<이름>` | 캐릭터 ocid | 12시간 | 작음 | 전부 |
| `maple:championroster:v1:<ocid>` | 유니온 챔피언 명단 | 6시간 | 작음 | 명단이 빈 날 보정 |
| `maple:levelband:v1:weeks` | 레벨 구간 표본 (주 단위, 60주) | **없음** | 수십 KB | 차트 기준선 |

데이터시트·추이는 지나간 날짜 30일, 오늘치 6시간, 아직 굳지 않은 시점 30분이다.

차트는 숫자 몇 개만 필요해서 작은 쪽을 쓴다. 일간 30지점을 다 받아도 6KB 다.
**계산 규칙(전투력식·헥사 조각표)을 바꾸면 두 접두사 버전을 같이 올려야 한다** — 안 그러면 고친 값이
30일 동안 안 보인다. 버전을 올리면 배포 직후 조회가 전부 콜드라 느리다. 앱은 `MAPLE_CACHE_TYPE=redis` 로 켠다
(빼면 프로세스 메모리 캐시로 돌아간다 — 롤백 수단이기도 하다).
지나간 날짜는 넥슨이 같은 값을 주므로 30일, 오늘치는 6시간, 아직 굳지 않은 시점은
30분 둔다. 포트는 밖으로 내지 않고 compose 네트워크 안에서만 `redis:6379` 로 붙는다.
Redis 가 죽어도 앱은 캐시 미스로 넘어가 계속 뜬다 (느려질 뿐이다).

- 키 수: `docker compose exec redis redis-cli DBSIZE`
- 메모리: `docker compose exec redis redis-cli INFO memory`
- 캐시 비우기: `docker compose exec redis redis-cli FLUSHALL`
- 종류별 키 보기: `docker compose exec redis redis-cli --scan --pattern 'maple:history:v5:*'`
  (`maple:datasheet:v11:*`, `maple:ocid:*` 도 같은 식으로)
- 만료가 안 걸렸는지 확인: `docker compose exec redis redis-cli TTL maple:levelband:v1:weeks`
  → `-1` 이어야 한다 (`-2` 는 키가 없다는 뜻)

데이터시트 한 지점이 약 43KB 라 1GB 면 2만 지점 남짓 들어간다 (추이 지점은 200B 라
사실상 상한에 안 걸린다).
넘으면 오래 안 쓴 것부터 버리는데, **만료가 걸린 키만** 버린다(`volatile-lru`).
레벨 구간 표본은 만료 없이 넣어 두어 메모리가 차도 남는다 — `allkeys-lru` 였다면 이게
밀려났을 때 다음 부팅에 8,000명을 다시 재게 된다. 5분마다 RDB 를 남겨 컨테이너를
재시작해도 캐시가 산다.

서버 쪽 전제: 80/443 이 firewalld 와 OCI 보안 목록(VCN) 양쪽에서 열려 있어야 한다.
