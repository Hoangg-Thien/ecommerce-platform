package com.ecommerce.category.dto.request;

import com.ecommerce.category.entity.Category;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CategoryRequest {

    @NotBlank(message = "Category name is required")
    private String name;

    private String description;
}
