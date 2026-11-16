package io.github.maiconthales.jmc_ai_analyzer_api.config;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.filter.CorsFilter;

class WebCorsConfigurationTest {

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        FrontendCorsProperties properties = new FrontendCorsProperties();
        properties.setAllowedOrigins(List.of("http://localhost:4200"));
        properties.validate();
        mvc = MockMvcBuilders.standaloneSetup(new CorsProbeController())
                .addFilters(new CorsFilter(new WebCorsConfiguration(properties).corsConfigurationSource()))
                .build();
    }

    @Test
    void returnsCorsHeadersForPermittedApiOrigin() throws Exception {
        mvc.perform(get("/api/probe").header("Origin", "http://localhost:4200"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", equalTo("http://localhost:4200")));
    }

    @Test
    void doesNotReturnCorsHeadersForUnpermittedOrigin() throws Exception {
        mvc.perform(get("/api/probe").header("Origin", "http://localhost:4300"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void answersPreflightWithRequiredMethodsAndHeaders() throws Exception {
        mvc.perform(options("/api/probe")
                        .header("Origin", "http://localhost:4200")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", equalTo("http://localhost:4200")))
                .andExpect(header().string("Access-Control-Allow-Methods", equalTo("GET,POST,OPTIONS")))
                .andExpect(header().string("Access-Control-Allow-Headers", equalTo("Content-Type")))
                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
    }

    @Test
    void doesNotApplyCorsOutsideApi() throws Exception {
        mvc.perform(get("/not-api").header("Origin", "http://localhost:4200"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void rejectsWildcardAndMalformedOrigins() {
        FrontendCorsProperties wildcard = new FrontendCorsProperties();
        wildcard.setAllowedOrigins(List.of("*"));
        assertThrows(IllegalStateException.class, wildcard::validate);

        FrontendCorsProperties malformed = new FrontendCorsProperties();
        malformed.setAllowedOrigins(List.of("localhost:4200"));
        assertThrows(IllegalStateException.class, malformed::validate);
    }

    @org.springframework.web.bind.annotation.RestController
    private static final class CorsProbeController {
        @org.springframework.web.bind.annotation.RequestMapping({"/api/probe", "/not-api"})
        String probe() {
            return "ok";
        }
    }
}
