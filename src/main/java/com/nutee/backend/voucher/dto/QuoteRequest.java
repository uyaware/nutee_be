package com.nutee.backend.voucher.dto;

import jakarta.validation.constraints.*;
import java.util.List;

/** Chỉ nhận các variant của giỏ đang đăng nhập và mã, không nhận giá/tổng tiền do client tính. */
public record QuoteRequest(@NotNull @Size(min = 1, max = 200) List<@NotNull @Min(1) Integer> variantIds,
        @Size(max = 20) String voucherCode) {}
