# 백엔드 계층·외부 연동 점검

기준일: 2026-11-08. SCRUM-95. 전체 구조 점검과 알림 기능 1차 분리를 구분한다. 기능 정책은 서비스 정책 §3·§7·§10·§13 및 D01~D13, 11월 5일 승인 변경을 유지한다.

## 현재 판단

계층형과 외부 연동 포트·어댑터를 함께 쓰는 방향이 적절하다. 이미 존재하는 `PushNotificationSender`, `PhotoEvaluationRecoveryHandler`, `PhotoObjectReader`, `PhotoObjectWriter`는 유지한다. 폴더 이름만 일괄 변경하거나 저장소 인터페이스를 무조건 추가하지 않는다. 일반 JDBC 저장소는 구체 클래스로 충분하다.

처음 조사한 컨트롤러 14개 중 8개가 SQL을 직접 실행했다. 이번에 알림 2개를 분리했으며 아래 6개는 그대로다. 프로젝트 전체 리팩토링 완료를 의미하지 않는다.

| 컨트롤러 | 점검 결과·이번 범위 |
|---|---|
| HealthController | HTTP 상태 확인, 유지 |
| ParticipantController | 인증된 세션 조회, 유지 |
| ParticipantSessionController | 입장 서비스 위임, 유지 |
| ParticipantOnboardingController | 서비스 위임, 유지. 하위 서비스 SQL 분리는 후속 |
| ParticipantWithdrawalController | 운영 서비스의 철회 위임, 유지 |
| InAppSurveyReminderController | 재안내 서비스 위임, 유지. 하위 서비스 SQL 분리는 후속 |
| NotificationController | 조회·읽음 SQL을 Repository, 판단을 Service로 이동 |
| NotificationSubscriptionController | 검증·잠금·트랜잭션을 Service, SQL을 Repository로 이동 |
| PromiseController | 생성·이력·수정·취소 SQL·정책 혼재. 잠금/멱등성 단위 후속 분리 필요 |
| ValidationSurveyController | 조회 중 만료 갱신·제출·건너뛰기 혼재. 24시간 경계와 대상 소유권 보존하며 후속 분리 |
| PhotoAttemptHistoryController | 소유권·조회 SQL 혼재. 읽기 전용 분리 후보 |
| PhotoSubmissionController | multipart·스토리지·원장·중복·잠금 혼재. 보상 삭제·동시성 시나리오와 함께 분리 필요 |
| PhotoEvaluationResultController | HTTP 콜백·상태 갱신 혼재. 지연 결과·철회·현재 attempt 검사 보존 필요 |
| ParticipantAccessController | 폐기 예정 접근 API의 SQL·기존 자격 경로. 새 입장 API와 혼용하지 말고 별도 이관 판단 |

## 이번 의존성

`NotificationController → NotificationQueryService → NotificationRepository`

`NotificationSubscriptionController → NotificationSubscriptionService → NotificationSubscriptionRepository`

구독 guard의 SQL도 Repository로 옮겼다. guard는 기존 참가자·세션 접근 정책을 계속 사용한다. 구독의 참가자 행 잠금 → 토큰 advisory 잠금 → 다른 구독 종료 → 기존 토큰 소유자 이전/삽입 순서와 서비스 전체 트랜잭션을 보존한다. 토큰 해시·저장소·발급 자격정보는 회전하지 않는다.

`DeviceChangeNotificationListener → DeviceChangePushSender ← FcmDeviceChangePushSender`

기존 기기 전환 리스너가 직접 사용하던 Firebase SDK를 어댑터로 이동했다. 포트의 결과는 접수·시험 검증·거절·실패이며 SDK 예외/Message 객체가 안쪽으로 넘어오지 않는다. 기존 약속용 포트는 promiseId·알림 유형을 요구하므로 가짜 약속 ID를 넣지 않고 별도 기기 전환 포트를 사용한다. 로그 마커·안내 문구·TTL·dry-run·커밋 후 비동기 실행을 유지한다.

## 변경하지 않은 경계와 위험

- API 경로·JSON 필드·상태 코드·공통 오류 문구 유지. 기존 OpenAPI 응답 스키마 이름도 명시적으로 유지한다.
- 읽음 API의 update→조회와 최초 read_at 보존 유지. 새 트랜잭션을 무작정 추가하지 않는다.
- NotificationDeliveryService는 이미 외부 Push 호출을 발송 트랜잭션 밖으로 분리한다. 기존 어댑터를 다시 만들지 않는다.
- AI worker는 `@Transactional` 안에서 외부 판정을 호출한다. 긴 잠금·timeout 위험이 있다. 이는 단순 폴더 변경으로 해결되지 않으며 claim/lease와 결과 반영을 나누는 별도 동시성 작업이 필요하다. 이번에 상태 전이를 바꾸지 않는다.
- 사진 제출·콜백의 기본 생성자가 Clock.systemUTC를 직접 선택하는 경로가 있다. Clock 주입 통일은 별도 기능 단위에서 경계 테스트와 함께 진행한다.
- 모델 응답 파싱·사진 저장소 HTTP는 이미 외부 구현에 있다. 기존 포트를 재명명하거나 중복 추가하지 않는다.

## 검증·인수

- 변경 영향 BE 6개 테스트 클래스 52건 통과(알림 HTTP·구독/소유권·FCM·전환 안내·입장·약속 재입장). 실패·오류·건너뜀 0. 기존 검증 조건을 삭제하지 않았으며 이전 참가자의 해제가 이전된 구독을 지우지 않는 검사와 오류 계약 검사를 보강했다.
- `mvnw -DskipTests package`: 컴파일·패키징 성공. 테스트 미실행 패키징 명령이며 별도 52건 결과와 구분한다.
- 전체 273개 이상 BE 테스트·실제 provider 발송 QA는 재실행하지 않았다. DB 마이그레이션·사진/설문/예약 정책은 변경하지 않았다.
- RN 영향 분석은 [별도 문서](../mobile/RN_MIGRATION_REVIEW.md). 원격 기기 전환 Push는 11월 5일 사용자 보고로 전경·페이지 닫힘 수신을 확인했지만 이번 구조 변경의 운영 재검증은 별개다.
- 다음 BE PR은 위 남은 컨트롤러 중 한 기능부터 선택한다. 은서님과 같은 파일을 동시에 바꾸지 않는다.
