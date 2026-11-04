# 사진 업로드 전처리

## 현재 동작

- 사용자가 선택할 수 있는 형식은 `image/jpeg`, `image/png`이다.
- 사진은 서버로 보내기 전에 브라우저에서 긴 변 최대 1920px로 축소한다.
- 업로드 파일은 JPEG로 변환하며 품질을 0.82부터 0.50까지 낮춰 최대 5 MiB를 목표로 한다.
- 원본 파일은 업로드하지 않고, 압축 결과만 사진 인증 API로 전송한다.
- EXIF 방향 정보를 고려해 이미지를 디코딩한다.
- 브라우저 압축에 실패하거나 5 MiB 이하로 만들 수 없으면 업로드를 중단한다.

## 서버 보호 한도

- Spring multipart 파일 최대 크기: 6 MB
- Spring multipart 요청 최대 크기: 7 MB
- 저장소 버킷과 AI worker의 실제 연동·기기별 QA는 배포 후 확인한다.

## 검증

- `npm run test:photo --workspace frontend`
- `npm run build --workspace frontend`
- `npm run lint --workspace frontend`
