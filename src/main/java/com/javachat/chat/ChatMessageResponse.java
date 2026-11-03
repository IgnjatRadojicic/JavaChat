package com.javachat.chat;

import java.time.Instant;

public record ChatMessageResponse(Long id, MessageRole role, String content, Instant createdAt) {

    public static ChatMessageResponse from(ChatMessage message) {
        return new ChatMessageResponse(message.getId(), message.getRole(), message.getContent(), message.getCreatedAt());
    }
}
