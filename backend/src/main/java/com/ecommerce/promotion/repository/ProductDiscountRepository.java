package com.ecommerce.promotion.repository;

import com.ecommerce.promotion.entity.ProductDiscount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.time.Instant;
import java.util.Collection;

public interface ProductDiscountRepository extends JpaRepository<ProductDiscount, Long> {
    List<ProductDiscount> findByProgramId(Long programId);

    @Query("""
        SELECT d FROM ProductDiscount d JOIN FETCH d.program p
        WHERE d.product.id IN :productIds
          AND p.status = com.ecommerce.promotion.enums.PromotionStatus.ACTIVE
          AND p.startDate <= :now AND p.endDate > :now
        """)
    List<ProductDiscount> findActiveForProducts(@Param("productIds") Collection<Long> productIds,
                                               @Param("now") Instant now);
}
