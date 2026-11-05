package com.ecommerce.promotion.repository;

import com.ecommerce.promotion.entity.VoucherUsage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import java.time.Instant;
import com.ecommerce.promotion.enums.VoucherUsageStatus;

public interface VoucherUsageRepository extends JpaRepository<VoucherUsage, Long> {
    Optional<VoucherUsage> findByOrderId(Long orderId);

    @Query("SELECT u.voucher.id FROM VoucherUsage u WHERE u.order.id = :orderId")
    Optional<Long> findVoucherIdByOrderId(@Param("orderId") Long orderId);

    @Modifying(flushAutomatically = true)
    @Transactional(propagation = Propagation.MANDATORY)
    @Query(value = """
        UPDATE voucher_usages SET status = 'RELEASED', released_at = :releasedAt
        WHERE order_id = :orderId AND status = 'APPLIED'
          AND EXISTS (SELECT 1 FROM orders WHERE id = :orderId AND status = 'CANCELLED')
        """, nativeQuery = true)
    int releaseIfOrderCancelled(@Param("orderId") Long orderId, @Param("releasedAt") Instant releasedAt);
    boolean existsByVoucherId(Long voucherId);
    boolean existsByVoucherIdAndUserIdAndStatus(Long voucherId, Long userId, VoucherUsageStatus status);

    @Modifying(flushAutomatically = true)
    @Transactional(propagation = Propagation.MANDATORY)
    @Query("""
        UPDATE VoucherUsage u SET u.status = com.ecommerce.promotion.enums.VoucherUsageStatus.RELEASED,
                                  u.releasedAt = :releasedAt
        WHERE u.order.id = :orderId AND u.status = com.ecommerce.promotion.enums.VoucherUsageStatus.APPLIED
        """)
    int releaseIfApplied(@Param("orderId") Long orderId, @Param("releasedAt") Instant releasedAt);
}
