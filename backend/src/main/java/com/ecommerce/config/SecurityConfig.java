package com.ecommerce.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(auth -> auth
                // Public auth endpoints
                .requestMatchers("/api/v1/auth/**").permitAll()
                
                // Public GET endpoints for categories and products
                .requestMatchers(HttpMethod.GET, "/api/v1/categories/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/products/**").permitAll()
                
                // Admin only endpoints for modifying categories and products
                .requestMatchers(HttpMethod.POST, "/api/v1/categories/**").hasAuthority("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/v1/categories/**").hasAuthority("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/categories/**").hasAuthority("ADMIN")
                
                .requestMatchers(HttpMethod.POST, "/api/v1/products/**").hasAuthority("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/v1/products/**").hasAuthority("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/products/**").hasAuthority("ADMIN")

                .requestMatchers(HttpMethod.PATCH, "/api/v1/orders/*/status").hasAuthority("ADMIN")
                
                // MoMo IPN 
                .requestMatchers(HttpMethod.POST, "/api/v1/payments/momo/ipn").permitAll()

                // MoMo Return
                .requestMatchers(HttpMethod.GET, "/api/v1/payments/momo/return").permitAll()

                // Any other request requires authentication
                .anyRequest().authenticated()
            )
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
            
        return http.build();
    }
}
