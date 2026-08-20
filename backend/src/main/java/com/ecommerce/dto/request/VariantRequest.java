package com.ecommerce.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class VariantRequest {

    @NotBlank(message = "Size is required")
    @Pattern(regexp = "^(39|40|41|42|43|44|45|46)$", message = "Size must be between 39 and 46")
    private String size;

    @NotNull(message = "Stock is required")
    @Min(value = 0, message = "Stock must be >= 0")
    private Integer stock = 0;
}
