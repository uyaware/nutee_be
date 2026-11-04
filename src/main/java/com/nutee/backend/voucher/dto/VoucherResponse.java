package com.nutee.backend.voucher.dto;

import com.nutee.backend.common.discount.Discounts;
import com.nutee.backend.voucher.entity.Voucher;
import java.time.Instant;
import java.util.*;

/** Dữ liệu quản trị; danh sách khách hàng chỉ xuất hiện trong API ADMIN. */
public record VoucherResponse(UUID id, String code, String type, int value, Instant startsAt, Instant endsAt,
        boolean isActive, String status, String scope, long minOrder, Long maxDiscount, int quantity,
        int maxUsesPerUser, List<UUID> userIds, List<CustomerOption> users) {
    public record CustomerOption(UUID id, String name, String email) {}
    public static VoucherResponse from(Voucher rule) {
        var users = rule.getUsers().stream().sorted(Comparator.comparing(user -> user.getEmail()))
                .map(user -> new CustomerOption(user.getId(), user.getFullName() == null ? user.getUsername() : user.getFullName(), user.getEmail())).toList();
        return new VoucherResponse(rule.getId(), rule.getCode(), rule.getType(), rule.getValue(), rule.getStartsAt(), rule.getEndsAt(),
                rule.isActive(), Discounts.status(rule.isActive(), rule.getStartsAt(), rule.getEndsAt(), Instant.now()), rule.getScope(),
                rule.getMinOrder(), rule.getMaxDiscount(), rule.getQuantity(), rule.getMaxUsesPerUser(), users.stream().map(CustomerOption::id).toList(), users);
    }
}
