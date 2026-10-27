package com.staffsuite.service;

import java.io.InputStream;

import org.springframework.core.io.Resource;

import com.staffsuite.exception.StorageException;

/**
 * Where uploaded files live. Business code depends only on this interface and treats the returned
 * {@code reference} as an opaque string to keep in the database, so the backend can be replaced
 * (for example by an S3-compatible store) without touching any caller.
 *
 * <p>A different implementation is selected with {@code staffsuite.storage.type}; the local
 * filesystem one is the default.
 */
public interface StorageService {

    /**
     * Saves the content as {@code directory/fileName} and returns the reference to persist. Never
     * overwrites an existing file.
     *
     * @throws StorageException if the name is unsafe or the content cannot be written
     */
    String store(String directory, String fileName, InputStream content);

    /** @throws StorageException if the reference is invalid or the file does not exist */
    Resource load(String reference);

    /** False for unknown or malformed references; never throws for those. */
    boolean exists(String reference);

    /** Removes the file. Deleting something that is already gone is not an error. */
    void delete(String reference);
}
