package com.ecommerce.auth.integration;

import com.ecommerce.auth.service.JwtService;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import jakarta.servlet.http.Cookie;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:tc:postgresql:17.9:///refresh_rotation", "rate-limit.enabled=false"})
@AutoConfigureMockMvc
class RefreshRotationIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;
    @Autowired JwtService jwt;

    @Test void rapidRotationPersistsUniqueTokensAndStillRejectsReusedCookie() throws Exception {
        jdbc.update("INSERT INTO users(email,password,role) VALUES ('rotation@test.local',?,'USER')", passwords.encode("Rotation123!"));
        var response = mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                .content("{\"email\":\"rotation@test.local\",\"password\":\"Rotation123!\"}"))
                .andExpect(status().isOk()).andReturn().getResponse();
        Cookie original = response.getCookie("refreshToken");
        Cookie current = original;
        var ids = new java.util.HashSet<String>();
        ids.add(jwt.extractClaim(current.getValue(), Claims::getId));
        for (int i = 0; i < 10; i++) {
            response = mvc.perform(post("/api/v1/auth/refresh").cookie(current))
                    .andExpect(status().isOk()).andReturn().getResponse();
            current = response.getCookie("refreshToken");
            assertThat(ids.add(jwt.extractClaim(current.getValue(), Claims::getId))).isTrue();
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM refresh_tokens WHERE revoked=false", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(DISTINCT token_hash) FROM refresh_tokens", Integer.class)).isEqualTo(11);
        mvc.perform(post("/api/v1/auth/refresh").cookie(original)).andExpect(status().isUnauthorized());
        // Existing reuse detection remains enabled: the successor is revoked too.
        mvc.perform(post("/api/v1/auth/refresh").cookie(current)).andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM refresh_tokens WHERE revoked=false", Integer.class)).isZero();
    }
}
