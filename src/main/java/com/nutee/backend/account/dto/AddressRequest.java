package com.nutee.backend.account.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Địa chỉ giao hàng do chủ tài khoản nhập, không chứa thông tin liên hệ. */
public record AddressRequest(
        @NotBlank @Size(max = 255) String provinceCity,
        @NotBlank @Size(max = 255) String wardCommune,
        @NotBlank @Size(min = 2, max = 240) String street,
        @NotNull Boolean isDefault) {
    public AddressRequest {
        provinceCity = provinceCity == null ? null : provinceCity.strip();
        wardCommune = wardCommune == null ? null : wardCommune.strip();
        street = street == null ? null : street.strip();
    }
}
