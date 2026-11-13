package com.nutee.backend.aftersale.dto;

import jakarta.validation.constraints.*;

/** Khách chỉ cung cấp món hàng và vấn đề; người sở hữu lấy từ phiên đăng nhập. */
public record CreateAfterSaleRequest(
        @NotNull @Min(1) Integer orderItemId,
        @NotBlank @Pattern(regexp = "warranty|return") String type,
        @NotBlank @Size(max = 2000) String reason,
        @Size(max = 5000) String description) {}
