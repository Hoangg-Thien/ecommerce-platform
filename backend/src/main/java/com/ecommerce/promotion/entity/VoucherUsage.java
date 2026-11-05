package com.ecommerce.promotion.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.Instant;
import com.ecommerce.order.entity.Order;
import com.ecommerce.user.entity.User;
import com.ecommerce.promotion.enums.VoucherUsageStatus;

@Entity
@Table(name = "voucher_usages")
@Getter
@Setter
@NoArgsConstructor
public class VoucherUsage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "voucher_id", nullable = false, updatable = false)
    private Voucher voucher;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false, unique = true, updatable = false)
    private Order order;
    @Column(nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal discountAmount;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VoucherUsageStatus status = VoucherUsageStatus.APPLIED;
    // The lifecycle service supplies this from the application's existing Clock.
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
    private Instant releasedAt;
}
