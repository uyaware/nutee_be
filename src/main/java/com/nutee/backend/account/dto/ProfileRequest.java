package com.nutee.backend.account.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Cập nhật tên, số điện thoại; có thể thêm địa chỉ đầu tiên trong cùng transaction. */
public record ProfileRequest(
        @NotBlank @Size(min = 2, max = 80) String fullName,
        @Pattern(regexp = "(?:|0\\d{9}|\\+84\\d{9})") String phone,
        @Valid AddressRequest address) {
    public ProfileRequest {
        fullName = fullName == null ? null : fullName.strip();
        phone = phone == null ? null : phone.strip();
    }

    /** Cho phép xóa số liên hệ; lưu số có +84 theo dạng 0 để khớp schema. */
    public String normalizedPhone() {
        if (phone == null || phone.isBlank()) return null;
        return phone.startsWith("+84") ? "0" + phone.substring(3) : phone;
    }
}
