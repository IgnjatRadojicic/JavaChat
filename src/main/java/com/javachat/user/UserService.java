package com.javachat.user;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public User syncGoogleUser(String googleSub, String email, String name, String pictureUrl) {
        return userRepository.findByGoogleSub(googleSub)
            .map(user -> {
                user.recordLogin(email, name, pictureUrl);
                return user;
            })
            .orElseGet(() -> userRepository.save(new User(googleSub, email, name, pictureUrl)));
    }
}