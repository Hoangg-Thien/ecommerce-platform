package com.ecommerce.controller;

import com.ecommerce.dto.request.LoginRequest;
import com.ecommerce.dto.request.RefeshTokenRequest;
import com.ecommerce.dto.request.RegisterRequest;
import com.ecommerce.dto.response.AuthResponse;
import com.ecommerce.dto.response.UserResponse;
import com.ecommerce.exception.InvalidTokenException;
import com.ecommerce.service.JwtService;
import com.ecommerce.service.UserService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        // Register user and get UserRespone
        UserResponse userRespone = userService.register(request);
        
        // Load UserDetails to generate token
        UserDetails userDetails = userDetailsService.loadUserByUsername(userRespone.getEmail());

        String accessToken = jwtService.generateToken(userDetails);
        String refreshToken = jwtService.generateRefeshToken(userDetails);
        
        // Return AuthRespone
        AuthResponse authRespone = AuthResponse.builder()
        .accessToken(accessToken)
        .refeshToken(refreshToken)
        .id(userRespone.getId())
        .email(userRespone.getEmail())
        .role(userRespone.getRole())
        .build();
        
        return ResponseEntity.status(HttpStatus.CREATED).body(authRespone);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        // Authenticate credentials
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );
        
        // Load UserDetails to generate token
        UserDetails userDetails = userDetailsService.loadUserByUsername(request.getEmail());
        String accessToken = jwtService.generateToken(userDetails);
        String refreshToken = jwtService.generateRefeshToken(userDetails);
        
        // Fetch user from DB to get ID and roles
        com.ecommerce.entity.User user = userService.findByEmail(request.getEmail());
        
        // Return AuthRespone
        AuthResponse authRespone = AuthResponse.builder()
        .accessToken(accessToken)
        .refeshToken(refreshToken)
        .id(user.getId())
        .email(user.getEmail())
        .role(user.getRole())
        .build();
        
        return ResponseEntity.ok(authRespone);
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refeshToken(@Valid @RequestBody RefeshTokenRequest request){
        String refreshToken = request.getRefeshToken();

        // Extract email from refresh token
        String userEmail;
        try{
            userEmail = jwtService.extractUsername(refreshToken);
        }catch (Exception e) {
            throw new InvalidTokenException("Invalid refresh token format or signature");
        }

        if(userEmail == null) {
            throw new InvalidTokenException("Invalid refresh token: email not found");
        }

        // Load the user from the database and check if the token is valid
        UserDetails userDetails = userDetailsService.loadUserByUsername(userEmail);
        if(!jwtService.isTokenValid(refreshToken, userDetails)){
                throw new InvalidTokenException("Refresh token is expired or invalid");
        }

        // Create a new access token
        String newAccessToken = jwtService.generateToken(userDetails);
        com.ecommerce.entity.User user = userService.findByEmail(userEmail);

        AuthResponse authResponse = AuthResponse.builder()
        .accessToken(newAccessToken)
        .refeshToken(refreshToken)
        .id(user.getId())
        .email(user.getEmail())
        .role(user.getRole())
        .build();

        return ResponseEntity.ok(authResponse);
    }
}
