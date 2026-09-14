package com.ecommerce.dto.response;

import com.ecommerce.enums.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO dùng cho Admin User Management — bổ sung thống kê đơn hàng.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminUserResponse {
    private Long id;
    private String email;
    private Role role;
    private Long totalOrders;       // tổng số đơn đã đặt
    private Long completedOrders;   // đơn DONE
}
