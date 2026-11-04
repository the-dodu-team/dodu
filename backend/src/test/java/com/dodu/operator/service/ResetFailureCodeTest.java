package com.dodu.operator.service;

import com.dodu.operator.cli.ResetFailureCode;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ResetFailureCodeTest {
    @Test
    void classifiesNestedSqlWithoutExposingMessages() {
        assertEquals("DB_BUSY", ResetFailureCode.of(new RuntimeException(new SQLException("private detail", "55P03"))));
        assertEquals("DB_AUTH_FAILED", ResetFailureCode.of(new SQLException("private detail", "28P01")));
        assertEquals("DB_CONNECTION_FAILED", ResetFailureCode.of(new SQLException("private detail", "08001")));
        assertEquals("SCHEMA_NOT_READY", ResetFailureCode.of(new SQLException("private detail", "42P01")));
        assertEquals("UNEXPECTED_FAILURE", ResetFailureCode.of(new IllegalStateException("private credential")));
        assertEquals("STORAGE_HTTP_403", ResetFailureCode.of(new com.dodu.intervention.PhotoStorageRequestException(403)));
    }
}
