package com.ecommerce.order.entity;

import com.ecommerce.product.entity.Product;

import java.math.BigDecimal;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "order_items")
@Getter
@Setter
@NoArgsConstructor
public class OrderItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    // kich thuoc chon tai thoi diem dat hang
    private String size;

    // so luong mua
    @NotNull(message = "Quantity is required")
    @Min(value = 1, message = "Quantity must be at least 1")
    @Column(nullable = false)
    private Integer quantity;

    // gia tai thoi diem dat hang
    @NotNull(message = "Price is required")
    @Column(nullable = false)
    private BigDecimal price;

    @Column(nullable = false, precision = 38, scale = 2)
    private BigDecimal originalPrice;

    @Column(precision = 5, scale = 2)
    private BigDecimal productDiscountPercent;

    @Column(name = "product_discount_program_id")
    private Long productDiscountProgramId;

    @PrePersist
    private void initializeLegacySnapshot() {
        if (originalPrice == null) {
            originalPrice = price;
        }
    }
}
