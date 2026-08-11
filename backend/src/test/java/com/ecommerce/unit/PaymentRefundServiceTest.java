package com.ecommerce.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

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
import com.ecommerce.enums.PaymentStatus;
import com.ecommerce.repository.PaymentRepository;
import com.ecommerce.service.MomoService;
import com.ecommerce.service.PaymentRefundService;

@ExtendWith(MockitoExtension.class)
class PaymentRefundServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private MomoService momoService;

    @InjectMocks
    private PaymentRefundService paymentRefundService;

    private Payment payment;

    @BeforeEach
    void setUp() {
        payment = new Payment();
        payment.setId(1L);
        Order order = new Order();
        order.setId(1L);
        payment.setOrder(order);
    }

    @Test
    @DisplayName("Idempotency: call processRefund twice, MoMo API should be called only once")
    void processRefund_CalledTwice_CallsMomoApiOnce() {
        // Arrange
        payment.setPaymentStatus(PaymentStatus.REFUNDING); // Simulate already refunding or refunded
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));

        // Act
        paymentRefundService.processRefund(1L);

        // Assert
        verify(momoService, never()).refundPayment(any());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    @DisplayName("MoMo API fail -> status remains REFUNDING and exception is thrown")
    void processRefund_MomoApiFails_StatusRemainsRefunding() {
        // Arrange
        payment.setPaymentStatus(PaymentStatus.FAILED);
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
        doThrow(new RuntimeException("MoMo API Error")).when(momoService).refundPayment(payment);

        // Act & Assert
        Exception exception = assertThrows(RuntimeException.class, () -> {
            paymentRefundService.processRefund(1L);
        });
        
        assertEquals("MoMo API Error", exception.getMessage());
        
        // Assert state is REFUNDING (from the first save)
        assertEquals(PaymentStatus.REFUNDING, payment.getPaymentStatus());
        // Verify save was called only once (to save REFUNDING state)
        verify(paymentRepository, times(1)).save(payment);
    }
}
