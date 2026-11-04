package com.nutee.backend.order.dto;

import jakarta.validation.constraints.*;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Chốt số tiền đã xem trước; mọi giá, tồn kho, voucher được backend kiểm tra lại. */
public record CreateOrderRequest(@NotNull UUID idempotencyKey,
        @NotNull @Size(min = 1, max = 200) List<@NotNull @Min(1) Integer> variantIds,
        @Size(max = 20) String voucherCode, @NotNull @Min(0) Long expectedTotal,
        @NotNull @Min(1) Integer addressId,
        @Size(max = 1000) String note, @NotBlank @Pattern(regexp = "cod|qr") String paymentMethod) {
    public CreateOrderRequest {
        voucherCode = voucherCode == null ? "" : voucherCode.strip().toUpperCase(Locale.ROOT);
        note = note == null ? "" : note.strip();
        if (variantIds != null) variantIds = variantIds.stream().sorted(java.util.Comparator.nullsFirst(Integer::compareTo)).toList();
    }
}
