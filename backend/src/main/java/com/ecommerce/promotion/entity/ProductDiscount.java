package com.ecommerce.promotion.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import com.ecommerce.product.entity.Product;

@Entity
@Table(name = "product_discounts")
@Getter
@Setter
@NoArgsConstructor
public class ProductDiscount {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "promotion_program_id", nullable = false)
    private PromotionProgram program;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;
    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal discountPercent;
}
