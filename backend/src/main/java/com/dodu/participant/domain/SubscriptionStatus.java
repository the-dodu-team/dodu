package com.dodu.participant.domain;

/** {@code notification_subscription.lifecycle_status} 값(개발 결정). */
public enum SubscriptionStatus {
    ACTIVE("Active"),
    INACTIVE("Inactive");

    private final String dbValue;

    SubscriptionStatus(String dbValue) {
        this.dbValue = dbValue;
    }

    /** DB에 저장되는 문자열 값. */
    public String dbValue() {
        return dbValue;
    }
}