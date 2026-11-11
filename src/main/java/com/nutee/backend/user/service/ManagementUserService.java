package com.nutee.backend.user.service;

import com.nutee.backend.common.exception.ApiException;
import com.nutee.backend.common.response.PageResponse;
import com.nutee.backend.user.dto.ManagementUserAccessRequest;
import com.nutee.backend.user.dto.ManagementUserResponse;
import com.nutee.backend.user.dto.ManagementUserCreateRequest;
import com.nutee.backend.user.entity.User;
import com.nutee.backend.user.repository.UserRepository;
import java.util.Locale;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ManagementUserService {
    private final UserRepository users;
    private final PasswordEncoder passwords;

    @Transactional
    public ManagementUserResponse create(ManagementUserCreateRequest request) {
        String email = request.email().strip().toLowerCase(Locale.ROOT);
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Mật khẩu không được vượt quá 72 byte UTF-8.");
        }
        if (users.existsByEmailIgnoreCase(email)) throw new ApiException(HttpStatus.CONFLICT, "Email đã được sử dụng.");
        User user = User.create("user_" + UUID.randomUUID().toString().replace("-", ""), email,
                passwords.encode(request.password()), request.name().strip(), false, null);
        user.updateAccess(request.role().toUpperCase(Locale.ROOT), true);
        return ManagementUserResponse.from(users.saveAndFlush(user));
    }

    @Transactional(readOnly = true)
    public PageResponse<ManagementUserResponse> list(String q, String role, String status, int page, int size) {
        if (!role.matches("all|customer|staff|admin") || !status.matches("all|active|locked")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Bộ lọc không hợp lệ.");
        }
        String search = "%" + q.strip().toLowerCase(Locale.ROOT) + "%";
        Specification<User> spec = (root, query, cb) -> cb.and(
                cb.or(cb.like(cb.lower(root.get("email")), search), cb.like(cb.lower(root.get("fullName")), search),
                        cb.like(root.get("phone"), search)),
                role.equals("all") ? cb.conjunction() : cb.equal(root.get("role"), role.toUpperCase(Locale.ROOT)),
                status.equals("all") ? cb.conjunction() : cb.equal(root.get("active"), status.equals("active")));
        return PageResponse.from(users.findAll(spec, PageRequest.of(page, size, Sort.by("createdAt").descending().and(Sort.by("id"))))
                .map(ManagementUserResponse::from));
    }

    @Transactional(readOnly = true)
    public ManagementUserResponse get(UUID id) {
        return ManagementUserResponse.from(users.findById(id).orElseThrow(ManagementUserService::notFound));
    }

    @Transactional
    public ManagementUserResponse update(UUID actor, UUID id, ManagementUserAccessRequest request) {
        if (actor.equals(id)) throw new ApiException(HttpStatus.FORBIDDEN, "Không thể thay đổi tài khoản đang đăng nhập.");
        var admins = users.findActiveAdminsLocked();
        User user = users.findLockedById(id).orElseThrow(ManagementUserService::notFound);
        if (user.isActive() && user.getRole().equals("ADMIN") && admins.size() <= 1
                && (!request.isActive() || !request.role().equals("admin"))) {
            throw new ApiException(HttpStatus.CONFLICT, "Phải giữ ít nhất một quản trị viên đang hoạt động.");
        }
        user.updateAccess(request.role().toUpperCase(Locale.ROOT), request.isActive());
        return ManagementUserResponse.from(user);
    }

    private static ApiException notFound() { return new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy tài khoản."); }
}
