package com.ecommerce.promotion.repository;

import com.ecommerce.promotion.entity.PromotionProgram;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface PromotionProgramRepository extends JpaRepository<PromotionProgram, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PromotionProgram p WHERE p.id=:id")
    Optional<PromotionProgram> findForUpdate(@Param("id") Long id);

    @Query(value="""
        SELECT EXISTS (SELECT 1 FROM voucher_usages u JOIN vouchers v ON v.id=u.voucher_id WHERE v.promotion_program_id=:id)
            OR EXISTS (SELECT 1 FROM orders o JOIN order_discounts d ON d.id=o.order_discount_id WHERE d.promotion_program_id=:id)
            OR EXISTS (SELECT 1 FROM order_items WHERE product_discount_program_id=:id)
        """,nativeQuery=true)
    boolean hasHistory(@Param("id") Long id);
}
