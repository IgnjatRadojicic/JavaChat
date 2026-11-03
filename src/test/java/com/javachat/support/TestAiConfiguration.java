package com.javachat.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;

@TestConfiguration(proxyBeanMethods = false)
public class TestAiConfiguration {

    @Bean
    FakeChatModel fakeChatModel() {
        return new FakeChatModel();
    }

    @Bean
    FakeEmbeddingModel fakeEmbeddingModel() {
        return new FakeEmbeddingModel();
    }

    /**
     * Runs @Async work (the rolling summary) on the calling thread, so tests can assert on its
     * result right after a reply instead of waiting for a background thread.
     */
    @Bean
    TaskExecutor taskExecutor() {
        return new SyncTaskExecutor();
    }
}
