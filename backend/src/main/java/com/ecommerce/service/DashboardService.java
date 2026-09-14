package com.ecommerce.service;

import com.ecommerce.dto.response.DashboardStatsResponse;
import com.ecommerce.dto.response.DashboardStatsResponse.RecentOrderItem;
import com.ecommerce.dto.response.DashboardStatsResponse.TopProductItem;
import com.ecommerce.entity.Order;
import com.ecommerce.enums.OrderStatus;
import com.ecommerce.repository.OrderRepository;
import com.ecommerce.repository.ProductRepository;
import com.ecommerce.repository.ProductVariantRepository;
import com.ecommerce.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {

    private static final int LOW_STOCK_THRESHOLD = 5;
    private static final int TOP_PRODUCTS_LIMIT = 5;
    private static final int RECENT_ORDERS_LIMIT = 8;

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;

    @Transactional(readOnly = true)
    public DashboardStatsResponse getStats() {
        log.info("Building dashboard stats...");

        // ---- Revenue ----
        BigDecimal totalRevenue = orderRepository.sumRevenueCompleted();
        BigDecimal todayRevenue = orderRepository.sumRevenueTodayCompleted();

        // ---- Orders ----
        long totalOrders = orderRepository.count();
        long todayOrders = orderRepository.countOrdersToday();
        long pendingOrders = orderRepository.countByStatus(OrderStatus.PENDING)
                           + orderRepository.countByStatus(OrderStatus.CONFIRMED);
        long shippingOrders = orderRepository.countByStatus(OrderStatus.SHIPPING);
        long completedOrders = orderRepository.countByStatus(OrderStatus.DONE);
        long cancelledOrders = orderRepository.countByStatus(OrderStatus.CANCELLED);
        long awaitingPaymentOrders = orderRepository.countByStatus(OrderStatus.AWAITING_PAYMENT);

        // ---- Users & Products ----
        long totalUsers = userRepository.count();
        long totalProducts = productRepository.count();
        long lowStockVariants = productVariantRepository.countLowStockVariants(LOW_STOCK_THRESHOLD);
        long outOfStockVariants = productVariantRepository.countByStock(0);

        // ---- Top Products ----
        List<Object[]> topRaw = orderRepository.findTopSellingProducts(
            PageRequest.of(0, TOP_PRODUCTS_LIMIT)
        );
        List<TopProductItem> topProducts = topRaw.stream()
            .map(row -> TopProductItem.builder()
                .productId(((Number) row[0]).longValue())
                .productName((String) row[1])
                .imageUrl((String) row[2])
                .unitsSold(((Number) row[3]).longValue())
                .build())
            .toList();

        // ---- Recent Orders ----
        List<Order> recentRaw = orderRepository.findRecentOrders(
            PageRequest.of(0, RECENT_ORDERS_LIMIT)
        );
        List<RecentOrderItem> recentOrders = recentRaw.stream()
            .map(o -> RecentOrderItem.builder()
                .orderId(o.getId())
                .userEmail(o.getUser() != null ? o.getUser().getEmail() : "—")
                .status(o.getStatus())
                .totalPrice(o.getTotalPrice())
                .paymentMethod(o.getPaymentMethod())
                .createdAt(o.getCreateAt())
                .build())
            .toList();

        return DashboardStatsResponse.builder()
            .totalRevenue(totalRevenue)
            .todayRevenue(todayRevenue)
            .totalOrders(totalOrders)
            .todayOrders(todayOrders)
            .pendingOrders(pendingOrders)
            .shippingOrders(shippingOrders)
            .completedOrders(completedOrders)
            .cancelledOrders(cancelledOrders)
            .awaitingPaymentOrders(awaitingPaymentOrders)
            .totalUsers(totalUsers)
            .totalProducts(totalProducts)
            .lowStockVariants(lowStockVariants)
            .outOfStockVariants(outOfStockVariants)
            .topProducts(topProducts)
            .recentOrders(recentOrders)
            .build();
    }
}
