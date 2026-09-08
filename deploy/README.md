# 배포

`main` 에 푸시하면 `.github/workflows/deploy.yml` 이 돈다.

1. 프런트 테스트·빌드 → gradle 단위 테스트 → `bootJar` (골든 테스트는 픽스처가 로컬 전용이라 제외)
2. `Dockerfile` 로 arm64 이미지를 만들어 `ghcr.io/kimgooner/maple-web-proj-v2:{latest, sha-xxxxxxx}` 로 푸시
3. SSH 로 OCI 서버(`/opt/mapledelta`)에 compose 파일을 복사하고 `docker compose pull && up -d`

서버에는 이 디렉터리의 `docker-compose.yml`, `Caddyfile` 과 서버에만 있는 `.env`
(`.env.example` 참고) 가 있다. 넥슨 키는 `.env` 에만 둔다.

- GitHub 시크릿: `OCI_HOST`, `OCI_USER`, `OCI_SSH_KEY` (배포 전용 ed25519 키)
- 서버 GHCR 로그인은 배포 순간에만 워크플로 토큰으로 한다. 상시 로그인 없음.
- 되돌리기: 서버 `.env` 의 `APP_TAG` 를 `sha-xxxxxxx` 로 바꾸고 `docker compose up -d`
- 도메인: `mapledelta.kr` (www 포함). Caddy 가 Let's Encrypt 인증서를 받아 자동 갱신하고
  http 는 https 로 넘긴다. IP 직접 접속은 HTTP 로 남겨 둔다 (점검용).
- 로그: `docker compose logs -f app`

## Redis 캐시

데이터시트 캐시가 `redis` 컨테이너에 있다. 앱은 `MAPLE_CACHE_TYPE=redis` 로 켠다
(빼면 프로세스 메모리 캐시로 돌아간다 — 롤백 수단이기도 하다).
지나간 날짜는 넥슨이 같은 값을 주므로 30일, 오늘치는 6시간, 아직 굳지 않은 시점은
30분 둔다. 포트는 밖으로 내지 않고 compose 네트워크 안에서만 `redis:6379` 로 붙는다.
Redis 가 죽어도 앱은 캐시 미스로 넘어가 계속 뜬다 (느려질 뿐이다).

- 키 수: `docker compose exec redis redis-cli DBSIZE`
- 메모리: `docker compose exec redis redis-cli INFO memory`
- 캐시 비우기: `docker compose exec redis redis-cli FLUSHALL`
- 데이터시트 키만 보기: `docker compose exec redis redis-cli --scan --pattern 'maple:datasheet:v5:*'`

데이터시트 한 지점이 약 48KB 라 1GB 면 2만 지점(월간 조회 기준 2천 캐릭터쯤) 들어간다.
넘으면 오래 안 쓴 것부터 버린다(`allkeys-lru`). 5분마다 RDB 를 남겨 컨테이너를
재시작해도 캐시가 산다.

서버 쪽 전제: 80/443 이 firewalld 와 OCI 보안 목록(VCN) 양쪽에서 열려 있어야 한다.
