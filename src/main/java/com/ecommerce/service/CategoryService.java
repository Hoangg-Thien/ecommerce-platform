package com.ecommerce.service;

import com.ecommerce.dto.request.CategoryRequest;
import com.ecommerce.dto.respone.CategoryRespone;
import com.ecommerce.entity.Category;
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

    public List<CategoryRespone> findAll() {
        return categoryRepository.findAll()
                .stream()
                .map(CategoryRespone::new)
                .toList();
    }

    public CategoryRespone findById(Long id) {
        return new CategoryRespone(getCategory(id));
    }

    private Category getCategory(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category", id));
    }

    @Transactional
    public CategoryRespone create(CategoryRequest request) {
        Category category = new Category();
        category.setName(request.getName());
        category.setDescription(request.getDescription());
        category = categoryRepository.save(category);
        return new CategoryRespone(category);
    }

    @Transactional
    public CategoryRespone update(Long id, CategoryRequest request) {
        Category category = getCategory(id);
        category.setName(request.getName());
        category.setDescription(request.getDescription());
        category = categoryRepository.save(category);
        return new CategoryRespone(category);
    }

    @Transactional
    public void delete(Long id) {
        Category category = getCategory(id);
        categoryRepository.delete(category);
    }
}
