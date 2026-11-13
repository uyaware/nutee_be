package com.nutee.backend;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import com.nutee.backend.aftersale.controller.*;
import com.nutee.backend.aftersale.dto.CreateAfterSaleRequest;
import com.nutee.backend.aftersale.service.AfterSaleService;
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

@WebMvcTest(controllers = {AfterSaleController.class, ManagementAfterSaleController.class})
@Import({SecurityConfiguration.class, SecurityErrorHandler.class, GlobalExceptionHandler.class})
@ActiveProfiles("test")
class AfterSaleWebTest {
    @Autowired private MockMvc mvc;
    @MockitoBean private JwtDecoder decoder;
    @MockitoBean private AfterSaleService service;
    @MockitoBean(name = "managementAccess") private ManagementAccess access;
    private final UUID owner = UUID.randomUUID();

    private Cookie login(String role) {
        when(decoder.decode("test")).thenReturn(Jwt.withTokenValue("test").header("alg", "HS256")
                .subject(owner.toString()).claim("role", role).build());
        return new Cookie("accessToken", "test");
    }

    @Test void requiresLoginAndCsrfAndIgnoresClientOwnership() throws Exception {
        mvc.perform(get("/api/v1/after-sales")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/after-sales").cookie(login("CUSTOMER"))
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/after-sales").cookie(login("CUSTOMER")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderItemId\":11,\"type\":\"warranty\",\"reason\":\"Test problem\"}"))
                .andExpect(status().isCreated()).andExpect(header().string("Cache-Control", "no-store"));
        verify(service).create(owner, new CreateAfterSaleRequest(11, "warranty", "Test problem", null));
    }

    @Test void validatesInputAndRejectsCustomerManagementAccess() throws Exception {
        mvc.perform(post("/api/v1/after-sales").cookie(login("CUSTOMER")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"orderItemId\":0,\"type\":\"refund\",\"reason\":\"\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/management/after-sales").cookie(login("CUSTOMER")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test void checksCurrentStaffAccessAndPassesExpectedState() throws Exception {
        Cookie cookie = login("STAFF");
        when(access.isStaffOrAdmin(any())).thenReturn(false);
        mvc.perform(get("/api/v1/management/after-sales").cookie(cookie)).andExpect(status().isForbidden());
        when(access.isStaffOrAdmin(any())).thenReturn(true);
        mvc.perform(post("/api/v1/management/after-sales/4/status").cookie(cookie).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"expectedStatus\":\"pending\",\"status\":\"reviewing\"}"))
                .andExpect(status().isOk());
        verify(service).transition(owner, 4, "pending", "reviewing");
    }
}
