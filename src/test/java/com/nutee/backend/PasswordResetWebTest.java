package com.nutee.backend;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.nutee.backend.auth.controller.PasswordResetController;
import com.nutee.backend.auth.service.PasswordResetService;
import com.nutee.backend.common.exception.GlobalExceptionHandler;
import com.nutee.backend.security.SecurityConfiguration;
import com.nutee.backend.security.SecurityErrorHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = PasswordResetController.class)
@Import({SecurityConfiguration.class, SecurityErrorHandler.class, GlobalExceptionHandler.class})
@ActiveProfiles("test")
class PasswordResetWebTest {
    @Autowired private MockMvc mvc;
    @MockitoBean private JwtDecoder decoder;
    @MockitoBean private PasswordResetService passwords;

    @Test
    void forgotAndResetArePublicAndNeverCacheResponses() throws Exception {
        mvc.perform(post("/api/v1/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"test@example.com\"}"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.message").isNotEmpty());
        verify(passwords).forgot("test@example.com");
        String token = "a".repeat(43);
        mvc.perform(post("/api/v1/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\",\"password\":\"StrongPassword123!\"}"))
                .andExpect(status().isNoContent()).andExpect(header().string("Cache-Control", "no-store"));
        verify(passwords).reset(token, "StrongPassword123!");
    }

    @Test
    void rejectsInvalidEmailShortPasswordsAndMalformedResetTokens() throws Exception {
        mvc.perform(post("/api/v1/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"invalid\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("email"));
        mvc.perform(post("/api/v1/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"invalid\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(passwords);
    }
}
