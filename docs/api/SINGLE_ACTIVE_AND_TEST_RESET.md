# 동시 활성 약속·전체 이력·테스트 초기화

## 적용 기준

2026-11-05 사용자 승인: 동시에 활성 약속은 1개이며 종료되면 같은 날짜에도 다음 약속을 만들 수 있다. 파생 정책 우선순위는 OPEN_DECISIONS 상단에 기록하며 원문은 보존한다.

- 참가자 행 잠금으로 생성 요청을 직렬화한다. 서로 다른 멱등키도 활성 1개, 같은 키는 같은 약속을 반환한다. 날짜 사용 원장은 최초 사용 감사용으로 유지하지만 날짜 중복을 차단하지 않는다.
- active/history 조회와 생성 시 서버 시간으로 만료 상태를 정리한다. 마감된 Scheduled/AuthAvailable 중 Evaluating·Completed·TechFailed attempt가 없는 미제출/거절 종료만 AuthIncomplete로 저장하고 자기보고 기간을 준비한다.
- 사진 접수와 만료 정리는 같은 약속 행 잠금을 사용한다. 정상 접수된 판정 중 약속은 마감 후에도 기다린다. 홈은 마감 이후 서버 상태를 주기적으로 재조회하며 브라우저에서 결과를 확정하지 않는다.
- 전체 보기는 약속 ID별 날짜·작업명·완료/미완료·취소·기술 문제·철회를 표시한다. 같은 날짜 여러 건을 합치지 않는다. 주간 인증 집계에는 완료/미완료만 포함한다.
- 기존 laptop/book 소문자 도구값은 읽기 응답에서 정상화한다. 알 수 없는 도구를 임의로 컴퓨터로 바꾸지 않는다. 목록 실패 시 전체 보기에서 재시도 가능하며 새 참가자/401/로그아웃은 이전 목록을 지운다.
- DB 마이그레이션은 없으며 Render와 FE Production 모두 새 버전 반영 필요.

## 승인된 초기화

- 사용자 선택: 참가자·발급 코드 유지, 약속·사진·설문·알림 기록 초기화.
- 유지: 참가자·접근 자격·기기·세션·구독 설정·연구 기간·정책 버전·운영 감사·접근 실패 제한. 참가자 단독 등록 이벤트도 유지한다.
- 삭제: promise, promise_date_usage, promise_schedule_revision, promise_creation_request, auth_attempt, photo_asset, photo_evaluation_job, validation_survey, notification_delivery와 약속/인증에 연결된 behavior_event.
- 대상 테이블 NOWAIT 잠금·예상 외 FK 확인 후 연결된 Storage 객체를 삭제하고 DB를 정리한다. 파일 실패 시 DB 메타데이터를 보존한다. 외부 파일과 DB는 원자적이지 않아 일부 파일만 지워질 수 있다. Storage 삭제는 SDK와 같은 JSON prefixes 목록으로 정확한 객체만 지정한다. 이미 없는 객체는 성공한 빈 목록으로 재시도하고, 버킷 404는 실패로 보존한다.
- 스키마·테이블·버킷은 유지하고 CASCADE를 쓰지 않는다. DB에 연결되지 않은 Storage 고아 객체까지 삭제했다고 주장하지 않는다.
- 공개 API 없음. operator의 `reset-test-data --scope=keep-participants`만 제공한다. `scripts/reset-test-data.ps1`에서 Render와 같은 자격정보를 숨겨 입력하며 프로세스 환경에서만 사용 후 정리한다. ResultPath에는 RESET_OK/FAILED 집계만 기록한다.
- 입력 완료 후 승인된 삭제가 실행된다. 실제 RESET_OK 집계 전에는 초기화 완료로 보고하지 않는다. 실행 중 새 약속·사진 제출을 중지한다.
- operator 프로필에서는 스케줄러를 켜지 않는다. 로컬 알림·사진 worker가 초기화와 경쟁하지 않으며 Flyway 초기화는 완료한 뒤 실행한다. 로컬 FCM은 끄고 기존 환경은 종료 후 복원한다.
- 시작/실행 실패는 표준 출력의 `RESET_FAILED stage=STARTUP|RESET code=...`로만 기록한다. DB_BUSY·DB_AUTH_FAILED·DB_CONNECTION_FAILED·SCHEMA_NOT_READY·STORAGE_REQUEST_FAILED 등 허용된 코드만 제공하고 예외 원문은 출력하지 않는다. 창을 종료하면 비밀값을 정리하므로 새 창에서는 다시 입력해야 한다.
- Storage HTTP 실패는 `STORAGE_HTTP_400/401/403/404/...`처럼 숫자만 구분한다. 응답 본문·사진 경로·키는 기록하지 않는다. 새 sb_secret 형식은 apikey 헤더로만, 기존 JWT형 서버 키는 기존 헤더로 전달한다.
- 수정된 입력창은 실패 시 종료하지 않으면 프로세스 메모리에만 설정을 보관한다. Enter로 같은 승인 범위를 재시도하거나 S로 Storage 설정만 교체할 수 있고, Q/창 종료는 설정을 정리한다. 파일에 비밀값을 저장하지 않는다.
- 근거: [Supabase SDK remove](https://github.com/supabase/storage-js/blob/main/src/packages/StorageFileApi.ts), [공식 API 키 안내](https://supabase.com/docs/guides/getting-started/api-keys). 단일 객체 DELETE 자체가 미지원이라는 뜻은 아니며 요청 본문·자격정보 형식 처리를 보완한 것이다.

## 검증

### 실제 실행 결과 — 2026-11-05

사용자 보고와 안전한 로컬 결과 파일에서 `RESET_OK` 확인. 약속 3건·연결된 사진 객체 1건 처리, 전체 DB 행 21건 삭제, 참가자 31명·발급 자격 31건 보존. 이 집계에는 자격정보나 사진 경로가 포함되지 않는다. 승인한 초기화는 완료됐으며 고아 객체 전체 검사·운영 모바일 QA와는 구분한다.

- 전체 verify: Docker 포함 BE 267건, 실패·오류·건너뛰기 0. FE 통합 35건·빌드·비밀값 검사 통과. lint 오류 없이 경고 유지.
- 실제 JAR CLI도 별도 Docker DB에서 실행해 약속 1건 삭제·참가자 1명/자격 1건 보존·종료코드 0 확인. 잘못된 합성 DB 비밀번호는 `RESET_FAILED stage=STARTUP code=DB_AUTH_FAILED`, 종료코드 3으로 구분 확인. 실제 운영 데이터 초기화 결과와는 별개다.
- DB: 종료 후 당일 생성·다른 키 동시 생성 1건·만료 저장/설문 1회·지연 판정 보호·구형 도구 읽기·초기화 참가자/코드 보존·Storage 실패 시 DB 보존·사진 선삭제 순서.
- `npm run test:browser-history --workspace frontend`: 합성 API에서 목록 실패 복구·같은 날짜 5건/종료 사유·인증 집계 2건만 포함·다음 약속 생성·JS 오류 0.
- 실제 Supabase 초기화·모바일/운영 QA는 별도 기록한다. 개발용 테스트 실행만으로 실제 삭제 성공을 주장하지 않는다.
