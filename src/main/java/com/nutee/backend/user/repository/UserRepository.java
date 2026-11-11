package com.nutee.backend.user.repository;

import com.nutee.backend.user.entity.User;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

/** Truy cập tài khoản và khóa dòng khi thay đổi phiên hoặc tiêu thụ mã khôi phục. */
public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {
    /** Tìm và khóa tài khoản theo hash để mã khôi phục chỉ được dùng một lần. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<User> findByPasswordResetHash(String hash);
    /** Khóa tài khoản trong transaction để hai request không thể cùng dùng một refresh token. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<User> findLockedById(UUID id);

    /** Khóa tất cả admin theo cùng thứ tự để ngăn hạ quyền admin cuối cùng đồng thời. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.role = 'ADMIN' and u.active = true order by u.id")
    List<User> findActiveAdminsLocked();

    Optional<User> findByEmailIgnoreCase(String email);
    Optional<User> findByUsernameIgnoreCase(String username);
    boolean existsByEmailIgnoreCase(String email);
    boolean existsByUsernameIgnoreCase(String username);
}
