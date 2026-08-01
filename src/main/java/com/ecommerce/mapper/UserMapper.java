package com.ecommerce.mapper;

import com.ecommerce.dto.response.UserResponse;
import com.ecommerce.entity.User;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    public UserResponse toUserRespone(User user) {
        if (user == null) {
            return null;
        }

        return UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .roles(user.getRoles())
                .build();
    }
}
