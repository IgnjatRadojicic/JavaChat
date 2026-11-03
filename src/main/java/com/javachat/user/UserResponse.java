package com.javachat.user;

public record UserResponse(String email, String name, String pictureUrl) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getEmail(), user.getName(), user.getPictureUrl());
    }
}
