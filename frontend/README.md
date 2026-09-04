# frontend

전투력 변화 뷰어 화면. React + Vite + TypeScript.

```bash
npm install
npm run dev      # http://localhost:5173/app/  (/api 는 8080 으로 프록시)
npm test         # vitest, 순수 로직만
npm run build    # ../src/main/resources/static/app 으로 출력 (gitignore)
```

백엔드는 `NEXON_API_KEY=... ./gradlew bootRun` 으로 따로 띄운다. 빌드 뒤에는
Spring 이 `http://localhost:8080/app/` 에서 같은 화면을 서빙한다.

API 명세는 `.plan/general/2026-09-04-frontend-api-handoff.md`, 화면 구상은
`.plan/general/2026-09-04-frontend-concept.md`.
