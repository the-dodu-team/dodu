package com.dodu.operator.cli;

/** Only allowlisted categories leave the process; exception text and credentials never do. */
public final class ResetFailureCode {
    private ResetFailureCode() { }
    public static String of(Throwable failure) {
        for (int depth = 0; failure != null && depth < 20; depth++, failure = failure.getCause()) {
            if (failure instanceof java.sql.SQLException sql && sql.getSQLState() != null) {
                String state = sql.getSQLState();
                if ("55P03".equals(state)) return "DB_BUSY";
                if ("28P01".equals(state) || "28000".equals(state)) return "DB_AUTH_FAILED";
                if (state.startsWith("08")) return "DB_CONNECTION_FAILED";
                if ("42P01".equals(state)) return "SCHEMA_NOT_READY";
                if ("23503".equals(state)) return "DEPENDENT_RECORDS";
                return "DB_OPERATION_FAILED";
            }
            String message = failure.getMessage();
            if ("Photo storage must be configured before reset".equals(message)) return "STORAGE_NOT_CONFIGURED";
            if ("Reset scope has an unexpected dependent table".equals(message)) return "UNEXPECTED_DEPENDENCY";
            if (message != null && message.startsWith("Photo storage")) return "STORAGE_REQUEST_FAILED";
        }
        return "UNEXPECTED_FAILURE";
    }
}
