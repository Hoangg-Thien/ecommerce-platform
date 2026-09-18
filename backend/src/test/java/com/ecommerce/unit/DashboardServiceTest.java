package com.ecommerce.unit;

import com.ecommerce.dto.response.DashboardStatsResponse;
import com.ecommerce.entity.Order;
import com.ecommerce.entity.User;
import com.ecommerce.enums.OrderStatus;
import com.ecommerce.repository.OrderRepository;
import com.ecommerce.repository.ProductRepository;
import com.ecommerce.repository.ProductVariantRepository;
import com.ecommerce.repository.UserRepository;
import com.ecommerce.service.DashboardService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private UserRepository userRepository;
    @Mock private ProductRepository productRepository;
    @Mock private ProductVariantRepository productVariantRepository;

    @InjectMocks
    private DashboardService dashboardService;

    // =========================================================================
    // getStats() — DB rỗng, không được throw exception
    // =========================================================================

    @Test
    void getStats_WhenDatabaseIsEmpty_ShouldReturnZeroedStatsWithoutException() {
        // Stub tất cả queries trả về giá trị rỗng/zero
        when(orderRepository.sumRevenueCompleted()).thenReturn(BigDecimal.ZERO);
        when(orderRepository.sumRevenueTodayCompleted()).thenReturn(BigDecimal.ZERO);
        when(orderRepository.count()).thenReturn(0L);
        when(orderRepository.countOrdersToday()).thenReturn(0L);
        when(orderRepository.countByStatus(any(OrderStatus.class))).thenReturn(0L);
        when(userRepository.count()).thenReturn(0L);
        when(productRepository.count()).thenReturn(0L);
        when(productVariantRepository.countLowStockVariants(anyInt())).thenReturn(0L);
        when(productVariantRepository.countByStock(0)).thenReturn(0L);
        when(orderRepository.findTopSellingProducts(any(Pageable.class))).thenReturn(List.of());
        when(orderRepository.findRecentOrders(any(Pageable.class))).thenReturn(List.of());

        DashboardStatsResponse result = assertDoesNotThrow(() -> dashboardService.getStats());

        assertNotNull(result);
        assertEquals(BigDecimal.ZERO, result.getTotalRevenue());
        assertEquals(BigDecimal.ZERO, result.getTodayRevenue());
        assertEquals(0L, result.getTotalOrders());
        assertEquals(0L, result.getTotalUsers());
        assertEquals(0L, result.getTotalProducts());
        assertNotNull(result.getTopProducts());
        assertTrue(result.getTopProducts().isEmpty());
        assertNotNull(result.getRecentOrders());
        assertTrue(result.getRecentOrders().isEmpty());
    }

    @Test
    void getStats_WhenDataExists_ShouldAggregateCorrectly() {
        when(orderRepository.sumRevenueCompleted()).thenReturn(new BigDecimal("5000000"));
        when(orderRepository.sumRevenueTodayCompleted()).thenReturn(new BigDecimal("500000"));
        when(orderRepository.count()).thenReturn(42L);
        when(orderRepository.countOrdersToday()).thenReturn(5L);
        when(orderRepository.countByStatus(OrderStatus.PENDING)).thenReturn(3L);
        when(orderRepository.countByStatus(OrderStatus.CONFIRMED)).thenReturn(2L);
        when(orderRepository.countByStatus(OrderStatus.SHIPPING)).thenReturn(7L);
        when(orderRepository.countByStatus(OrderStatus.DONE)).thenReturn(25L);
        when(orderRepository.countByStatus(OrderStatus.CANCELLED)).thenReturn(4L);
        when(orderRepository.countByStatus(OrderStatus.AWAITING_PAYMENT)).thenReturn(1L);
        when(userRepository.count()).thenReturn(100L);
        when(productRepository.count()).thenReturn(50L);
        when(productVariantRepository.countLowStockVariants(5)).thenReturn(8L);
        when(productVariantRepository.countByStock(0)).thenReturn(2L);
        when(orderRepository.findTopSellingProducts(any(Pageable.class))).thenReturn(List.of());
        when(orderRepository.findRecentOrders(any(Pageable.class))).thenReturn(List.of());

        DashboardStatsResponse result = dashboardService.getStats();

        // pendingOrders = PENDING + CONFIRMED = 3 + 2
        assertEquals(5L, result.getPendingOrders());
        assertEquals(7L, result.getShippingOrders());
        assertEquals(25L, result.getCompletedOrders());
        assertEquals(4L, result.getCancelledOrders());
        assertEquals(1L, result.getAwaitingPaymentOrders());
        assertEquals(100L, result.getTotalUsers());
        assertEquals(8L, result.getLowStockVariants());
        assertEquals(2L, result.getOutOfStockVariants());
    }

    @Test
    void getStats_WhenRecentOrdersHaveNullUser_ShouldUseDefaultEmail() {
        Order orderWithNullUser = new Order();
        orderWithNullUser.setId(1L);
        orderWithNullUser.setStatus(OrderStatus.PENDING);
        orderWithNullUser.setTotalPrice(BigDecimal.valueOf(100));
        // user = null — edge case

        // Stub minimum để method chạy được
        when(orderRepository.sumRevenueCompleted()).thenReturn(BigDecimal.ZERO);
        when(orderRepository.sumRevenueTodayCompleted()).thenReturn(BigDecimal.ZERO);
        when(orderRepository.count()).thenReturn(1L);
        when(orderRepository.countOrdersToday()).thenReturn(0L);
        when(orderRepository.countByStatus(any(OrderStatus.class))).thenReturn(0L);
        when(userRepository.count()).thenReturn(0L);
        when(productRepository.count()).thenReturn(0L);
        when(productVariantRepository.countLowStockVariants(anyInt())).thenReturn(0L);
        when(productVariantRepository.countByStock(0)).thenReturn(0L);
        when(orderRepository.findTopSellingProducts(any(Pageable.class))).thenReturn(List.of());
        when(orderRepository.findRecentOrders(any(Pageable.class))).thenReturn(List.of(orderWithNullUser));

        DashboardStatsResponse result = assertDoesNotThrow(() -> dashboardService.getStats());

        assertNotNull(result.getRecentOrders());
        assertEquals(1, result.getRecentOrders().size());
        // user null → fallback "—"
        assertEquals("—", result.getRecentOrders().get(0).getUserEmail());
    }

    @Test
    void getStats_WhenTopProductsExist_ShouldMapCorrectly() {
        Object[] row = new Object[]{ 10L, "Nike Air Max", "https://img.url/shoe.jpg", 150L };

        when(orderRepository.sumRevenueCompleted()).thenReturn(BigDecimal.ZERO);
        when(orderRepository.sumRevenueTodayCompleted()).thenReturn(BigDecimal.ZERO);
        when(orderRepository.count()).thenReturn(0L);
        when(orderRepository.countOrdersToday()).thenReturn(0L);
        when(orderRepository.countByStatus(any(OrderStatus.class))).thenReturn(0L);
        when(userRepository.count()).thenReturn(0L);
        when(productRepository.count()).thenReturn(0L);
        when(productVariantRepository.countLowStockVariants(anyInt())).thenReturn(0L);
        when(productVariantRepository.countByStock(0)).thenReturn(0L);
        List<Object[]> topRows = new java.util.ArrayList<>();
        topRows.add(row);
        when(orderRepository.findTopSellingProducts(any(Pageable.class))).thenReturn(topRows);
        when(orderRepository.findRecentOrders(any(Pageable.class))).thenReturn(new java.util.ArrayList<>());


        DashboardStatsResponse result = dashboardService.getStats();

        assertEquals(1, result.getTopProducts().size());
        DashboardStatsResponse.TopProductItem topProduct = result.getTopProducts().get(0);
        assertEquals(10L, topProduct.getProductId());
        assertEquals("Nike Air Max", topProduct.getProductName());
        assertEquals("https://img.url/shoe.jpg", topProduct.getImageUrl());
        assertEquals(150L, topProduct.getUnitsSold());
    }

    @Test
    void getStats_WithFullRecentOrder_ShouldMapAllFieldsCorrectly() {
        User user = new User();
        user.setId(5L);
        user.setEmail("customer@gmail.com");

        Order order = new Order();
        order.setId(99L);
        order.setUser(user);
        order.setStatus(OrderStatus.DONE);
        order.setTotalPrice(new BigDecimal("350000"));
        order.setCreateAt(LocalDateTime.now());

        when(orderRepository.sumRevenueCompleted()).thenReturn(BigDecimal.ZERO);
        when(orderRepository.sumRevenueTodayCompleted()).thenReturn(BigDecimal.ZERO);
        when(orderRepository.count()).thenReturn(1L);
        when(orderRepository.countOrdersToday()).thenReturn(0L);
        when(orderRepository.countByStatus(any(OrderStatus.class))).thenReturn(0L);
        when(userRepository.count()).thenReturn(0L);
        when(productRepository.count()).thenReturn(0L);
        when(productVariantRepository.countLowStockVariants(anyInt())).thenReturn(0L);
        when(productVariantRepository.countByStock(0)).thenReturn(0L);
        when(orderRepository.findTopSellingProducts(any(Pageable.class))).thenReturn(List.of());
        when(orderRepository.findRecentOrders(any(Pageable.class))).thenReturn(List.of(order));

        DashboardStatsResponse result = dashboardService.getStats();

        assertEquals(1, result.getRecentOrders().size());
        var recentOrder = result.getRecentOrders().get(0);
        assertEquals(99L, recentOrder.getOrderId());
        assertEquals("customer@gmail.com", recentOrder.getUserEmail());
        assertEquals(OrderStatus.DONE, recentOrder.getStatus());
        assertEquals(new BigDecimal("350000"), recentOrder.getTotalPrice());
    }
}
