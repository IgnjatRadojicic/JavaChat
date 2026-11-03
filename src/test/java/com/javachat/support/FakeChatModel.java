package com.javachat.support;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/**
 * Stands in for Azure OpenAI. It records every prompt it receives, so tests can check exactly
 * what context the app sent, and it can be told what to answer, what to stream, or how to fail.
 */
public class FakeChatModel implements ChatModel {

    public static final String SUMMARY_PROMPT_MARKER = "running summary";

    private final List<Prompt> prompts = new CopyOnWriteArrayList<>();
    private volatile Function<Prompt, String> replies;
    private volatile List<String> streamTokens;
    private volatile RuntimeException callFailure;
    private volatile RuntimeException streamFailureAfterTokens;

    public FakeChatModel() {
        reset();
    }

    public void reset() {
        prompts.clear();
        replies = prompt -> isSummaryPrompt(prompt) ? "A short summary." : "ok";
        streamTokens = List.of("ok");
        callFailure = null;
        streamFailureAfterTokens = null;
    }

    public void replyWith(Function<Prompt, String> replies) {
        this.replies = replies;
    }

    public void streamTokens(String... tokens) {
        this.streamTokens = List.of(tokens);
    }

    public void failCallsWith(RuntimeException failure) {
        this.callFailure = failure;
    }

    public void failStreamAfterTokensWith(RuntimeException failure) {
        this.streamFailureAfterTokens = failure;
    }

    /** Prompts sent for chat replies, leaving out background summary requests. */
    public List<Prompt> chatPrompts() {
        return prompts.stream().filter(prompt -> !isSummaryPrompt(prompt)).toList();
    }

    public List<Prompt> summaryPrompts() {
        return prompts.stream().filter(FakeChatModel::isSummaryPrompt).toList();
    }

    public Prompt lastChatPrompt() {
        List<Prompt> chat = chatPrompts();
        if (chat.isEmpty()) {
            throw new AssertionError("The chat model was never called");
        }
        return chat.getLast();
    }

    public static String systemText(Prompt prompt) {
        return prompt.getInstructions().stream()
            .filter(message -> message.getMessageType() == MessageType.SYSTEM)
            .map(Message::getText)
            .findFirst()
            .orElse("");
    }

    /** The conversation part of a prompt (everything except the system message), in order. */
    public static List<Message> conversation(Prompt prompt) {
        return prompt.getInstructions().stream()
            .filter(message -> message.getMessageType() != MessageType.SYSTEM)
            .toList();
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        prompts.add(prompt);
        if (callFailure != null) {
            throw callFailure;
        }
        return response(replies.apply(prompt));
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        prompts.add(prompt);
        Flux<ChatResponse> tokens = Flux.fromIterable(streamTokens).map(FakeChatModel::response);
        if (streamFailureAfterTokens != null) {
            tokens = tokens.concatWith(Flux.error(streamFailureAfterTokens));
        }
        return tokens;
    }

    private static boolean isSummaryPrompt(Prompt prompt) {
        return systemText(prompt).contains(SUMMARY_PROMPT_MARKER);
    }

    private static ChatResponse response(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }
}
