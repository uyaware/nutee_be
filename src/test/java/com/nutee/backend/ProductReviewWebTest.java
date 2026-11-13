package com.nutee.backend;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import com.nutee.backend.product.review.controller.ProductReviewController;
import com.nutee.backend.product.review.dto.SaveReviewRequest;
import com.nutee.backend.product.review.service.ProductReviewService;
import com.nutee.backend.common.exception.GlobalExceptionHandler;
import com.nutee.backend.security.*;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = ProductReviewController.class)
@Import({SecurityConfiguration.class, SecurityErrorHandler.class, GlobalExceptionHandler.class})
@ActiveProfiles("test")
class ProductReviewWebTest {
    @Autowired private MockMvc mvc;
    @MockitoBean private JwtDecoder decoder;
    @MockitoBean private ProductReviewService service;
    private final UUID owner = UUID.randomUUID();
    private Cookie login() {
        when(decoder.decode("review-test")).thenReturn(Jwt.withTokenValue("review-test").header("alg", "HS256")
                .subject(owner.toString()).claim("role", "CUSTOMER").build());
        return new Cookie("accessToken", "review-test");
    }

    @Test void requiresAuthenticationCsrfAndUsesOwnerFromVerifiedCookie() throws Exception {
        mvc.perform(get("/api/v1/orders/2/items/11/review")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/v1/orders/2/items/11/review").cookie(login()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/orders/2/items/11/review").cookie(login()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"rating\":4,\"comment\":\"Good product\"}"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"));
        verify(service).save(owner, 2, 11, new SaveReviewRequest(4, "Good product"));
    }

    @Test void rejectsInvalidRatingsAndOversizedComments() throws Exception {
        for (String rating : new String[] {"0", "6", "null"}) {
            mvc.perform(put("/api/v1/orders/2/items/11/review").cookie(login()).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content("{\"rating\":" + rating + ",\"comment\":\"Test\"}"))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(put("/api/v1/orders/2/items/11/review").cookie(login()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"rating\":5,\"comment\":\"" + "a".repeat(1001) + "\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
