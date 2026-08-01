package com.ecommerce.controller;

import com.ecommerce.dto.request.LoginRequest;
import com.ecommerce.dto.request.RegisterRequest;
import com.ecommerce.dto.respone.AuthRespone;
import com.ecommerce.dto.respone.UserRespone;
import com.ecommerce.service.JwtService;
import com.ecommerce.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;

    @PostMapping("/register")
    public ResponseEntity<AuthRespone> register(@Valid @RequestBody RegisterRequest request) {
        // Register user and get UserRespone
        UserRespone userRespone = userService.register(request);
        
        // Load UserDetails to generate token
        UserDetails userDetails = userDetailsService.loadUserByUsername(userRespone.getEmail());
        String token = jwtService.generateToken(userDetails);
        
        // Return AuthRespone
        AuthRespone authRespone = new AuthRespone(
                token,
                userRespone.getId(),
                userRespone.getEmail(),
                userRespone.getRoles()
        );
        
        return ResponseEntity.ok(authRespone);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthRespone> login(@Valid @RequestBody LoginRequest request) {
        // Authenticate credentials
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );
        
        // Load UserDetails to generate token
        UserDetails userDetails = userDetailsService.loadUserByUsername(request.getEmail());
        String token = jwtService.generateToken(userDetails);
        
        // Fetch user from DB to get ID and roles
        com.ecommerce.entity.User user = userService.findByEmail(request.getEmail());
        
        // Return AuthRespone
        AuthRespone authRespone = new AuthRespone(
                token,
                user.getId(),
                user.getEmail(),
                user.getRoles()
        );
        
        return ResponseEntity.ok(authRespone);
    }
}
