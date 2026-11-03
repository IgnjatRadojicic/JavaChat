package com.javachat.user;

public record UserResponse(Long id, String email, String name, String pictureUrl) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getName(), user.getPictureUrl());
    }
}