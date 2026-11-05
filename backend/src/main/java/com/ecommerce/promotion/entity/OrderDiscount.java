package com.ecommerce.promotion.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;

@Entity
@Table(name = "order_discounts")
@Getter
@Setter
@NoArgsConstructor
public class OrderDiscount {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "promotion_program_id", nullable = false)
    private PromotionProgram program;
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal minimumAmount;
    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal discountPercent;
    @Column(precision = 15, scale = 2)
    private BigDecimal maximumDiscount;
}
