package com.dodu.intervention;

/** Carries a safe HTTP status only, never a response body, path, or credential. */
public final class PhotoStorageRequestException extends RuntimeException {
    private final int status;
    public PhotoStorageRequestException(int status) { super("Photo storage request failed"); this.status = status; }
    public int status() { return status; }
}
