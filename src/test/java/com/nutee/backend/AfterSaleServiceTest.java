package com.nutee.backend;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.nutee.backend.aftersale.dto.CreateAfterSaleRequest;
import com.nutee.backend.aftersale.entity.*;
import com.nutee.backend.aftersale.repository.*;
import com.nutee.backend.aftersale.service.AfterSaleService;
import com.nutee.backend.common.exception.ApiException;
import com.nutee.backend.order.entity.Order;
import com.nutee.backend.order.item.entity.OrderItem;
import com.nutee.backend.order.item.repository.OrderItemRepository;
import com.nutee.backend.order.repository.OrderRepository;
import com.nutee.backend.product.entity.Product;
import com.nutee.backend.product.variant.entity.ProductVariant;
import com.nutee.backend.user.entity.User;
import com.nutee.backend.user.repository.UserRepository;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class AfterSaleServiceTest {
    private final AfterSaleRepository requests = mock(AfterSaleRepository.class);
    private final AfterSaleHistoryRepository histories = mock(AfterSaleHistoryRepository.class);
    private final OrderItemRepository items = mock(OrderItemRepository.class);
    private final OrderRepository orders = mock(OrderRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final AfterSaleService service = new AfterSaleService(requests, histories, items, orders, users);
    private final User user = User.create("buyer", "buyer@example.invalid", "hash", "Buyer", true, null);
    private final Order order = mock(Order.class);
    private final OrderItem item = mock(OrderItem.class);
    private final List<AfterSaleHistory> events = new ArrayList<>();
    private AfterSaleRequest saved;
    private final CreateAfterSaleRequest input = new CreateAfterSaleRequest(11, "warranty", "  Không khởi động  ", null);

    @BeforeEach void configure() {
        when(users.findById(user.getId())).thenReturn(Optional.of(user));
        when(items.findById(11)).thenReturn(Optional.of(item));
        when(item.getId()).thenReturn(11);
        when(item.getOrderId()).thenReturn(2);
        when(item.getOrder()).thenReturn(order);
        when(item.getQuantity()).thenReturn(3);
        when(item.getCalculatedPrice()).thenReturn(BigDecimal.TEN);
        when(order.getId()).thenReturn(2);
        when(order.getUserId()).thenReturn(user.getId());
        when(order.getUser()).thenReturn(user);
        when(order.getStatus()).thenReturn("delivered");
        when(orders.findLockedById(2)).thenReturn(Optional.of(order));
        var variant = mock(ProductVariant.class);
        var product = mock(Product.class);
        when(item.getVariant()).thenReturn(variant);
        when(variant.getProduct()).thenReturn(product);
        when(product.getId()).thenReturn(1);
        when(product.getName()).thenReturn("Product");
        when(requests.findByOrderItem_Id(11)).thenReturn(List.of());
        when(requests.saveAndFlush(any())).thenAnswer(call -> {
            saved = call.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 4);
            return saved;
        });
        when(requests.findDetailedById(4)).thenAnswer(call -> Optional.ofNullable(saved));
        when(requests.findLockedById(4)).thenAnswer(call -> Optional.ofNullable(saved));
        when(histories.saveAndFlush(any())).thenAnswer(call -> {
            AfterSaleHistory event = call.getArgument(0);
            ReflectionTestUtils.setField(event, "actor", user);
            events.add(event);
            return event;
        });
        when(histories.findByRequestIdInOrderByIdAsc(anyList())).thenAnswer(call -> List.copyOf(events));
    }

    @Test void createsFullPurchasedLineAndInitialHistory() {
        var response = service.create(user.getId(), input);
        assertThat(response.quantity()).isEqualTo(3);
        assertThat(response.reason()).isEqualTo("Không khởi động");
        assertThat(response.status()).isEqualTo("pending");
        assertThat(response.history()).hasSize(1);
        assertThat(response.userId()).isEqualTo(user.getId());
    }

    @Test void rejectsForeignAndUndeliveredOrdersBeforeWriting() {
        when(order.getUserId()).thenReturn(UUID.randomUUID());
        assertThatThrownBy(() -> service.create(user.getId(), input)).isInstanceOf(ApiException.class);
        when(order.getUserId()).thenReturn(user.getId());
        when(order.getStatus()).thenReturn("shipping");
        assertThatThrownBy(() -> service.create(user.getId(), input)).isInstanceOf(ApiException.class);
        verify(requests, never()).saveAndFlush(any());
    }

    @Test void rejectsDuplicateActiveRequestAndHidesForeignDetails() {
        service.create(user.getId(), input);
        when(requests.findByOrderItem_Id(11)).thenReturn(List.of(saved));
        assertThatThrownBy(() -> service.create(user.getId(), input)).isInstanceOf(ApiException.class);
        when(order.getUserId()).thenReturn(UUID.randomUUID());
        assertThatThrownBy(() -> service.get(user.getId(), 4)).isInstanceOf(ApiException.class);
        verify(requests, times(1)).saveAndFlush(any());
    }

    @Test void enforcesWorkflowAndRejectsStaleOrTerminalTransitions() {
        service.create(user.getId(), input);
        assertThatThrownBy(() -> service.transition(user.getId(), 4, "pending", "completed")).isInstanceOf(ApiException.class);
        service.transition(user.getId(), 4, "pending", "reviewing");
        assertThatThrownBy(() -> service.transition(user.getId(), 4, "pending", "reviewing")).isInstanceOf(ApiException.class);
        service.transition(user.getId(), 4, "reviewing", "approved");
        service.transition(user.getId(), 4, "approved", "processing");
        var response = service.transition(user.getId(), 4, "processing", "completed");
        assertThat(response.history()).hasSize(5);
        assertThat(response.status()).isEqualTo("completed");
        assertThatThrownBy(() -> service.transition(user.getId(), 4, "completed", "reviewing")).isInstanceOf(ApiException.class);
    }

    @Test void completedReturnCannotBeRequestedTwice() {
        service.create(user.getId(), new CreateAfterSaleRequest(11, "return", "Không phù hợp", null));
        service.transition(user.getId(), 4, "pending", "reviewing");
        service.transition(user.getId(), 4, "reviewing", "approved");
        service.transition(user.getId(), 4, "approved", "processing");
        service.transition(user.getId(), 4, "processing", "completed");
        when(requests.findByOrderItem_Id(11)).thenReturn(List.of(saved));
        assertThatThrownBy(() -> service.create(user.getId(), new CreateAfterSaleRequest(11, "return", "Lần nữa", null)))
                .isInstanceOf(ApiException.class);
        verify(order, never()).advance(anyString());
    }
}
