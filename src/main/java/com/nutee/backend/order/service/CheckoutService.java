package com.nutee.backend.order.service;

import com.nutee.backend.common.exception.ApiException;
import com.nutee.backend.order.dto.CreateOrderRequest;
import com.nutee.backend.order.dto.OrderResponse;
import com.nutee.backend.payment.service.PaymentService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** Tạo đơn trước, sau đó gọi payOS ngoài transaction để đơn có thể được khôi phục khi lỗi mạng. */
@Service
@RequiredArgsConstructor
@Slf4j
public class CheckoutService {
    private final OrderService orders;
    private final PaymentService payments;

    public OrderResponse create(UUID owner, CreateOrderRequest request) {
        var order = orders.create(owner, request);
        if (!"qr".equals(order.paymentMethod()) || "paid".equals(order.paymentStatus())) {
            return order;
        }
        try {
            return order.withPayment(payments.create(owner, order.id()), null);
        } catch (ApiException exception) {
            log.warn("Payment creation postponed for order {}", order.id());
            return order.withPayment(null, "Đơn hàng đã tạo, nhưng chưa thể tạo mã QR. Vui lòng thử lại.");
        }
    }
}
