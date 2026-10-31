package com.nutee.backend.voucher.dto;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Voucher áp dụng sau promotion; quantity là tổng hạn mức sử dụng của rule. */
public record VoucherRequest(
        @NotBlank @Pattern(regexp = "[A-Z0-9_-]{3,20}") String code,
        @NotBlank @Pattern(regexp = "fixed|percent") String type,
        @NotNull @Min(1) @Max(1000000000) Integer value,
        @NotNull Instant startsAt, @NotNull Instant endsAt, @NotNull Boolean isActive,
        @NotBlank @Pattern(regexp = "all|user") String scope,
        @NotNull @Min(0) @Max(1000000000000L) Long minOrder,
        @Min(1) @Max(1000000000000L) Long maxDiscount,
        @NotNull @Min(0) Integer quantity, @NotNull @Min(1) Integer maxUsesPerUser,
        @NotNull @Size(max = 200) List<@NotNull UUID> userIds) {
    public VoucherRequest { code = code == null ? null : code.strip().toUpperCase(Locale.ROOT); }
}
