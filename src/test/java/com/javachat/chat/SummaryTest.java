package com.javachat.chat;

import com.javachat.support.FakeChatModel;
import com.javachat.support.IntegrationTest;
import com.javachat.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Messages that fall out of the window are folded into a summary in batches of at least ten.
 * The marker must move to exactly the last summarised message, so nothing is summarised twice
 * and nothing is skipped.
 */
class SummaryTest extends IntegrationTest {

    @Autowired
    ChatService chatService;

    @Test
    void nineMessagesOutsideTheWindowAreNotEnoughToSummarise() {
        User user = newUser("nine");
        seedConversation(user, 29);

        chatService.reply(user, "question");

        assertThat(chatModel.summaryPrompts()).isEmpty();
        assertThat(summaryRepository.findById(user.getId())).isEmpty();
    }

    @Test
    void exactlyTenMessagesOutsideTheWindowAreSummarisedAndTheMarkerMovesToTheLastOne() {
        User user = newUser("ten");
        List<ChatMessage> seeded = seedConversation(user, 30);

        chatService.reply(user, "question");

        assertThat(chatModel.summaryPrompts()).hasSize(1);
        String summaryInput = userText(chatModel.summaryPrompts().getFirst());
        assertThat(summaryInput)
            .contains("(none yet)")
            .contains("message 001")
            .contains("message 010")
            .doesNotContain("message 011");

        ConversationSummary summary = summaryRepository.findById(user.getId()).orElseThrow();
        assertThat(summary.getSummarizedUpToMessageId()).isEqualTo(seeded.get(9).getId());
        assertThat(summary.getSummary()).isEqualTo("A short summary.");
    }

    @Test
    void nextRoundBuildsOnThePreviousSummaryAndNeverResendsSummarisedMessages() {
        User user = newUser("two-rounds");
        seedConversation(user, 30);
        chatService.reply(user, "first question");
        chatModel.reset();
        chatModel.replyWith(prompt -> FakeChatModel.systemText(prompt).contains(FakeChatModel.SUMMARY_PROMPT_MARKER)
            ? "A longer summary." : "ok");

        for (int i = 0; i < 10; i++) {
            seedMessage(user, i % 2 == 0 ? MessageRole.USER : MessageRole.ASSISTANT, String.format("later %02d", i));
        }
        chatService.reply(user, "second question");

        assertThat(chatModel.summaryPrompts()).hasSize(1);
        String summaryInput = userText(chatModel.summaryPrompts().getFirst());
        assertThat(summaryInput)
            .contains("A short summary.")
            .contains("message 011")
            .doesNotContain("message 010");
        assertThat(summaryRepository.findById(user.getId())).get()
            .extracting(ConversationSummary::getSummary).isEqualTo("A longer summary.");
    }

    @Test
    void conversationThatFitsTheWindowIsNeverSummarised() {
        User user = newUser("short");
        seedConversation(user, 6);

        chatService.reply(user, "question");

        assertThat(chatModel.summaryPrompts()).isEmpty();
    }

    private static String userText(Prompt prompt) {
        return FakeChatModel.conversation(prompt).stream()
            .map(org.springframework.ai.chat.messages.Message::getText)
            .reduce("", String::concat);
    }
}
