package com.ecommerce.promotion.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.util.Locale;
import com.ecommerce.promotion.enums.PromotionStatus;

@Entity
@Table(name = "vouchers")
@Getter
@Setter
@NoArgsConstructor
public class Voucher {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "promotion_program_id", nullable = false)
    private PromotionProgram program;
    @Column(nullable = false, length = 50, unique = true)
    private String code;
    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal discountPercent;
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal minimumAmount = BigDecimal.ZERO;
    @Column(precision = 15, scale = 2)
    private BigDecimal maximumDiscount;
    @Column(nullable = false)
    private Integer quantity;
    // Updated only by conditional repository queries, never by a stale entity save.
    @Column(nullable = false, insertable = false, updatable = false)
    private Integer usedQuantity = 0;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PromotionStatus status = PromotionStatus.ACTIVE;

    public void setCode(String code) {
        this.code = code == null ? null : code.trim().toUpperCase(Locale.ROOT);
    }
}
