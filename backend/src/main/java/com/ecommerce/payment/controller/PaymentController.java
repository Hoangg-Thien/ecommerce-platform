package com.ecommerce.payment.controller;


import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ecommerce.payment.dto.response.PaymentResponse;
import com.ecommerce.payment.entity.Payment;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.payment.mapper.PaymentMapper;
import com.ecommerce.payment.repository.PaymentRepository;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentRepository paymentRepository;
    private final PaymentMapper paymentMapper;

    // Xem trạng thái payment theo orderId
    // Yêu cầu authentication (user đăng nhập)
    @GetMapping("/order/{orderId}")

    public ResponseEntity<PaymentResponse> getPaymentByOrderId(
        java.security.Principal principal,
        @PathVariable Long orderId
    ){
        Payment payment = paymentRepository.findByOrderId(orderId)
        .orElseThrow(() -> new ResourceNotFoundException("Payment", orderId));
        
        // Ownership check: đảm bảo user chỉ xem được payment của chính mình
        if (principal == null || !payment.getOrder().getUser().getEmail().equals(principal.getName())) {
            return ResponseEntity.status(403).build(); // 403 Forbidden
        }
        
        return ResponseEntity.ok(paymentMapper.toPaymentResponse(payment));
    }
}
