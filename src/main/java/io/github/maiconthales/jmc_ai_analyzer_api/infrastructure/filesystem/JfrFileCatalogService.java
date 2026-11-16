package io.github.maiconthales.jmc_ai_analyzer_api.infrastructure.filesystem;

import io.github.maiconthales.jmc_ai_analyzer_api.dto.JfrFileResponse;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Catalogues and safely resolves files from the configured JFR directory. */
@Service
public class JfrFileCatalogService {

    /** Absolute response-size guard; configuration cannot disable this bound. */
    public static final int MAX_CATALOG_FILES = 10_000;

    private final Path dataDirectory;
    private final int maxFiles;

    @Autowired
    public JfrFileCatalogService(
            @Value("${jfr.data-dir:./jfr-data}") String dataDirectory,
            @Value("${jfr.catalog.max-files:100}") int maxFiles) {
        this(Path.of(dataDirectory), maxFiles);
    }

    /** Constructor useful for unit tests and for callers that already have a Path. */
    public JfrFileCatalogService(Path dataDirectory, int maxFiles) {
        if (maxFiles < 1 || maxFiles > MAX_CATALOG_FILES) {
            throw new IllegalArgumentException(
                    "The JFR catalog limit must be between 1 and " + MAX_CATALOG_FILES);
        }
        this.dataDirectory = dataDirectory.toAbsolutePath().normalize();
        this.maxFiles = maxFiles;
    }

    public List<JfrFileResponse> listFiles() {
        if (!Files.isDirectory(dataDirectory) || !Files.isReadable(dataDirectory)) {
            return List.of();
        }

        try (var files = Files.list(dataDirectory)) {
            return files
                    .filter(this::isReadableJfrFile)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .limit(maxFiles)
                    .map(this::toResponseIfStillReadable)
                    .filter(java.util.Objects::nonNull)
                    .toList();
        } catch (IOException e) {
            // A directory can disappear or become inaccessible between the checks.
            return List.of();
        }
    }

    /** Returns metadata for an opaque id, revalidating the file at the point of use. */
    public JfrFileResponse metadata(String id) {
        try {
            return toResponse(securePath(id));
        } catch (JfrMetadataReadException e) {
            throw new JfrFileNotFoundException(id);
        }
    }

    /**
     * Opens a controlled file without returning a previously validated Path.
     *
     * The real-path check is performed immediately before opening. The remaining
     * filesystem race window is the unavoidable limitation of Path-based Java I/O;
     * callers requiring stronger guarantees must retain/use this stream and not
     * resolve the identifier into a Path themselves.
     */
    public InputStream open(String id) throws IOException {
        Path path = securePath(id);
        return Files.newInputStream(path);
    }

    /**
     * Resolves an opaque catalog id for an internal MCP invocation.
     *
     * <p>This is intentionally the only path-valued operation exposed to the
     * MCP adapter. It repeats all catalog validation at point of use; callers
     * must not pass a path supplied by HTTP input.</p>
     */
    public Path resolveForMcp(String id) {
        return securePath(id);
    }

    /** Recreates a controlled catalog id from persisted session metadata. */
    public String idForFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            throw new InvalidJfrFileIdException(fileName);
        }
        try {
            Path path = Path.of(fileName);
            if (path.isAbsolute() || path.getNameCount() != 1
                    || !fileName.toLowerCase(Locale.ROOT).endsWith(".jfr")) {
                throw new InvalidJfrFileIdException(fileName);
            }
            String id = encodeId(fileName);
            JfrFileResponse metadata = metadata(id);
            if (!metadata.name().equals(fileName)) {
                throw new InvalidJfrFileIdException(fileName);
            }
            return id;
        } catch (InvalidPathException e) {
            throw new InvalidJfrFileIdException(fileName);
        }
    }

    private Path securePath(String id) {
        if (!isControlledId(id)) {
            throw new InvalidJfrFileIdException(id);
        }
        String fileName = decodeId(id);
        Path resolved;
        try {
            Path fileNamePath = Path.of(fileName);
            if (fileNamePath.isAbsolute() || fileNamePath.getNameCount() != 1
                    || fileName.equals(".") || fileName.equals("..")) {
                throw new InvalidJfrFileIdException(id);
            }
            resolved = dataDirectory.resolve(fileNamePath).normalize();
        } catch (InvalidPathException e) {
            throw new InvalidJfrFileIdException(id);
        }
        if (!resolved.getParent().equals(dataDirectory) || !isReadableJfrFile(resolved)) {
            throw new JfrFileNotFoundException(id);
        }
        return resolved;
    }

    private boolean isControlledId(String id) {
        if (id == null || id.isBlank()) {
            return false;
        }
        try {
            String decoded = decodeId(id);
            Path path = Path.of(decoded);
            return id.equals(encodeId(decoded))
                    && !path.isAbsolute()
                    && path.getNameCount() == 1
                    && !decoded.equals(".")
                    && !decoded.equals("..");
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private String decodeId(String id) {
        return new String(Base64.getUrlDecoder().decode(id), StandardCharsets.UTF_8);
    }

    private String encodeId(String fileName) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(fileName.getBytes(StandardCharsets.UTF_8));
    }

    private boolean isReadableJfrFile(Path path) {
        if (!Files.isRegularFile(path)
                || !Files.isReadable(path)
                || !path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jfr")) {
            return false;
        }
        try {
            // Resolve symlinks as well as lexical traversal. A symlink in the catalog
            // must not make a file outside the configured directory addressable.
            return path.toRealPath().getParent().equals(dataDirectory.toRealPath());
        } catch (IOException e) {
            return false;
        }
    }

    private JfrFileResponse toResponseIfStillReadable(Path path) {
        try {
            return toResponse(path);
        } catch (JfrMetadataReadException e) {
            // Files may disappear or become unreadable while the directory stream is open.
            return null;
        }
    }

    private JfrFileResponse toResponse(Path path) {
        try {
            return new JfrFileResponse(
                    encodeId(path.getFileName().toString()),
                    path.getFileName().toString(),
                    Files.size(path),
                    Files.getLastModifiedTime(path).toInstant());
        } catch (IOException e) {
            // The file may change while listing; omit it rather than exposing partial metadata.
            throw new JfrMetadataReadException(path, e);
        }
    }

    private static class JfrMetadataReadException extends RuntimeException {
        JfrMetadataReadException(Path path, IOException cause) {
            super("Could not read JFR metadata: " + path.getFileName(), cause);
        }
    }
}
