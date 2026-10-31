package com.ecommerce.product.service;

import com.ecommerce.product.dto.request.ProductRequest;
import com.ecommerce.product.dto.request.VariantRequest;
import com.ecommerce.product.dto.response.ProductResponse;
import com.ecommerce.product.dto.response.VariantResponse;
import com.ecommerce.promotion.service.ProductSalePricingService;
import com.ecommerce.promotion.repository.ProductDiscountRepository;
import com.ecommerce.category.entity.Category;
import com.ecommerce.product.entity.Product;
import com.ecommerce.product.entity.ProductVariant;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.product.mapper.ProductMapper;
import com.ecommerce.category.repository.CategoryRepository;
import com.ecommerce.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.ecommerce.common.dto.PageResponse;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ecommerce.product.search.ProductSpecifications;
import org.springframework.data.jpa.domain.Specification;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.List;
import java.math.BigDecimal;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductMapper productMapper;
    private final ProductBaseCache baseCache;
    private final ProductSalePricingService salePricing;
    private final ProductDiscountRepository productDiscounts;

    @Transactional(readOnly = true)   // để lazy-load variants không phụ thuộc open-in-view
    public PageResponse<ProductResponse> findAll(Long categoryId, String keyword, Pageable pageable) {
        Specification<Product> spec = ProductSpecifications.hasCategory(categoryId)
                .and(ProductSpecifications.matchesKeyword(keyword));
        Page<Product> page = productRepository.findAll(spec, pageable);
        var response = PageResponse.of(page.map(productMapper::toProductResponse));
        response.setContent(withSalePrices(response.getContent()));
        return response;
    }

    public ProductResponse findById(Long id) {
        return withSalePrices(List.of(baseCache.findById(id))).get(0);
    }

    @Transactional
    public ProductResponse create(ProductRequest request) {
        Product product = new Product();
        mapRequestToProduct(request, product);
        Product saved = productRepository.save(product);
        log.info("Product created successfully: id={}, name='{}', price={}", saved.getId(), saved.getName(), saved.getPrice());
        return withSalePrices(List.of(productMapper.toProductResponse(saved))).get(0);
    }

    @Transactional
    @CacheEvict(value = "product", key = "#id")
    public ProductResponse update(Long id, ProductRequest request) {
        Product saved = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));
        mapRequestToProduct(request, saved);
        log.info("Product updated successfully: id={}, name='{}'", saved.getId(), saved.getName());
        return withSalePrices(List.of(productMapper.toProductResponse(productRepository.save(saved)))).get(0);
    }

    @Transactional
    @CacheEvict(value = "product", key = "#id")
    public void delete(Long id) {
        Product deleted = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));
        productDiscounts.deleteByProductId(id);
        productRepository.delete(deleted);
        log.info("Product deleted successfully: id={}, name='{}'", id, deleted.getName());
    }

    private List<ProductResponse> withSalePrices(List<ProductResponse> products) {
        var prices = products.stream().collect(Collectors.toMap(ProductResponse::getId, ProductResponse::getPrice));
        var sales = salePricing.price(prices);
        return products.stream().map(base -> {
            var sale = sales.get(base.getId());
            // Copy nested mutable DTOs too: callers must never modify cached catalog values.
            var variants = base.getVariants() == null ? null : base.getVariants().stream()
                    .map(v -> new VariantResponse(v.getId(), v.getSize(), v.getStock())).toList();
            return base.toBuilder().variants(variants)
                    .salePrice(sale == null ? base.getPrice() : sale.unitPrice())
                    .discountPercent(sale == null ? BigDecimal.ZERO : sale.percent())
                    .saleEndsAt(sale == null ? null : sale.endsAt()).build();
        }).toList();
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
