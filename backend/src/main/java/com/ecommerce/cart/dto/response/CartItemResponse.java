package com.ecommerce.cart.dto.response;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartItemResponse {
    private Long id;
    private Long productId;
    private Long variantId;
    private String productName;
    private String imageUrl;
    private String size;
    private BigDecimal productPrice;
    private BigDecimal unitPrice;
    private BigDecimal productDiscountPercent;
    private java.time.Instant saleEndsAt;
    private BigDecimal originalSubTotal;
    private Integer quantity;
    private BigDecimal subTotal; // unitPrice * quantity after product discount
}
