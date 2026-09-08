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
- 되돌리기: 서버 `.env` 의 `APP_TAG` 를 `sha-xxxxxxx` 로 바꾸고 `docker compose up -d`
  (`APP_TAG` 하나가 앱·web 두 이미지를 같이 가리킨다 — 둘은 같이 움직인다)
- 도메인: `mapledelta.kr` (www 포함). Caddy 가 Let's Encrypt 인증서를 받아 자동 갱신하고
  http 는 https 로 넘긴다. IP 직접 접속은 HTTP 로 남겨 둔다 (점검용).
- 로그: `docker compose logs -f app`

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

| 키 | 무엇 | 크기 | 쓰는 곳 |
|---|---|---|---|
| `maple:history:v2:<ocid>:<date>` | 추이 지점 (레벨·전투력·솔 에르다 조각) | 약 200B | 차트 (SSE) |
| `maple:datasheet:v5:<ocid>:<date>` | 데이터시트 통째 | 약 43KB | 구간 상세 팝업, monthly·yearly |
| `maple:ocid:<이름>` | 캐릭터 ocid | 작음 | 전부 |

차트는 숫자 몇 개만 필요해서 작은 쪽을 쓴다. 일간 30지점을 다 받아도 6KB 다.
**계산 규칙(전투력식·헥사 조각표)을 바꾸면 두 접두사 버전을 같이 올려야 한다** — 안 그러면 고친 값이
30일 동안 안 보인다. 앱은 `MAPLE_CACHE_TYPE=redis` 로 켠다
(빼면 프로세스 메모리 캐시로 돌아간다 — 롤백 수단이기도 하다).
지나간 날짜는 넥슨이 같은 값을 주므로 30일, 오늘치는 6시간, 아직 굳지 않은 시점은
30분 둔다. 포트는 밖으로 내지 않고 compose 네트워크 안에서만 `redis:6379` 로 붙는다.
Redis 가 죽어도 앱은 캐시 미스로 넘어가 계속 뜬다 (느려질 뿐이다).

- 키 수: `docker compose exec redis redis-cli DBSIZE`
- 메모리: `docker compose exec redis redis-cli INFO memory`
- 캐시 비우기: `docker compose exec redis redis-cli FLUSHALL`
- 종류별 키 보기: `docker compose exec redis redis-cli --scan --pattern 'maple:history:v2:*'`
  (`maple:datasheet:v5:*`, `maple:ocid:*` 도 같은 식으로)

데이터시트 한 지점이 약 43KB 라 1GB 면 2만 지점 남짓 들어간다 (추이 지점은 200B 라
사실상 상한에 안 걸린다).
넘으면 오래 안 쓴 것부터 버린다(`allkeys-lru`). 5분마다 RDB 를 남겨 컨테이너를
재시작해도 캐시가 산다.

서버 쪽 전제: 80/443 이 firewalld 와 OCI 보안 목록(VCN) 양쪽에서 열려 있어야 한다.
