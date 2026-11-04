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
- 대상 테이블 NOWAIT 잠금·예상 외 FK 확인 후 연결된 Storage 객체를 삭제하고 DB를 정리한다. 파일 실패 시 DB 메타데이터를 보존한다. 외부 파일과 DB는 원자적이지 않아 일부 파일만 지워질 수 있다. 삭제 404는 이미 없음으로 처리해 재시도한다.
- 스키마·테이블·버킷은 유지하고 CASCADE를 쓰지 않는다. DB에 연결되지 않은 Storage 고아 객체까지 삭제했다고 주장하지 않는다.
- 공개 API 없음. operator의 `reset-test-data --scope=keep-participants`만 제공한다. `scripts/reset-test-data.ps1`에서 Render와 같은 자격정보를 숨겨 입력하며 프로세스 환경에서만 사용 후 정리한다. ResultPath에는 RESET_OK/FAILED 집계만 기록한다.
- 입력 완료 후 승인된 삭제가 실행된다. 실제 RESET_OK 집계 전에는 초기화 완료로 보고하지 않는다. 실행 중 새 약속·사진 제출을 중지한다.
- operator 프로필에서는 스케줄러를 켜지 않는다. 로컬 알림·사진 worker가 초기화와 경쟁하지 않으며 Flyway 초기화는 완료한 뒤 실행한다. 로컬 FCM은 끄고 기존 환경은 종료 후 복원한다.
- 시작/실행 실패는 표준 출력의 `RESET_FAILED stage=STARTUP|RESET code=...`로만 기록한다. DB_BUSY·DB_AUTH_FAILED·DB_CONNECTION_FAILED·SCHEMA_NOT_READY·STORAGE_REQUEST_FAILED 등 허용된 코드만 제공하고 예외 원문은 출력하지 않는다. 실패 후 비밀값은 정리되므로 재실행 시 다시 입력해야 한다.

## 검증

- 전체 verify: Docker 포함 BE 264건, 실패·오류·건너뛰기 0. FE 통합 35건·빌드·비밀값 검사 통과. lint 오류 없이 경고 유지.
- 실제 JAR CLI도 별도 Docker DB에서 실행해 약속 1건 삭제·참가자 1명/자격 1건 보존·종료코드 0 확인. 잘못된 합성 DB 비밀번호는 `RESET_FAILED stage=STARTUP code=DB_AUTH_FAILED`, 종료코드 3으로 구분 확인. 실제 운영 데이터 초기화 결과와는 별개다.
- DB: 종료 후 당일 생성·다른 키 동시 생성 1건·만료 저장/설문 1회·지연 판정 보호·구형 도구 읽기·초기화 참가자/코드 보존·Storage 실패 시 DB 보존·사진 선삭제 순서.
- `npm run test:browser-history --workspace frontend`: 합성 API에서 목록 실패 복구·같은 날짜 5건/종료 사유·인증 집계 2건만 포함·다음 약속 생성·JS 오류 0.
- 실제 Supabase 초기화·모바일/운영 QA는 별도 기록한다. 개발용 테스트 실행만으로 실제 삭제 성공을 주장하지 않는다.
