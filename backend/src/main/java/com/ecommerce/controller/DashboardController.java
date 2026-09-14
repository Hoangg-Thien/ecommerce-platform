package com.ecommerce.controller;

import com.ecommerce.dto.response.DashboardStatsResponse;
import com.ecommerce.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    /**
     * GET /api/v1/admin/dashboard
     * Trả về toàn bộ số liệu thống kê cho Dashboard admin.
     * Được bảo vệ bởi SecurityConfig rule: /api/v1/admin/** → ADMIN only.
     */
    @GetMapping
    public ResponseEntity<DashboardStatsResponse> getStats() {
        return ResponseEntity.ok(dashboardService.getStats());
    }
}
