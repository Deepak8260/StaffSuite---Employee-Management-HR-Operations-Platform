package com.staffsuite.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.staffsuite.exception.ApiException;
import com.staffsuite.exception.StorageException;
import com.staffsuite.util.FileTypes;

import lombok.RequiredArgsConstructor;

/**
 * Leave-request attachments. Validation (size, real file type, ownership) lives here; where the
 * bytes are kept is the {@link StorageService}'s business.
 */
@Service
@RequiredArgsConstructor
public class AttachmentStorageService {

    public static final long MAX_BYTES = 5L * 1024 * 1024;

    private static final String DIR = "leave-attachments";
    private static final Pattern URL_PATTERN =
            Pattern.compile("^" + DIR + "/(\\d+)/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(pdf|png|jpg)$");

    private final StorageService storage;

    /** Saves the file for an employee and returns its storage reference. */
    public String store(Long employeeId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Please choose a file to upload");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "File is too large (maximum 5 MB)");
        }
        try {
            String extension = FileTypes.detectExtension(file)
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Only PDF, PNG and JPEG files are allowed"));
            try (InputStream in = file.getInputStream()) {
                return storage.store(DIR + "/" + employeeId, UUID.randomUUID() + "." + extension, in);
            }
        } catch (IOException | StorageException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not store the attachment");
        }
    }

    /** True if the reference is a well-formed attachment path, belongs to this employee, and the file exists. */
    public boolean isOwnedAndPresent(String url, Long employeeId) {
        Matcher m = URL_PATTERN.matcher(url);
        return m.matches() && m.group(1).equals(String.valueOf(employeeId)) && storage.exists(url);
    }

    public Resource load(String url) {
        if (!URL_PATTERN.matcher(url).matches() || !storage.exists(url)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Attachment not found");
        }
        try {
            return storage.load(url);
        } catch (StorageException e) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Attachment not found");
        }
    }

    public String contentTypeOf(String url) {
        return FileTypes.contentTypeOf(extensionOf(url));
    }

    public String extensionOf(String url) {
        return url.substring(url.lastIndexOf('.') + 1);
    }
}
