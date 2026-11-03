package com.javachat.chat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "conversation_summaries")
public class ConversationSummary {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false)
    private String summary;

    @Column(name = "summarized_up_to_message_id", nullable = false)
    private Long summarizedUpToMessageId;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ConversationSummary() {
        // required by JPA
    }

    public ConversationSummary(Long userId, String summary, Long summarizedUpToMessageId) {
        this.userId = userId;
        this.summary = summary;
        this.summarizedUpToMessageId = summarizedUpToMessageId;
        this.updatedAt = Instant.now();
    }

    public void update(String summary, Long summarizedUpToMessageId) {
        this.summary = summary;
        this.summarizedUpToMessageId = summarizedUpToMessageId;
        this.updatedAt = Instant.now();
    }

    public Long getUserId() { return userId; }
    public String getSummary() { return summary; }
    public Long getSummarizedUpToMessageId() { return summarizedUpToMessageId; }
    public Instant getUpdatedAt() { return updatedAt; }
}