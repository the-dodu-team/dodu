# 인앱 자기보고 재안내·권한 복구

기준: D02·D07의 최초 안내 후 24시간·추가 Push 없음·인앱 재안내 1회, 261021 기능 설명의 알림·카메라 권한 필요 및 기존 흐름 복귀. 담당 추적: SCRUM-76.

## 재안내 계약

`POST /api/promises/{promiseId}/validation-survey/inapp-reminder`

- 현재 참가자 세션 인증 필요, 요청 본문 없음. 공통 성공 envelope의 `data.show`가 boolean이며 `serverTime` 포함.
- 현재 소유 약속, 인증 완료/미완료 대상, Pending/Prompted/Reminded, `availableAt ≤ 서버 시각 < expiresAt`, 활성 세션·참가자·접근 기간에서만 가능.
- `inapp_reprompted_at IS NULL`인 행을 조건부 UPDATE. 여러 탭·노드가 동시에 요청해도 한 요청만 `show=true`. 이후는 false. 타인·없는 약속·기간 밖·완료된 설문도 false로 자원 존재를 노출하지 않는다. 인증 자체가 없으면 401.
- 기존 V1 컬럼 재사용, 새 마이그레이션 없음. 설문 상태·응답 가능 시각을 변경하지 않고 추가 Push를 생성하지 않는다.
- 이 시각은 **재안내 시도 기록**이다. 실제 표시·열람·Push 수신의 증거가 아니다. 응답 유실·탭 종료가 발생하면 표시되지 않을 수 있으며 자동으로 재발급하지 않는다.
- FE는 서버 알림 원장에서 얻은 후보를 확인하고 실제 약속 정보가 준비되었을 때 요청한다. StrictMode effect 재실행은 같은 진행 중 요청을 공유한다.
- 바텀시트 닫기/Escape는 응답 상태 유지. 지금 작성하기는 기존 설문으로 이동. 건너뛰기는 서버 skip API 성공 후 닫는다. Skipped·Submitted·Expired는 제출 API에서 다시 열지 못한다.
- 타인 설문 GET은 해당 설문의 Prompted/Expired 상태도 갱신하지 않는다.

## 권한 복구

- 서버 온보딩 완료는 과거 이력이다. 현재 기기 권한으로 대체하지 않는다.
- 알림과 카메라가 허용되어야 서비스 조작 가능. 포커스 복귀·페이지 복원·권한 변경·알림 상태 확인으로 해제를 감지한다. 설정 경로는 화면 안 안내 박스로 제공하며 OS 설정을 직접 열 수 있다고 표시하지 않는다.
- 다시 확인은 실제 알림 상태 및 카메라 권한을 확인한다. 카메라는 영상만 요청하고 모든 트랙을 즉시 종료한다. 중복 클릭은 보호한다.
- 카메라 권한 조회 API 미지원 환경은 허용으로 추정하지 않는다. 사용자 클릭으로 카메라를 재확인한다. HTTPS·지원 브라우저·iOS 홈 화면 설치가 필요하면 해당 안내를 표시한다.
- 복구 중 기존 화면은 숨김/inert 상태로 보존하며 폼·선택 사진·서버 판정 시계를 초기화하지 않는다. 이미 접수한 판정의 조회는 계속될 수 있다. 권한이 복구되면 원래 화면 제목/열린 모달로 포커스를 돌린다.
- 권한 문제를 인증 거절이나 사용자 실패로 저장하지 않는다. 브라우저 권한이 있어도 FCM 구독 생성·기기 수신을 검증한 것은 아니다.

## 검증·재현

- `npm run verify`: Docker/PostgreSQL 포함 BE 254건, 실패·오류·건너뛰기 0. FE 테스트·빌드·비밀값 검사 통과. lint는 경고가 남으며 오류 없음.
- 최종 FE 수정 후 `npm run test:integration-ui --workspace frontend`: 34건 통과.
- DB 통합: 동시에 4개 재안내 요청 중 1개만 성공, 타인 조회/재안내 무변경, 비인증 401, 시작·만료 경계, 종료/비대상/철회 차단, 건너뛰기 후 재제출 충돌.
- 재현 가능한 브라우저 테스트를 `frontend/tests/reminder-recovery.browser.cjs`에 저장. 모든 API·권한·이미지는 합성 fixture이며 실서비스 자격정보를 쓰지 않는다.
- 먼저 `npm run build --workspace frontend`, 별도 터미널에서 `npm run preview --workspace frontend -- --host 127.0.0.1 --port 4175`. 다음 `npm run test:browser-recovery --workspace frontend`.
- 기본 대상은 localhost:4175. 다른 주소는 `DODU_TEST_BASE_URL`, Chrome 실행 파일은 `DODU_CHROME_PATH`로 지정. Windows 기본 Chrome을 사용하며 다른 환경은 Playwright Chromium 설치 필요.
- Chrome 검증: StrictMode 재안내 1회, 닫기 시 응답 유지, 재진입 시 미노출, 명시적 건너뛰기 종료, 알림/카메라 해제 차단·복구, 인증 화면 복귀, 권한 조회 미지원 시 실제 카메라 재확인·트랙 종료, 320px 가로 넘침 없음·JS 오류 0.

## 배포 및 미확인

- 새 재안내 API를 Render에 먼저 배포한 뒤 FE Production 반영 필요. BE가 이전 버전이면 재안내를 표시하지 않고 기존 작성 진입은 유지한다.
- 2026-11-05 Figma 개별 노드 재조회도 Starter 도구 호출 한도 오류. 책·자기보고 분기·재안내·빈 기록 등의 원본 시각 비교는 완료하지 못했다. 기존 컴포넌트와 제공된 기능 설명으로 동작을 보완했으며 최종 픽셀 일치로 보고하지 않는다.
- 실제 iOS/Android 권한·FCM·운영 QA는 별도이며 합성 브라우저 테스트로 대체하지 않는다.
- npm audit의 기존 개발 도구(concurrently/shell-quote·Vite) 경고는 남아 있다. 이번 lock 변경은 브라우저 검증용 Playwright 두 패키지만 추가한다.
