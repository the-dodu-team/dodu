# FE 실제 흐름 인수 기록

기준: 서비스 운영 정책 v2.5.2 / 현재 백엔드 API 계약

## 연결 범위

| 화면 동작 | API | FE 처리 |
|---|---|---|
| 약속 생성 | `POST /api/promises` | `Idempotency-Key` 생성 후 서버 응답으로 상태 갱신 |
| 약속 시간 수정 | `PATCH /api/promises/{promiseId}/schedule` | 서버 `version`을 `expectedVersion`으로 전달 |
| 약속 취소 | `POST /api/promises/{promiseId}/cancel` | 서버 성공 후 로컬 약속 제거 |
| 사진 제출 | `POST /api/promises/{promiseId}/photo-attempts` | JPG/PNG 한 장과 `clientRequestId` 제출, `processingStatus` 표시 |
| 인증 이력 | `GET /api/promises/{promiseId}/auth-attempts` | 판정·처리 상태·모델 버전만 표시 |
| 자기보고 | `GET/POST .../validation-survey`, `POST .../skip` | 서버 상태를 표시하고 미응답을 다음 약속 생성 차단에 사용하지 않음 |
| 알림 | `GET /api/participant/notifications`, `PUT .../{id}/read` | 읽음 상태를 서버 결과로 갱신 |

## 검증 상태

- 자동 검증: FE build/lint와 API 계약 테스트 실행 대상.
- 백엔드 자동 검증: 기존 MockMvc/Testcontainers 테스트에서 약속·사진·이력·자기보고·알림 소유권 및 상태 경계를 확인.
- Swagger 수동 검증: 로컬 Swagger UI/OpenAPI 200, 입장 200, 약속 생성·조회·멱등·수정·취소·알림 조회를 확인. 사진 T 이전 제출은 정책대로 409 `PHOTO_WINDOW_CLOSED`. 실제 배포·Push·AI 운영 검증은 별도.
- 운영 검증: 외부 사진 저장소, AI worker callback, Push provider, 모바일 기기 QA가 준비된 뒤 수행.

민감한 사진 원문, 접근 코드, API 자격정보는 이 문서와 테스트 출력에 기록하지 않는다.
| 약속 이력 | `GET /api/promises/history` | 로그인 시 서버 이력을 조회해 주간 지난 약속과 이력 화면에 표시. 취소·철회 결과는 인증 이력에서 제외 |
