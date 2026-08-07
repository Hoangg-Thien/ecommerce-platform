package com.ecommerce.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ecommerce.dto.request.CheckoutRequest;
import com.ecommerce.dto.response.CheckoutResponse;
import com.ecommerce.service.payment.CheckoutService;

import io.swagger.v3.oas.annotations.parameters.RequestBody;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/checkout")
@RequiredArgsConstructor
public class CheckoutController {
    private CheckoutService checkoutService;

    public ResponseEntity<CheckoutResponse> checkout(
        @AuthenticationPrincipal UserDetails userDetails,
        @Valid @RequestBody CheckoutRequest request
    ){
        CheckoutResponse response = checkoutService.checkout(userDetails.getUsername(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
