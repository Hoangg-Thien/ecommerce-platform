package com.ecommerce.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;

import org.springframework.stereotype.Service;

import com.ecommerce.entity.RefreshToken;
import com.ecommerce.entity.User;
import com.ecommerce.exception.InvalidTokenException;
import com.ecommerce.repository.RefreshTokenRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;

    // bam chuoi
    private String hashToken(String rawToken){

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte [] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Could not hash token", e);
        }
    }

    // ghi refresh token vao db 
    public RefreshToken createRefreshToken(User user, String rawToken, long expirationMs){

        RefreshToken rt = new RefreshToken();
        rt.setUser(user);
        rt.setTokenHash(hashToken(rawToken));
        rt.setExpiryDate(Instant.now().plusMillis(expirationMs)); // + thoi gian tai voi thoi gian song
        return refreshTokenRepository.save(rt);
    }

    // huy token khi user logout
    public void revokeToken(String rawToken){

        String hash = hashToken(rawToken);
        refreshTokenRepository.findByTokenHash(hash).ifPresent(rt -> {
            rt.setRevoked(true);
            refreshTokenRepository.save(rt);
            log.info("Revoked refresh token for user id {}", rt.getUser().getId());
        });
    }

    // kiem tra token co hop le khi user goi api refresh
    public void verifyNotRevoked(String rawToken){

        String hash = hashToken(rawToken);
        RefreshToken rt = refreshTokenRepository.findByTokenHash(hash)
        .orElseThrow(() -> new InvalidTokenException("Refresh token does not exist in database"));

        if(rt.isRevoked()){
            // reuse detection: neu token truyen len bi huy -> bao dong
            revokeAllUserToken(rt.getUser().getId());
            throw new InvalidTokenException("Detected reuse of a revoked refresh token. Please login again");
        }
    }

    public void revokeAllUserToken(Long userId){
        refreshTokenRepository.revokeAllUserTokens(userId);
        log.warn("Revoked all refresh tokens for user id {} due to reuse detection!", userId);
    }
}
