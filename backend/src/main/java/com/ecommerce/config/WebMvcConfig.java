package com.ecommerce.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Autowired(required = false)
    private IdempotencyInterceptor interceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Đăng ký idempotency interceptor cho tất cả /api/** endpoints
        if (interceptor != null) {
            registry.addInterceptor(interceptor).addPathPatterns("/api/**");
        }
    }
}
