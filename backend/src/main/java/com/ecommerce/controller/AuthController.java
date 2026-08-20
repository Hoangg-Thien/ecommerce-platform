package com.ecommerce.controller;

import com.ecommerce.dto.request.LoginRequest;
import com.ecommerce.dto.request.RegisterRequest;
import com.ecommerce.dto.response.AuthResponse;
import com.ecommerce.dto.response.UserResponse;
import com.ecommerce.entity.User;
import com.ecommerce.exception.InvalidTokenException;
import com.ecommerce.service.JwtService;
import com.ecommerce.service.RefreshTokenService;
import com.ecommerce.service.UserService;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.bind.annotation.CookieValue;
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
    private final RefreshTokenService refreshTokenService;

    @org.springframework.beans.factory.annotation.Value("${application.security.jwt.refresh-token.expiration:604800000}")
    private long refreshExpiration;

    @Value("${application.security.cookie.secure:false}")
    private boolean cookieSecure;

    @Value("${application.security.cookie.same-site:Lax}")
    private String cookieSameSite;

     private void setRefreshTokenCookie(HttpServletResponse response, String refreshToken) {
        ResponseCookie cookie = ResponseCookie.from("refreshToken", refreshToken)
        .httpOnly(true)
        .secure(cookieSecure)
        .sameSite(cookieSameSite)
        .path("/api/v1/auth/refresh")
        .maxAge(7 * 24 * 60 * 60)
        .build();
        
        response.addHeader(org.springframework.http.HttpHeaders.SET_COOKIE, cookie.toString());
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid 
        @RequestBody RegisterRequest request,
        HttpServletResponse response) {
        // Register user and get UserRespone
        UserResponse userRespone = userService.register(request);
        
        // Load UserDetails to generate token
        UserDetails userDetails = userDetailsService.loadUserByUsername(userRespone.getEmail());

        String accessToken = jwtService.generateToken(userDetails);
        String refreshToken = jwtService.generateRefreshToken(userDetails);

        User user = userService.findByEmail(userRespone.getEmail());
        refreshTokenService.createRefreshToken(user, refreshToken, refreshExpiration);

        // set cookie
        setRefreshTokenCookie(response, refreshToken);
        
        // Return AuthRespone
        AuthResponse authRespone = AuthResponse.builder()
        .accessToken(accessToken)
        .id(userRespone.getId())
        .email(userRespone.getEmail())
        .role(userRespone.getRole())
        .build();
        
        return ResponseEntity.status(HttpStatus.CREATED).body(authRespone);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid
        @RequestBody LoginRequest request,
        HttpServletResponse response) {
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
        String refreshToken = jwtService.generateRefreshToken(userDetails);

        setRefreshTokenCookie(response, refreshToken);
        
        // Fetch user from DB to get ID and roles
        User user = userService.findByEmail(request.getEmail());

        refreshTokenService.createRefreshToken(user, refreshToken, refreshExpiration);
        
        // Return AuthRespone
        AuthResponse authRespone = AuthResponse.builder()
        .accessToken(accessToken)
        .id(user.getId())
        .email(user.getEmail())
        .role(user.getRole())
        .build();
        
        return ResponseEntity.ok(authRespone);
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refreshToken(
        @CookieValue(name = "refreshToken", required = false)
        String refreshToken,
        HttpServletResponse response
        ){

        if(refreshToken == null){
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        refreshTokenService.verifyNotRevoked(refreshToken);

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

        // rotation: revoke the old refresh token currently in use
        refreshTokenService.revokeToken(refreshToken);

        // create new access token and refresh token
        String newAccessToken = jwtService.generateToken(userDetails);
        String newRefreshToken = jwtService.generateRefreshToken(userDetails);

        // save new refresh token in database
        User user = userService.findByEmail(userEmail);
        refreshTokenService.createRefreshToken(user, newRefreshToken, refreshExpiration);

        // set a new cookie to return to the client
        setRefreshTokenCookie(response, newRefreshToken);

        AuthResponse  authResponse = AuthResponse.builder()
        .accessToken(newAccessToken)
        .id(user.getId())
        .email(user.getEmail())
        .role(user.getRole())
        .build();

        return ResponseEntity.ok(authResponse);
    }

    // ham phu tro de xoa cookie
    private void cleanRefreshTokenCookies(HttpServletResponse response, String refreshToken){
        ResponseCookie cookie = ResponseCookie.from("refreshToken", "")
        .httpOnly(true)
        .secure(cookieSecure)
        .sameSite(cookieSameSite)
        .path("/api/v1/auth/refresh")
        .maxAge(0)
        .build();
        
        response.addHeader(org.springframework.http.HttpHeaders.SET_COOKIE, cookie.toString());
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(
        @CookieValue(name = "refreshToken", required = false) String refreshToken,
        HttpServletResponse response
    ){
        // revoke token trong db neu trinh duyen gui cookie len
        if(refreshToken != null){
            try {
                refreshTokenService.revokeToken(refreshToken);
            } catch (Exception e) {
            }
        }

        // phan hoi va bat trinh duyet xoa cookie
        cleanRefreshTokenCookies(response, refreshToken);

        return ResponseEntity.ok().build();
    }
}
