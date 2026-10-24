package com.dodu.operator.domain;

/** 운영자 작업 결과. operator_audit_log.result에 저장됩니다. */
public enum OperatorResult {
    SUCCESS,
    REJECTED,
    FAILED;

    public String dbValue() {
        return name();
    }
}