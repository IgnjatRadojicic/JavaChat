package com.javachat.chat;

import com.javachat.support.FakeChatModel;
import com.javachat.support.IntegrationTest;
import com.javachat.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What the model is given. The window keeps the newest messages within 20 messages and 8,000
 * characters, in reading order, and nothing from another user ever ends up in someone's prompt.
 */
class ConversationContextTest extends IntegrationTest {

    @Autowired
    ChatService chatService;

    @Test
    void firstMessageOfANewUserHasNoHistoryNoSummaryAndNoFacts() {
        User user = newUser("fresh");

        chatService.reply(user, "hello");

        Prompt prompt = chatModel.lastChatPrompt();
        assertThat(texts(prompt)).containsExactly("hello");
        assertThat(FakeChatModel.systemText(prompt))
            .contains("Your name is JavaChat")
            .doesNotContain("Summary of the earlier conversation")
            .doesNotContain("Known facts");
    }

    @Test
    void historyIsSentOldestFirstWithTheNewMessageLast() {
        User user = newUser("order");
        seedConversation(user, 4);

        chatService.reply(user, "newest question");

        Prompt prompt = chatModel.lastChatPrompt();
        assertThat(texts(prompt)).containsExactly(
            "message 001", "message 002", "message 003", "message 004", "newest question");
        assertThat(FakeChatModel.conversation(prompt))
            .extracting(Message::getMessageType)
            .containsExactly(MessageType.USER, MessageType.ASSISTANT, MessageType.USER, MessageType.ASSISTANT, MessageType.USER);
    }

    @Test
    void onlyTheNewestTwentyMessagesAreSent() {
        User user = newUser("cap");
        seedConversation(user, 30);

        chatService.reply(user, "question");

        List<String> texts = texts(chatModel.lastChatPrompt());
        assertThat(texts).hasSize(21);
        assertThat(texts.getFirst()).isEqualTo("message 011");
        assertThat(texts).doesNotContain("message 010");
    }

    @Test
    void messagesFillingTheBudgetExactlyAreAllKept() {
        User user = newUser("budget-exact");
        seedMessage(user, MessageRole.USER, "x");
        seedMessage(user, MessageRole.ASSISTANT, "a".repeat(4000));
        seedMessage(user, MessageRole.USER, "b".repeat(4000));

        chatService.reply(user, "question");

        assertThat(texts(chatModel.lastChatPrompt()))
            .containsExactly("a".repeat(4000), "b".repeat(4000), "question");
    }

    @Test
    void oneCharacterOverTheBudgetDropsTheOlderMessage() {
        User user = newUser("budget-over");
        seedMessage(user, MessageRole.ASSISTANT, "a".repeat(4001));
        seedMessage(user, MessageRole.USER, "b".repeat(4000));

        chatService.reply(user, "question");

        assertThat(texts(chatModel.lastChatPrompt())).containsExactly("b".repeat(4000), "question");
    }

    @Test
    void anotherUsersMessagesNeverEnterTheWindow() {
        User me = newUser("me");
        User other = newUser("other");
        seedMessage(other, MessageRole.USER, "the other user's secret");
        seedMessage(me, MessageRole.USER, "my own earlier message");

        chatService.reply(me, "question");

        assertThat(texts(chatModel.lastChatPrompt()))
            .containsExactly("my own earlier message", "question");
    }

    @Test
    void onlyTheUsersOwnMemoriesAreRecalledEvenWhenAnotherUsersMatchBetter() {
        User me = newUser("memory-me");
        User other = newUser("memory-other");
        // Mine shares one word with the question, theirs shares almost all of it, so theirs is the better match.
        memoryService.remember(me.getId(), "Ignjat enjoys hiking near the lake");
        memoryService.remember(other.getId(), "Bob loves hiking in the mountains every weekend");

        chatService.reply(me, "where should I go hiking in the mountains this weekend");

        assertThat(FakeChatModel.systemText(chatModel.lastChatPrompt()))
            .contains("Ignjat enjoys hiking near the lake")
            .doesNotContain("Bob");
    }

    @Test
    void existingSummaryIsGivenToTheModel() {
        User user = newUser("summary");
        summaryRepository.save(new ConversationSummary(user.getId(), "Earlier they discussed Flyway naming.", 0L));

        chatService.reply(user, "question");

        assertThat(FakeChatModel.systemText(chatModel.lastChatPrompt()))
            .contains("Summary of the earlier conversation")
            .contains("Earlier they discussed Flyway naming.");
    }

    private static List<String> texts(Prompt prompt) {
        return FakeChatModel.conversation(prompt).stream().map(Message::getText).toList();
    }
}
