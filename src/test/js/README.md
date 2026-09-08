# 백엔드 내부 대시보드 검증

백엔드 실행 후 `http://localhost:8080/index6.html`에서 닉네임을 검색한다.
`?characterName=닉네임&range=monthly`로 바로 열 수 있다. 기본 범위는 `daily`다.
정적 파일이므로 별도 프론트 빌드는 필요 없다. 실행 중 파일을 바꿨다면
`./gradlew processResources` 후 다시 열거나 `bootRun`을 다시 실행한다.
실제 조회에는 서버용 `NEXON_API_KEY`가 필요하다.

프로덕션의 현재 nginx는 `/api/`만 백엔드로 전달한다. 따라서 이 내부 페이지는
백엔드 직접 접속용이며, 지금 설정으로 `mapledelta.kr/index6.html`에 노출되지는 않는다.

## 순수 로직 테스트

저장소 루트에서:

```bash
node --test src/test/js/internal-dashboard.test.mjs
```

결측/0 구분, 기간 정렬, 기본 선택, 차트 결측 구간, 고정/퍼센트 스탯,
변경 건수와 이미지 URL 정책을 검증한다.

## 브라우저 테스트

Playwright와 Chromium이 설치된 환경에서 백엔드를 먼저 실행한다.
테스트는 history SSE와 detail 요청만 가로채며, HTML/CSS/JS는 실제 Spring에서 받는다.
외부 API 키나 넥슨 요청을 사용하지 않는다. 화면 캡처는 모의 데이터다.

```bash
node src/test/js/internal-dashboard-browser.cjs
```

선택 환경변수:

- `PLAYWRIGHT_MODULE_PATH`: 기본 `playwright`를 찾지 못할 때 패키지 절대 경로
- `CHROME_PATH`: Chromium 실행 파일 경로 (기본 macOS Google Chrome)
- `DASHBOARD_URL`: 백엔드 주소 (기본 `http://localhost:8080`)
- `DASHBOARD_SCREENSHOT_DIR`: 캡처 저장 경로 (기본 `/tmp/mapledelta-dashboard-qa`)

검증 항목: 데스크톱/390px 모바일, 조회/기간 전환, 카테고리 필터, 상세 펼침,
동일/역순 날짜 선택, 키보드 선택, 상세 오류/재시도/응답 역전, 빠른 재검색,
SSE 서버 오류/전송 실패/잘못된 JSON, 0/1개 지점, 결측·0 값, 데몬어벤져 배지,
API 문자열의 HTML 비실행.
