package com.nutee.backend.account.controller;

import com.nutee.backend.account.dto.AddressRequest;
import com.nutee.backend.account.dto.ProfileRequest;
import com.nutee.backend.account.dto.ProfileResponse;
import com.nutee.backend.account.service.ProfileService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** API hồ sơ và sổ địa chỉ của tài khoản trong access JWT, mọi request ghi đều yêu cầu CSRF. */
@RestController
@RequestMapping("/api/v1/account")
@RequiredArgsConstructor
public class ProfileController {
    private final ProfileService service;

    @GetMapping("/profile")
    public ResponseEntity<ProfileResponse> get(@AuthenticationPrincipal Jwt jwt) {
        return response(service.get(UUID.fromString(jwt.getSubject())), HttpStatus.OK);
    }

    @PutMapping("/profile")
    public ResponseEntity<ProfileResponse> update(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ProfileRequest request) {
        return response(service.update(UUID.fromString(jwt.getSubject()), request), HttpStatus.OK);
    }

    @PostMapping("/addresses")
    public ResponseEntity<ProfileResponse> addAddress(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody AddressRequest request) {
        return response(service.addAddress(UUID.fromString(jwt.getSubject()), request), HttpStatus.CREATED);
    }

    @PutMapping("/addresses/{id}")
    public ResponseEntity<ProfileResponse> updateAddress(@AuthenticationPrincipal Jwt jwt,
            @PathVariable @Min(1) int id, @Valid @RequestBody AddressRequest request) {
        return response(service.updateAddress(UUID.fromString(jwt.getSubject()), id, request), HttpStatus.OK);
    }

    @PutMapping("/addresses/{id}/default")
    public ResponseEntity<ProfileResponse> setDefault(@AuthenticationPrincipal Jwt jwt, @PathVariable @Min(1) int id) {
        return response(service.setDefault(UUID.fromString(jwt.getSubject()), id), HttpStatus.OK);
    }

    @DeleteMapping("/addresses/{id}")
    public ResponseEntity<ProfileResponse> removeAddress(@AuthenticationPrincipal Jwt jwt, @PathVariable @Min(1) int id) {
        return response(service.removeAddress(UUID.fromString(jwt.getSubject()), id), HttpStatus.OK);
    }

    private ResponseEntity<ProfileResponse> response(ProfileResponse profile, HttpStatus status) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(profile);
    }
}
