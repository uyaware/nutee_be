package com.nutee.backend.account.service;

import com.nutee.backend.account.dto.AddressRequest;
import com.nutee.backend.account.dto.AddressResponse;
import com.nutee.backend.account.dto.ProfileRequest;
import com.nutee.backend.account.dto.ProfileResponse;
import com.nutee.backend.account.entity.UserAddress;
import com.nutee.backend.account.repository.UserAddressRepository;
import com.nutee.backend.auth.dto.UserResponse;
import com.nutee.backend.auth.repository.AuthProviderRepository;
import com.nutee.backend.common.exception.ApiException;
import com.nutee.backend.user.entity.User;
import com.nutee.backend.user.repository.UserRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Quản lý hồ sơ và địa chỉ; khóa tài khoản để tuần tự hóa thay đổi địa chỉ mặc định. */
@Service
@RequiredArgsConstructor
@Transactional
public class ProfileService {
    private final UserRepository users;
    private final UserAddressRepository addresses;
    private final AuthProviderRepository providers;

    /** Đọc hồ sơ của tài khoản còn hoạt động; không nhận userId từ request body. */
    @Transactional(readOnly = true)
    public ProfileResponse get(UUID userId) {
        return response(activeUser(userId, false));
    }

    /** Lưu hồ sơ và địa chỉ tùy chọn nguyên tử, không thay đổi email, quyền hoặc thông tin đăng nhập. */
    public ProfileResponse update(UUID userId, ProfileRequest request) {
        User user = activeUser(userId, true);
        user.updateProfile(request.fullName(), request.normalizedPhone());
        if (request.address() != null) addAddress(user, request.address());
        return response(user);
    }

    /** Thêm địa chỉ; địa chỉ đầu tiên luôn là mặc định. */
    public ProfileResponse addAddress(UUID userId, AddressRequest request) {
        User user = activeUser(userId, true);
        addAddress(user, request);
        return response(user);
    }

    /** Sửa địa chỉ của chính chủ, giữ mặc định hiện tại nếu chưa chọn địa chỉ thay thế. */
    public ProfileResponse updateAddress(UUID userId, int id, AddressRequest request) {
        User user = activeUser(userId, true);
        UserAddress address = ownedAddress(userId, id);
        boolean isDefault = request.isDefault() || Boolean.TRUE.equals(address.getDefaultAddress());
        if (isDefault) clearDefault(userId);
        address.update(request, isDefault);
        addresses.flush();
        return response(user);
    }

    /** Chọn đúng một địa chỉ mặc định, flush địa chỉ cũ trước để không vi phạm unique index. */
    public ProfileResponse setDefault(UUID userId, int id) {
        User user = activeUser(userId, true);
        UserAddress address = ownedAddress(userId, id);
        clearDefault(userId);
        address.setDefault(true);
        addresses.flush();
        return response(user);
    }

    /** Xóa địa chỉ; nếu có đơn hàng tham chiếu, database từ chối và transaction được rollback. */
    public ProfileResponse removeAddress(UUID userId, int id) {
        User user = activeUser(userId, true);
        UserAddress address = ownedAddress(userId, id);
        boolean wasDefault = Boolean.TRUE.equals(address.getDefaultAddress());
        addresses.delete(address);
        addresses.flush();
        if (wasDefault) {
            addresses.findByUser_IdOrderByIdAsc(userId).stream().findFirst()
                    .ifPresent(next -> next.setDefault(true));
            addresses.flush();
        }
        return response(user);
    }

    private void addAddress(User user, AddressRequest request) {
        boolean isDefault = request.isDefault() || addresses.findByUser_IdOrderByIdAsc(user.getId()).isEmpty();
        if (isDefault) clearDefault(user.getId());
        addresses.saveAndFlush(new UserAddress(user, request, isDefault));
    }

    private void clearDefault(UUID userId) {
        addresses.findByUser_IdOrderByIdAsc(userId).forEach(address -> address.setDefault(false));
        addresses.flush();
    }

    private UserAddress ownedAddress(UUID userId, int id) {
        return addresses.findByIdAndUser_Id(id, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy địa chỉ."));
    }

    private User activeUser(UUID id, boolean lock) {
        User user = (lock ? users.findLockedById(id) : users.findById(id))
                .orElseThrow(() -> new BadCredentialsException("Invalid account."));
        if (!user.isActive()) throw new BadCredentialsException("Invalid account.");
        return user;
    }

    private ProfileResponse response(User user) {
        return new ProfileResponse(UserResponse.from(user), user.getPhone(),
                providers.findByUserIdAndProvider(user.getId(), "GOOGLE").isPresent(),
                addresses.findByUser_IdOrderByIdAsc(user.getId()).stream().map(AddressResponse::from).toList());
    }
}
