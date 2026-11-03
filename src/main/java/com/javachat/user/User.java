package com.javachat.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "google_sub", nullable = false, unique = true, updatable = false)
    private String googleSub;

    @Column(nullable = false)
    private String email;

    private String name;

    @Column(name = "picture_url")
    private String pictureUrl;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_login_at", nullable = false)
    private Instant lastLoginAt;

    protected User() {
        // required by JPA
    }

    public User(String googleSub, String email, String name, String pictureUrl) {
        this.googleSub = googleSub;
        this.email = email;
        this.name = name;
        this.pictureUrl = pictureUrl;
        this.createdAt = Instant.now();
        this.lastLoginAt = this.createdAt;
    }

    public void recordLogin(String email, String name, String pictureUrl) {
        this.email = email;
        this.name = name;
        this.pictureUrl = pictureUrl;
        this.lastLoginAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getGoogleSub() { return googleSub; }
    public String getEmail() { return email; }
    public String getName() { return name; }
    public String getPictureUrl() { return pictureUrl; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastLoginAt() { return lastLoginAt; }
}