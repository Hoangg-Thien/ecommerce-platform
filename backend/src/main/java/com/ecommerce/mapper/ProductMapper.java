package com.ecommerce.mapper;

import com.ecommerce.dto.response.ProductResponse;
import com.ecommerce.dto.response.VariantResponse;
import com.ecommerce.entity.Product;
import com.ecommerce.entity.ProductVariant;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class ProductMapper {

    public ProductResponse toProductResponse(Product product) {
        if (product == null) {
            return null;
        }

        int totalStock = 0;
        List<VariantResponse> variantResponses = null;

        if (product.getVariants() != null) {
            variantResponses = product.getVariants().stream()
                    .map(v -> new VariantResponse(v.getId(), v.getSize(), v.getStock()))
                    .collect(Collectors.toList());
            
            totalStock = product.getVariants().stream()
                    .mapToInt(ProductVariant::getStock)
                    .sum();
        }

        return ProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .price(product.getPrice())
                .stock(totalStock)
                .imageUrl(product.getImageUrl())
                .description(product.getDescription())
                .categoryId(product.getCategory() != null ? product.getCategory().getId() : null)
                .categoryName(product.getCategory() != null ? product.getCategory().getName() : null)
                .variants(variantResponses)
                .build();
    }
}
