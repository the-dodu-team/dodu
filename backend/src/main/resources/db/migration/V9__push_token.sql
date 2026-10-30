-- V9: FCM 발송에 필요한 푸시 토큰 원문.
-- 로그와 API 응답에는 절대 노출하지 않는다.
alter table notification_subscription
    add column push_token varchar(4096);