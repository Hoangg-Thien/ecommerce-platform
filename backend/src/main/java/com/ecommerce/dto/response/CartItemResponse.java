package com.ecommerce.dto.response;

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
    private Integer quantity;
    private BigDecimal subTotal; // price * quantity
}
