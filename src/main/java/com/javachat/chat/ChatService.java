package com.javachat.chat;

import com.javachat.user.User;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;
import com.javachat.memory.MemoryService;
import java.util.stream.Collectors;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import com.javachat.memory.MemoryTools;
import java.util.Map;

@Service
public class ChatService {

    private static final String SYSTEM_PROMPT = """
            Your name is JavaChat. You are a friendly, helpful assistant created by Ignjat Radojicic.
            When someone asks who or what you are, introduce yourself as JavaChat.
            Do not call yourself ChatGPT.
            If someone asks which AI model powers you, say that you run on an OpenAI GPT model through Azure.
            When the user shares lasting facts about themselves, save them with the saveMemory tool.
            Keep your answers clear and concise.
            """;

    private static final int HISTORY_CHAR_BUDGET = 8_000;

    private final ChatClient chatClient;
    private final ChatMessageRepository chatMessageRepository;
    private final MemoryTools memoryTools;
    private final MemoryService memoryService;


    public ChatService(ChatClient.Builder chatClientBuilder, ChatMessageRepository chatMessageRepository, MemoryTools memoryTools, MemoryService memoryService) {
        this.chatClient = chatClientBuilder.build();
        this.chatMessageRepository = chatMessageRepository;
        this.memoryTools = memoryTools;
        this.memoryService = memoryService;
    }

    public String reply(User user, String userMessage) {
        List<Message> history = loadHistory(user.getId());
        List<String> memories = memoryService.recall(user.getId(), userMessage);

        String reply = chatClient.prompt()
            .system(buildSystemPrompt(memories))
            .messages(history)
            .user(userMessage)
            .tools(memoryTools)
            .toolContext(Map.of(MemoryTools.USER_ID_CONTEXT_KEY, user.getId()))
            .call()
            .content();

        chatMessageRepository.saveAll(List.of(
            new ChatMessage(user, MessageRole.USER, userMessage),
            new ChatMessage(user, MessageRole.ASSISTANT, reply)));

        return reply;
    }

    public List<ChatMessage> getHistory(Long userId) {
        List<ChatMessage> messages = new ArrayList<>(chatMessageRepository.findTop50ByUserIdOrderByIdDesc(userId));
        Collections.reverse(messages);
        return messages;
    }

    private List<Message> loadHistory(Long userId) {
        List<Message> history = new ArrayList<>();
        int usedChars = 0;

        for (ChatMessage message : chatMessageRepository.findTop20ByUserIdOrderByIdDesc(userId)) {
            usedChars += message.getContent().length();
            if (usedChars > HISTORY_CHAR_BUDGET) {
                break;
            }
            history.add(toAiMessage(message));
        }

        Collections.reverse(history);
        return history;
    }
    private String buildSystemPrompt(List<String> memories) {
        if (memories.isEmpty()) {
            return SYSTEM_PROMPT;
        }

        String facts = memories.stream()
            .map(memory -> "- " + memory)
            .collect(Collectors.joining("\n"));

        return SYSTEM_PROMPT + """

                Known facts about the user from earlier conversations:
                %s
                Use these facts when they are relevant. Do not save them again.
                """.formatted(facts);
    }

    private Message toAiMessage(ChatMessage message) {
        return switch (message.getRole()) {
            case USER -> new UserMessage(message.getContent());
            case ASSISTANT -> new AssistantMessage(message.getContent());
        };
    }
}