package com.nutee.backend.voucher.controller;

import com.nutee.backend.common.response.PageResponse;
import com.nutee.backend.voucher.dto.QuoteRequest;
import com.nutee.backend.voucher.service.DiscountQuoteService;
import com.nutee.backend.voucher.service.VoucherService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

/** Voucher được cấp cho chính chủ và báo giá checkout có CSRF, không cho client tự khai tổng tiền. */
@RestController @RequestMapping("/api/v1") @RequiredArgsConstructor
public class DiscountQuoteController {
    private final DiscountQuoteService quotes;
    private final VoucherService vouchers;
    @GetMapping("/vouchers") public ResponseEntity<PageResponse<VoucherService.AvailableVoucher>> available(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") @Min(0) int page, @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(vouchers.available(UUID.fromString(jwt.getSubject()), page, size));
    }
    @PostMapping("/checkout/quote") public ResponseEntity<DiscountQuoteService.Quote> quote(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody QuoteRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(quotes.quote(UUID.fromString(jwt.getSubject()), request));
    }
}
