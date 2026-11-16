package io.github.maiconthales.jmc_ai_analyzer_api.api;

import io.github.maiconthales.jmc_ai_analyzer_api.dto.JfrFileResponse;
import io.github.maiconthales.jmc_ai_analyzer_api.infrastructure.filesystem.JfrFileCatalogService;
import io.github.maiconthales.jmc_ai_analyzer_api.infrastructure.filesystem.InvalidJfrFileIdException;
import io.github.maiconthales.jmc_ai_analyzer_api.infrastructure.filesystem.JfrFileNotFoundException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v1/jfr-files", produces = MediaType.APPLICATION_JSON_VALUE)
public class JfrFileController {

    private final JfrFileCatalogService catalog;

    public JfrFileController(JfrFileCatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping
    public List<JfrFileResponse> listFiles() {
        return catalog.listFiles();
    }

    // Kept as an internal validation endpoint for the future analysis flow; it does not expose a path.
    @GetMapping("/{id}")
    public JfrFileResponse getFile(@PathVariable String id) {
        return catalog.metadata(id);
    }

}
