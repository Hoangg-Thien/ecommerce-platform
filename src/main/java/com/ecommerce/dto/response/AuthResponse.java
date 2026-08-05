package com.ecommerce.dto.response;

import com.ecommerce.enums.Role;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {
    
    private String token;
    private Long id;
    private String email;    
    private Role role;
}
