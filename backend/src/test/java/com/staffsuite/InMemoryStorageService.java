package com.staffsuite;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

import com.staffsuite.exception.StorageException;
import com.staffsuite.service.StorageService;

/** A StorageService that keeps files in memory: proves callers depend only on the interface. */
class InMemoryStorageService implements StorageService {

    final Map<String, byte[]> files = new ConcurrentHashMap<>();

    @Override
    public String store(String directory, String fileName, InputStream content) {
        String key = directory + "/" + fileName;
        try {
            if (files.putIfAbsent(key, content.readAllBytes()) != null) {
                throw new StorageException("already exists");
            }
        } catch (IOException e) {
            throw new StorageException("read failed", e);
        }
        return key;
    }

    @Override
    public Resource load(String reference) {
        byte[] bytes = files.get(reference);
        if (bytes == null) {
            throw new StorageException("File not found");
        }
        return new ByteArrayResource(bytes);
    }

    @Override
    public boolean exists(String reference) {
        return files.containsKey(reference);
    }

    @Override
    public void delete(String reference) {
        files.remove(reference);
    }
}
