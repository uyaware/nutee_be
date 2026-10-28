package com.nutee.backend;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import com.nutee.backend.security.SecurityConfiguration;
import com.nutee.backend.security.SecurityErrorHandler;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.test.context.ActiveProfiles;

@WebMvcTest(controllers = JwtSecurityWebTest.ProbeController.class)
@Import({SecurityConfiguration.class, SecurityErrorHandler.class, JwtSecurityWebTest.ProbeController.class})
@ActiveProfiles("test")
class JwtSecurityWebTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private JwtDecoder decoder;

    @Test
    void verifiedBearerTokenAuthenticatesWithoutSession() throws Exception {
        given(decoder.decode("verified-token")).willReturn(Jwt.withTokenValue("verified-token")
                .header("alg", "HS256").subject("test-subject")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build());
        mvc.perform(get("/api/v1/_test/jwt").header("Authorization", "Bearer verified-token"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Set-Cookie"));
    }

    @Test
    void invalidBearerTokenGetsConsistentUnauthorizedResponse() throws Exception {
        given(decoder.decode(anyString())).willThrow(new BadJwtException("private-decoder-detail"));
        mvc.perform(get("/api/v1/_test/jwt").header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.detail").value("Authentication is required."));
    }

    @RestController
    @TestComponent
    static class ProbeController {

        @GetMapping("/api/v1/_test/jwt")
        String secured() {
            return "verified";
        }
    }
}
