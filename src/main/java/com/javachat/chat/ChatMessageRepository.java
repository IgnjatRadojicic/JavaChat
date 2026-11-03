package com.javachat.chat;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findTop20ByUserIdOrderByIdDesc(Long userId);

    List<ChatMessage> findTop50ByUserIdOrderByIdDesc(Long userId);
}