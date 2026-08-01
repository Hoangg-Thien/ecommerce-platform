package com.ecommerce.dto.respone;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AuthRespone {
    
    private String token;
    private Long id;
    private String email;
    private Set<String> roles;
}
