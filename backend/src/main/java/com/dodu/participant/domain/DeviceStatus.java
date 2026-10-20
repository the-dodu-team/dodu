package com.dodu.participant.domain;

/** {@code participant_device.lifecycle_status} 값(개발 결정). 참가자당 활성 기기는 1개입니다. */
public enum DeviceStatus {
    ACTIVE("Active"),
    INACTIVE("Inactive");

    private final String dbValue;

    DeviceStatus(String dbValue) {
        this.dbValue = dbValue;
    }

    /** DB에 저장되는 문자열 값. */
    public String dbValue() {
        return dbValue;
    }
}