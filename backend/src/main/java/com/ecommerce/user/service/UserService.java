package com.ecommerce.user.service;

import com.ecommerce.common.exception.EmailAlreadyExistsException;

import com.ecommerce.auth.dto.request.RegisterRequest;
import com.ecommerce.user.dto.response.UserResponse;
import com.ecommerce.cart.entity.Cart;
import com.ecommerce.user.entity.User;
import com.ecommerce.user.enums.Role;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.user.mapper.UserMapper;
import com.ecommerce.cart.repository.CartRepository;
import com.ecommerce.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final CartRepository cartRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;

    @Transactional
    public UserResponse register(RegisterRequest request) {

        log.info("Attempting to register new user with email: {}", request.getEmail());

        if (userRepository.existsByEmail(request.getEmail())) {
            log.warn("Registration rejected: Email '{}' already exists", request.getEmail());
            throw new com.ecommerce.common.exception.EmailAlreadyExistsException("Email is already registered");
        }

        User user = new User();
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(Role.USER);

        User savedUser = userRepository.save(user);
        
        // Khởi tạo ngay giỏ hàng trống cho user để tránh lỗi Race Condition (409 Conflict)
        // khi gọi getCart và addToCart đồng thời ở lần đầu tiên.
        Cart cart = new Cart();
        cart.setUser(savedUser);
        cartRepository.save(cart);
        
        log.info("User registered successfully: id={}, email={}, role={}",
        savedUser.getId(), savedUser.getEmail(), savedUser.getRole());
        return userMapper.toUserRespone(savedUser);
    }

    public UserResponse findById(Long id) {
        return userMapper.toUserRespone(userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id)));
    }

    public User findByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));
    }
}
