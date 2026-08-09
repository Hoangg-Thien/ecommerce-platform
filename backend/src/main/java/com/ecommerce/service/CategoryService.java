package com.ecommerce.service;

import com.ecommerce.dto.request.CategoryRequest;
import com.ecommerce.dto.response.CategoryResponse;
import com.ecommerce.entity.Category;
import com.ecommerce.exception.DuplicateResourceException;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.mapper.CategoryMapper;
import com.ecommerce.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final CategoryMapper categoryMapper;

    @Cacheable(value = "categories")
    public List<CategoryResponse> findAll() {
        return categoryRepository.findAll()
                .stream()
                .map(categoryMapper::toCategoryResponse)
                .toList();
    }

    public CategoryResponse findById(Long id) {
        return categoryMapper.toCategoryResponse(getCategory(id));
    }

    private Category getCategory(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category", id));
    }

    @Transactional
    @CacheEvict(value = "categories", allEntries = true) // xoa sach toan bo cache
    public CategoryResponse create(CategoryRequest request) {
        if (categoryRepository.existsByName(request.getName())) {
            log.warn("Category creation failed: Name '{}' already exists", request.getName());
            throw new DuplicateResourceException("Category name already exists");
        }
        Category category = new Category();
        category.setName(request.getName());
        category.setDescription(request.getDescription());
        Category saved = categoryRepository.save(category);
        log.info("Category created successfully: id={}, name='{}'", saved.getId(), saved.getName());
        return categoryMapper.toCategoryResponse(saved);
    }


    @Transactional
    @CacheEvict(value = "categories", allEntries = true) // xoa sach toan bo cache
    public CategoryResponse update(Long id, CategoryRequest request) {
        Category category = getCategory(id);
        
        if (!category.getName().equals(request.getName()) && categoryRepository.existsByName(request.getName())) {
            log.warn("Category update failed: Name '{}' already exists", request.getName());
            throw new DuplicateResourceException("Category name already exists");
        }
        
        category.setName(request.getName());
        category.setDescription(request.getDescription());
        Category saved = categoryRepository.save(category);
        log.info("Category updated successfully: id={}, name='{}'", saved.getId(), saved.getName());
        return categoryMapper.toCategoryResponse(saved);
    }

    @Transactional
    public void delete(Long id) {
        Category category = getCategory(id);
        categoryRepository.delete(category);
        log.info("Category deleted successfully: id={}, name='{}'", id, category.getName());
    }
}
