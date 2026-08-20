package com.ecommerce.unit;

import com.ecommerce.dto.request.MockPaymentRequest;
import com.ecommerce.dto.response.PaymentResponse;
import com.ecommerce.entity.Order;
import com.ecommerce.entity.Payment;
import com.ecommerce.entity.User;
import com.ecommerce.enums.PaymentStatus;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.exception.UnauthorizedAccessException;
import com.ecommerce.mapper.PaymentMapper;
import com.ecommerce.repository.PaymentRepository;
import com.ecommerce.service.MockPaymentService;
import com.ecommerce.service.PaymentFulfillmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MockPaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentFulfillmentService paymentFulfillmentService;

    @Mock
    private PaymentMapper paymentMapper;

    @InjectMocks
    private MockPaymentService mockPaymentService;

    private Payment payment;
    private Order order;
    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setEmail("user@gmail.com");

        order = new Order();
        order.setId(100L);
        order.setUser(user);

        payment = new Payment();
        payment.setId(1L);
        payment.setOrder(order);
        payment.setPaymentStatus(PaymentStatus.PENDING);
    }

    @Test
    void simulatePayment_WhenScenarioFail_ShouldProcessFailure() {
        MockPaymentRequest request = new MockPaymentRequest();
        request.setScenario("FAIL");

        when(paymentRepository.findByOrderId(100L)).thenReturn(Optional.of(payment));
        when(paymentMapper.toPaymentResponse(payment)).thenReturn(new PaymentResponse());

        PaymentResponse result = mockPaymentService.simulatePayment(100L, request, "user@gmail.com");

        assertNotNull(result);
        verify(paymentFulfillmentService, times(1)).processFailure(eq(payment), anyString());
        verify(paymentFulfillmentService, never()).processSuccess(any(), anyString());
    }

    @Test
    void simulatePayment_WhenScenarioPending_ShouldNotChangeDatabase() {
        MockPaymentRequest request = new MockPaymentRequest();
        request.setScenario("PENDING");

        when(paymentRepository.findByOrderId(100L)).thenReturn(Optional.of(payment));
        when(paymentMapper.toPaymentResponse(payment)).thenReturn(new PaymentResponse());

        PaymentResponse result = mockPaymentService.simulatePayment(100L, request, "user@gmail.com");

        assertNotNull(result);
        verify(paymentFulfillmentService, never()).processFailure(any(), anyString());
        verify(paymentFulfillmentService, never()).processSuccess(any(), anyString());
    }
}
