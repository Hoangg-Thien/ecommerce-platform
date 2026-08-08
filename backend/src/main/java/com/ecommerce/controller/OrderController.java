package com.ecommerce.controller;

import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ecommerce.dto.request.UpdateOrderStatusRequest;
import com.ecommerce.dto.response.OrderResponse;
import com.ecommerce.service.OrderService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping("/create")
    public ResponseEntity<OrderResponse> createOrder(
        @AuthenticationPrincipal UserDetails userDetails
    ){
        OrderResponse orderResponse = orderService.createOrder(userDetails.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED).body(orderResponse);
    }

    // GET /api/orders 
    @GetMapping
    public ResponseEntity<List<OrderResponse>> getUserOrders(
        @AuthenticationPrincipal UserDetails userDetails
    ){
        List<OrderResponse> orders = orderService.getUserOrders(userDetails.getUsername());
        return ResponseEntity.ok(orders);
    }

    // PATCH /api/orders/{id}/status
    @PatchMapping("/{id}/status")
    public ResponseEntity<OrderResponse> updateOrderStatus(
        @PathVariable Long id,
        @Valid @RequestBody
        UpdateOrderStatusRequest request
    ){
        OrderResponse responses = orderService.updateOrderStatus(id, request.getOrderStatus());
        return ResponseEntity.ok(responses);
    }
}
