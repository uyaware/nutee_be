package com.nutee.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.nutee.backend.account.entity.UserAddress;
import com.nutee.backend.common.exception.ApiException;
import com.nutee.backend.order.entity.Order;
import com.nutee.backend.order.entity.OrderHistory;
import com.nutee.backend.order.item.repository.OrderItemRepository;
import com.nutee.backend.order.repository.OrderHistoryRepository;
import com.nutee.backend.order.repository.OrderRepository;
import com.nutee.backend.order.service.ManagementOrderService;
import com.nutee.backend.order.service.OrderService;
import com.nutee.backend.payment.service.PaymentTransactions;
import com.nutee.backend.payment.port.PaymentGateway;
import com.nutee.backend.payment.repository.TransactionLogRepository;
import com.nutee.backend.product.variant.repository.ProductVariantRepository;
import com.nutee.backend.user.entity.User;
import com.nutee.backend.user.repository.UserRepository;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

class OrderWorkflowTest {
    @Test void unpaidPayosCannotAdvance() {
        OrderRepository orders = mock(OrderRepository.class);
        OrderHistoryRepository histories = mock(OrderHistoryRepository.class);
        Order order = mock(Order.class);
        when(orders.findLockedById(1)).thenReturn(Optional.of(order));
        when(order.getStatus()).thenReturn("pending");
        when(order.getPaymentMethod()).thenReturn("qr");
        when(order.getPaymentStatus()).thenReturn("pending");
        var service = new ManagementOrderService(orders, mock(OrderItemRepository.class), histories,
                mock(OrderService.class), mock(PaymentTransactions.class), mock(UserRepository.class));
        assertThatThrownBy(() -> service.transition(UUID.randomUUID(), 1, "confirmed"))
                .isInstanceOfSatisfying(ApiException.class, error -> assertThat(error.getStatus()).isEqualTo(HttpStatus.CONFLICT));
        verify(order, never()).advance(anyString());
        verifyNoInteractions(histories);
    }

    @Test void codDeliveryRecordsPayment() {
        User user = mock(User.class);
        UserAddress address = mock(UserAddress.class);
        when(user.getId()).thenReturn(UUID.randomUUID());
        when(address.getId()).thenReturn(1);
        Order order = Order.create(user, UUID.randomUUID(), "hash", address, "", "cod", 100, 0, 0, 0, null, Instant.now());
        assertThat(order.getPaymentStatus()).isEqualTo("unpaid");
        order.advance("delivered");
        assertThat(order.getStatus()).isEqualTo("delivered");
        assertThat(order.getPaymentStatus()).isEqualTo("paid");
        assertThat(order.getPaymentAmount()).isEqualByComparingTo("100");
    }

    @Test void skipsInvalidStatusTransitions() {
        OrderRepository orders = mock(OrderRepository.class);
        OrderHistoryRepository histories = mock(OrderHistoryRepository.class);
        Order order = mock(Order.class);
        when(orders.findLockedById(1)).thenReturn(Optional.of(order));
        when(order.getStatus()).thenReturn("pending");
        var service = new ManagementOrderService(orders, mock(OrderItemRepository.class), histories,
                mock(OrderService.class), mock(PaymentTransactions.class), mock(UserRepository.class));
        assertThatThrownBy(() -> service.transition(UUID.randomUUID(), 1, "shipping")).isInstanceOf(ApiException.class);
        verify(order, never()).advance(anyString());
        verifyNoInteractions(histories);
    }

    @Test void verifiedPayosConfirmationAddsSystemHistory() {
        OrderRepository orders = mock(OrderRepository.class);
        OrderHistoryRepository histories = mock(OrderHistoryRepository.class);
        TransactionLogRepository logs = mock(TransactionLogRepository.class);
        Order order = mock(Order.class);
        when(orders.orderIdForPaymentCode(123)).thenReturn(Optional.of(1));
        when(orders.findLockedById(1)).thenReturn(Optional.of(order));
        when(logs.findFirstByProviderAndReferenceOrderByIdAsc("payos", "payos:ref")).thenReturn(Optional.empty());
        when(order.getId()).thenReturn(1);
        when(order.getPaymentMethod()).thenReturn("qr");
        when(order.getPaymentAttemptCount()).thenReturn(1);
        when(order.getTotal()).thenReturn(BigDecimal.valueOf(100));
        when(order.getStatus()).thenReturn("pending", "confirmed", "confirmed");
        when(order.getPaymentStatus()).thenReturn("pending", "paid");
        var transactions = new PaymentTransactions(orders, histories, logs, mock(OrderItemRepository.class),
                mock(ProductVariantRepository.class), mock(UserRepository.class));
        var event = new PaymentGateway.Event(123, 100, "VND", "link", "ref", "00");
        assertThat(transactions.webhook(event, Map.of(), "{}")).isEqualTo("paid");
        var saved = ArgumentCaptor.forClass(OrderHistory.class);
        verify(histories).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo("confirmed");
        assertThat(saved.getValue().getUpdatedBy()).isNull();
    }
}
