package com.ecommerce.dto.response;

import com.ecommerce.entity.Category;
import lombok.Getter;

@Getter
public class CategoryResponse {
    private final Long id;
    private final String name;
    private final String description;

    public CategoryResponse(Category category){
        this.id = category.getId();
        this.name = category.getName();
        this.description = category.getDescription();
    }
}
