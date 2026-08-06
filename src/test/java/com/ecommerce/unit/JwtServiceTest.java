package com.ecommerce.unit;

import com.ecommerce.service.JwtService;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

public class JwtServiceTest {
    private JwtService jwtService;
    private UserDetails userDetails;

    // base64 256-bit secret key
    private final String secretKey = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";

    @BeforeEach
    void SetUp(){
        jwtService = new JwtService();

        ReflectionTestUtils.setField(jwtService, "secretKey", secretKey);
        ReflectionTestUtils.setField(jwtService, "jwtExpiration", 1000 * 60 * 60); // 1 hour

        userDetails = new User(
            "user@gmail.com",
            "password123",
            List.of(new SimpleGrantedAuthority("USER"))
        );
    }

    @Test
    void generationToken_ShouldReturnNonEmptyToken() {
        String token = jwtService.generateToken(userDetails);
        String username = jwtService.extractUsername(token);
        assertEquals("user@gmail.com", username);
    }

    @Test 
    void isTokenValid_WhenUserMatches_ShouldReturnTrue() {
        String token = jwtService.generateToken(userDetails);
        boolean isValid = jwtService.isTokenValid(token, userDetails);
        assertTrue(isValid);
    }

    @Test
    void isTokenValid_WhenUserDoesNotMatch_ShouldReturnFalse() {
        String token = jwtService.generateToken(userDetails);
        UserDetails otherUser = new User(
            "other@gmail.com",
            "password123",
            List.of(new SimpleGrantedAuthority("USER"))
        );
        boolean isValid = jwtService.isTokenValid(token, otherUser);
        assertFalse(isValid);
    }
}
