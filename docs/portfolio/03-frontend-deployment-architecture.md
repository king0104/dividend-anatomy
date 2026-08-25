# 03. 프론트엔드 배포 아키텍처 — Docker 통합안을 Vercel 분리 배포로 되돌린 과정

**날짜**: 2026-08-25
**관련 커밋**: `722ead8`(React 프론트엔드 + Vercel 배포), `0a08b04`(자동배포 검증용 빈 커밋)
**관련 문서**: [12. 배당 안전도 스코어 데이터 소스](../decisions/12-dividend-safety-score-data-source.md),
[13. 타임머신 가격 수집 기간](../decisions/13-timemachine-price-window.md)의 후속 작업

## 문제

타임머신 시뮬레이터 백엔드(`47192f2`)를 끝내고 React 프론트엔드를 계획하면서,
처음엔 "기존 Spring Boot Docker 이미지 안에 프론트 빌드 산출물을 같이 넣는다"는
계획을 세우고 Plan Mode 승인까지 받았다. 근거는 "예전에 별도 FastAPI 서비스를
만들지 않기로 했다"는 과거 결정이었는데 — 실제로 짜고 보니 이 근거가 틀렸다.
**"백엔드를 하나로 유지한다"와 "프론트도 같은 배포 단위에 넣는다"는 서로 다른
결정인데 하나로 뭉뚱그린 것**이다. 사용자가 "프론트엔드는 별개로 배포하는게
좋은 거 아니야? 백엔드랑 같은 도커 파일에 넣을거야?"라고 지적하고 나서야
이 혼동을 알아챘다.

## 조치

**1. 배포 단위 재설계**: Docker 멀티스테이지에 Node 빌드 스테이지를 추가하려던
계획을 버리고, `frontend/`를 Vercel에 독립 배포하기로 다시 계획했다(같은
세션에서 Plan Mode를 두 번 들어감 — 처음 승인된 계획을 실행하기 전에 사용자
피드백으로 뒤엎은 경우).

**2. 예상 못한 진짜 기술적 장벽 — mixed content**: Vercel 독립 배포로 방향을
바꾸자마자 새로운 문제가 나왔다. 백엔드가 평문 HTTP NodePort
(`144.24.86.105:30081`)로만 노출되는데, Vercel은 HTTPS로만 서빙한다 — HTTPS
페이지에서 HTTP API를 직접 호출하면 브라우저 mixed-content 정책에 막힌다.
CORS를 백엔드에 추가하는 걸로는 이 문제를 풀 수 없다(CORS는 origin 허용
문제고, mixed-content는 프로토콜 자체를 막는 문제라 계층이 다르다).

**해결**: Vercel의 `rewrites`로 `/api/*`를 백엔드 NodePort URL에
서버사이드 프록시했다(`frontend/vercel.json`). 브라우저는 항상 Vercel
도메인(HTTPS) 하나만 보고, 실제 HTTP 호출은 Vercel 엣지가 서버 대 서버로
수행한다 — 그 결과 CORS 설정도, 백엔드 TLS 인증서도 전혀 필요 없어졌다.
동시에 이 설계는 프론트 코드를 더 단순하게 만들었다: `api/timemachine.ts`는
dev/prod 환경변수 분기 없이 항상 상대경로 `/api/...`만 호출한다(로컬은 Vite
dev proxy, 배포본은 Vercel rewrite가 각각 라우팅).

**3. 캔버스 공유 이미지의 로고 왜곡 버그**: 화면4(1080×1080 공유 이미지)의
로고를 정사각형 영역에 `drawImage(logo, x, y, size, size)`로 그렸는데,
실제 로고 파일(위키미디어 커먼즈에서 받음)의 가로세로 비율이 브랜드마다
전혀 달랐다 — 존슨앤드존슨(500×91), 코카콜라(500×157)처럼 매우 넓적한
로고가 정사각형으로 눌려 찌그러졌다. 8개 로고 파일을 실제로 받아서
`Read` 도구로 하나하나 렌더링해보다가 발견했다 — placeholder 텍스트
배지로만 테스트했다면 절대 못 잡았을 버그다. 원본 비율을 유지한 채 원
안에 맞추도록 스케일 계산을 고쳤다.

