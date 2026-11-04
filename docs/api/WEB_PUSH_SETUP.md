# 웹 푸시 연결·검증

근거: 서비스 정책 §3·§7·§10, D02·D07·D10·D12. 발송 성공은 수신·열람 증거가 아니다.

## 배포 설정

- Vercel `Config`: `VITE_FIREBASE_API_KEY`, `VITE_FIREBASE_PROJECT_ID`, `VITE_FIREBASE_MESSAGING_SENDER_ID`, `VITE_FIREBASE_APP_ID`, `VITE_FIREBASE_VAPID_KEY`. 웹 앱과 VAPID는 같은 Firebase 프로젝트 기준. 실제 값은 저장소에 넣지 않는다.
- Render Secret File: `firebase-service-account.json`. 환경변수 `DODU_FCM_CREDENTIALS_PATH=/etc/secrets/firebase-service-account.json`, `DODU_FCM_ENABLED=true`, `DODU_FCM_DRY_RUN=false`. JSON 비밀키는 Vercel에 넣지 않는다. 무료 Spark 유지.
- 계정 설정은 사용자 입력 완료 보고이며 실제 기기 수신 검증과 별개다. 이 코드를 포함한 FE/BE 배포가 필요하다.

## 동작·보호

- 현재 세션과 알림 허용 상태에서만 SDK를 지연 로드한다. 전체 미설정은 비활성, 일부 누락은 연결 실패다. 기존 온보딩·카메라 권한 조건을 대체하지 않는다.
- 번들된 루트 service worker 등록 객체와 VAPID를 `getToken`에 전달한다. `PUT /api/participant/notification-subscription`의 `subscriptionKey`는 FCM 등록 토큰이며 Web Push JSON이 아니다.
- 세션 변경·화면 복귀·주기 확인에서 갱신하고 동일 세션·토큰은 중복 PUT하지 않는다. 연결 실패 재시도는 설정 화면 안에서 제공한다. 전역 서버 재확인 배너는 제거한다.
- 전경은 messageId 중복을 제거한 일반 안내를 표시한다. 백그라운드는 notification payload의 SDK 자동 표시만 사용하며 `showNotification`을 중복 호출하지 않는다. 열람 API를 자동 호출하지 않는다.
- 클릭은 홈 또는 허용한 설문 UUID 경로만 열고 임의 URL은 무시한다. 토큰·알림 원문·사진·설문을 로그에 남기지 않는다.
- 로그아웃은 등록 작업 중단·대기 → 서버 구독 해제 → SDK 토큰 해제 순서다. 서버는 참가자 행 잠금 후 세션을 재검증하고 토큰 등록을 직렬화한다. 전역 고유 토큰 원장을 현재 참가자로 이전하므로 이전 소유자 해제가 새 구독에 영향을 주지 않는다.
- 이미 외부 큐에 들어간 메시지의 회수는 보장하지 않는다. 권한 허용·구독 저장·FCM 접수·실제 표시·사용자 열람은 별개다.

## 검증

- `node --test frontend/tests/push.test.cjs`: 권한·부분 설정·미지원, 중복 등록·토큰 갱신, 로그아웃 지연 결과·PUT 해제 순서, 오류 복구, 전경 중복·안전한 경로 8건.
- `frontend/tests/push.browser.cjs`: 합성 공개 설정으로 `.tools/push-browser-dist` 빌드 후 4177에서 실행. 토큰 SDK는 합성 어댑터, worker 활성화·구독 HTTP·전경 화면·로그아웃은 실제 Chrome 경로. 운영 수신 시험으로 취급하지 않는다.
- 서버 `NotificationControllerIntegrationTests`: 반복 구독·민감정보 비노출·해제, 동일 브라우저 토큰의 참가자 이전, 인증 이후 철회 세션 차단 등 6건 통과.
- 최초 전체 검증 270건 중 참가자 이전 1건 실패: 토큰 해시 전역 유일 제약을 확인하여 행 소유자 이전으로 수정. 수정 후 해당 6건 통과. 최종 전체 검증은 상태 기록 참조.
- 실제 기기 QA: 최신 Production 재입장 → 알림 허용 → 설정 연결 상태 → 약속 T에 전경·백그라운드 각각 수신 확인. iPhone은 지원 OS의 홈 화면 설치 PWA로 검증. 일시 실패 재시도·영구 토큰 오류의 운영 원장 확인도 필요하다.

공식 근거: [토큰 등록](https://firebase.google.com/docs/cloud-messaging/web/get-started), [수신](https://firebase.google.com/docs/cloud-messaging/web/receive-messages).

## 기기 변경 확인

- 사용자 승인: 새 기기에서 확인 후 전환, 기존 기기는 안내만 받음. 기존 기기의 승인·응답·알림 수신은 전환 조건이 아니다.
- `POST /api/participant/sessions`: 기존 `code`, `deviceKey`에 선택적 `confirmDeviceChange: true`를 추가한다. 생략·null은 false다. 유효 코드이고 만료되지 않은 다른 기기 활성 세션이 있으면 409 `DEVICE_CHANGE_CONFIRMATION_REQUIRED`를 반환하며 세션·기기·구독을 변경하지 않는다.
- 새 기기는 경고와 확인·취소 모달을 표시한다. 취소는 추가 요청을 보내지 않는다. 확인은 같은 코드를 메모리에서 재전송한다. 코드를 로그·영구 저장소에 보관하지 않는다.
- 확인 후 참가자 잠금 안에서 기존 세션·기기·구독 종료 및 새 세션 발급. 같은 기기 재입장·첫 입장·이미 만료된 기존 세션에는 경고를 요구하지 않는다.
- 기존 구독 토큰은 변경 전 메모리 이벤트에만 보관하고 트랜잭션 커밋 후 발송한다. 전환 완료 안내이며 승인 요청이 아니다. 실행기·FCM 실패는 새 로그인 성공을 되돌리지 않는다. 이 보조 안내는 영구 원장·재시도를 추가하지 않은 최선형 발송이다. 실제 수신은 보장하지 않는다.
- 기존 기기의 전경 FCM 안내는 서버 세션을 재확인하며, 교체된 세션은 `SESSION_REPLACED` 안내와 함께 입장 화면으로 이동한다. 백그라운드에서 알림을 클릭해도 서버가 최종 접근 상태를 확인한다.
- 최종 `npm run verify`: 백엔드 273건 실패·오류·건너뜀 0, FE 단위·계약·lint·빌드·비밀값 검사 통과. 브라우저 5개 흐름(푸시·기기 변경·재입장·전체 이력·권한 복구) 통과. 푸시 토큰/서버 응답은 합성이며 운영 FCM 실기기 QA를 대체하지 않는다.
