package com.nutee.backend.management.dashboard.controller;

import com.nutee.backend.management.dashboard.dto.*;
import com.nutee.backend.management.dashboard.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** Tách quyền số liệu quản trị và số đếm vận hành; luôn kiểm quyền hiện tại trong DB. */
@RestController @RequestMapping("/api/v1/management/dashboard") @RequiredArgsConstructor
public class DashboardController {
    private final DashboardService service;
    @GetMapping
    @PreAuthorize("hasRole('ADMIN') and @managementAccess.isAdmin(authentication)")
    public ResponseEntity<DashboardResponse> summary(@RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.summary(days));
    }
    @GetMapping("/operations")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF') and @managementAccess.isStaffOrAdmin(authentication)")
    public ResponseEntity<OperationsDashboardResponse> operations() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.operations());
    }
}
