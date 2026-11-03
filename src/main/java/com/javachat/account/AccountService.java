package com.javachat.account;

import com.javachat.memory.MemoryService;
import com.javachat.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {

    private final UserRepository userRepository;
    private final MemoryService memoryService;

    public AccountService(UserRepository userRepository, MemoryService memoryService) {
        this.userRepository = userRepository;
        this.memoryService = memoryService;
    }

    @Transactional
    public void deleteAccount(Long userId) {
        memoryService.forgetAll(userId);
        userRepository.deleteById(userId);
    }
}
