package com.javachat.chat;

import java.time.Instant;

public record ChatMessageResponse(MessageRole role, String content, Instant createdAt) {

    public static ChatMessageResponse from(ChatMessage message) {
        return new ChatMessageResponse(message.getRole(), message.getContent(), message.getCreatedAt());
    }
}