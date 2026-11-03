package com.javachat.chat;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class ChatService {

    private static final String SYSTEM_PROMPT = """
            Your name is JavaChat. You are a friendly, helpful assistant created by Ignjat Radojicic.
            When someone asks who or what you are, introduce yourself as JavaChat.
            Do not call yourself ChatGPT.
            If someone asks which AI model powers you, say that you run on an OpenAI GPT model through Azure.
            Keep your answers clear and concise.
            """;
    private final ChatClient chatClient;    

    public ChatService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder
            .defaultSystem(SYSTEM_PROMPT)
            .build();     
    }    

    public String reply(String userMessage) {
        return chatClient.prompt()
            .user(userMessage)
            .call()
            .content();
    }
}