package com.staffsuite.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

import org.springframework.web.multipart.MultipartFile;

/**
 * Decides what an upload really is from its first bytes, ignoring the file name and the content type
 * the client claims. Only PDF, PNG and JPEG are accepted.
 */
public final class FileTypes {

    private FileTypes() {
    }

    /** "pdf", "png" or "jpg", or empty if the content is none of those. */
    public static Optional<String> detectExtension(MultipartFile file) throws IOException {
        byte[] head = new byte[8];
        int read;
        try (InputStream in = file.getInputStream()) {
            read = in.readNBytes(head, 0, head.length);
        }
        if (read >= 4 && head[0] == '%' && head[1] == 'P' && head[2] == 'D' && head[3] == 'F') {
            return Optional.of("pdf");
        }
        if (read >= 8 && (head[0] & 0xFF) == 0x89 && head[1] == 'P' && head[2] == 'N' && head[3] == 'G') {
            return Optional.of("png");
        }
        if (read >= 3 && (head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8 && (head[2] & 0xFF) == 0xFF) {
            return Optional.of("jpg");
        }
        return Optional.empty();
    }

    public static String contentTypeOf(String extension) {
        return switch (extension) {
            case "pdf" -> "application/pdf";
            case "png" -> "image/png";
            default -> "image/jpeg";
        };
    }
}
