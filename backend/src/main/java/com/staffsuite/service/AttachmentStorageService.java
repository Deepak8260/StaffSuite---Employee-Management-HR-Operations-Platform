package com.staffsuite.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.staffsuite.config.AppProperties;
import com.staffsuite.exception.ApiException;

/**
 * Stores leave attachments under the configured storage directory. File names are generated
 * (never taken from the client) and the type is decided from the file's own bytes.
 */
@Service
public class AttachmentStorageService {

    public static final long MAX_BYTES = 5L * 1024 * 1024;

    private static final String DIR = "leave-attachments";
    private static final Pattern URL_PATTERN =
            Pattern.compile("^" + DIR + "/(\\d+)/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(pdf|png|jpg)$");

    private final Path root;

    public AttachmentStorageService(AppProperties props) {
        this.root = Paths.get(props.storage().path()).toAbsolutePath().normalize();
    }

    /** Saves the file for an employee and returns its relative storage path. */
    public String store(Long employeeId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Please choose a file to upload");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "File is too large (maximum 5 MB)");
        }
        try {
            String extension = detectExtension(file);
            Path folder = root.resolve(DIR).resolve(String.valueOf(employeeId)).normalize();
            Files.createDirectories(folder);
            String fileName = UUID.randomUUID() + "." + extension;
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, folder.resolve(fileName));
            }
            return DIR + "/" + employeeId + "/" + fileName;
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not store the attachment");
        }
    }

    /** True if the path is a well-formed attachment path, belongs to this employee, and the file exists. */
    public boolean isOwnedAndPresent(String url, Long employeeId) {
        Matcher m = URL_PATTERN.matcher(url);
        return m.matches() && m.group(1).equals(String.valueOf(employeeId)) && Files.isRegularFile(resolve(url));
    }

    public Resource load(String url) {
        if (!URL_PATTERN.matcher(url).matches()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Attachment not found");
        }
        Path path = resolve(url);
        if (!Files.isRegularFile(path)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Attachment not found");
        }
        return new PathResource(path);
    }

    public String contentTypeOf(String url) {
        return switch (url.substring(url.lastIndexOf('.') + 1)) {
            case "pdf" -> "application/pdf";
            case "png" -> "image/png";
            default -> "image/jpeg";
        };
    }

    public String extensionOf(String url) {
        return url.substring(url.lastIndexOf('.') + 1);
    }

    private Path resolve(String url) {
        Path path = root.resolve(url).normalize();
        if (!path.startsWith(root)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Attachment not found");
        }
        return path;
    }

    private static String detectExtension(MultipartFile file) throws IOException {
        byte[] head = new byte[8];
        int read;
        try (InputStream in = file.getInputStream()) {
            read = in.readNBytes(head, 0, head.length);
        }
        if (read >= 4 && head[0] == '%' && head[1] == 'P' && head[2] == 'D' && head[3] == 'F') {
            return "pdf";
        }
        if (read >= 8 && (head[0] & 0xFF) == 0x89 && head[1] == 'P' && head[2] == 'N' && head[3] == 'G') {
            return "png";
        }
        if (read >= 3 && (head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8 && (head[2] & 0xFF) == 0xFF) {
            return "jpg";
        }
        throw new ApiException(HttpStatus.BAD_REQUEST, "Only PDF, PNG and JPEG files are allowed");
    }
}
