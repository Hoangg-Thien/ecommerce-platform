package com.ecommerce.product.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.util.List;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class ProductResponse {
    private Long id;
    private String name;
    private BigDecimal price;
    private BigDecimal salePrice;
    private BigDecimal discountPercent;
    private java.time.Instant saleEndsAt;
    private Integer stock; // Computed from variants
    private String imageUrl;
    private String description;
    private Long categoryId;
    private String categoryName;
    private List<VariantResponse> variants;
}
