package com.ecommerce.repository;

import java.time.LocalDateTime;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.ecommerce.entity.IdempotencyKey;

@Repository
public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, String>{
    @Modifying
    @Transactional
    @Query("DELETE FROM IdempotencyKey i WHERE i.createdAt < :cutoff")
    int deleteKeysOlderThan(@Param("cutoff") LocalDateTime cutoff);
}
