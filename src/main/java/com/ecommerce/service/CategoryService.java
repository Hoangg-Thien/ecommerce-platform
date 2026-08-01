package com.ecommerce.service;

import com.ecommerce.dto.request.CategoryRequest;
import com.ecommerce.dto.response.CategoryResponse;
import com.ecommerce.entity.Category;
import com.ecommerce.exception.DuplicateResourceException;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public List<CategoryResponse> findAll() {
        return categoryRepository.findAll()
                .stream()
                .map(CategoryResponse::new)
                .toList();
    }

    public CategoryResponse findById(Long id) {
        return new CategoryResponse(getCategory(id));
    }

    private Category getCategory(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category", id));
    }

    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        if (categoryRepository.existsByName(request.getName())) {
            throw new DuplicateResourceException("Category name already exists");
        }
        Category category = new Category();
        category.setName(request.getName());
        category.setDescription(request.getDescription());
        category = categoryRepository.save(category);
        return new CategoryResponse(category);
    }

    @Transactional
    public CategoryResponse update(Long id, CategoryRequest request) {
        Category category = getCategory(id);
        
        if (!category.getName().equals(request.getName()) && categoryRepository.existsByName(request.getName())) {
            throw new DuplicateResourceException("Category name already exists");
        }
        
        category.setName(request.getName());
        category.setDescription(request.getDescription());
        category = categoryRepository.save(category);
        return new CategoryResponse(category);
    }

    @Transactional
    public void delete(Long id) {
        Category category = getCategory(id);
        categoryRepository.delete(category);
    }
}
