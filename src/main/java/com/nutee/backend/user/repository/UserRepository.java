package com.nutee.backend.user.repository;

import com.nutee.backend.user.entity.User;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

/** Truy cập tài khoản và khóa dòng khi thay đổi phiên hoặc tiêu thụ mã khôi phục. */
public interface UserRepository extends JpaRepository<User, UUID> {
    /** Tìm và khóa tài khoản theo hash để mã khôi phục chỉ được dùng một lần. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<User> findByPasswordResetHash(String hash);
    /** Khóa tài khoản trong transaction để hai request không thể cùng dùng một refresh token. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<User> findLockedById(UUID id);

    /** Tìm tài khoản theo email, không phân biệt hoa thường. */
    Optional<User> findByEmailIgnoreCase(String email);
    /** Tìm tài khoản theo username, không phân biệt hoa thường. */
    Optional<User> findByUsernameIgnoreCase(String username);
    /** Kiểm tra email đã được đăng ký hay chưa. */
    boolean existsByEmailIgnoreCase(String email);
    /** Kiểm tra username đã được sử dụng hay chưa. */
    boolean existsByUsernameIgnoreCase(String username);
}
