package com.nutee.backend;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.nutee.backend.management.dashboard.controller.DashboardController;
import com.nutee.backend.management.dashboard.service.DashboardService;
import com.nutee.backend.common.exception.GlobalExceptionHandler;
import com.nutee.backend.security.*;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = DashboardController.class)
@Import({SecurityConfiguration.class, SecurityErrorHandler.class, GlobalExceptionHandler.class})
@ActiveProfiles("test")
class DashboardWebTest {
    @Autowired private MockMvc mvc;
    @MockitoBean private JwtDecoder decoder;
    @MockitoBean private DashboardService service;
    @MockitoBean(name = "managementAccess") private ManagementAccess access;
    private Cookie login(String role) {
        when(decoder.decode("dashboard-test")).thenReturn(Jwt.withTokenValue("dashboard-test").header("alg", "HS256")
                .subject(UUID.randomUUID().toString()).claim("role", role).build());
        return new Cookie("accessToken", "dashboard-test");
    }
    @Test void onlyActiveAdminCanReadFinancialDashboard() throws Exception {
        mvc.perform(get("/api/v1/management/dashboard")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/management/dashboard").cookie(login("CUSTOMER"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/management/dashboard").cookie(login("STAFF"))).andExpect(status().isForbidden());
        when(access.isAdmin(any())).thenReturn(false);
        mvc.perform(get("/api/v1/management/dashboard").cookie(login("ADMIN"))).andExpect(status().isForbidden());
        verifyNoInteractions(service);
        when(access.isAdmin(any())).thenReturn(true);
        mvc.perform(get("/api/v1/management/dashboard?days=7").cookie(login("ADMIN")))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"));
        verify(service).summary(7);
    }
    @Test void staffOperationsAlsoChecksCurrentDatabaseAccess() throws Exception {
        when(access.isStaffOrAdmin(any())).thenReturn(false);
        mvc.perform(get("/api/v1/management/dashboard/operations").cookie(login("STAFF"))).andExpect(status().isForbidden());
        when(access.isStaffOrAdmin(any())).thenReturn(true);
        mvc.perform(get("/api/v1/management/dashboard/operations").cookie(login("STAFF")))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"));
        verify(service).operations();
    }
}
