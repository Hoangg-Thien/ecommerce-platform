package com.ecommerce.order.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ecommerce.order.entity.Order;
import com.ecommerce.payment.entity.Payment;
import com.ecommerce.order.enums.OrderStatus;
import com.ecommerce.payment.enums.PaymentStatus;
import com.ecommerce.order.repository.OrderRepository;
import com.ecommerce.order.service.OrderCancellationService;

@ExtendWith(MockitoExtension.class)
class OrderCancellationServiceTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock private com.ecommerce.promotion.service.VoucherUsageService voucherUsageService;
    @Mock private com.ecommerce.payment.repository.PaymentRepository paymentRepository;
    @org.mockito.Spy private java.time.Clock clock = java.time.Clock.systemUTC();

    @InjectMocks
    private OrderCancellationService orderCancellationService;

    private Order order;

    @BeforeEach
    void setUp() {
        order = new Order();
        order.setId(1L);
    }

    @Test
    @DisplayName("cancelExpiredOrder - Should do nothing if order is already CANCELLED (Idempotency)")
    void cancelExpiredOrder_AlreadyCancelled_ShouldDoNothing() {
        // Arrange
        order.setStatus(OrderStatus.CANCELLED);
        when(orderRepository.findPaymentMethodById(1L)).thenReturn(Optional.of(com.ecommerce.payment.enums.PaymentMethod.VNPAY));
        when(orderRepository.lockRootById(1L)).thenReturn(Optional.of(order));

        // Act
        orderCancellationService.cancelExpiredOrder(1L);

        // Assert
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    @DisplayName("cancelExpiredOrder - Should cancel order and payment if AWAITING_PAYMENT")
    void cancelExpiredOrder_ValidOrder_ShouldCancel() {
        // Arrange
        order.setStatus(OrderStatus.AWAITING_PAYMENT);
        Payment payment = new Payment();
        payment.setPaymentStatus(PaymentStatus.PENDING);
        order.setPayment(payment);
        order.setPaymentMethod(com.ecommerce.payment.enums.PaymentMethod.VNPAY);
        payment.setExpiresAt(clock.instant().minusSeconds(900));
        when(paymentRepository.lockByOrderId(1L)).thenReturn(Optional.of(payment));
        order.setCreateAt(java.time.LocalDateTime.now(clock).minusHours(1));
        
        when(orderRepository.findPaymentMethodById(1L)).thenReturn(Optional.of(com.ecommerce.payment.enums.PaymentMethod.VNPAY));
        when(orderRepository.lockRootById(1L)).thenReturn(Optional.of(order));

        // Act
        orderCancellationService.cancelExpiredOrder(1L);

        // Assert
        assertEquals(OrderStatus.CANCELLED, order.getStatus());
        assertEquals(PaymentStatus.PENDING, payment.getPaymentStatus());
        assertEquals(com.ecommerce.payment.enums.RefundStatus.RECONCILIATION_REQUIRED, payment.getRefundStatus());
        verify(orderRepository).save(order);
    }
}
