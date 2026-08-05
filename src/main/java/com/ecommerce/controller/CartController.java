package com.ecommerce.controller;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ecommerce.dto.request.AddToCartRequest;
import com.ecommerce.dto.response.CartResponse;
import com.ecommerce.service.CartService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;


@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class CartController {
    private final UserDetailsService userDetailsService;
    private final CartService cartService;

    @PostMapping("/add")
    public ResponseEntity<CartResponse> addToCart(
        @AuthenticationPrincipal UserDetails userdetails,
        @Valid @RequestBody AddToCartRequest request
    ){
        CartResponse response = cartService.addToCart(userdetails.getUsername(), request);
        return ResponseEntity.ok(response);
    }
    
}
