package com.ecommerce.controller;

import org.springframework.http.HttpStatus;
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
import com.ecommerce.dto.response.PageResponse;
import com.ecommerce.service.OrderService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.RequestParam;

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

    // GET /api/orders?page=0&size=10&sortBy=createAt&sortDir=desc
    @GetMapping
    public ResponseEntity<PageResponse<OrderResponse>> getUserOrders(
        @AuthenticationPrincipal UserDetails userDetails,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "10") int size,
        @RequestParam(defaultValue = "createAt") String sortBy,
        @RequestParam(defaultValue = "desc") String sortDir
    ){
        if (page < 0) {
            page = 0;
        }
        if (size <= 0) {
            size = 10;
        } else if (size > 50) {
            size = 50;
        }

        Sort sort = sortDir.equalsIgnoreCase(Sort.Direction.ASC.name())
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();

        Pageable pageable = PageRequest.of(page, size, sort);
        PageResponse<OrderResponse> orders = orderService.getUserOrders(userDetails.getUsername(), pageable);
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
