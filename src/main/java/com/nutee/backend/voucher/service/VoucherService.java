package com.nutee.backend.voucher.service;

import com.nutee.backend.common.discount.DiscountFilters;
import com.nutee.backend.common.discount.Discounts;
import com.nutee.backend.common.exception.ApiException;
import com.nutee.backend.common.response.PageResponse;
import com.nutee.backend.order.repository.OrderRepository;
import com.nutee.backend.user.entity.User;
import com.nutee.backend.user.repository.UserRepository;
import com.nutee.backend.voucher.dto.VoucherRequest;
import com.nutee.backend.voucher.dto.VoucherResponse;
import com.nutee.backend.voucher.entity.Voucher;
import com.nutee.backend.voucher.repository.VoucherRepository;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Quản trị và kiểm tra voucher; xem trước giá không tiêu thụ lượt sử dụng. */
@Service @RequiredArgsConstructor @Transactional(readOnly = true)
public class VoucherService {
    private final VoucherRepository vouchers;
    private final UserRepository users;
    private final OrderRepository orders;

    public record AvailableVoucher(UUID id, String code, String type, int value, long minOrder, Long maxDiscount,
            Instant endsAt, long remaining, int maxUsesPerUser, long remainingPerUser) {}
    public record AppliedVoucher(UUID id, String code, long discount) {}

    public PageResponse<VoucherResponse> list(String q, String status, int page, int size) {
        var result = vouchers.findAll(DiscountFilters.matching(q, status), PageRequest.of(page, size, Sort.by("code", "id")));
        var loaded = vouchers.findDistinctByIdIn(result.getContent().stream().map(Voucher::getId).toList()).stream().collect(Collectors.toMap(Voucher::getId, rule -> rule));
        return PageResponse.from(result.map(rule -> VoucherResponse.from(loaded.get(rule.getId()))));
    }
    public VoucherResponse get(UUID id) { return VoucherResponse.from(require(id)); }

    public PageResponse<VoucherResponse.CustomerOption> customers(String q, int page, int size) {
        if (q.length() > 100) throw new ApiException(HttpStatus.BAD_REQUEST, "Từ khóa quá dài.");
        String text = q.strip().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        var result = users.findAll((root, query, cb) -> cb.and(cb.equal(root.get("role"), "CUSTOMER"), cb.isTrue(root.get("active")),
                cb.or(cb.like(cb.lower(root.get("email")), "%" + text + "%", '\\'), cb.like(cb.lower(root.get("fullName")), "%" + text + "%", '\\'))),
                PageRequest.of(page, size, Sort.by("email", "id")));
        return PageResponse.from(result.map(user -> new VoucherResponse.CustomerOption(user.getId(), user.getFullName() == null ? user.getUsername() : user.getFullName(), user.getEmail())));
    }

    @Transactional public VoucherResponse create(VoucherRequest request) { return VoucherResponse.from(vouchers.saveAndFlush(new Voucher(request, recipients(request)))); }
    @Transactional public VoucherResponse update(UUID id, VoucherRequest request) {
        var rule = locked(id);
        if (request.quantity() < orders.countVoucherUses(id)) throw new ApiException(HttpStatus.CONFLICT, "Số lượng không được nhỏ hơn số lượt đã sử dụng.");
        rule.update(request, recipients(request)); vouchers.flush(); return VoucherResponse.from(rule);
    }
    @Transactional public VoucherResponse active(UUID id, boolean enabled) { var rule = locked(id); rule.setActive(enabled); return VoucherResponse.from(rule); }
    @Transactional public void delete(UUID id) {
        var rule = locked(id);
        if (orders.existsByVoucherId(id)) throw new ApiException(HttpStatus.CONFLICT, "Voucher đã có đơn hàng sử dụng. Hãy tạm dừng thay vì xóa.");
        rule.getUsers().clear(); vouchers.flush(); vouchers.delete(rule); vouchers.flush();
    }

