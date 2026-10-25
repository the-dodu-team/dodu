package com.dodu.intervention;

/** Reads a private photo object without exposing storage credentials to the AI adapter. */
public interface PhotoObjectReader {
    PhotoObject read(String storageKey, String contentType);

    record PhotoObject(String contentType, byte[] bytes) {
        public PhotoObject {
            if (contentType == null || contentType.isBlank()) {
                throw new IllegalArgumentException("contentType is required");
            }
            if (bytes == null || bytes.length == 0) {
                throw new IllegalArgumentException("photo bytes are required");
            }
        }
    }
}
