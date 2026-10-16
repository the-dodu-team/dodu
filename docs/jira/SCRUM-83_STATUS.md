# SCRUM-83 구현 기록

- Jira: [SCRUM-83](https://dodu-mobile.atlassian.net/browse/SCRUM-83)
- 담당: mono(강민)
- Sprint: SCRUM Sprint 3
- 커밋: 작업 중
- 상태: 부분 구현. Jira 완료 처리 전.

## 이번 증분

- `POST /api/promises/{promiseId}/photo-attempts` multipart API 추가
- 활성 세션과 약속 참가자 소유권 검증
- 서버 수신시각 기준 `T ≤ received < T+30` 검증
- `clientRequestId`·fingerprint 기반 retry/conflict 처리
- `photo_asset` 메타데이터와 `auth_attempt`를 트랜잭션으로 저장
- 기존 partial unique index로 약속당 동시 `Evaluating` 1개 보장

## 검증

- `PhotoSubmissionFingerprintTests`: 2건 성공
- Java 컴파일 및 테스트 컴파일: 성공
- DB/Testcontainers 통합 검증: 아직 미실행

## 남은 SCRUM-83 범위

- Docker/Testcontainers에서 V2 마이그레이션과 실제 동시 insert 검증
- 외부 객체 저장소 업로드·삭제 adapter 연결
- 서버 환경의 파일 크기·MIME 허용 범위 최종 합의

이 증분만으로 SCRUM-83 전체 완료로 표시하지 않는다.
