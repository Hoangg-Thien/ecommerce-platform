package com.ecommerce.dto;

import com.ecommerce.entity.Product;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
public class ProductResponse {
    private final Long id;
    private final String name;
    private final BigDecimal price;
    private final Integer stock;
    private final String description;
    private final Long categoryId;
    private final String categoryName;

    public ProductResponse(Product product) {
        this.id = product.getId();
        this.name = product.getName();
        this.price = product.getPrice();
        this.stock = product.getStock();
        this.description = product.getDescription();
        this.categoryId = product.getCategory() != null ? product.getCategory().getId() : null;
        this.categoryName = product.getCategory() != null ? product.getCategory().getName() : null;
    }
}
