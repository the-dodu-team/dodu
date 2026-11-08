package com.dodu.notification;

/** 기기 전환 안내의 외부 발송 포트. SDK 예외·요청 객체를 내부로 노출하지 않는다. */
public interface DeviceChangePushSender {
    Result send(String token);
    enum Status { ACCEPTED, VALIDATED, REJECTED, FAILED }
    record Result(Status status, String failureCode) { }
}
