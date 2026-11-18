package io.github.maiconthales.jmc_ai_analyzer_api.system.services;

import io.github.maiconthales.jmc_ai_analyzer_api.system.components.UploadValidator;
import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.BatchResponse;
import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.UploadRequest;
import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.processing.StoredFile;
import io.github.maiconthales.jmc_ai_analyzer_api.system.enums.Environment;
import io.github.maiconthales.jmc_ai_analyzer_api.system.enums.ErrorCode;
import io.github.maiconthales.jmc_ai_analyzer_api.system.exceptions.InvalidUploadException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;
import java.util.zip.GZIPInputStream;

/**
 * Upload de um lote: valida, grava cada arquivo no blob (deduplicando por SHA-256) e persiste o lote.
 * É tudo ou nada: em qualquer falha, os objetos criados nesta requisição são apagados.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UploadService {

    static final byte[] JFR_MAGIC = {'F', 'L', 'R', 0};
    static final byte[] GZIP_MAGIC = {31, -117};
    public static final String KEY_PREFIX = "recordings/";
    static final int MAX_NAME = 255;

    private final UploadValidator validator;
    private final TempFileService tempFiles;
    private final BlobStorageService blobStorage;
    private final BatchCreationService batchCreator;

    /**
     * Valida e grava os arquivos, criando o lote.
     * Em caso de falha, remove os objetos criados nesta requisição.
     *
     * @param request O pedido de upload contendo metadados do lote.
     * @param files   A lista de arquivos a serem enviados.
     * @return A resposta do lote criado.
     */
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

    /**
     * Armazena o arquivo no blob storage, deduplicando por SHA-256.
     * Se o arquivo já existir, não faz upload novamente.
     *
     * @param file        O arquivo a ser armazenado.
     * @param createdKeys A lista de chaves criadas durante o upload.
     * @return O arquivo armazenado.
     */
    private StoredFile store(MultipartFile file, List<String> createdKeys) {
        String name = sanitize(file.getOriginalFilename());
        Path temp = tempFiles.create(".jfr");
        try {
            String sha256 = copyAndNormalizeJfr(file, temp, name);

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

    /**
     * Copia o arquivo para o destino, normalizando e validando o conteúdo JFR.
     * Calcula o SHA-256 do conteúdo normalizado.
     *
     * @param file     O arquivo a ser copiado e normalizado.
     * @param target   O caminho de destino para o arquivo normalizado.
     * @param fileName O nome do arquivo original.
     * @return O SHA-256 do conteúdo normalizado.
     * @throws IOException Se ocorrer um erro de I/O durante a cópia ou normalização.
     */
    static String copyAndNormalizeJfr(MultipartFile file, Path target, String fileName) throws IOException {
        MessageDigest digest = sha256();
        try (InputStream jfrStream = openValidatedJfrStream(file.getInputStream(), fileName);
             DigestInputStream digestIn = new DigestInputStream(jfrStream, digest);
             OutputStream out = Files.newOutputStream(target)) {

            digestIn.transferTo(out);
            return HexFormat.of().formatHex(digest.digest());
        } catch (java.util.zip.ZipException e) {
            throw new InvalidUploadException(ErrorCode.INVALID_FILE,
                    "O arquivo '" + fileName + "' está corrompido.");
        }
    }

    /**
     * Abre o InputStream do arquivo, validando se é um JFR ou um GZIP contendo um JFR.
     * Se for GZIP, descompacta e valida o conteúdo.
     *
     * @param rawIn    O InputStream bruto do arquivo.
     * @param fileName O nome do arquivo original, usado para mensagens de erro.
     * @return Um InputStream válido do conteúdo JFR.
     * @throws IOException            Se ocorrer um erro de I/O durante a leitura ou descompactação.
     * @throws InvalidUploadException Se o arquivo não for um JFR válido ou um GZIP contendo um JFR válido.
     */
    private static InputStream openValidatedJfrStream(InputStream rawIn, String fileName) throws IOException {
        BufferedInputStream bufferedIn = new BufferedInputStream(rawIn);
        byte[] header = peekHeader(bufferedIn, 4);

        if (isGzip(header)) {
            BufferedInputStream decompressedIn = new BufferedInputStream(new GZIPInputStream(bufferedIn));
            if (!Arrays.equals(peekHeader(decompressedIn, JFR_MAGIC.length), JFR_MAGIC)) {
                decompressedIn.close();
                throw new InvalidUploadException(ErrorCode.INVALID_FILE,
                        "O arquivo GZIP '" + fileName + "' não contém uma gravação JFR válida.");
            }
            return decompressedIn;
        }

        if (!Arrays.equals(header, JFR_MAGIC)) {
            bufferedIn.close();
            throw new InvalidUploadException(ErrorCode.INVALID_FILE,
                    "O arquivo '" + fileName + "' não é uma gravação JFR.");
        }

        return bufferedIn;
    }

    /**
     * Lê os primeiros bytes do InputStream sem consumir o stream, permitindo que ele seja lido novamente.
     *
     * @param in     O BufferedInputStream a ser lido.
     * @param length O número de bytes a serem lidos do início do stream.
     * @return Um array de bytes contendo os primeiros bytes lidos.
     * @throws IOException Se ocorrer um erro de I/O durante a leitura.
     */
    private static byte[] peekHeader(BufferedInputStream in, int length) throws IOException {
        in.mark(length);
        byte[] header = in.readNBytes(length);
        in.reset();
        return header;
    }

    /**
     * Verifica se o cabeçalho corresponde a um arquivo GZIP.
     *
     * @param header O array de bytes contendo os primeiros bytes do arquivo.
     * @return true se o cabeçalho corresponder a um GZIP, false caso contrário.
     */
    private static boolean isGzip(byte[] header) {
        return header.length >= 2
                && header[0] == GZIP_MAGIC[0]
                && header[1] == GZIP_MAGIC[1];
    }

    /**
     * Remove o objeto do blob storage de forma silenciosa, registrando um aviso em caso de falha.
     *
     * @param key A chave do objeto a ser removido.
     */
    private void deleteQuietly(String key) {
        try {
            blobStorage.delete(key);
        } catch (RuntimeException e) {
            log.warn("Não foi possível remover o objeto {} após falha no upload", key, e);
        }
    }

    /**
     * Sanitiza o nome do arquivo, removendo caracteres inválidos e limitando o tamanho.
     *
     * @param originalName O nome original do arquivo.
     * @return O nome sanitizado do arquivo.
     */
    static String sanitize(String originalName) {
        String name = originalName == null ? "recording.jfr" : originalName;
        name = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);
        name = name.replaceAll("\\p{Cntrl}", "").strip();
        if (name.isEmpty()) {
            name = "recording.jfr";
        }
        return name.length() > MAX_NAME ? name.substring(name.length() - MAX_NAME) : name;
    }

    /**
     * Substitui caracteres não ASCII por underscores.
     *
     * @param value O valor a ser processado.
     * @return O valor contendo apenas caracteres ASCII.
     */
    private static String asciiOnly(String value) {
        return value.replaceAll("[^\\x20-\\x7E]", "_");
    }

    /**
     * Cria uma instância de MessageDigest para SHA-256.
     *
     * @return A instância de MessageDigest para SHA-256.
     */
    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }
}
