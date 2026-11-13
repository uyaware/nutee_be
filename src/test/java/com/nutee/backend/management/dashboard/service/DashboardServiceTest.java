package com.nutee.backend.management.dashboard.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.nutee.backend.aftersale.repository.AfterSaleRepository;
import com.nutee.backend.order.repository.OrderRepository;
import com.nutee.backend.product.repository.ProductRepository;
import com.nutee.backend.promotion.repository.PromotionRepository;
import com.nutee.backend.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DashboardServiceTest {
    @Test void windowsFollowVietnamDateAcrossUtcMidnightAndRejectUnsupportedPeriods() {
        var orders = mock(OrderRepository.class);
        when(orders.dashboardPaidValue(any(), any())).thenReturn(BigDecimal.TEN);
        var service = new DashboardService(orders, mock(ProductRepository.class), mock(PromotionRepository.class),
                mock(UserRepository.class), mock(AfterSaleRepository.class),
                Clock.fixed(Instant.parse("2026-11-13T18:00:00Z"), ZoneOffset.UTC));
        var seven = service.summary(7);
        assertThat(seven.fromDate()).isEqualTo(LocalDate.of(2026, 11, 8));
        assertThat(seven.throughDate()).isEqualTo(LocalDate.of(2026, 11, 14));
        assertThat(seven.buckets()).hasSize(7);
        assertThat(seven.paidValue()).isEqualByComparingTo("70");
        var start = ArgumentCaptor.forClass(Instant.class);
        var end = ArgumentCaptor.forClass(Instant.class);
        verify(orders).dashboardStatuses(start.capture(), end.capture());
        assertThat(start.getValue()).isEqualTo(Instant.parse("2026-11-07T17:00:00Z"));
        assertThat(end.getValue()).isEqualTo(Instant.parse("2026-11-14T17:00:00Z"));
        assertThat(service.summary(30).buckets()).hasSize(6);
        assertThatThrownBy(() -> service.summary(365)).isInstanceOf(com.nutee.backend.common.exception.ApiException.class);
    }
}
