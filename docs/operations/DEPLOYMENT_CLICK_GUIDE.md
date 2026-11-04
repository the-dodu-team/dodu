# Dodu 배포 클릭 가이드

저장소에 배포 설정을 넣어 두었으므로, 아래 순서대로 계정에서 값을 입력하고 버튼을 누르면 된다.

## 1. Supabase 준비

1. Supabase에서 새 프로젝트 생성
2. Storage에서 `dodu-photos` 버킷 생성
3. 버킷은 **Private**로 유지
4. 데이터베이스 연결 정보와 프로젝트 URL을 복사

사진 원본과 운영 자격정보는 저장소·GitHub에 입력하지 않는다. 아래 Render 입력 화면에 직접 붙여 넣는다.

## 2. 백엔드 배포

[Render에서 Dodu 배포 시작](https://dashboard.render.com/blueprint/new?repo=https%3A%2F%2Fgithub.com%2Fthe-dodu-team%2Fdodu)

Blueprint가 `render.yaml`과 `deploy/backend.Dockerfile`을 사용한다. Render 화면에서 다음 값만 입력한다.

- Supabase PostgreSQL JDBC URL
- DB 사용자명
- DB 비밀번호
- 프런트 배포 후 발급되는 프런트 주소를 `DODU_ALLOWED_ORIGINS`에 입력
- Supabase 프로젝트 URL
- Supabase Storage 운영 자격정보

`DODU_ACCESS_CODE_PEPPER`는 Render의 자동 생성 값을 사용한다. Swagger는 기본적으로 꺼져 있다. 개발 검증 때만 Render 환경변수에서 `DODU_SWAGGER_ENABLED=true`로 바꾼다.

배포 완료 후 다음 주소가 `{"status":"ok"}`를 반환하면 백엔드가 기동된 것이다.

`https://<Render-서비스-주소>/api/health`

## 3. 프런트엔드 배포

[Vercel에서 Dodu 가져오기](https://vercel.com/new/clone?repository-url=https%3A%2F%2Fgithub.com%2Fthe-dodu-team%2Fdodu&root-directory=frontend)

Import 화면에서:

- Root Directory: `frontend`
- Framework: Vite
- 환경변수 `VITE_DODU_API_BASE_URL`: Render 백엔드 주소

그 다음 Deploy를 누른다. Vercel 설정은 `frontend/vercel.json`에 포함되어 있다.

## 4. 마지막 연결

1. Vercel 배포 주소를 복사한다.
2. Render의 `DODU_ALLOWED_ORIGINS`에 주소를 입력한다.
3. Render에서 Manual Deploy를 누른다.
4. Vercel에서 입장 코드 발급·입장, 약속 조회를 확인한다.

Supabase Storage를 아직 연결하지 않을 때는 사진 기능을 검증하지 않는다. Storage 입력을 완료한 뒤 `DODU_PHOTO_STORAGE_PROVIDER=supabase`로 변경하고 재배포한다.

### Supabase 활성화 후 기동 실패

`SupabasePhotoObjectStore: No default constructor found`는 자격정보 오류가 아니라 Spring 생성자 선택 오류다. 환경변수 주입 생성자를 명시한 수정 커밋 이후 버전을 `Manual Deploy → Deploy latest commit`으로 배포한다. `supabase` 설정과 기존 자격정보는 유지한다.

- 배포 후 `/api/health` HTTP 200 확인
- 실제 프런트 출처를 사용한 CORS preflight 허용 여부 확인
- 실제 사진 업로드·읽기·삭제 검증은 별도로 진행

객체 저장소 단위 테스트는 HTTP 호출을 직접 검증하며, `SupabasePhotoObjectStoreContextTests`는 `supabase` 설정에서 Spring 빈 생성과 읽기·쓰기 인터페이스 연결을 검증한다. 외부 Supabase 자격정보나 실제 사진은 테스트에 사용하지 않는다.

## 저장소에 포함하지 않는 것

- Supabase·Push·AI 운영 자격정보
- `.env`, 개인 환경설정 파일
- 사진 원본
- AI 모델 폴더와 테스트 출력물

현재 설정은 결제 자동 전환을 켜지 않는다. Render·Vercel·Supabase의 실제 무료 한도와 중지 조건은 각 계정의 결제 화면에서 확인한다.
