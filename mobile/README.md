# Dodu RN 전환 준비

SCRUM-96 / Sprint 6 예정. 이 디렉터리는 브라우저에 의존하지 않는 모바일 기반 코드이며 **아직 실행 가능한 RN 앱이나 변환된 화면은 아니다.**

## 현재 준비

- `src/session/nativeSession.ts`: 비동기 보안 저장소 복원, 동기 토큰 캐시, 설치 ID 유지, 저장·로그아웃 직렬화.
- `tsconfig.json`: DOM·브라우저 타입 없이 검사한다.
- `tests/native-session.test.cjs`: 복원 전 차단·재실행 복원·저장 실패·동시 복원·늦은 저장 뒤 로그아웃 검증.
- 루트에서 `npm ci` 후 `npm run mobile:check`. 기존 frontend workspace의 TypeScript를 재사용하며 RN SDK 설치·빌드 검증은 하지 않는다.

## 인증 연결 순서

1. native 보안 저장소와 설치 ID 저장소, UUID 공급자를 주입한다.
2. `await session.initialize()` 완료 전에는 로그인·인증 API 호출을 시작하지 않는다. 실패하면 재시도 화면을 보여준다.
3. 복원한 토큰은 서버 `/api/participant/me`로 검증한 뒤에만 홈을 연다. 로컬 토큰 존재가 로그인 유효성의 증거는 아니다.
4. 로그인 성공 시 `await session.saveToken(token)` 후 화면을 전환한다. 코드 원문은 저장하지 않는다.
5. 로그아웃·401·기기 교체 시 기존 비동기 요청 결과를 폐기하고 `await session.clear()`를 호출한다. 서버 세션 철회·Push 해지는 상위 조정 계층에서 처리한다. 이 모듈은 서버 API를 호출하지 않는다.
6. 토큰 저장에는 iOS Keychain·Android Keystore 기반 어댑터를 사용한다. 설치 ID는 로그아웃 시 유지하며 앱 삭제·재설치 시 유지된다고 가정하지 않는다.

토큰 저장소의 읽기·쓰기·삭제 실패는 호출자에게 전달한다. 오류 원문·토큰·코드는 UI와 로그에 출력하지 않는다. 삭제 실패 시 메모리 인증은 해제하지만 영구 삭제 성공으로 보고하지 않는다. 다음 부팅에서도 서버 검증이 필요하다.

## 다음 구현 순서

1. RN 런타임·SDK 버전을 고정하고 앱 골격 생성. Expo development build를 우선 검토하되 아직 확정·설치하지 않는다. 실제 native FCM 연결에는 SDK 제약 확인이 필요하다.
2. native 저장소 어댑터와 앱 부팅·세션·HTTPS API 연결.
3. 참가자 입장·기기 전환 확인·온보딩·권한 복구 화면.
4. 약속 생성·수정·취소·전체 이력·자기보고 화면.
5. native 카메라·JPEG/PNG 압축·multipart 업로드·지연 판정 복원.
6. native FCM 등록·전경/백그라운드·종료 후 탭 진입. 웹 VAPID·Service Worker 및 Expo Push 토큰을 기존 FCM 토큰 대신 사용하지 않는다.
7. Android/iOS 실기기 QA. Windows에서 iOS 로컬 빌드를 완료했다고 보고하지 않는다. 유료 클라우드 빌드·과금 등록은 수행하지 않는다.

## 재사용·별도 구현

| 재사용 후보 | 별도 native 구현 |
|---|---|
| `frontend/src/shared/apiTypes.ts`의 서버 DTO | View/Text/Pressable·내비게이션·접근성 |
| 순수 약속·자기보고·사진 상태 모델 | 브라우저 localStorage 대체 보안 저장소 |
| 서버 소유권·시간·멱등성 계약 | 카메라·파일 URI·압축·앱 권한·AppState |
| 직접 매핑할 번역 키와 테마 토큰 | 웹 CSS·dialog·inert·DOM 포커스 대체 |

커스텀 i18n과 라이트/다크는 직접 매핑 원칙을 유지한다. 번역 키·변수 및 역할별 색상 토큰을 먼저 합의하고 화면마다 연결한다. 이 준비 작업은 번역·테마 화면을 구현하지 않는다. 서버 시간·KST 정책은 표시 언어와 무관하게 유지한다.

웹 PR #43은 별도 진행 중이다. 해당 인사·토스트·권한·로딩 변경은 이 브랜치에 미리 섞지 않으며 dev 반영 후 필요한 계약을 동기화한다.

참고: [RN 보안](https://reactnative.dev/docs/security), [Expo development build](https://docs.expo.dev/develop/development-builds/introduction/). 일반 AsyncStorage는 암호화 보안 저장소가 아니다. Development build는 native 라이브러리와 설정을 앱에 포함하는 방식이다.
