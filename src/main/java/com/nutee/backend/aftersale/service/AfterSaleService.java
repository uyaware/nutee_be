package com.nutee.backend.aftersale.service;

import com.nutee.backend.aftersale.dto.*;
import com.nutee.backend.aftersale.entity.*;
import com.nutee.backend.aftersale.repository.*;
import com.nutee.backend.common.exception.ApiException;
import com.nutee.backend.common.response.PageResponse;
import com.nutee.backend.order.item.repository.OrderItemRepository;
import com.nutee.backend.order.repository.OrderRepository;
import com.nutee.backend.user.repository.UserRepository;
import com.nutee.backend.user.entity.User;
import jakarta.persistence.criteria.*;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Hậu mãi toàn dòng hàng, kiểm tra chính chủ và ghi trạng thái bằng bảng lịch sử hiện có. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AfterSaleService {
    private static final Set<String> STATUSES = Set.of("pending", "reviewing", "approved", "rejected", "processing", "completed");
    private static final Map<String, Set<String>> STEPS = Map.of(
            "pending", Set.of("reviewing"), "reviewing", Set.of("approved", "rejected"),
            "approved", Set.of("processing"), "processing", Set.of("completed"));
    private final AfterSaleRepository requests;
    private final AfterSaleHistoryRepository histories;
    private final OrderItemRepository items;
    private final OrderRepository orders;
    private final UserRepository users;

    /** Phân trang tại database; owner null chỉ được truyền từ controller quản trị đã kiểm quyền. */
    public PageResponse<AfterSaleResponse> list(UUID owner, int page, int size, String status, String type, String q) {
        if (owner != null) active(owner);
        if ((!"all".equals(status) && !STATUSES.contains(status)) || !Set.of("all", "warranty", "return").contains(type)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Bộ lọc hậu mãi không hợp lệ.");
        }
        String search = "%" + q.strip().toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        Integer searchId;
        try { searchId = Integer.valueOf(q.strip()); }
        catch (NumberFormatException exception) { searchId = null; }
        final Integer requestedId = searchId;
        Specification<AfterSaleRequest> filter = (root, query, cb) -> {
            var item = root.join("orderItem");
            var order = item.join("order");
            List<Predicate> predicates = new ArrayList<>();
            if (owner != null) predicates.add(cb.equal(order.get("userId"), owner));
            if (!"all".equals(type)) predicates.add(cb.equal(root.get("type"), type));
            if (!"all".equals(status)) {
                // Lấy lịch sử cuối bằng id, không thêm cột trạng thái trùng dữ liệu vào bảng yêu cầu.
                var latest = query.subquery(Integer.class);
                var h = latest.from(AfterSaleHistory.class);
                latest.select(cb.max(h.get("id"))).where(cb.equal(h.get("requestId"), root.get("id")));
                var matching = query.subquery(Integer.class);
                var event = matching.from(AfterSaleHistory.class);
                matching.select(event.get("requestId")).where(cb.equal(event.get("id"), latest), cb.equal(event.get("status"), status));
                Predicate matches = cb.exists(matching);
                if ("pending".equals(status)) matches = cb.or(matches, cb.isNull(latest));
                predicates.add(matches);
            }
            if (!q.isBlank()) {
                predicates.add(cb.or(cb.like(cb.lower(order.get("orderCode")), search, '!'),
                        cb.like(cb.lower(cb.coalesce(item.<String>get("productName"), item.join("variant").join("product").<String>get("name"))), search, '!'),
                        requestedId == null ? cb.disjunction() : cb.equal(root.get("id"), requestedId)));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        var result = requests.findAll(filter, PageRequest.of(page, size, Sort.by("createdAt", "id").descending()));
        var grouped = historyById(result.stream().map(AfterSaleRequest::getId).toList());
        return PageResponse.from(result.map(request -> response(request, grouped.getOrDefault(request.getId(), List.of()))));
    }

    /** Khách chỉ đọc yêu cầu thuộc đơn của mình; quản trị dùng controller đã kiểm quyền. */
    public AfterSaleResponse get(UUID owner, int id) {
        if (owner != null) active(owner);
        var request = requests.findDetailedById(id).orElseThrow(AfterSaleService::missing);
        if (owner != null && !owner.equals(request.getOrderItem().getOrder().getUserId())) throw missing();
        return response(request, historyById(List.of(id)).getOrDefault(id, List.of()));
    }

    /** Khóa đơn trước khi kiểm tra trùng để hai lần bấm gửi không tạo hai yêu cầu đang xử lý. */
    @Transactional
    public AfterSaleResponse create(UUID owner, CreateAfterSaleRequest input) {
        var user = active(owner);
        var item = items.findById(input.orderItemId()).orElseThrow(AfterSaleService::missing);
        var order = orders.findLockedById(item.getOrderId()).orElseThrow(AfterSaleService::missing);
        if (!owner.equals(order.getUserId())) throw missing();
        if (!"delivered".equals(order.getStatus())) throw new ApiException(HttpStatus.CONFLICT, "Chỉ gửi hậu mãi cho đơn đã giao.");
        var existing = requests.findByOrderItem_Id(item.getId());
        var history = historyById(existing.stream().map(AfterSaleRequest::getId).toList());
        for (var entry : existing) {
            String state = status(history.getOrDefault(entry.getId(), List.of()));
            if (!Set.of("rejected", "completed").contains(state)
                    || ("return".equals(input.type()) && "return".equals(entry.getType()) && "completed".equals(state))) {
                throw new ApiException(HttpStatus.CONFLICT, "Dòng sản phẩm đã có yêu cầu đang xử lý hoặc đã hoàn tất đổi/trả. Hãy kiểm tra danh sách hậu mãi.");
            }
        }
        String reason = input.reason().strip();
        if (reason.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "Nhập lý do yêu cầu hậu mãi.");
        var request = requests.saveAndFlush(new AfterSaleRequest(item, input.type(), reason,
                input.description() == null ? null : input.description().strip()));
        histories.saveAndFlush(new AfterSaleHistory(request.getId(), user, "pending"));
        return get(owner, request.getId());
    }

    /** Chỉ đi tiếp đúng một bước và kiểm tra trạng thái mà nhân viên đã xem để tránh ghi đè. */
    @Transactional
    public AfterSaleResponse transition(UUID actor, int id, String expected, String next) {
        var user = active(actor);
        requests.findLockedById(id).orElseThrow(AfterSaleService::missing);
        String current = status(historyById(List.of(id)).getOrDefault(id, List.of()));
        if (!current.equals(expected) || !STEPS.getOrDefault(current, Set.of()).contains(next)) {
            throw new ApiException(HttpStatus.CONFLICT, "Trạng thái đã thay đổi hoặc bước xử lý không hợp lệ. Hãy tải lại yêu cầu.");
        }
        histories.saveAndFlush(new AfterSaleHistory(id, user, next));
        return get(null, id);
    }

    private Map<Integer, List<AfterSaleHistory>> historyById(List<Integer> ids) {
        if (ids.isEmpty()) return Map.of();
        return histories.findByRequestIdInOrderByIdAsc(ids).stream().collect(Collectors.groupingBy(AfterSaleHistory::getRequestId));
    }

    private static String status(List<AfterSaleHistory> history) {
        return history.isEmpty() ? "pending" : history.get(history.size() - 1).getStatus();
    }

    private AfterSaleResponse response(AfterSaleRequest request, List<AfterSaleHistory> history) {
        var item = request.getOrderItem();
        var order = item.getOrder();
        var user = order.getUser();
        var address = order.getAddress();
        String line = address == null ? null : java.util.stream.Stream.of(address.getStreet(), address.getWardCommune(), address.getProvinceCity())
                .filter(value -> value != null && !value.isBlank()).collect(Collectors.joining(", "));
        var product = item.getVariant().getProduct();
        return new AfterSaleResponse(request.getId(), order.getId(), order.getOrderCode(), item.getId(), order.getUserId(),
                request.getType(), item.getQuantity(), request.getReason(), request.getDescription(), status(history), request.getCreatedAt(),
                new AfterSaleResponse.Item(item.getId().toString(), product.getId().toString(),
                        item.getProductName() == null ? product.getName() : item.getProductName(),
                        item.getImageUrl(), item.getConfiguration(), item.getQuantity(), item.getCalculatedPrice(), item.getWarrantyExpiredAt()),
                new AfterSaleResponse.Contact(user.getFullName(), user.getPhone(), line),
                history.stream().map(event -> new AfterSaleResponse.History(event.getStatus(), event.getOccurredAt(), event.getUserId(),
                        event.getActor().getFullName() == null ? event.getActor().getUsername() : event.getActor().getFullName())).toList());
    }

    private User active(UUID id) {
        return users.findById(id).filter(user -> user.isActive())
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "Tài khoản không khả dụng."));
    }
    private static ApiException missing() { return new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy yêu cầu hoặc sản phẩm đã mua."); }
}
