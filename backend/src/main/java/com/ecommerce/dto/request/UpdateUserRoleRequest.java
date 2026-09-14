package com.ecommerce.dto.request;

import com.ecommerce.enums.Role;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateUserRoleRequest {
    @NotNull(message = "Role không được để trống")
    private Role role;
}
