package com.javachat.chat;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findTop20ByUserIdOrderByIdDesc(Long userId);

    List<ChatMessage> findTop50ByUserIdOrderByIdDesc(Long userId);

    @Query("""
            select m from ChatMessage m
            where m.user.id = :userId
            and m.id > :afterId
            and m.id < :beforeId
            order by m.id
            """)
    List<ChatMessage> findBetween(Long userId, Long afterId, Long beforeId);

    @Modifying
    @Query("delete from ChatMessage m where m.user.id = :userId")
    int deleteAllByUserId(Long userId);
}