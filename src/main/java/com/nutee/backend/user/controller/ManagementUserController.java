package com.nutee.backend.user.controller;

import com.nutee.backend.common.response.PageResponse;
import com.nutee.backend.user.dto.ManagementUserAccessRequest;
import com.nutee.backend.user.dto.ManagementUserResponse;
import com.nutee.backend.user.dto.ManagementUserCreateRequest;
import com.nutee.backend.user.service.ManagementUserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api/v1/management/users")
@PreAuthorize("hasRole('ADMIN') and @managementAccess.isAdmin(authentication)")
@RequiredArgsConstructor
public class ManagementUserController {
    private final ManagementUserService service;

    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public ManagementUserResponse create(@Valid @RequestBody ManagementUserCreateRequest request) { return service.create(request); }

    @GetMapping
    public PageResponse<ManagementUserResponse> list(@RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "all") String role, @RequestParam(defaultValue = "all") String status,
            @RequestParam(defaultValue = "0") @Min(0) int page, @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.list(q, role, status, page, size);
    }

    @GetMapping("/{id}")
    public ManagementUserResponse get(@PathVariable UUID id) { return service.get(id); }

    @PutMapping("/{id}/access")
    public ManagementUserResponse update(@PathVariable UUID id, @Valid @RequestBody ManagementUserAccessRequest request,
            Authentication authentication) { return service.update(UUID.fromString(authentication.getName()), id, request); }
}
