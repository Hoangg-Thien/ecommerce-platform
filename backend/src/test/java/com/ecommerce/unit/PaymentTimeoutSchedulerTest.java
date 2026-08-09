package com.ecommerce.unit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ecommerce.entity.Order;
import com.ecommerce.enums.OrderStatus;
import com.ecommerce.enums.PaymentMethod;
import com.ecommerce.repository.OrderRepository;
import com.ecommerce.scheduler.PaymentTimeoutScheduler;
import com.ecommerce.service.OrderCancellationService;

@ExtendWith(MockitoExtension.class)
class PaymentTimeoutSchedulerTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderCancellationService orderCancellationService;

    @InjectMocks
    private PaymentTimeoutScheduler scheduler;

    @Test
    @DisplayName("cancelExpiredMomoPayments - Should call cancellationService for each order and continue on error")
    void cancelExpiredMomoPayments_CallsServicePerOrder() {
        // Arrange
        Order order1 = new Order();
        order1.setId(1L);
        Order order2 = new Order();
        order2.setId(2L);
        Order order3 = new Order();
        order3.setId(3L);

        List<Order> expiredOrders = Arrays.asList(order1, order2, order3);

        when(orderRepository.findByStatusAndPaymentMethodAndCreateAtBefore(
                eq(OrderStatus.AWAITING_PAYMENT),
                eq(PaymentMethod.MOMO),
                any(LocalDateTime.class)))
                .thenReturn(expiredOrders);

        // Simulate an exception for order2 to ensure loop continues for order3
        org.mockito.Mockito.lenient().doThrow(new RuntimeException("Test Exception")).when(orderCancellationService).cancelExpiredOrder(2L);

        // Act
        scheduler.cancelExpiredMomoPayments();

        // Assert
        verify(orderCancellationService).cancelExpiredOrder(1L);
        verify(orderCancellationService).cancelExpiredOrder(2L);
        verify(orderCancellationService).cancelExpiredOrder(3L);
    }
}
