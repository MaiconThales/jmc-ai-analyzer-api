package io.github.maiconthales.jmc_ai_analyzer_api.system.components;

import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.UploadRequest;
import io.github.maiconthales.jmc_ai_analyzer_api.system.enums.Environment;
import io.github.maiconthales.jmc_ai_analyzer_api.system.enums.ErrorCode;
import io.github.maiconthales.jmc_ai_analyzer_api.system.exceptions.InvalidUploadException;
import io.github.maiconthales.jmc_ai_analyzer_api.system.properties.UploadProperties;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/** Validações do upload que não dependem do conteúdo do arquivo (V1 a V4 e V6 da spec). */
@Component
@RequiredArgsConstructor
public class UploadValidator {

	static final Pattern SOFTWARE = Pattern.compile("^[a-z0-9][a-z0-9-]{1,63}$");
	static final int MAX_VERSION = 64;
	static final int MAX_DESCRIPTION = 500;
	static final String EXTENSION = ".jfr";

	private final UploadProperties properties;

	/**
	 * Valida os campos e os arquivos.
	 *
	 * @return o ambiente convertido, ou {@code null} se não informado
	 */
	public Environment validate(UploadRequest request, List<MultipartFile> files) {
		if (request.software() == null || !SOFTWARE.matcher(request.software()).matches()) {
			throw invalid("'software' é obrigatório e deve seguir o padrão " + SOFTWARE.pattern() + ".");
		}
		if (request.softwareVersion() != null && request.softwareVersion().length() > MAX_VERSION) {
			throw invalid("'softwareVersion' aceita até " + MAX_VERSION + " caracteres.");
		}
		if (request.description() != null && request.description().length() > MAX_DESCRIPTION) {
			throw invalid("'description' aceita até " + MAX_DESCRIPTION + " caracteres.");
		}
		Environment environment = parseEnvironment(request.environment());
		validateFiles(files);
		return environment;
	}

	private void validateFiles(List<MultipartFile> files) {
		List<MultipartFile> present = files == null ? List.of() : files.stream().filter(f -> !f.isEmpty()).toList();
		if (present.isEmpty() || present.size() != files.size()) {
			throw new InvalidUploadException(ErrorCode.EMPTY_UPLOAD, "Envie ao menos um arquivo .jfr não vazio.");
		}
		if (present.size() > properties.maxFiles()) {
			throw new InvalidUploadException(ErrorCode.TOO_MANY_FILES,
					"Máximo de " + properties.maxFiles() + " arquivos por lote.");
		}
		long total = 0;
		for (MultipartFile file : present) {
			String name = file.getOriginalFilename();
			if (name == null || !name.toLowerCase(Locale.ROOT).endsWith(EXTENSION)) {
				throw new InvalidUploadException(ErrorCode.INVALID_FILE, "O arquivo '" + name + "' não tem extensão .jfr.");
			}
			if (file.getSize() > properties.maxFileSize().toBytes()) {
				throw new InvalidUploadException(ErrorCode.FILE_TOO_LARGE,
						"O arquivo '" + name + "' excede " + properties.maxFileSize() + ".");
			}
			total += file.getSize();
		}
		if (total > properties.maxRequestSize().toBytes()) {
			throw new InvalidUploadException(ErrorCode.FILE_TOO_LARGE,
					"O lote excede " + properties.maxRequestSize() + ".");
		}
	}

	private static Environment parseEnvironment(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			return Environment.valueOf(value.trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			throw invalid("'environment' deve ser PROD, HML, DEV ou LOCAL.");
		}
	}

	private static InvalidUploadException invalid(String detail) {
		return new InvalidUploadException(ErrorCode.INVALID_PARAMETER, detail);
	}
}
