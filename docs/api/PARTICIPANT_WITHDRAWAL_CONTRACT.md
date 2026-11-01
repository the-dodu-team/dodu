# 참가자 철회 API

## DELETE `/api/participant/account`

현재 인증된 참가자의 세션으로 철회를 요청한다. 접근 코드나 참가자 ID를 요청 본문으로 받지 않는다.

### 요청

```http
DELETE /api/participant/account
Authorization: Bearer <session-token>
```

### 성공 응답

```json
{
  "status": "success",
  "data": {
    "lifecycleStatus": "Withdrawn",
    "endedSessions": 1,
    "deactivatedSubscriptions": 1,
    "requestedAt": "2026-11-01T12:00:00Z"
  },
  "serverTime": "2026-11-01T12:00:00Z"
}
```

철회 시 세션·알림 구독을 종료하고 활성 약속을 `Withdrawn`으로 마감한다. 대기 중 자기보고와 미발송 알림은 무효화하며, 사진 원본은 `photo_asset.delete_due_at`을 현재 서버 시각으로 설정해 private storage retention worker가 삭제한다. 사진 바이트·접근 코드·원본 설문 응답은 응답이나 로그에 포함하지 않는다.

인증이 없거나 이미 철회된 세션이면 공통 오류 응답의 `ACCESS_REQUIRED` 또는 `ACCESS_REVOKED`를 반환한다.
