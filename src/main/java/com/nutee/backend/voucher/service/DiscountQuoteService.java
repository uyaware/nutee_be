package com.nutee.backend.voucher.service;

import com.nutee.backend.cart.dto.CartItemResponse;
import com.nutee.backend.cart.dto.CartItemStatus;
import com.nutee.backend.cart.service.CartService;
import com.nutee.backend.common.exception.ApiException;
import com.nutee.backend.voucher.dto.QuoteRequest;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Báo giá không tạo đơn, không trừ lượt voucher và luôn đọc lại giá/tồn kho giỏ từ backend. */
@Service @RequiredArgsConstructor @Transactional(readOnly = true)
public class DiscountQuoteService {
    private final CartService cart;
    private final VoucherService vouchers;
    public record Quote(List<CartItemResponse> items, long subtotal, long promotionDiscount, long voucherDiscount,
            long discount, long total, String voucherCode, boolean shippingConfirmed) {}

    public Quote quote(UUID userId, QuoteRequest request) {
        Set<Integer> ids = new HashSet<>(request.variantIds());
        if (ids.size() != request.variantIds().size()) throw new ApiException(HttpStatus.BAD_REQUEST, "Biến thể bị lặp.");
        var rows = cart.get(userId).items().stream().filter(item -> ids.contains(item.variantId())).toList();
        if (rows.size() != ids.size() || rows.stream().anyMatch(item -> item.status() != CartItemStatus.AVAILABLE))
            throw new ApiException(HttpStatus.CONFLICT, "Giỏ đã thay đổi hoặc có sản phẩm không đủ điều kiện mua. Hãy kiểm tra lại giỏ.");
        long subtotal = rows.stream().mapToLong(item -> (long) item.originalPrice() * item.quantity()).sum();
        long afterPromotion = rows.stream().mapToLong(CartItemResponse::lineTotal).sum();
        var applied = request.voucherCode() == null || request.voucherCode().isBlank() ? null : vouchers.apply(userId, request.voucherCode(), afterPromotion);
        long voucherDiscount = applied == null ? 0 : applied.discount();
        return new Quote(rows, subtotal, subtotal - afterPromotion, voucherDiscount, subtotal - afterPromotion + voucherDiscount,
                afterPromotion - voucherDiscount, applied == null ? null : applied.code(), false);
    }
}
