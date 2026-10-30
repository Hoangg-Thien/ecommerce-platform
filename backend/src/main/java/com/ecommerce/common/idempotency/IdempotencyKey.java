package com.ecommerce.common.idempotency;

import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "idempotency_keys")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class IdempotencyKey {

    @Id
    private String id;

    private LocalDateTime createdAt;

    private Long orderId;

    public IdempotencyKey(String id, LocalDateTime createdAt) {
        this.id = id;
        this.createdAt = createdAt;
    }
}
