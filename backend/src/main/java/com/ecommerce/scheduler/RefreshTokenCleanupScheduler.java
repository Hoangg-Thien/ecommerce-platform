package com.ecommerce.scheduler;

import java.time.Instant;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.ecommerce.repository.RefreshTokenRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class RefreshTokenCleanupScheduler {

    private final RefreshTokenRepository refreshTokenRepository;

    // cron "0 0 2 * * ?" chay vao 2h sang moi ngay
    @Scheduled(cron = "0 0 2 * * ?")
    public void cleanUpTokens() {
        log.info("Start the job clean up stale refresh tokens...");
        int deletedCount = refreshTokenRepository.deleteExpiredOrRevokedTokens(Instant.now());
        log.info("Cleanup complete. Removed {} expired/revoked refresh token from database", deletedCount);
    }
}
