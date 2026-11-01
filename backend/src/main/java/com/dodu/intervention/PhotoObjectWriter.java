package com.dodu.intervention;

/** Stores private photo bytes and returns the opaque storage key persisted in PostgreSQL. */
public interface PhotoObjectWriter {
    String write(String storageKey, String contentType, byte[] bytes);

    void delete(String storageKey);
}
