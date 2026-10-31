package com.ecommerce.promotion.repository;

import com.ecommerce.promotion.entity.ProductDiscount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.time.Instant;
import java.util.Collection;

public interface ProductDiscountRepository extends JpaRepository<ProductDiscount, Long> {
    @Modifying
    @Transactional(propagation = Propagation.MANDATORY)
    @Query("DELETE FROM ProductDiscount d WHERE d.product.id = :productId")
    void deleteByProductId(@Param("productId") Long productId);
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
