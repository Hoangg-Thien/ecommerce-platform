package com.ecommerce.dto.respone;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthRespone {
    
    private String token;
    private Long id;
    private String email;
    private Set<String> roles;
}
