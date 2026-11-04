package com.nutee.backend.order.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nutee.backend.account.repository.UserAddressRepository;
import com.nutee.backend.common.exception.ApiException;
import com.nutee.backend.common.response.PageResponse;
import com.nutee.backend.order.dto.*;
import com.nutee.backend.order.entity.Order;
import com.nutee.backend.order.item.entity.OrderItem;
import com.nutee.backend.order.item.repository.OrderItemRepository;
import com.nutee.backend.order.repository.OrderRepository;
import com.nutee.backend.product.variant.repository.ProductVariantRepository;
import com.nutee.backend.cart.repository.CartItemRepository;
import com.nutee.backend.user.repository.UserRepository;
import com.nutee.backend.voucher.repository.VoucherRepository;
import com.nutee.backend.voucher.dto.QuoteRequest;
import com.nutee.backend.voucher.service.DiscountQuoteService;
import com.nutee.backend.payment.port.PaymentGateway;
import com.nutee.backend.payment.adapter.PaymentProperties;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Tạo đơn nguyên tử: idempotency, snapshot, quota voucher và tồn kho cùng một transaction. */
@Service @RequiredArgsConstructor @Transactional(readOnly = true)
public class OrderService {
    private final UserRepository users;
    private final UserAddressRepository addresses;
    private final OrderRepository orders;
    private final OrderItemRepository items;
    private final ProductVariantRepository variants;
    private final CartItemRepository cart;
    private final VoucherRepository vouchers;
    private final DiscountQuoteService quotes;
    private final PaymentProperties properties;
    private final PaymentGateway gateway;
    private final ObjectMapper json;

