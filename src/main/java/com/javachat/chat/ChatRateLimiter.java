package com.javachat.chat;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ChatRateLimiter {

    private static final int MESSAGES_PER_MINUTE = 10;
    private static final int MESSAGES_PER_DAY = 200;

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public void check(String userKey) {
        Bucket bucket = buckets.computeIfAbsent(userKey, key -> newBucket());
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        if (!probe.isConsumed()) {
            // Round up, so an exact 6 second wait says 6 and not 7, and a 5.2 second wait says 6.
            long nanos = probe.getNanosToWaitForRefill();
            long retryAfterSeconds = Math.max(1, (nanos + 999_999_999) / 1_000_000_000);
            throw new RateLimitExceededException(retryAfterSeconds);
        }
    }

    private Bucket newBucket() {
        return Bucket.builder()
            .addLimit(limit -> limit.capacity(MESSAGES_PER_MINUTE).refillGreedy(MESSAGES_PER_MINUTE, Duration.ofMinutes(1)))
            .addLimit(limit -> limit.capacity(MESSAGES_PER_DAY).refillGreedy(MESSAGES_PER_DAY, Duration.ofDays(1)))
            .build();
    }
}
