package com.ecommerce.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * Tổng hợp tất cả số liệu cho Dashboard Admin.
 */
@Data
@Builder
public class DashboardStatsResponse {

    // ---- Revenue ----
    private BigDecimal totalRevenue;        // doanh thu tổng (đơn DONE)
    private BigDecimal todayRevenue;        // doanh thu hôm nay

    // ---- Orders ----
    private long totalOrders;
    private long todayOrders;
    private long pendingOrders;             // PENDING + CONFIRMED
    private long shippingOrders;            // SHIPPING
    private long completedOrders;           // DONE
    private long cancelledOrders;           // CANCELLED
    private long awaitingPaymentOrders;     // AWAITING_PAYMENT

    // ---- Users & Products ----
    private long totalUsers;
    private long totalProducts;
    private long lowStockVariants;          // variant có stock 1-5
    private long outOfStockVariants;        // variant có stock = 0

    // ---- Lists ----
    private List<TopProductItem> topProducts;
    private List<RecentOrderItem> recentOrders;

    @Data
    @Builder
    public static class TopProductItem {
        private Long productId;
        private String productName;
        private String imageUrl;
        private Long unitsSold;
    }

    @Data
    @Builder
    public static class RecentOrderItem {
        private Long orderId;
        private String userEmail;
        private com.ecommerce.enums.OrderStatus status;
        private BigDecimal totalPrice;
        private java.time.LocalDateTime createdAt;
        private com.ecommerce.enums.PaymentMethod paymentMethod;
    }
}
