-- V10: FCM 발송에 필요한 푸시 토큰 원문. 배포된 V9 사진 보관 마이그레이션은 보존한다.
-- 로그와 API 응답에는 절대 노출하지 않는다.
alter table notification_subscription
    add column push_token varchar(4096);
