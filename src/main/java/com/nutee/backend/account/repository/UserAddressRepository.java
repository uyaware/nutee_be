package com.nutee.backend.account.repository;

import com.nutee.backend.account.entity.UserAddress;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Mọi truy vấn địa chỉ đều giới hạn theo chủ tài khoản trong JWT. */
public interface UserAddressRepository extends JpaRepository<UserAddress, Integer> {
    List<UserAddress> findByUser_IdOrderByIdAsc(UUID userId);
    Optional<UserAddress> findByIdAndUser_Id(int id, UUID userId);
}
