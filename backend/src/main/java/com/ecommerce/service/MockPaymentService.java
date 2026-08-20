package com.ecommerce.service;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.ecommerce.dto.request.MockPaymentRequest;
import com.ecommerce.dto.response.PaymentResponse;
import com.ecommerce.entity.Order;
import com.ecommerce.entity.Payment;
import com.ecommerce.enums.PaymentStatus;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.exception.UnauthorizedAccessException;
import com.ecommerce.mapper.PaymentMapper;
import com.ecommerce.repository.PaymentRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class MockPaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentFulfillmentService paymentFulfillmentService;
    private final PaymentMapper paymentMapper;

    public PaymentResponse simulatePayment(Long orderId, MockPaymentRequest request, String username) {
        
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found for orderId: " + orderId));

        Order order = payment.getOrder();
        
        if (!order.getUser().getEmail().equals(username)) {
            log.warn("Unauthorized access attempt to mock payment. User: {}, OrderId: {}", username, orderId);
            throw new UnauthorizedAccessException("You are not authorized to simulate payment for this order");
        }

        if (payment.getPaymentStatus() != PaymentStatus.PENDING) {
            log.info("Payment is not PENDING (current status: {}), rejecting mock simulate request for orderId: {}", 
                     payment.getPaymentStatus(), orderId);
            throw new IllegalArgumentException("Payment has already been processed or is not in PENDING state");
        }

        String scenario = request.getScenario();
        
        switch (scenario) {
            case "SUCCESS":
                String mockTransactionId = "MOCK_" + UUID.randomUUID().toString();
                paymentFulfillmentService.processSuccess(payment, mockTransactionId);
                break;
            case "FAIL":
                paymentFulfillmentService.processFailure(payment, "User simulated failure via Mock Gateway");
                break;
            case "PENDING":
                log.info("Mock payment simulated PENDING for orderId: {}. No database changes made.", orderId);
                break;
            default:
                throw new IllegalArgumentException("Invalid scenario: " + scenario);
        }

        // Return updated payment
        return paymentMapper.toPaymentResponse(payment);
    }
}