    @Transactional
    public OrderResponse create(UUID userId, CreateOrderRequest request) {
        var user = users.findLockedById(userId).filter(value -> value.isActive() && "CUSTOMER".equals(value.getRole()))
                .orElseThrow(() -> new BadCredentialsException("Invalid customer account."));
        String hash = hash(request);
        var previous = orders.findByUserIdAndIdempotencyKey(userId, request.idempotencyKey());
        if (previous.isPresent()) {
            if (!hash.equals(previous.get().getRequestHash())) throw new ApiException(HttpStatus.CONFLICT, "Yêu cầu đã được dùng với nội dung khác. Hãy kiểm tra đơn đã tạo.");
            return response(previous.get());
        }
        if (user.getFullName() == null || user.getFullName().isBlank() || user.getPhone() == null || !user.getPhone().matches("0\\d{9}"))
            throw new ApiException(HttpStatus.BAD_REQUEST, "Vui lòng cập nhật họ tên và số điện thoại trong hồ sơ trước khi đặt hàng.");
        var address = addresses.findByIdAndUser_Id(request.addressId(), userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy địa chỉ."));
        var voucher = request.voucherCode().isEmpty() ? null : vouchers.findLockedByCodeIgnoreCase(request.voucherCode())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Voucher không tồn tại."));
        var locked = variants.findLockedByIdIn(request.variantIds(), Sort.by("id"));
        if (locked.size() != request.variantIds().size()) throw new ApiException(HttpStatus.CONFLICT, "Danh sách sản phẩm đã thay đổi.");
        var quote = quotes.quote(userId, new QuoteRequest(request.variantIds(), request.voucherCode()));
        long total = Math.addExact(quote.total(), properties.shippingFee());
        if (total != request.expectedTotal()) throw new ApiException(HttpStatus.CONFLICT, "Giá hoặc ưu đãi đã thay đổi. Hãy tải lại báo giá và xác nhận lại.");
        if ("qr".equals(request.paymentMethod()) && total > 0 && !gateway.enabled()) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Thanh toán payOS chưa được cấu hình. Bạn có thể chọn COD.");
        Order order = orders.saveAndFlush(Order.create(user, request.idempotencyKey(), hash, address, request.note(), request.paymentMethod(),
                quote.subtotal(), quote.promotionDiscount(), quote.voucherDiscount(), Math.toIntExact(properties.shippingFee()), voucher == null ? null : voucher.getId(), Instant.now().plusSeconds(properties.expiryMinutes() * 60L)));
        var byId = locked.stream().collect(Collectors.toMap(value -> value.getId(), value -> value));
        for (var row : quote.items()) {
            var variant = byId.get(row.variantId()); variant.reserve(row.quantity());
            String configuration = row.specifications().stream().filter(spec -> "configuration".equals(spec.name()))
                    .map(spec -> spec.value()).findFirst().orElseGet(() -> row.specifications().stream()
                            .filter(spec -> !"kind".equals(spec.name())).map(spec -> spec.value()).collect(Collectors.joining(" / ")));
            items.save(new OrderItem(order.getId(), variant, row.quantity(), row.price(), row.originalPrice(), row.name(), row.sku(), row.imageUrl(), configuration));
            cart.findByUser_IdAndVariant_Id(userId, row.variantId()).ifPresent(cart::delete);
        }
        items.flush(); variants.flush(); cart.flush(); return response(order);
    }

    public OrderResponse get(UUID userId, int id) { active(userId); return response(owned(userId, id)); }
    public PageResponse<OrderResponse> list(UUID userId, int page, int size, String status, String search) {
        active(userId);
        if (!Set.of("all", "pending", "confirmed", "packing", "shipping", "delivered", "cancelled").contains(status)) throw new ApiException(HttpStatus.BAD_REQUEST, "Trạng thái đơn không hợp lệ.");
        String escaped = search.toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_");
        var result = orders.history(userId, status, "%" + escaped + "%", PageRequest.of(page, size));
        var grouped = result.isEmpty() ? Map.<Integer, List<OrderItem>>of() : items.findByOrderIdInOrderByIdAsc(result.stream().map(Order::getId).toList()).stream().collect(Collectors.groupingBy(OrderItem::getOrderId));
        return PageResponse.from(result.map(order -> response(order, grouped.getOrDefault(order.getId(), List.of()))));
    }
    public Order owned(UUID userId, int id) { return orders.findByIdAndUserId(id, userId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy đơn hàng.")); }
    private void active(UUID userId) { if (users.findById(userId).filter(user -> user.isActive()).isEmpty()) throw new BadCredentialsException("Invalid account."); }
    private String hash(CreateOrderRequest request) {
        try { return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(request))); }
        catch (java.security.NoSuchAlgorithmException | com.fasterxml.jackson.core.JsonProcessingException exception) { throw new IllegalStateException("Cannot fingerprint checkout request", exception); }
    }
    public OrderResponse response(Order order) {
        return response(order, items.findByOrderIdOrderByIdAsc(order.getId()));
    }
    private OrderResponse response(Order order, List<OrderItem> orderItems) {
        var user = order.getUser();
        var address = order.getAddress();
        String addressLine = address == null ? null : java.util.stream.Stream.of(address.getStreet(), address.getWardCommune(), address.getProvinceCity())
                .filter(value -> value != null && !value.isBlank()).collect(Collectors.joining(", "));
        var rows = orderItems.stream().map(item -> new OrderResponse.Item(item.getId(), item.getVariant().getProduct().getId(), item.getVariant().getId(),
                item.getProductName() == null ? item.getVariant().getProduct().getName() : item.getProductName(), item.getSkuSnapshot(), item.getImageUrl(), item.getConfiguration(), item.getQuantity(), item.getCalculatedPrice(),
                item.getOriginalPrice() == null ? item.getCalculatedPrice() : java.math.BigDecimal.valueOf(item.getOriginalPrice()))).toList();
        return new OrderResponse(order.getId(), order.getOrderCode(), order.getStatus(), order.getPaymentMethod(), order.getPaymentStatus(), amount(order.getSubtotal()), order.getPromotionDiscount(), order.getVoucherDiscount(), amount(order.getDiscount()),
                order.getShipping() == null ? 0 : order.getShipping(), amount(order.getTotal()), order.getAddressId(), user == null ? null : user.getFullName(), user == null ? null : user.getPhone(), addressLine, order.getNote(), order.getCreatedAt(), order.getPaymentExpiresAt(), rows, null, null);
    }
    private java.math.BigDecimal amount(java.math.BigDecimal value) { return value == null ? java.math.BigDecimal.ZERO : value; }
}
