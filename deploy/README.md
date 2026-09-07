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
- 도메인 연결: `.env` 의 `SITE_ADDRESS` 를 도메인으로 바꾸고 `docker compose up -d caddy`. Caddy 가 인증서를 받는다.
- 로그: `docker compose logs -f app`

서버 쪽 전제: 80/443 이 firewalld 와 OCI 보안 목록(VCN) 양쪽에서 열려 있어야 한다.