    /** Chỉ công khai rule được cấp cho chính chủ, không gửi email/ID của người nhận khác. */
    public PageResponse<AvailableVoucher> available(UUID userId, int page, int size) {
        activeUser(userId);
        var result = vouchers.available(userId, Instant.now(), PageRequest.of(page, size));
        var ids = result.getContent().stream().map(Voucher::getId).toList();
        var uses = ids.isEmpty() ? Map.<UUID, Long>of() : orders.voucherUsage(ids).stream().collect(Collectors.toMap(OrderRepository.UsageCount::getVoucherId, OrderRepository.UsageCount::getUses));
        var personal = ids.isEmpty() ? Map.<UUID, Long>of() : orders.userVoucherUsage(ids, userId).stream().collect(Collectors.toMap(OrderRepository.UsageCount::getVoucherId, OrderRepository.UsageCount::getUses));
        return PageResponse.from(result.map(rule -> new AvailableVoucher(
                rule.getId(), rule.getCode(), rule.getType(), rule.getValue(), rule.getMinOrder(), rule.getMaxDiscount(), rule.getEndsAt(),
                Math.max(0, rule.getQuantity() - uses.getOrDefault(rule.getId(), 0L)), rule.getMaxUsesPerUser(),
                Math.max(0, rule.getMaxUsesPerUser() - personal.getOrDefault(rule.getId(), 0L)))));
    }

    /** Kiểm tra người nhận, thời hạn và lượt trước khi giảm trên tổng sau promotion. */
    public AppliedVoucher apply(UUID userId, String code, long subtotal) {
        activeUser(userId);
        String normalized = code.strip().toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z0-9_-]{3,20}")) throw new ApiException(HttpStatus.BAD_REQUEST, "Mã voucher không hợp lệ.");
        var rule = vouchers.findByCodeIgnoreCase(normalized).orElseThrow(() -> unavailable());
        if (!("all".equals(rule.getScope()) || ("user".equals(rule.getScope()) && rule.getUsers().stream().anyMatch(user -> user.getId().equals(userId))))) throw unavailable();
        if (!"active".equals(Discounts.status(rule.isActive(), rule.getStartsAt(), rule.getEndsAt(), Instant.now())))
            throw new ApiException(HttpStatus.CONFLICT, "Voucher chưa bắt đầu, đã hết hạn hoặc đang tạm dừng.");
        if (rule.getQuantity() == null || orders.countVoucherUses(rule.getId()) >= rule.getQuantity()
                || rule.getMaxUsesPerUser() == null || orders.countUserVoucherUses(rule.getId(), userId) >= rule.getMaxUsesPerUser())
            throw new ApiException(HttpStatus.CONFLICT, "Voucher đã hết lượt hoặc tài khoản đã đạt giới hạn sử dụng.");
        if (subtotal < rule.getMinOrder()) throw new ApiException(HttpStatus.CONFLICT, "Chưa đạt giá trị đơn hàng tối thiểu của voucher.");
        if (rule.getValue() == null || rule.getValue() < 1 || (!"fixed".equals(rule.getType()) && !"percent".equals(rule.getType()))
                || ("percent".equals(rule.getType()) && rule.getValue() > 100)) throw new ApiException(HttpStatus.CONFLICT, "Voucher hiện không thể áp dụng.");
        long discount = Discounts.amount(subtotal, rule.getType(), rule.getValue());
        if (rule.getMaxDiscount() != null) discount = Math.min(discount, rule.getMaxDiscount());
        return new AppliedVoucher(rule.getId(), rule.getCode(), discount);
    }

    private List<User> recipients(VoucherRequest request) {
        Set<UUID> ids = new HashSet<>(request.userIds());
        if (ids.size() != request.userIds().size() || ("user".equals(request.scope()) && ids.isEmpty()) || ("all".equals(request.scope()) && !ids.isEmpty()))
            throw new ApiException(HttpStatus.BAD_REQUEST, "Danh sách khách hàng không phù hợp với phạm vi voucher.");
        var recipients = users.findAllById(ids);
        if (recipients.size() != ids.size() || recipients.stream().anyMatch(user -> !user.isActive() || !"CUSTOMER".equals(user.getRole())))
            throw new ApiException(HttpStatus.BAD_REQUEST, "Chỉ được cấp voucher cho khách hàng còn hoạt động.");
        return recipients;
    }
    private ApiException unavailable() { return new ApiException(HttpStatus.NOT_FOUND, "Voucher không tồn tại hoặc không được cấp cho tài khoản này."); }
    private void activeUser(UUID id) { if (users.findById(id).filter(User::isActive).isEmpty()) throw new BadCredentialsException("Invalid account."); }
    private Voucher require(UUID id) { return vouchers.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy voucher.")); }
    private Voucher locked(UUID id) { return vouchers.findLockedById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy voucher.")); }
}
