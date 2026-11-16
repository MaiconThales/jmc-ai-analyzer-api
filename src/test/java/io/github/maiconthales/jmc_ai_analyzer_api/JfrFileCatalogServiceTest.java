package io.github.maiconthales.jmc_ai_analyzer_api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.github.maiconthales.jmc_ai_analyzer_api.infrastructure.filesystem.InvalidJfrFileIdException;
import io.github.maiconthales.jmc_ai_analyzer_api.infrastructure.filesystem.JfrFileCatalogService;
import io.github.maiconthales.jmc_ai_analyzer_api.infrastructure.filesystem.JfrFileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JfrFileCatalogServiceTest {

    @TempDir
    Path tempDirectory;

    @Test
    void listsOnlyReadableRegularJfrFilesAndAppliesLimit() throws Exception {
        Files.writeString(tempDirectory.resolve("valid.jfr"), "jfr");
        Files.writeString(tempDirectory.resolve("ignored.txt"), "text");
        Files.createDirectory(tempDirectory.resolve("nested.jfr"));
        Files.writeString(tempDirectory.resolve("second.jfr"), "second");

        var files = new JfrFileCatalogService(tempDirectory, 1).listFiles();

        assertEquals(1, files.size());
        assertEquals(Base64.getUrlEncoder().withoutPadding()
                .encodeToString("second.jfr".getBytes()), files.getFirst().id());
        assertEquals("second.jfr", files.getFirst().name());
    }

    @Test
    void rejectsTraversalAndUnknownIds() throws Exception {
        Files.writeString(tempDirectory.resolve("valid.jfr"), "jfr");
        var service = new JfrFileCatalogService(tempDirectory, 10);

        assertThrows(InvalidJfrFileIdException.class, () -> service.metadata("../valid.jfr"));
        assertThrows(InvalidJfrFileIdException.class, () -> service.metadata("sub/valid.jfr"));
        assertThrows(JfrFileNotFoundException.class, () -> service.metadata(
                Base64.getUrlEncoder().withoutPadding()
                        .encodeToString("missing.jfr".getBytes())));
    }

    @Test
    void supportsOpaqueIdsForLinuxNamesContainingBackslash() throws Exception {
        assumeTrue(java.io.File.separatorChar == '/',
                "A backslash is a path separator on Windows, not a Linux filename character");
        String fileName = "capture\\2026.jfr";
        Files.writeString(tempDirectory.resolve(fileName), "jfr");

        var service = new JfrFileCatalogService(tempDirectory, 10);
        var file = service.listFiles().getFirst();

        assertTrue(!file.id().contains("/"));
        assertEquals(fileName, service.metadata(file.id()).name());
    }

    @Test
    void rejectsMalformedOpaqueIdAndCatalogLimitAboveAbsoluteCeiling() {
        var service = new JfrFileCatalogService(tempDirectory, 10);
        assertThrows(InvalidJfrFileIdException.class, () -> service.metadata("%%%"));
        assertThrows(IllegalArgumentException.class,
                () -> new JfrFileCatalogService(tempDirectory, JfrFileCatalogService.MAX_CATALOG_FILES + 1));
    }

    @Test
    void treatsFileDisappearingBeforeMetadataAsNotFoundWithoutExposingPath() throws Exception {
        Path file = tempDirectory.resolve("vanishing.jfr");
        Files.writeString(file, "jfr");
        var service = new JfrFileCatalogService(tempDirectory, 10);
        String id = service.listFiles().getFirst().id();
        Files.delete(file);

        JfrFileNotFoundException error = assertThrows(
                JfrFileNotFoundException.class, () -> service.metadata(id));
        assertTrue(!error.getMessage().contains(tempDirectory.toString()));
    }

    @Test
    void rejectsSymlinkOutsideConfiguredDirectory() throws Exception {
        Path outside = tempDirectory.getParent().resolve("outside.jfr");
        Files.writeString(outside, "outside");
        Path link = tempDirectory.resolve("link.jfr");
        try {
            try {
                Files.createSymbolicLink(link, outside);
            } catch (java.nio.file.FileSystemException e) {
                assumeTrue(false, "Symbolic links are unavailable on this Windows test host");
            }
            var service = new JfrFileCatalogService(tempDirectory, 10);
            assertTrue(service.listFiles().isEmpty());
        } finally {
            Files.deleteIfExists(link);
            Files.deleteIfExists(outside);
        }
    }

    @Test
    void acceptsConfiguredDirectoryThatIsASymlinkAndKeepsFilesInsideItsRealRoot() throws Exception {
        Path realDirectory = tempDirectory.resolve("real");
        Files.createDirectory(realDirectory);
        Files.writeString(realDirectory.resolve("recording.jfr"), "jfr");
        Path configuredLink = tempDirectory.resolve("configured");
        try {
            try {
                Files.createSymbolicLink(configuredLink, realDirectory);
            } catch (java.nio.file.FileSystemException e) {
                assumeTrue(false, "Symbolic links are unavailable on this Windows test host");
            }
            var service = new JfrFileCatalogService(configuredLink, 10);
            assertEquals("recording.jfr", service.listFiles().getFirst().name());
        } finally {
            Files.deleteIfExists(configuredLink);
        }
    }
}
