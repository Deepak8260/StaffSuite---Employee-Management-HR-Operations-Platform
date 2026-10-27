package com.staffsuite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.staffsuite.config.AppProperties;
import com.staffsuite.exception.StorageException;
import com.staffsuite.service.LocalStorageService;
import com.staffsuite.service.StorageService;

/** The real filesystem implementation, on its own (no Spring context). */
class LocalStorageServiceTests {

    @TempDir Path root;
    StorageService storage;

    @BeforeEach
    void setUp() {
        storage = new LocalStorageService(new AppProperties(null, null,
                new AppProperties.Storage(root.toString(), "local"), null, null, null, null));
    }

    private static ByteArrayInputStream bytes(String s) {
        return new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void storesLoadsAndDeletesFiles() throws IOException {
        String ref = storage.store("employee-documents/7", "abc.pdf", bytes("hello"));
        assertThat(ref).isEqualTo("employee-documents/7/abc.pdf");
        assertThat(storage.exists(ref)).isTrue();
        assertThat(Files.readString(root.resolve(ref))).isEqualTo("hello");
        assertThat(storage.load(ref).getContentAsString(StandardCharsets.UTF_8)).isEqualTo("hello");

        storage.delete(ref);
        assertThat(storage.exists(ref)).isFalse();
        storage.delete(ref);   // deleting something already gone is not an error
        assertThatThrownBy(() -> storage.load(ref)).isInstanceOf(StorageException.class);
    }

    @Test
    void neverOverwritesAnExistingFile() {
        storage.store("d", "same.pdf", bytes("first"));
        assertThatThrownBy(() -> storage.store("d", "same.pdf", bytes("second"))).isInstanceOf(StorageException.class);
    }

    @Test
    void rejectsReferencesThatCouldEscapeTheStorageRoot() {
        for (String bad : new String[] { "../outside.txt", "a/../../outside.txt", "/etc/passwd", "a//b.txt",
                "a/b c.txt", "a\\b.txt", "", "..", "a/..", "C:/Windows/win.ini" }) {
            assertThat(storage.exists(bad)).as("exists(%s)", bad).isFalse();
            assertThatThrownBy(() -> storage.load(bad)).as("load(%s)", bad).isInstanceOf(StorageException.class);
            assertThatThrownBy(() -> storage.store(bad, "x.pdf", bytes("x"))).as("store(%s)", bad)
                    .isInstanceOf(StorageException.class);
            assertThatThrownBy(() -> storage.delete(bad)).as("delete(%s)", bad).isInstanceOf(StorageException.class);
        }
        assertThat(root.getParent().resolve("outside.txt")).doesNotExist();
    }
}