**4. 로그인 없이 먼저 배포 → 나중에 계정 연결**: 사용자가 Vercel을 써본 적이
없어서 계정 인증을 어떻게 처리할지가 문제였다. `vercel deploy --temporary`가
로그인 없이 즉시 배포하고 60분짜리 익명 URL과 "claim" 링크를 주는 기능이라는
걸 CLI 에러 메시지에서 발견하고 이걸로 먼저 배포부터 끝냈다 — 사용자는 claim
링크만 클릭해서 계정에 연결하면 됐다.

**5. 자동배포 설정 중 대시보드 UI 미스매치**: 자동배포를 걸려면 Vercel
프로젝트의 "Root Directory"를 `frontend`로 지정해야 하는데(이 저장소는
백엔드+프론트가 한 저장소에 있는 모노레포 구조), 사용자 화면에는 그 설정
필드 자체가 안 보였다 — 대시보드 UI 버전 차이인지 프로젝트 상태 때문인지
원격으로는 확인할 방법이 없었다. 화면을 못 보는 상태로 대시보드 경로를
계속 추측해서 안내하는 대신, `vercel login`(디바이스 인증 코드 방식)으로
사용자에게 이 기기의 CLI만 인증시키고, 그 뒤로는 `vercel project update
--root-directory frontend --framework vite`로 CLI/API를 통해 직접 설정했다
— UI를 못 보는 문제를 CLI로 완전히 우회한 것.

## 검증

**API 체인 전체를 실제 환경에서 확인**: 로컬 Vite dev server 프록시
단계뿐 아니라, **실제 배포된 HTTPS Vercel URL**에서도 `/api/tickers/KO/timemachine`을
호출해 응답을 받았다 — 코카콜라 10년/일시불/50만원 케이스가
`finalValueReinvestKrw=1405022, finalValueNoReinvestKrw=1251030,
differenceKrw=153992, totalReturnPercent=181.0`로, 이전에 운영 DB 원시
데이터를 별도 Python 스크립트로 독립 재계산해 맞춰둔 값과 정확히 일치했다.
mixed-content 우회 설계가 이론이 아니라 실제로 브라우저 환경(정확히는
그와 동일한 프로토콜 조건)에서 동작함을 확인한 것이다.

**자동배포는 실제로 push해서 확인**: Root Directory를 CLI로 설정한 뒤
"됐겠지"라고 믿지 않고, `git commit --allow-empty` + `git push`로 실제
트리거해봤다. 26초 뒤 `vercel list`에 새 배포가 `Ready` 상태로 나타났고,
그 배포로 다시 사이트·API·로고를 curl로 재확인했다.

| 확인 항목 | 결과 |
|---|---|
| Vercel HTTPS → `/api/*` rewrite → 실제 백엔드 | 200, 손계산 검증값과 정확히 일치 |
| 로고 8개 파일 배포 후 서빙 | 8개 전부 200 |
| Root Directory 미설정 상태에서 자동배포 시도 | (설정 전이라 시도 안 함 — 설정 없이 뒀다면 저장소 루트 기준으로 빌드가 실패했을 것) |
| Root Directory=`frontend` 설정 후 push→배포 | 26초 내 `Ready`, 사이트 정상 |

## 다음/한계

- **화면을 직접 본 적이 없다**: 이 세션엔 브라우저 자동화 도구가 없어서
  레이아웃·애니메이션 타이밍·공유 이미지의 실제 시각적 품질은 검증하지
  못했다. API 응답 정확성과 배포 파이프라인 동작만 확인된 상태 — 사용자가
  직접 열어봐야 하는 부분으로 남겨뒀다.
- Vercel 프로젝트 이름이 `temporary-instant-maple-13v3wqn`으로 자동
  생성된 상태 그대로다. 나중에 dashboard에서 이름을 바꾸면 기본
  `*.vercel.app` URL도 같이 바뀌므로, 그때 정적 사이트 3곳(`index.html`,
  `cashflow.html`, `detail.html`)의 nav 링크를 다시 갱신해야 한다.
- 백엔드가 여전히 평문 HTTP NodePort만 노출한다는 근본 제약은 이번에
  안 건드리고 우회만 했다(Vercel rewrite). TLS를 백엔드에 직접 붙이는 건
  범위 밖으로 남겨둔다 — 지금 구조로는 Vercel을 통하지 않는 다른
  클라이언트(예: 모바일 앱)가 생기면 다시 마주칠 문제다.
