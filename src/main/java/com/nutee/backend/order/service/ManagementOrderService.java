package com.nutee.backend.order.service;

import com.nutee.backend.common.exception.ApiException;
import com.nutee.backend.common.response.PageResponse;
import com.nutee.backend.order.dto.ManagementOrderHistoryResponse;
import com.nutee.backend.order.dto.OrderResponse;
import com.nutee.backend.order.entity.Order;
import com.nutee.backend.order.entity.OrderHistory;
import com.nutee.backend.order.item.entity.OrderItem;
import com.nutee.backend.order.item.repository.OrderItemRepository;
import com.nutee.backend.order.repository.OrderHistoryRepository;
import com.nutee.backend.order.repository.OrderRepository;
import com.nutee.backend.payment.service.PaymentTransactions;
import com.nutee.backend.user.repository.UserRepository;
import com.nutee.backend.user.entity.User;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ManagementOrderService {
    private final OrderRepository orders;
    private final OrderItemRepository items;
    private final OrderHistoryRepository histories;
    private final OrderService orderService;
    private final PaymentTransactions payments;
    private final UserRepository users;

    public PageResponse<OrderResponse> list(String q, String status, String payment, String sort, int page, int size) {
        if (!Set.of("all", "pending", "confirmed", "packing", "shipping", "delivered", "cancelled").contains(status)
                || !Set.of("all", "unpaid", "new", "pending", "creating", "error", "paid", "cancelled", "expired", "review").contains(payment)
                || !Set.of("newest", "oldest").contains(sort)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Bộ lọc đơn hàng không hợp lệ.");
        }
        String search = "%" + q.strip().toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        Specification<Order> filter = (root, query, cb) -> {
            var user = root.join("user", jakarta.persistence.criteria.JoinType.LEFT);
            return cb.and(
                    cb.or(cb.like(cb.lower(root.get("orderCode")), search, '!'), cb.like(cb.lower(user.get("fullName")), search, '!'),
                            cb.like(user.get("phone"), search, '!')),
                    status.equals("all") ? cb.conjunction() : cb.equal(root.get("status"), status),
                    payment.equals("all") ? cb.conjunction() : cb.equal(root.get("paymentStatus"), payment));
        };
        Sort direction = sort.equals("oldest") ? Sort.by("createdAt", "id").ascending() : Sort.by("createdAt", "id").descending();
        var result = orders.findAll(filter, PageRequest.of(page, size, direction));
        Map<Integer, List<OrderItem>> grouped = result.isEmpty() ? Map.of() : items.findByOrderIdInOrderByIdAsc(
                result.stream().map(Order::getId).toList()).stream().collect(Collectors.groupingBy(OrderItem::getOrderId));
        return PageResponse.from(result.map(order -> orderService.response(order, grouped.getOrDefault(order.getId(), List.of()))));
    }

    public OrderResponse get(int id) {
        return orderService.response(orders.findById(id).orElseThrow(ManagementOrderService::missing));
    }

    public List<ManagementOrderHistoryResponse> history(int id) {
        if (!orders.existsById(id)) throw missing();
        var events = histories.findByOrderIdOrderByOccurredAtAscIdAsc(id);
        var actors = events.stream().map(OrderHistory::getUpdatedBy).filter(java.util.Objects::nonNull).distinct().toList();
        Map<UUID, User> byId = users.findAllById(actors).stream().collect(Collectors.toMap(User::getId, user -> user));
        return events.stream().map(event -> {
            User actor = event.getUpdatedBy() == null ? null : byId.get(event.getUpdatedBy());
            String name = event.getUpdatedBy() == null ? null : actor == null ? "Tài khoản không xác định"
                    : actor.getFullName() == null || actor.getFullName().isBlank() ? actor.getUsername() : actor.getFullName();
            return new ManagementOrderHistoryResponse(event.getStatus(), event.getOccurredAt(), name);
        }).toList();
    }

    /** Khóa đơn khi chuyển trạng thái để không ghi đè đối soát hay thao tác khác. */
    @Transactional
    public OrderResponse transition(UUID actor, int id, String next) {
        Order order = orders.findLockedById(id).orElseThrow(ManagementOrderService::missing);
        if ("cancelled".equals(next)) {
            payments.cancelManagementCod(actor, id);
            return orderService.response(order);
        }
        Map<String, String> steps = Map.of("pending", "confirmed", "confirmed", "packing", "packing", "shipping", "shipping", "delivered");
        if (order.getStatus() == null || !next.equals(steps.get(order.getStatus()))) {
            throw new ApiException(HttpStatus.CONFLICT, "Trạng thái đơn đã thay đổi hoặc bước xử lý không hợp lệ.");
        }
        if ("qr".equals(order.getPaymentMethod()) && !"paid".equals(order.getPaymentStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "Đơn payOS cần được xác nhận thanh toán trước khi xử lý.");
        }
        if (!"qr".equals(order.getPaymentMethod()) && !"cod".equals(order.getPaymentMethod())) {
            throw new ApiException(HttpStatus.CONFLICT, "Phương thức thanh toán của đơn không hợp lệ.");
        }
        if ("cod".equals(order.getPaymentMethod()) && !"unpaid".equals(order.getPaymentStatus()) && !"paid".equals(order.getPaymentStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "Trạng thái thanh toán COD cần được kiểm tra trước khi xử lý.");
        }
        if ("review".equals(order.getPaymentStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "Thanh toán đang cần đối soát.");
        }
        order.advance(next);
        histories.save(OrderHistory.create(id, actor, next));
        return orderService.response(order);
    }

    private static ApiException missing() { return new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy đơn hàng."); }
}
