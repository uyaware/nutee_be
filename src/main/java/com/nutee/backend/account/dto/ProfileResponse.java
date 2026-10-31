package com.nutee.backend.account.dto;

import com.nutee.backend.auth.dto.UserResponse;
import java.util.List;

/** Hồ sơ, trạng thái liên kết Google và sổ địa chỉ của tài khoản hiện tại. */
public record ProfileResponse(UserResponse user, String phone, boolean googleLinked, List<AddressResponse> addresses) {}
