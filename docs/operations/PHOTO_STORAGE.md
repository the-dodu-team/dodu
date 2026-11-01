# 사진 저장소 운영 기준

## 선택

사진 원본 저장소는 Cloudflare R2 private bucket을 사용한다. R2의 S3 호환 API를 통해 provider를 추상화한다.

- 버킷 공개 접근: 차단
- 객체 암호화: R2 기본 암호화 적용
- 객체 키: 참가자 식별정보를 포함하지 않는 무작위 UUID 기반 키
- DB: `photo_asset.storage_key`와 콘텐츠 메타데이터만 저장
- 브라우저: S3 자격정보와 공개 URL을 받지 않음
- 업로드/조회/삭제: 백엔드의 `PhotoObjectStore` 경계를 통해 수행

## 자격정보

1. Cloudflare R2 API Token을 버킷 단위로 발급하고 객체 읽기·쓰기 권한만 부여한다.
2. Access Key ID와 Secret Access Key는 Secret Manager 또는 배포 플랫폼 secret에만 저장한다.
3. 개발·스테이징·운영 자격정보는 분리한다. 저장소, Jira, 로그, 테스트 fixture에 실제 값·토큰·키 파일을 기록하지 않는다.
4. 운영 자격정보는 담당자가 발급·보관하고, 정기 교체 및 유출 의심 시 즉시 폐기·재발급한다. 교체 시 애플리케이션 재배포 후 기존 자격을 폐기한다.

## 보관·삭제

- 약속의 공식 개입 종료시각에 30일을 더한 시각을 `delete_due_at`로 계산한다.
- 삭제 worker는 due 시각이 지난 객체를 R2에서 삭제한 뒤 DB 메타데이터를 정리한다. R2 삭제 실패 행은 원본 없이도 재시도할 수 있는 삭제 작업으로 남긴다.
- 참가자 철회·삭제 요청은 연결된 객체와 메타데이터 삭제 작업을 즉시 예약한다.
- R2 Lifecycle rule은 worker 장애에 대비한 안전망으로만 사용하며, 앱의 정책 삭제보다 늦은 보수적 기간으로 설정한다. 정확한 30일 판정은 DB의 due 시각과 worker가 담당한다.

## 아직 필요한 운영 작업

- Cloudflare 계정·버킷 이름과 R2 endpoint 확정
- R2 API Token 생성 및 담당자 확인
- 실제 bucket policy, lifecycle rule, 업로드·삭제 smoke test
- Testcontainers 또는 별도 S3 호환 테스트 환경을 이용한 통합 테스트
