package com.theotech.common.exception;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Envelope shapes for every error class, without a Spring context. */
class GlobalExceptionHandlerTest {

    private MockMvc mvc;

    @RestController
    static class ThrowingController {
        record Body(@NotBlank(message = "required") String name) {}

        @GetMapping("/t/not-found") void notFound() { throw new NotFoundException("Product", 42); }
        @GetMapping("/t/conflict") void conflict() { throw new ConflictException("DUPLICATE_SKU", "SKU exists"); }
        @GetMapping("/t/forbidden") void forbidden() { throw new ForbiddenException("No"); }
        @GetMapping("/t/denied") void denied() { throw new AccessDeniedException("denied"); }
        @GetMapping("/t/stale") void stale() { throw new OptimisticLockingFailureException("stale"); }
        @GetMapping("/t/integrity") void integrity() { throw new DataIntegrityViolationException("fk"); }
        @GetMapping("/t/boom") void boom() { throw new IllegalStateException("secret internal detail"); }
        @PostMapping("/t/validate") String validate(@Valid @RequestBody Body body) { return "ok"; }
    }

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void notFoundIs404WithCode() throws Exception {
        mvc.perform(get("/t/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Product not found: 42"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void conflictIs409() throws Exception {
        mvc.perform(get("/t/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_SKU"));
    }

    @Test
    void forbiddenVariantsAre403() throws Exception {
        mvc.perform(get("/t/forbidden")).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(get("/t/denied")).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void staleAndIntegrityAre409() throws Exception {
        mvc.perform(get("/t/stale")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_RECORD"));
        mvc.perform(get("/t/integrity")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DATA_INTEGRITY"));
    }

    @Test
    void unexpectedErrorHidesDetailsAndGivesReference() throws Exception {
        mvc.perform(get("/t/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith("Unexpected error. Reference: ")))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret"))));
    }

    @Test
    void beanValidationGivesFieldMap() throws Exception {
        mvc.perform(post("/t/validate").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.name").value("required"));
    }

    @Test
    void malformedJsonIs400() throws Exception {
        mvc.perform(post("/t/validate").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }
}
