package com.ecommerce.config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import lombok.Getter;

@Configuration
@Getter
public class MomoConfig {
    @Value("${momo.partner-code}")
    private String partnerCode;

    @Value("${momo.access-key}")
    private String accessKey;
    
    @Value("${momo.secret-key}")
    private String secretKey;

    @Value("${momo.api-url}")
    private String apiUrl;

    @Value("${momo.redirect-url}")
    private String redirectUrl;
    
    @Value("${momo.ipn-url}")
    private String ipnUrl;
    
    // RestTemplate để gọi HTTP đến MoMo API
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
