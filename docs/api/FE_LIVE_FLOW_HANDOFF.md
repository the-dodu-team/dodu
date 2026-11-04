# FE 실제 흐름 인수 기록

기준: 서비스 운영 정책 v2.5.2 / 현재 백엔드 API 계약

## 연결 범위

| 화면 동작 | API | FE 처리 |
|---|---|---|
| 참가자 입장 | `POST /api/participant/sessions` | CLI 발급 코드를 제출하고 공통 envelope의 `data.sessionToken` 저장. 구형 `/api/access/enter`는 사용하지 않음 |
| 약속 생성 | `POST /api/promises` | `Idempotency-Key` 생성 후 서버 응답으로 상태 갱신 |
| 약속 시간 수정 | `PATCH /api/promises/{promiseId}/schedule` | 서버 `version`을 `expectedVersion`으로 전달 |
| 약속 취소 | `POST /api/promises/{promiseId}/cancel` | 서버 성공 후 로컬 약속 제거 |
| 사진 제출 | `POST /api/promises/{promiseId}/photo-attempts` | JPG/PNG 한 장과 `clientRequestId` 제출, `processingStatus` 표시 |
| 인증 이력 | `GET /api/promises/{promiseId}/auth-attempts` | 판정·처리 상태·모델 버전만 표시 |
| 자기보고 | `GET/POST .../validation-survey`, `POST .../skip` | 서버 상태를 표시하고 미응답을 다음 약속 생성 차단에 사용하지 않음 |
| 알림 | `GET /api/participant/notifications`, `PUT .../{id}/read` | 읽음 상태를 서버 결과로 갱신 |

## 검증 상태

- 실제 API와 서버 약속 ID가 있는 홈은 운영 빌드에서도 인증 시간 내 사진 제출 화면 진입을 허용한다. 미리보기 인증은 개발 환경에서만 허용한다. 카메라 연결 여부로 홈 인증 버튼을 비활성화하지 않는다.

- 입장·조회·제출 요청은 연결부터 응답 본문 읽기까지 합산 60초를 초과하면 중단하고 실패를 알린다. 입장 버튼은 재활성화한다. 시간 초과는 서버가 요청을 처리하지 않았다는 뜻이 아니며 POST 자동 재전송은 하지 않는다.
- 활성 약속 조회의 빈 HTTP 200 본문과 JSON null은 약속 없음으로 처리한다.
- Vercel dev Preview가 Ready인 것과 Production 공개 주소에 적용된 것은 별도다. 입장 경로 수정 배포를 Production으로 승격한 뒤 실제 공개 주소의 `/api/participant/sessions` 호출을 확인한다.

- 자동 검증: FE build/lint와 API 계약 테스트 실행 대상.
- 백엔드 자동 검증: 기존 MockMvc/Testcontainers 테스트에서 약속·사진·이력·자기보고·알림 소유권 및 상태 경계를 확인.
- Swagger 수동 검증: 로컬 Swagger UI/OpenAPI 200, 입장 200, 약속 생성·조회·멱등·수정·취소·알림 조회를 확인. 사진 T 이전 제출은 정책대로 409 `PHOTO_WINDOW_CLOSED`. 실제 배포·Push·AI 운영 검증은 별도.
- 운영 검증: 외부 사진 저장소, AI worker callback, Push provider, 모바일 기기 QA가 준비된 뒤 수행.

민감한 사진 원문, 접근 코드, API 자격정보는 이 문서와 테스트 출력에 기록하지 않는다.
| 약속 이력 | `GET /api/promises/history` | 로그인 시 서버 이력을 조회해 주간 지난 약속과 이력 화면에 표시. 취소·철회 결과는 인증 이력에서 제외 |
# 2026-11-05 통합 앱 진입점 보완

- 운영 진입점은 `main.tsx` → `LiveParticipantApp`이며, 서버의 `onboardingCompleted=false`이면 `LiveOnboardingFlow`를 표시한다. 닫기는 현재 화면에서만 안내를 숨기며 서버 완료나 Push 구독 완료로 기록하지 않는다.
- 참가자·자기보고 클라이언트도 `VITE_DODU_API_BASE_URL`을 사용한다. 상대 API 경로가 Vercel 정적 화면으로 잘못 향하지 않도록 공통 전송 어댑터를 주입한다.
- 기존 브라우저 저장소의 세션과 기기 식별자를 복원한다. 만료·철회 시 구형 토큰도 제거한다.
- 사진 화면은 처음 진입할 때 시도 목록을 조회하고 `Evaluating`이면 제출을 잠근 채 3초 간격으로 조회한다. 닫아도 서버 판정은 취소되지 않는다. 쓰기는 자동 재전송하지 않고 결과 불명 요청은 동일 멱등 식별자를 유지한다.
- 오류 응답은 공통 envelope·기존 `{code}`·빈 본문에 대응한다. 원시 예외·provider 응답을 화면에 표시하지 않는다. 정당한 인증·소유권·상태 충돌의 HTTP 오류까지 성공으로 바꾸지는 않는다.
- 신규 PR 테스트: `npm run test:integration-ui --workspace frontend`. 전체 검증: `npm run verify`.
- 카메라 권한 버튼은 실제 `getUserMedia`를 호출하고 확인 직후 모든 트랙을 종료한다. 권한 거부·미지원·기기 없음·사용 중·비보안 연결을 구분해 안내하며 실제 허용 시에만 카메라 완료 화면으로 이동한다. 완료하기는 홈으로 돌아가며 서버 온보딩 완료 저장과는 구분한다. 브라우저 권한 재설정·철회는 사용자가 사이트 설정에서 수행한다.
