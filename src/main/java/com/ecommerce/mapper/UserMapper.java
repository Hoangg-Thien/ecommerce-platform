package com.ecommerce.mapper;

import com.ecommerce.dto.respone.UserRespone;
import com.ecommerce.entity.User;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    public UserRespone toUserRespone(User user) {
        if (user == null) {
            return null;
        }

        return UserRespone.builder()
                .id(user.getId())
                .email(user.getEmail())
                .roles(user.getRoles())
                .build();
    }
}
