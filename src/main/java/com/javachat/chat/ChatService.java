package com.javachat.chat;

import com.javachat.memory.MemoryService;
import com.javachat.memory.MemoryTools;
import com.javachat.user.User;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
    private final SummaryService summaryService;

    public ChatService(ChatClient.Builder chatClientBuilder,
                       ChatMessageRepository chatMessageRepository,
                       MemoryTools memoryTools,
                       MemoryService memoryService,
                       SummaryService summaryService) {
        this.chatClient = chatClientBuilder.build();
        this.chatMessageRepository = chatMessageRepository;
        this.memoryTools = memoryTools;
        this.memoryService = memoryService;
        this.summaryService = summaryService;
    }

    private record PreparedChat(List<ChatMessage> window, ChatClient.ChatClientRequestSpec request) {}

    public ChatMessage reply(User user, String userMessage) {
        PreparedChat prepared = prepare(user, userMessage);
        String reply = prepared.request().call().content();
        return saveExchange(user, userMessage, reply, prepared.window());
    }

    public List<ChatMessage> getHistory(Long userId, Long beforeId) {
        List<ChatMessage> newestFirst = beforeId == null
            ? chatMessageRepository.findTop50ByUserIdOrderByIdDesc(userId)
            : chatMessageRepository.findTop50ByUserIdAndIdLessThanOrderByIdDesc(userId, beforeId);

        List<ChatMessage> messages = new ArrayList<>(newestFirst);
        Collections.reverse(messages);
        return messages;
    }

    @Transactional
    public void clearHistory(Long userId) {
        chatMessageRepository.deleteAllByUserId(userId);
        summaryService.clear(userId);
    }

    private PreparedChat prepare(User user, String userMessage) {
        List<ChatMessage> window = loadWindow(user.getId());
        List<String> memories = memoryService.recall(user.getId(), userMessage);
        Optional<String> summary = summaryService.findSummary(user.getId());

        ChatClient.ChatClientRequestSpec request = chatClient.prompt()
            .system(buildSystemPrompt(summary, memories))
            .messages(window.stream().map(this::toAiMessage).toList())
            .user(userMessage)
            .tools(memoryTools)
            .toolContext(Map.of(MemoryTools.USER_ID_CONTEXT_KEY, user.getId()));

        return new PreparedChat(window, request);
    }

    private ChatMessage saveExchange(User user, String userMessage, String reply, List<ChatMessage> window) {
        List<ChatMessage> saved = chatMessageRepository.saveAll(List.of(
            new ChatMessage(user, MessageRole.USER, userMessage),
            new ChatMessage(user, MessageRole.ASSISTANT, reply)));

        if (!window.isEmpty()) {
            summaryService.updateIfNeeded(user.getId(), window.getFirst().getId());
        }

        return saved.getLast();
    }

    private List<ChatMessage> loadWindow(Long userId) {
        List<ChatMessage> window = new ArrayList<>();
        int usedChars = 0;

        for (ChatMessage message : chatMessageRepository.findTop20ByUserIdOrderByIdDesc(userId)) {
            usedChars += message.getContent().length();
            if (usedChars > HISTORY_CHAR_BUDGET) {
                break;
            }
            window.add(message);
        }

        Collections.reverse(window);
        return window;
    }

    private String buildSystemPrompt(Optional<String> summary, List<String> memories) {
        StringBuilder prompt = new StringBuilder(SYSTEM_PROMPT);

        summary.ifPresent(text -> prompt
            .append("\nSummary of the earlier conversation:\n")
            .append(text)
            .append('\n'));

        if (!memories.isEmpty()) {
            prompt.append("\nKnown facts about the user from earlier conversations:\n");
            memories.forEach(memory -> prompt.append("- ").append(memory).append('\n'));
            prompt.append("Use these facts when they are relevant. Do not save them again.\n");
        }

        return prompt.toString();
    }

    private Message toAiMessage(ChatMessage message) {
        return switch (message.getRole()) {
            case USER -> new UserMessage(message.getContent());
            case ASSISTANT -> new AssistantMessage(message.getContent());
        };
    }
}