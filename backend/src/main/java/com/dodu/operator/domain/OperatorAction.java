package com.dodu.operator.domain;

/** 운영자 작업 종류. operator_audit_log.action_type에 저장됩니다(계약 §9). */
public enum OperatorAction {
    ISSUE_CREDENTIAL,
    REISSUE_CREDENTIAL,
    WITHDRAW;

    public String dbValue() {
        return name();
    }
}