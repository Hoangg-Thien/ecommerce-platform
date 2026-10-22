package com.ecommerce.controller;

import com.ecommerce.dto.request.ProductRequest;
import com.ecommerce.dto.response.ProductResponse;
import com.ecommerce.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import com.ecommerce.dto.response.PageResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.ecommerce.util.SearchTextUtils;

import java.util.Set;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("id", "name", "price");

    private final ProductService productService;

    // GET /api/v1/products?page=0&size=10&sortBy=id&sortDir=asc&categoryId=1&q=keyword
    @GetMapping
    public ResponseEntity<PageResponse<ProductResponse>> getAll(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {

        if (page < 0) page = 0;
        if (size <= 0) size = 10; else if (size > 50) size = 50;

        String keyword = q == null ? "" : q.trim();
        if (keyword.length() > SearchTextUtils.MAX_KEYWORD_LENGTH)
            throw new IllegalArgumentException("Từ khóa tối đa 100 ký tự");
        if (!keyword.isEmpty() && SearchTextUtils.normalize(keyword).length() < SearchTextUtils.MIN_KEYWORD_LENGTH)
            throw new IllegalArgumentException("Từ khóa tối thiểu 2 ký tự");

        return ResponseEntity.ok(productService.findAll(categoryId, keyword, buildPageable(page, size, sortBy, sortDir, !keyword.isEmpty())));
    }

    private Pageable buildPageable(int page, int size, String sortBy, String sortDir, boolean hasKeyword) {
        boolean wantsRelevance = sortBy == null || sortBy.isBlank() || "relevance".equalsIgnoreCase(sortBy);
        if (wantsRelevance && hasKeyword) {
            return PageRequest.of(page, size);                 // KHÔNG Sort: để Specification tự orderBy theo độ liên quan
        }
        String field = wantsRelevance ? "id" : sortBy;
        if (!ALLOWED_SORT_FIELDS.contains(field))
            throw new IllegalArgumentException("sortBy không hợp lệ. Cho phép: id, name, price, relevance");
        Sort sort = "desc".equalsIgnoreCase(sortDir) ? Sort.by(field).descending() : Sort.by(field).ascending();
        return PageRequest.of(page, size, sort);
    }

    // GET /api/products/{id}
    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(productService.findById(id));
    }

    // POST /api/products
    @PostMapping
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody ProductRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productService.create(request));
    }

    // PUT /api/products/{id}
    @PutMapping("/{id}")
    public ResponseEntity<ProductResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody ProductRequest request) {
        return ResponseEntity.ok(productService.update(id, request));
    }

    // DELETE /api/products/{id}
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        productService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
