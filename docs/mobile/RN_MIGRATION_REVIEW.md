# React Native 전환 영향 점검

2026-11-08 / SCRUM-95. RN 화면·Android/iOS 앱 구성은 Sprint 6 범위다. 이번 작업은 현행 코드 분석과 재사용 경계 준비이며 RN 실행·빌드·실기기 호환 확인을 의미하지 않는다.

## 결론

백엔드 계층/포트 정리 때문에 RN 전환이 막히지는 않는다. RN도 같은 HTTPS·Bearer 세션·참가자/약속 ID·멱등성 키 계약을 사용할 수 있다. 다만 현재 React DOM 화면과 브라우저 기능은 그대로 이식할 수 없고 아래 연결부가 필요하다. 웹을 WebView로 감싸는 것과 실제 RN 화면 변환은 다른 작업이다.

## 실제 코드 기준 재사용 범위

| 영역 | 파일 | 판단·이식 작업 |
|---|---|---|
| 실제 앱 진입 | `frontend/src/main.tsx` → `features/live/LiveParticipantApp.tsx` | 옛 `App.tsx` 미리보기부터 변환하지 않는다. 서버 연결 흐름을 기준으로 native UI 구성 |
| 공통 데이터 | `shared/apiTypes.ts` | DTO·서버 상태·도구 값 재사용. API envelope 혼재 처리 유지 |
| 순수 정책·표시 모델 | `promiseHistory`, `savedPromiseHistory`, `livePhotoPresentation`, `selfReportModel` | DOM 미사용 함수 재사용 가능. 서버 시간을 최종 기준으로 유지 |
| 화면 | `components/`, `ReportScreens`, `LivePhotoAuth`, `LiveOnboardingFlow` | HTML·CSS·SVG import·dialog·focus·inert는 RN View/Text/Modal·접근성으로 다시 구현 |
| 세션 | `features/live/liveSession.ts` | 현재 동기 Storage 캐시. 비동기 저장소를 그대로 넣으면 token()이 Promise가 되어 인증이 깨질 수 있음. 부팅 때 보안 저장소 복원 완료 후 API/화면 시작, 저장·삭제 완료 순서 보장 |
| 구형 세션 도구 | `platform/participantSession.ts` | 실사용 LiveSession과 키·clear 동작이 다름. 그대로 RN 기본 저장소로 선택하지 않는다. 실제 웹 로그아웃은 기기 키를 유지하지만 이 도구는 지움 |
| 기기 ID·멱등성 | `liveSession`, `participantPromiseApi` | 설치 단위 UUID 공급자 주입. 로그아웃 때 기기 ID를 재생성하지 않음. 네트워크 재시도는 같은 요청 키·본문 유지 |
| API 연결 | `doduApi`, `participantPromiseApi`, `liveSurveyApi` | fetch·DTO·오류 계약 재사용 가능. Vite import.meta.env 대신 RN 앱 구성에서 base URL 주입. 네이티브 fetch/AbortSignal 옵션 지원은 선택한 RN 런타임에서 확인 |
| 사진 | `compressPhoto.ts`, `LivePhotoAuth`, `doduApi.submitPhoto` | File/input/canvas/createImageBitmap/object URL은 웹 전용. native 카메라의 파일 URI·MIME·파일명을 multipart `photo`로 전송하는 어댑터 필요. JPEG/PNG·용량 제한, 회전·EXIF·HEIC 변환 별도 검증 |
| 권한 | `notificationPermission`, `cameraPermission`, `PermissionRecoveryGate` | Notification/navigator 권한 조회 대신 native 권한 API·설정 복귀 흐름 필요. 권한 허용과 토큰 등록·카메라 사용 가능을 구분 |
| Push | `firebasePush`, `firebaseWorker`, `foregroundPush`, `pushClick` | 웹 Firebase messaging/SW/VAPID는 native 수신기로 재사용하지 않음. Android/iOS 앱 등록·native FCM 토큰, iOS APNs 설정, 전경 표시·백그라운드 수신·탭 이동 연결 필요 |
| 진입·복귀 | `window.location`, self-report 쿼리, focus/visibility 이벤트 | native navigation/deep link/AppState로 교체. 푸시 클릭에서 받은 promiseId는 서버에서 소유권 재검증 |

## 서버와 이어지는 부분

