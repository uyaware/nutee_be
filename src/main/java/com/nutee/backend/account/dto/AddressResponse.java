package com.nutee.backend.account.dto;

import com.nutee.backend.account.entity.UserAddress;

/** Địa chỉ an toàn để hiển thị, không công khai entity hoặc quan hệ người dùng. */
public record AddressResponse(int id, String provinceCity,
        String wardCommune, String street, boolean isDefault) {
    public static AddressResponse from(UserAddress address) {
        return new AddressResponse(address.getId(),
                address.getProvinceCity(), address.getWardCommune(), address.getStreet(),
                Boolean.TRUE.equals(address.getDefaultAddress()));
    }
}
