package com.javachat.chat;

import com.jayway.jsonpath.JsonPath;
import com.javachat.support.IntegrationTest;
import com.javachat.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cursor pagination must walk the whole history without gaps or duplicates and never cross into
 * another user's messages. Clearing a conversation removes messages and summary but keeps memories.
 */
class HistoryTest extends IntegrationTest {

    @Autowired
    ChatService chatService;

    @Test
    void pagingWithTheCursorVisitsEveryMessageExactlyOnce() throws Exception {
        User user = newUser("pager");
        seedConversation(user, 120);

        List<Long> page1 = ids(history(user, null));
        List<Long> page2 = ids(history(user, page1.getFirst()));
        List<Long> page3 = ids(history(user, page2.getFirst()));
        List<Long> page4 = ids(history(user, page3.getFirst()));

        assertThat(page1).hasSize(50).isSorted();
        assertThat(page2).hasSize(50).isSorted().allMatch(id -> id < page1.getFirst());
        assertThat(page3).hasSize(20).isSorted();
        assertThat(page4).isEmpty();

        List<Long> all = new ArrayList<>();
        all.addAll(page3);
        all.addAll(page2);
        all.addAll(page1);
        assertThat(all).hasSize(120).isSorted();
        assertThat(new HashSet<>(all)).hasSize(120);
    }

    @Test
    void cursorPointingIntoAnotherUsersRangeStillReturnsOnlyYourMessages() throws Exception {
        User me = newUser("cursor-me");
        User other = newUser("cursor-other");
        seedConversation(me, 3);
        List<ChatMessage> theirs = seedConversation(other, 3);

        String body = history(me, theirs.getLast().getId() + 1);

        List<String> contents = JsonPath.read(body, "$[*].content");
        assertThat(contents).containsExactly("message 001", "message 002", "message 003");
    }

    @Test
    void nonNumericCursorIsA400Problem() throws Exception {
        User user = newUser("bad-cursor");

        mvc.perform(get("/api/chat/history").param("before", "abc").with(loginAs(user)))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void clearingRemovesMessagesAndSummaryButKeepsMemoriesAndOtherUsers() throws Exception {
        User me = newUser("clear-me");
        User other = newUser("clear-other");
        seedConversation(me, 4);
        seedConversation(other, 4);
        summaryRepository.save(new ConversationSummary(me.getId(), "old summary", 1L));
        memoryService.remember(me.getId(), "Ignjat loves hiking");

        mvc.perform(delete("/api/chat/history").with(loginAs(me)).with(csrf()))
            .andExpect(status().isNoContent());

        assertThat(messagesOf(me)).isEmpty();
        assertThat(summaryRepository.findById(me.getId())).isEmpty();
        assertThat(memoryCount(me)).isEqualTo(1);
        assertThat(messagesOf(other)).hasSize(4);
    }

    @Test
    void clearingAnEmptyConversationIsStill204() throws Exception {
        User user = newUser("clear-empty");

        mvc.perform(delete("/api/chat/history").with(loginAs(user)).with(csrf()))
            .andExpect(status().isNoContent());
        mvc.perform(delete("/api/chat/history").with(loginAs(user)).with(csrf()))
            .andExpect(status().isNoContent());
    }

    @Test
    void afterClearingTheNextReplyStartsWithAnEmptyWindow() throws Exception {
        User user = newUser("clear-then-chat");
        seedConversation(user, 6);
        mvc.perform(delete("/api/chat/history").with(loginAs(user)).with(csrf()))
            .andExpect(status().isNoContent());

        chatService.reply(user, "fresh start");

        assertThat(com.javachat.support.FakeChatModel.conversation(chatModel.lastChatPrompt()))
            .extracting(org.springframework.ai.chat.messages.Message::getText)
            .containsExactly("fresh start");
    }

    private String history(User user, Long before) throws Exception {
        var request = get("/api/chat/history").with(loginAs(user));
        if (before != null) {
            request = request.param("before", before.toString());
        }
        return mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private static List<Long> ids(String body) {
        List<Number> raw = JsonPath.read(body, "$[*].id");
        return raw.stream().map(Number::longValue).toList();
    }
}
