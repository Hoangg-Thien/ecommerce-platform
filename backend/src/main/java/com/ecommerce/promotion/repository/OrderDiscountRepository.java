package com.ecommerce.promotion.repository;

import com.ecommerce.promotion.entity.OrderDiscount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.time.Instant;

public interface OrderDiscountRepository extends JpaRepository<OrderDiscount, Long> {
    @Query(value="SELECT EXISTS (SELECT 1 FROM orders WHERE order_discount_id=:id)",nativeQuery=true)
    boolean hasOrderHistory(@Param("id") Long id);
    List<OrderDiscount> findByProgramId(Long programId);

    @Query("""
        SELECT d FROM OrderDiscount d JOIN FETCH d.program p
        WHERE p.status = com.ecommerce.promotion.enums.PromotionStatus.ACTIVE
          AND p.startDate <= :now AND p.endDate > :now
        """)
    List<OrderDiscount> findActiveAt(@Param("now") Instant now);
}
