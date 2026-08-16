package com.ecommerce.controller;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ecommerce.dto.request.MockPaymentRequest;
import com.ecommerce.dto.response.PaymentResponse;
import com.ecommerce.service.MockPaymentService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/mock-payments")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "payment.momo.mock-enabled", havingValue = "true")
public class MockPaymentController {

    private final MockPaymentService mockPaymentService;

    @PostMapping("/{orderId}/simulate")
    public ResponseEntity<PaymentResponse> simulatePayment(
            @PathVariable Long orderId,
            @Valid @RequestBody MockPaymentRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        
        PaymentResponse response = mockPaymentService.simulatePayment(orderId, request, userDetails.getUsername());
        return ResponseEntity.ok(response);
    }
}
