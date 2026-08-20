package com.ecommerce.service;

import com.ecommerce.dto.request.ProductRequest;
import com.ecommerce.dto.request.VariantRequest;
import com.ecommerce.dto.response.ProductResponse;
import com.ecommerce.entity.Category;
import com.ecommerce.entity.Product;
import com.ecommerce.entity.ProductVariant;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.mapper.ProductMapper;
import com.ecommerce.repository.CategoryRepository;
import com.ecommerce.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.ecommerce.dto.response.PageResponse;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductMapper productMapper;

    public PageResponse<ProductResponse> findAll(Long categoryId, Pageable pageable) {
        Page<Product> productPage = categoryId != null
                ? productRepository.findByCategoryId(categoryId, pageable)
                : productRepository.findAll(pageable);
        return PageResponse.of(productPage.map(productMapper::toProductResponse));
    }

    @Cacheable(value = "product", key = "#id")
    public ProductResponse findById(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));
        return productMapper.toProductResponse(product);
    }

    @Transactional
    public ProductResponse create(ProductRequest request) {
        Product product = new Product();
        mapRequestToProduct(request, product);
        Product saved = productRepository.save(product);
        log.info("Product created successfully: id={}, name='{}', price={}", saved.getId(), saved.getName(), saved.getPrice());
        return productMapper.toProductResponse(saved);
    }

    @Transactional
    @CacheEvict(value = "product", key = "#id")
    public ProductResponse update(Long id, ProductRequest request) {
        Product saved = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));
        mapRequestToProduct(request, saved);
        log.info("Product updated successfully: id={}, name='{}'", saved.getId(), saved.getName());
        return productMapper.toProductResponse(productRepository.save(saved));
    }

    @Transactional
    @CacheEvict(value = "product", key = "#id")
    public void delete(Long id) {
        Product deleted = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));
        productRepository.delete(deleted);
        log.info("Product deleted successfully: id={}, name='{}'", id, deleted.getName());
    }

    private void mapRequestToProduct(ProductRequest request, Product product) {
        product.setName(request.getName());
        product.setPrice(request.getPrice());
        product.setImageUrl(request.getImageUrl());
        product.setDescription(request.getDescription());

        if (request.getCategoryId() != null) {
            Category category = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("Category", request.getCategoryId()));
            product.setCategory(category);
        } else {
            product.setCategory(null);
        }

        if (request.getVariants() != null) {
            // Check for duplicate sizes in request
            Set<String> seenSizes = new HashSet<>();
            for (VariantRequest v : request.getVariants()) {
                if (!seenSizes.add(v.getSize())) {
                    throw new IllegalArgumentException("Duplicate size found in request: " + v.getSize());
                }
            }

            // Update existing or add new
            for (VariantRequest variantReq : request.getVariants()) {
                Optional<ProductVariant> existingVariantOpt = product.getVariants().stream()
                        .filter(v -> v.getSize().equals(variantReq.getSize()))
                        .findFirst();

                if (existingVariantOpt.isPresent()) {
                    ProductVariant existingVariant = existingVariantOpt.get();
                    existingVariant.setStock(variantReq.getStock());
                } else {
                    ProductVariant newVariant = new ProductVariant();
                    newVariant.setProduct(product);
                    newVariant.setSize(variantReq.getSize());
                    newVariant.setStock(variantReq.getStock());
                    product.getVariants().add(newVariant);
                }
            }

            // Set stock to 0 for variants that are not in the request to prevent deletion constraint failures
            product.getVariants().stream()
                    .filter(v -> request.getVariants().stream().noneMatch(reqV -> reqV.getSize().equals(v.getSize())))
                    .forEach(v -> v.setStock(0));
        }
    }
}
