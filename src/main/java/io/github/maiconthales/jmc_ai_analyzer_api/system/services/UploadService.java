package io.github.maiconthales.jmc_ai_analyzer_api.system.services;

import io.github.maiconthales.jmc_ai_analyzer_api.system.components.UploadValidator;
import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.BatchResponse;
import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.UploadRequest;
import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.processing.StoredFile;
import io.github.maiconthales.jmc_ai_analyzer_api.system.enums.Environment;
import io.github.maiconthales.jmc_ai_analyzer_api.system.enums.ErrorCode;
import io.github.maiconthales.jmc_ai_analyzer_api.system.exceptions.InvalidUploadException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Upload de um lote: valida, grava cada arquivo no blob (deduplicando por SHA-256) e persiste o lote.
 * É tudo ou nada: em qualquer falha, os objetos criados nesta requisição são apagados.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UploadService {

	static final byte[] JFR_MAGIC = {'F', 'L', 'R', 0};
	public static final String KEY_PREFIX = "recordings/";
	static final int MAX_NAME = 255;

	private final UploadValidator validator;
	private final TempFileService tempFiles;
	private final BlobStorageService blobStorage;
	private final BatchCreationService batchCreator;

	public BatchResponse upload(UploadRequest request, List<MultipartFile> files) {
		Environment environment = validator.validate(request, files);
		List<String> createdKeys = new ArrayList<>();
		try {
			List<StoredFile> stored = new ArrayList<>();
			for (MultipartFile file : files) {
				stored.add(store(file, createdKeys));
			}
			return batchCreator.create(request, environment, stored);
		} catch (RuntimeException e) {
			createdKeys.forEach(this::deleteQuietly);
			throw e;
		}
	}

	private StoredFile store(MultipartFile file, List<String> createdKeys) {
		String name = sanitize(file.getOriginalFilename());
		Path temp = tempFiles.create(".jfr");
		try {
			String sha256 = copyWithDigest(file, temp);
			if (!hasJfrMagic(temp)) {
				throw new InvalidUploadException(ErrorCode.INVALID_FILE, "O arquivo '" + name + "' não é uma gravação JFR.");
			}
			String key = KEY_PREFIX + sha256 + ".jfr";
			if (!blobStorage.exists(key)) {
				blobStorage.upload(key, temp, Map.of("original-name", asciiOnly(name), "sha256", sha256,
						"uploaded-at", Instant.now().toString()));
				createdKeys.add(key);
			}
			return new StoredFile(name, key, sha256, Files.size(temp));
		} catch (IOException e) {
			throw new UncheckedIOException("Falha ao ler o arquivo enviado", e);
		} finally {
			tempFiles.deleteQuietly(temp);
		}
	}

	private void deleteQuietly(String key) {
		try {
			blobStorage.delete(key);
		} catch (RuntimeException e) {
			log.warn("Não foi possível remover o objeto {} após falha no upload", key, e);
		}
	}

	static String copyWithDigest(MultipartFile file, Path target) throws IOException {
		MessageDigest digest = sha256();
		try (InputStream in = new DigestInputStream(file.getInputStream(), digest);
				OutputStream out = Files.newOutputStream(target)) {
			in.transferTo(out);
		}
		return HexFormat.of().formatHex(digest.digest());
	}

	static boolean hasJfrMagic(Path file) throws IOException {
		try (InputStream in = Files.newInputStream(file)) {
			return Arrays.equals(in.readNBytes(JFR_MAGIC.length), JFR_MAGIC);
		}
	}

	/** Remove diretórios e caracteres de controle e limita o tamanho do nome. */
	static String sanitize(String originalName) {
		String name = originalName == null ? "recording.jfr" : originalName;
		name = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);
		name = name.replaceAll("\\p{Cntrl}", "").strip();
		if (name.isEmpty()) {
			name = "recording.jfr";
		}
		return name.length() > MAX_NAME ? name.substring(name.length() - MAX_NAME) : name;
	}

	private static String asciiOnly(String value) {
		return value.replaceAll("[^\\x20-\\x7E]", "_");
	}

	private static MessageDigest sha256() {
		try {
			return MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 indisponível", e);
		}
	}
}
