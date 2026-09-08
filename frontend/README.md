# frontend

MapleDelta 화면. React + Vite + TypeScript.

디자인은 `src/main/resources/static/index6.html` + `internal-dashboard/` 내부 미리보기에서
옮겨 왔다. 스타일(`src/index.css`)은 그 `dashboard.css` 를 편 것이고, 컴포넌트가 같은
클래스 이름을 쓴다. 원본과 다른 점은 둘이다 — 넥슨 전투력을 화면에 두지 않고,
솔 에르다 조각을 차트의 두 번째 축으로 그린다.

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
