# frontend

전투력 변화 뷰어 화면. React + Vite + TypeScript.

```bash
npm install
npm run dev      # http://localhost:5173/  (/api 는 8080 으로 프록시)
npm test         # vitest, 순수 로직만
npm run build    # dist/ 로 출력 (gitignore)
```

백엔드는 `NEXON_API_KEY=... ./gradlew bootRun` 으로 따로 띄운다. Spring 은 화면을
서빙하지 않는다 — 배포에서는 `frontend/Dockerfile` 로 만든 nginx 컨테이너가
`dist/` 를 서빙하고 `/api` 만 앱으로 넘긴다 (`frontend/nginx.conf`).

API 명세는 `.plan/general/2026-09-04-frontend-api-handoff.md`, 화면 구상은
`.plan/general/2026-09-04-frontend-concept.md`.
