package com.ecommerce.order.controller;

import com.ecommerce.order.service.OrderService;

import com.ecommerce.order.dto.request.UpdateOrderStatusRequest;
import com.ecommerce.order.dto.response.AdminOrderResponse;
import com.ecommerce.common.dto.PageResponse;
import com.ecommerce.order.enums.OrderStatus;
import com.ecommerce.order.service.AdminOrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/orders")
@RequiredArgsConstructor
public class AdminOrderController {

    private final AdminOrderService adminOrderService;

    /**
     * GET /api/v1/admin/orders?page=0&size=10&status=PENDING&email=xxx
     * Lấy tất cả đơn hàng, tuỳ chọn filter theo trạng thái và email khách.
     */
    @GetMapping
    public ResponseEntity<PageResponse<AdminOrderResponse>> getAllOrders(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "10") int size,
        @RequestParam(required = false) OrderStatus status,
        @RequestParam(required = false) String email
    ) {
        if (page < 0) page = 0;
        if (size <= 0) size = 10;
        if (size > 100) size = 100;

        Pageable pageable = PageRequest.of(page, size, Sort.by("createAt").descending());
        PageResponse<AdminOrderResponse> result = adminOrderService.getAllOrders(status, email, pageable);
        return ResponseEntity.ok(result);
    }

    /**
     * GET /api/v1/admin/orders/{id}
     * Lấy chi tiết 1 đơn — ADMIN bypass check chủ sở hữu.
     */
    @GetMapping("/{id}")
    public ResponseEntity<AdminOrderResponse> getOrderById(@PathVariable Long id) {
        return ResponseEntity.ok(adminOrderService.getOrderById(id));
    }

    /**
     * PATCH /api/v1/admin/orders/{id}/status
     * Đổi trạng thái đơn — dùng lại logic từ OrderService.
     */
    @PatchMapping("/{id}/status")
    public ResponseEntity<AdminOrderResponse> updateOrderStatus(
        @PathVariable Long id,
        @Valid @RequestBody UpdateOrderStatusRequest request
    ) {
        return ResponseEntity.ok(adminOrderService.updateOrderStatus(id, request.getOrderStatus()));
    }
}
