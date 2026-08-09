package com.ecommerce.unit;

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

import com.ecommerce.entity.Order;
import com.ecommerce.entity.Payment;
import com.ecommerce.enums.OrderStatus;
import com.ecommerce.enums.PaymentStatus;
import com.ecommerce.repository.OrderRepository;
import com.ecommerce.service.OrderCancellationService;

@ExtendWith(MockitoExtension.class)
class OrderCancellationServiceTest {

    @Mock
    private OrderRepository orderRepository;

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
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

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
        
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        // Act
        orderCancellationService.cancelExpiredOrder(1L);

        // Assert
        assertEquals(OrderStatus.CANCELLED, order.getStatus());
        assertEquals(PaymentStatus.FAILED, payment.getPaymentStatus());
        verify(orderRepository).save(order);
    }
}
