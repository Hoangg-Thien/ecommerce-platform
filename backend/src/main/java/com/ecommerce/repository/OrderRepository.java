package com.ecommerce.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.ecommerce.entity.Order;
import com.ecommerce.enums.OrderStatus;
import com.ecommerce.enums.PaymentMethod;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    Page<Order> findByUserId(Long userId, Pageable pageable);

    @EntityGraph(attributePaths = {"payment"})
    List<Order> findByStatusAndPaymentMethodAndCreateAtBefore(
        OrderStatus status,
        PaymentMethod paymentMethod,
        LocalDateTime createAt);

    // ---- Admin queries (eager-load để tránh N+1) ----

    /**
     * Override findAll để đính kèm @EntityGraph — dùng bởi AdminOrderService.getAllOrders()
     * khi không có filter nào. Nếu không override, Hibernate sẽ lazy-load từng
     * association riêng lẻ → N+1 với mỗi order trên trang.
     */
    @Override
    @EntityGraph(attributePaths = {"user", "items", "items.product", "payment"})
    Page<Order> findAll(Pageable pageable);

    /**
     * Override findById để các call trong updateOrderStatus() và getOrderById()
     * cũng eager-load đầy đủ — tránh lazy-load thêm khi mapper chạy.
     */
    @Override
    @EntityGraph(attributePaths = {"user", "items", "items.product", "items.product.variants", "payment"})
    Optional<Order> findById(Long id);

    @EntityGraph(attributePaths = {"user", "items", "items.product", "payment"})
    Page<Order> findByStatus(OrderStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"user", "items", "items.product", "payment"})
    @Query("SELECT o FROM Order o JOIN o.user u WHERE LOWER(u.email) LIKE LOWER(CONCAT('%', :email, '%'))")
    Page<Order> findByUserEmailContainingIgnoreCase(@Param("email") String email, Pageable pageable);

    @EntityGraph(attributePaths = {"user", "items", "items.product", "payment"})
    @Query("SELECT o FROM Order o JOIN o.user u WHERE o.status = :status AND LOWER(u.email) LIKE LOWER(CONCAT('%', :email, '%'))")
    Page<Order> findByStatusAndUserEmailContainingIgnoreCase(
        @Param("status") OrderStatus status,
        @Param("email") String email,
        Pageable pageable);

    long countByUserId(Long userId);
    long countByUserIdAndStatus(Long userId, OrderStatus status);

    /**
     * Bẩt tất cả thống kê đơn hàng cho một tập user IDs bằng 1 query GROUP BY duy nhất.
     * Mỗi phần tử trả về là Object[3]: [userId (Long), totalOrders (Long), completedOrders (Long)].
     * Dùng bởi AdminUserService.getAllUsers() để tránh N×2 COUNT queries trên mỗi trang.
     */
    @Query("""
        SELECT o.user.id,
               COUNT(o.id),
               SUM(CASE WHEN o.status = com.ecommerce.enums.OrderStatus.DONE THEN 1 ELSE 0 END)
        FROM Order o
        WHERE o.user.id IN :userIds
        GROUP BY o.user.id
        """)
    List<Object[]> countOrderStatsByUserIds(@Param("userIds") List<Long> userIds);

    // ---- Dashboard aggregation ----

    // Tổng doanh thu (chỉ tính đơn DONE)
    @Query("SELECT COALESCE(SUM(o.totalPrice), 0) FROM Order o WHERE o.status = 'DONE'")
    java.math.BigDecimal sumRevenueCompleted();

    // Đơn theo từng trạng thái
    long countByStatus(OrderStatus status);

    // Đơn hôm nay
    @Query("SELECT COUNT(o) FROM Order o WHERE CAST(o.createAt AS date) = CURRENT_DATE")
    long countOrdersToday();

    // Doanh thu hôm nay
    @Query("SELECT COALESCE(SUM(o.totalPrice), 0) FROM Order o WHERE o.status = 'DONE' AND CAST(o.createAt AS date) = CURRENT_DATE")
    java.math.BigDecimal sumRevenueTodayCompleted();

    // Top sản phẩm bán chạy (theo số lượng đã bán trong đơn DONE)
    @Query("SELECT oi.product.id, oi.product.name, oi.product.imageUrl, SUM(oi.quantity) as sold " +
           "FROM OrderItem oi JOIN oi.order o " +
           "WHERE o.status = 'DONE' " +
           "GROUP BY oi.product.id, oi.product.name, oi.product.imageUrl " +
           "ORDER BY sold DESC")
    java.util.List<Object[]> findTopSellingProducts(org.springframework.data.domain.Pageable pageable);

    // Đơn gần đây — fetch user ngay để tránh N+1 khi DashboardService gọi o.getUser().getEmail()
    @EntityGraph(attributePaths = {"user"})
    @Query("SELECT o FROM Order o ORDER BY o.createAt DESC")
    java.util.List<Order> findRecentOrders(org.springframework.data.domain.Pageable pageable);
}
