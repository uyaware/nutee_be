package com.nutee.backend.product.review.service;

import com.nutee.backend.common.exception.ApiException;
import com.nutee.backend.order.item.entity.OrderItem;
import com.nutee.backend.order.item.repository.OrderItemRepository;
import com.nutee.backend.order.repository.OrderRepository;
import com.nutee.backend.product.repository.ProductRepository;
import com.nutee.backend.product.review.dto.*;
import com.nutee.backend.product.review.entity.*;
import com.nutee.backend.product.review.repository.*;
import com.nutee.backend.user.entity.User;
import com.nutee.backend.user.repository.UserRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Đánh giá hàng đã giao; khóa đơn và sản phẩm để gửi đồng thời không tạo đánh giá trùng hoặc sai điểm. */
@Service @RequiredArgsConstructor @Transactional(readOnly = true)
public class ProductReviewService {
    private final ProductReviewRepository reviews;
    private final ProductReviewHistoryRepository histories;
    private final OrderItemRepository items;
    private final OrderRepository orders;
    private final ProductRepository products;
    private final UserRepository users;

    public OwnReviewResponse get(UUID owner, int orderId, int itemId) {
        active(owner);
        var order = orders.findByIdAndUserId(orderId, owner).orElseThrow(ProductReviewService::missing);
        var item = items.findById(itemId).filter(value -> value.getOrderId() == orderId).orElseThrow(ProductReviewService::missing);
        if (!"delivered".equals(order.getStatus())) throw notDelivered();
        return reviews.findFirstByOrderItem_IdOrderByIdAsc(item.getId())
                .flatMap(review -> histories.findFirstByReview_IdOrderByCreatedAtDescIdDesc(review.getId()))
                .map(ProductReviewService::response).orElse(new OwnReviewResponse(null, null, "", null));
    }

    /** Gửi lại nội dung giống hệt không ghi thêm lịch sử; sửa thật giữ lịch sử cũ và cập nhật điểm. */
    @Transactional
    public OwnReviewResponse save(UUID owner, int orderId, int itemId, SaveReviewRequest input) {
        User user = active(owner);
        var order = orders.findLockedById(orderId).orElseThrow(ProductReviewService::missing);
        if (!owner.equals(order.getUserId())) throw missing();
        if (!"delivered".equals(order.getStatus())) throw notDelivered();
        OrderItem item = items.findById(itemId).filter(value -> value.getOrderId() == orderId).orElseThrow(ProductReviewService::missing);
        int productId = item.getVariant().getProduct().getId();
        var product = products.findLockedById(productId).orElseThrow(ProductReviewService::missing);
        var review = reviews.findFirstByOrderItem_IdOrderByIdAsc(itemId).orElseGet(() -> reviews.saveAndFlush(new ProductReview(item)));
        String comment = input.comment() == null ? "" : input.comment().strip();
        var previous = histories.findFirstByReview_IdOrderByCreatedAtDescIdDesc(review.getId());
        boolean unchanged = previous.isPresent() && input.rating().equals(previous.get().getRating())
                && comment.equals(previous.get().getComment());
        var event = unchanged ? previous.get() : histories.saveAndFlush(new ProductReviewHistory(review, user, input.rating(), comment));
        Double average = reviews.averageLatestRating(productId);
        product.updateAverageRating(BigDecimal.valueOf(average == null ? 0 : average).setScale(1, RoundingMode.HALF_UP));
        return response(event);
    }

    private User active(UUID owner) {
        return users.findById(owner).filter(User::isActive)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "Tài khoản không khả dụng."));
    }
    private static OwnReviewResponse response(ProductReviewHistory value) {
        return new OwnReviewResponse(value.getReview().getId(), value.getRating(), value.getComment(), value.getCreatedAt());
    }
    private static ApiException missing() { return new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy sản phẩm trong đơn hàng của bạn."); }
    private static ApiException notDelivered() { return new ApiException(HttpStatus.CONFLICT, "Chỉ đánh giá sản phẩm thuộc đơn đã giao."); }
}
