package com.ecommerce.dto.respone;

import com.ecommerce.entity.Category;
import lombok.Getter;

@Getter
public class CategoryRespone {
    private final Long id;
    private final String name;
    private final String description;

    public CategoryRespone(Category category){
        this.id = category.getId();
        this.name = category.getName();
        this.description = category.getDescription();
    }
}
