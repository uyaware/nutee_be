package com.nutee.backend;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nutee.backend.common.exception.GlobalExceptionHandler;
import com.nutee.backend.common.response.PageResponse;
import com.nutee.backend.security.SecurityConfiguration;
import com.nutee.backend.security.SecurityErrorHandler;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(controllers = InfrastructureWebTest.ProbeController.class,
        properties = "app.docs-enabled=false")
@Import({SecurityConfiguration.class, SecurityErrorHandler.class,
        GlobalExceptionHandler.class, InfrastructureWebTest.ProbeController.class})
@ActiveProfiles("test")
class InfrastructureWebTest {

    @MockitoBean
    private JwtDecoder decoder;

    @Autowired
    private MockMvc mvc;

    @Test
    void anonymousRequestsReceiveProblemJsonWithoutCreatingSession() throws Exception {
        mvc.perform(get("/api/v1/_test/page"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(header().doesNotExist("Set-Cookie"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.instance").value("/api/v1/_test/page"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    void validationReturnsFieldErrorsWithoutRejectedValues() throws Exception {
        mvc.perform(post("/api/v1/_test/validate").with(user("tester"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"private-invalid-input\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors[*].field", hasItem("email")))
                .andExpect(content().string(not(containsString("private-invalid-input"))));
    }

    @Test
    void malformedJsonHasSafeErrorMessage() throws Exception {
        mvc.perform(post("/api/v1/_test/validate").with(user("tester"))
                        .contentType(MediaType.APPLICATION_JSON).content("{invalid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Malformed request body."));
    }

    @Test
    void queryValidationUsesSameErrorFormat() throws Exception {
        mvc.perform(get("/api/v1/_test/query").param("quantity", "0").with(user("tester")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("quantity"));
    }

    @Test
    void methodSecurityDenialIsForbidden() throws Exception {
        mvc.perform(get("/api/v1/_test/restricted").with(user("tester")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void unexpectedFailuresDoNotExposeInternalDetails() throws Exception {
        mvc.perform(get("/api/v1/_test/failure").with(user("tester")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred."))
                .andExpect(content().string(not(containsString("internal-database-detail"))));
    }

    @Test
    void paginationBoundsRequestsAndMapsDtos() throws Exception {
        mvc.perform(get("/api/v1/_test/page").param("size", "10000").with(user("tester")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.content[0].label").value("example"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void documentationIsProtectedByDefault() throws Exception {
        mvc.perform(get("/api/v1/api-docs"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/swagger-ui/index.html"))
                .andExpect(status().isUnauthorized());
    }

    // Test-only routes exercise infrastructure without adding domain APIs.
    @RestController
    @TestComponent
    @RequestMapping("/api/v1/_test")
    static class ProbeController {

        @PostMapping("/validate")
        Input validate(@Valid @RequestBody Input input) {
            return input;
        }

        @GetMapping("/query")
        int query(@RequestParam @Min(1) int quantity) {
            return quantity;
        }

        @GetMapping("/restricted")
        @PreAuthorize("hasAuthority('infrastructure:admin')")
        void restricted() {
        }

        @GetMapping("/failure")
        void failure() {
            throw new IllegalStateException("internal-database-detail");
        }

        @GetMapping("/page")
        PageResponse<Item> page(Pageable pageable) {
            return PageResponse.from(new PageImpl<>(List.of(new Item("example")), pageable, 1));
        }
    }

    record Input(@NotBlank @Email String email) {
    }

    record Item(String label) {
    }
}
