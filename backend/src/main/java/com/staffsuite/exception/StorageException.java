package com.staffsuite.exception;

/** A storage backend could not complete an operation. Callers decide how to present it. */
public class StorageException extends RuntimeException {

    public StorageException(String message) {
        super(message);
    }

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
