package com.ecommerce.product.service;

import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.product.dto.response.ProductResponse;
import com.ecommerce.product.mapper.ProductMapper;
import com.ecommerce.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Cache only base catalog data. Live promotion fields are composed by ProductService. */
@Service
@RequiredArgsConstructor
public class ProductBaseCache {
    private final ProductRepository products;
    private final ProductMapper mapper;

    @Cacheable(value = "product", key = "#id")
    @Transactional(readOnly = true)
    public ProductResponse findById(Long id) {
        return mapper.toProductResponse(products.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id)));
    }
}
