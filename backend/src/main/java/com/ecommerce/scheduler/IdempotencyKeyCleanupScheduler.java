package com.ecommerce.scheduler;

import java.time.LocalDateTime;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.ecommerce.repository.IdempotencyKeyRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class IdempotencyKeyCleanupScheduler {
    
    private final IdempotencyKeyRepository idempotencyKeyRepository;

    @Scheduled(cron = "0 0 3 * * ?")
    public void cleanupKeys(){
        log.info("Start the job clean up stale idempotency keys...");
        // giu lai trong 24h va xoa cai cu hon
        LocalDateTime cutoffTime = LocalDateTime.now().minusHours(24);
        int deletedCount = idempotencyKeyRepository.deleteKeysOlderThan(cutoffTime);
        log.info("Cleanup complete. Removed {} stale idempotency keys from database", deletedCount);
    }
}
