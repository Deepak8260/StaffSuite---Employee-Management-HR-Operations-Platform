package com.staffsuite.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import com.staffsuite.config.AppProperties;
import com.staffsuite.exception.StorageException;

/** Development/default storage: files under the directory configured as FILE_STORAGE_PATH. */
@Service
@ConditionalOnProperty(name = "staffsuite.storage.type", havingValue = "local", matchIfMissing = true)
public class LocalStorageService implements StorageService {

    /** Letters, digits, dot, dash, underscore and forward slashes only. */
    private static final Pattern SAFE = Pattern.compile("^[A-Za-z0-9._/-]+$");

    private final Path root;

    public LocalStorageService(AppProperties props) {
        this.root = Paths.get(props.storage().path()).toAbsolutePath().normalize();
    }

    @Override
    public String store(String directory, String fileName, InputStream content) {
        String reference = directory + "/" + fileName;
        Path target = resolve(reference);
        try {
            Files.createDirectories(target.getParent());
            Files.copy(content, target);   // fails if it already exists: stored files are never overwritten
        } catch (IOException e) {
            throw new StorageException("Could not store the file", e);
        }
        return reference;
    }

    @Override
    public Resource load(String reference) {
        Path path = resolve(reference);
        if (!Files.isRegularFile(path)) {
            throw new StorageException("File not found");
        }
        return new PathResource(path);
    }

    @Override
    public boolean exists(String reference) {
        try {
            return Files.isRegularFile(resolve(reference));
        } catch (StorageException e) {
            return false;
        }
    }

    @Override
    public void delete(String reference) {
        try {
            Files.deleteIfExists(resolve(reference));
        } catch (IOException e) {
            throw new StorageException("Could not delete the file", e);
        }
    }

    /** Rejects anything that could escape the storage root before touching the filesystem. */
    private Path resolve(String reference) {
        if (reference == null || reference.isBlank() || !SAFE.matcher(reference).matches()
                || reference.startsWith("/") || reference.contains("..") || reference.contains("//")) {
            throw new StorageException("Invalid storage reference");
        }
        Path path = root.resolve(reference).normalize();
        if (!path.startsWith(root)) {
            throw new StorageException("Invalid storage reference");
        }
        return path;
    }
}
