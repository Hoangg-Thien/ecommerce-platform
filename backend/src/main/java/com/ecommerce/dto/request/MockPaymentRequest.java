package com.ecommerce.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class MockPaymentRequest {
    
    @NotBlank(message = "Scenario must not be blank")
    @Pattern(regexp = "^(SUCCESS|FAIL|PENDING)$", message = "Scenario must be either SUCCESS, FAIL, or PENDING")
    private String scenario;
}
