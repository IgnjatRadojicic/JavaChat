package com.javachat.chat;

import com.javachat.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "chat_messages")
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private MessageRole role;

    @Column(nullable = false, updatable = false)
    private String content;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ChatMessage() {
        // required by JPA
    }

    public ChatMessage(User user, MessageRole role, String content) {
        this.user = user;
        this.role = role;
        this.content = content;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public MessageRole getRole() { return role; }
    public String getContent() { return content; }
    public Instant getCreatedAt() { return createdAt; }
}