## 이번 재사용 기반 보완

- `PromisePayload`를 API 구현 파일에서 공통 타입 파일로 옮기고 기존 경로의 type export는 유지했다. 런타임 API 본문·엔드포인트는 바꾸지 않는다.
- `SyncKeyValueStorage`를 명시하여 웹 세션이 요구하는 동기 캐시와 비동기 보안 저장소 계약을 구분했다. 메모리 저장소는 동기 계약으로 사용할 수 있다.
- 실사용 `createLiveSession`의 DOM `Storage` 타입을 구조적 동기 인터페이스로 바꾸고 UUID 생성 함수를 주입 가능하게 했다. 기존 웹 호출은 그대로 동작한다.
- 이는 native 영구 저장 어댑터가 아니다. RN에서는 보안 저장소의 비동기 읽기·쓰기 완료를 처리하는 앱 부팅/세션 수명 구현이 여전히 필요하다. 비동기 함수를 동기 타입으로 단언하지 않는다.
- 검증: 저장소/세션 플랫폼 테스트, FE 통합 35건·푸시 12건·API 계약, 타입 검사·빌드·lint, Chrome 합성 재입장 흐름 성공. RN 빌드·실기기 검증 미실행. 기존 lint 경고 유지.

## 서버와 이어지는 부분

- 같은 Firebase 프로젝트에 Android/iOS 앱을 등록할 수 있다. 프로젝트를 무조건 새로 만들 필요는 없다. 서버용 JSON·Storage 자격은 앱에 넣지 않는다.
- `subscriptionKey`에는 native FCM 등록 토큰을 보낸다. Expo Push 토큰이나 Web Push JSON을 그대로 보내면 현재 FCM 어댑터와 호환되지 않는다. 선택 SDK에 맞춰 계약 확인이 필요하다.
- 서버의 공통 notification/data는 native에서도 활용할 수 있지만 WebpushConfig의 TTL·Urgency가 Android/APNs 설정을 대체하지 않는다. native 전경 표시·탭 이동·전원/잠금·토큰 회전 QA가 필요하다.
- 현재 `SESSION_REPLACED` 안내는 신규 기기 확인 후 기존 기기 안내만 수행한다. native에서도 이를 유지하고 API 401 시 세션을 종료한다. 기존 기기 승인 대기는 추가하지 않는다.
- RN native 네트워크 요청은 브라우저 CORS에 의존하지 않는다. WebView·RN Web은 웹 제약이 있으므로 배포 origin 설정을 임의로 완화하지 않는다.
- 앱이 백그라운드일 때 JS 타이머가 계속 돈다고 가정하지 않는다. 예정 알림은 서버가 결정하고 복귀 시 최신 상태·서버 시각을 다시 읽는다.

## Sprint 6 진행 순서

1. RN 앱 구조와 런타임 결정 후 Secure Storage·UUID·HTTP 구성 어댑터와 세션 복원 구현.
2. 참가자 입장·기기 전환 확인·온보딩을 native 화면으로 연결. 토큰을 단순 AsyncStorage에 저장하지 않는다.
3. 약속 생성·수정·취소·이력·자기보고 이식. 동시 활성 1개·기술 실패 제외·24시간 정책 유지.
4. native 촬영·압축·업로드·지연 판정 복원 구현. 사진/토큰을 로그에 남기지 않음.
5. native Push 토큰 등록·갱신·해제, 전경/백그라운드·종료 후 탭 진입 구현.
6. Android/iOS 실제 기기에서 권한 거절·복구·재입장·설문 만료·기기 전환·앱 재시작 검증.

카메라·보안 저장소·푸시 라이브러리를 이번에 임의 설치하지 않았다. RN 버전/런타임 미선정 상태에서 native 호환이나 앱스토어 준비 완료로 표시하지 않는다.

공식 근거: [RN 플랫폼별 코드](https://reactnative.dev/docs/platform-specific-code), [네트워크](https://reactnative.dev/docs/network), [기본 컴포넌트](https://reactnative.dev/docs/components-and-apis), [보안 저장소](https://reactnative.dev/docs/security), [RN Firebase 메시징](https://rnfirebase.io/messaging/usage), [웹 FCM 수신](https://firebase.google.com/docs/cloud-messaging/web/receive-messages).
