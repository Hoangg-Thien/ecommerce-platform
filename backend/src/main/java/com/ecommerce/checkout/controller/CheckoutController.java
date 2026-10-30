package com.ecommerce.checkout.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ecommerce.common.idempotency.Idempotent;
import com.ecommerce.checkout.dto.request.CheckoutRequest;
import com.ecommerce.checkout.dto.request.CheckoutPreviewRequest;
import com.ecommerce.checkout.dto.response.CheckoutResponse;
import com.ecommerce.checkout.dto.response.CheckoutPreviewResponse;
import com.ecommerce.checkout.service.CheckoutService;
import com.ecommerce.order.dto.response.OrderResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/checkout")
@RequiredArgsConstructor
public class CheckoutController {
    private final CheckoutService checkoutService;

    @PostMapping("/preview")
    public CheckoutPreviewResponse preview(
            @AuthenticationPrincipal UserDetails user,
            @RequestBody CheckoutPreviewRequest request) {
        return CheckoutPreviewResponse.from(checkoutService.preview(user.getUsername(), request));
    }

    @GetMapping("/result")
    public OrderResponse result(
            @AuthenticationPrincipal UserDetails user,
            @RequestHeader("Idempotency-Key") String key) {
        return checkoutService.findResult(user.getUsername(), key);
    }

    @Idempotent
    @PostMapping
    public ResponseEntity<CheckoutResponse> checkout(
        @AuthenticationPrincipal UserDetails userDetails,
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @Valid @RequestBody CheckoutRequest request
    ){
        CheckoutResponse response = checkoutService.checkout(userDetails.getUsername(), request, idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}

