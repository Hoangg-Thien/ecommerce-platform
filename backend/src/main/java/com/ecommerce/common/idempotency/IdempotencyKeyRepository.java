package com.ecommerce.common.idempotency;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import com.ecommerce.order.entity.Order;

@Repository
public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, String>{
    @Modifying
    @Transactional(propagation = Propagation.MANDATORY)
    @Query(value = "INSERT INTO idempotency_keys(id,created_at) VALUES (:key,:createdAt) ON CONFLICT (id) DO NOTHING", nativeQuery = true)
    int insertIfAbsent(@Param("key") String key, @Param("createdAt") LocalDateTime createdAt);

    @Modifying(flushAutomatically = true)
    @Transactional(propagation = Propagation.MANDATORY)
    @Query("UPDATE IdempotencyKey k SET k.orderId = :orderId WHERE k.id = :key AND k.orderId IS NULL")
    int linkOrder(@Param("key") String key, @Param("orderId") Long orderId);

    @Query("""
        SELECT o FROM Order o, IdempotencyKey k
        WHERE k.id = :key AND k.orderId = o.id AND o.user.email = :email
        """)
    Optional<Order> findResultForOwner(@Param("key") String key, @Param("email") String email);
    @Modifying
    @Transactional
    @Query("DELETE FROM IdempotencyKey i WHERE i.createdAt < :cutoff")
    int deleteKeysOlderThan(@Param("cutoff") LocalDateTime cutoff);
}
