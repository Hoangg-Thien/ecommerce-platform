package com.ecommerce.promotion.repository;

import com.ecommerce.promotion.entity.Voucher;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;

public interface VoucherRepository extends JpaRepository<Voucher, Long> {
    @EntityGraph(attributePaths = "program")
    Optional<Voucher> findByCode(String code);

    List<Voucher> findByProgramId(Long programId);

    @Transactional(propagation = Propagation.MANDATORY)
    @Query(value = "SELECT id FROM vouchers WHERE id = :id FOR UPDATE", nativeQuery = true)
    Optional<Long> lockId(@Param("id") Long id);

    // These primitives must participate in the checkout/cancellation transaction.
    // Bulk SQL does not refresh managed vouchers; callers must not reuse cached counters.
    @Modifying(flushAutomatically = true)
    @Transactional(propagation = Propagation.MANDATORY)
    @Query(value = "UPDATE vouchers SET used_quantity = used_quantity + 1 WHERE id = :id AND used_quantity < quantity", nativeQuery = true)
    int incrementUsageIfAvailable(@Param("id") Long id);

    @Modifying(flushAutomatically = true)
    @Transactional(propagation = Propagation.MANDATORY)
    @Query(value = "UPDATE vouchers SET used_quantity = used_quantity - 1 WHERE id = :id AND used_quantity > 0", nativeQuery = true)
    int decrementUsageIfPositive(@Param("id") Long id);
}
