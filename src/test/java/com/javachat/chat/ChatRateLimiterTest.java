package com.javachat.chat;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/** The token bucket on its own, including what happens when many requests arrive at once. */
class ChatRateLimiterTest {

    @Test
    void fiftySimultaneousRequestsFromOneUserLetExactlyTenThrough() throws Exception {
        ChatRateLimiter limiter = new ChatRateLimiter();
        ExecutorService pool = Executors.newFixedThreadPool(16);
        List<Callable<Boolean>> attempts = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            attempts.add(() -> {
                try {
                    limiter.check("same-user");
                    return true;
                } catch (RateLimitExceededException e) {
                    return false;
                }
            });
        }

        int allowed = 0;
        for (Future<Boolean> result : pool.invokeAll(attempts)) {
            if (result.get()) {
                allowed++;
            }
        }
        pool.shutdown();

        assertThat(allowed).isEqualTo(10);
    }

    @Test
    void simultaneousFirstRequestsFromANewUserShareOneBucket() throws Exception {
        ChatRateLimiter limiter = new ChatRateLimiter();
        ExecutorService pool = Executors.newFixedThreadPool(32);
        List<Callable<Boolean>> attempts = new ArrayList<>();
        for (int i = 0; i < 32; i++) {
            attempts.add(() -> {
                try {
                    limiter.check("brand-new-user");
                    return true;
                } catch (RateLimitExceededException e) {
                    return false;
                }
            });
        }

        long allowed = pool.invokeAll(attempts).stream().filter(future -> {
            try {
                return future.get();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }).count();
        pool.shutdown();

        assertThat(allowed).as("a second bucket created in a race would let more than 10 through").isEqualTo(10);
    }

    @Test
    void retryAfterIsAtLeastOneSecondAndNoMoreThanOneRefillInterval() {
        ChatRateLimiter limiter = new ChatRateLimiter();
        for (int i = 0; i < 10; i++) {
            limiter.check("user");
        }

        RateLimitExceededException rejected = catchThrowableOfType(RateLimitExceededException.class,
            () -> limiter.check("user"));

        assertThat(rejected.getRetryAfterSeconds()).isBetween(1L, 6L);
    }

    @Test
    void usersHaveIndependentBuckets() {
        ChatRateLimiter limiter = new ChatRateLimiter();
        for (int i = 0; i < 10; i++) {
            limiter.check("first");
        }

        for (int i = 0; i < 10; i++) {
            limiter.check("second");
        }
    }
}
