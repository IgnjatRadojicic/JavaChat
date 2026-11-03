package com.javachat.chat;

import com.javachat.support.IntegrationTest;
import com.javachat.user.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Message limits from the spec: not blank, at most 2000 characters. Invalid input must be rejected
 * before it costs anything, so it never reaches the model and never uses up rate limit tokens.
 */
class ChatValidationTest extends IntegrationTest {

    @ParameterizedTest
    @ValueSource(strings = {"{\"message\":\"\"}", "{\"message\":\"   \"}", "{\"message\":\"\\n\\t\"}", "{\"message\":null}", "{}"})
    void blankOrMissingMessageIsRejectedWithFieldError(String body) throws Exception {
        User user = newUser("blank");

        mvc.perform(chat(user, body))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.errors.message").exists());

        assertThat(chatModel.chatPrompts()).isEmpty();
        assertThat(messagesOf(user)).isEmpty();
    }

    @Test
    void messageOfExactly2000CharactersIsAccepted() throws Exception {
        User user = newUser("limit");

        mvc.perform(chat(user, json("a".repeat(2000)))).andExpect(status().isOk());

        assertThat(messagesOf(user)).extracting(ChatMessage::getContent).contains("a".repeat(2000));
    }

    @Test
    void messageOf2001CharactersIsRejected() throws Exception {
        User user = newUser("over");

        mvc.perform(chat(user, json("a".repeat(2001))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.message").exists());

        assertThat(chatModel.chatPrompts()).isEmpty();
    }

    @Test
    void malformedJsonIsA400ProblemNotA500() throws Exception {
        User user = newUser("malformed");

        mvc.perform(chat(user, "{\"message\": "))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void streamingEndpointValidatesBeforeStreaming() throws Exception {
        User user = newUser("stream-blank");

        mvc.perform(post("/api/chat/stream").with(loginAs(user)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        assertThat(chatModel.chatPrompts()).isEmpty();
    }

    @Test
    void invalidRequestsDoNotUseUpTheRateLimit() throws Exception {
        User user = newUser("invalid-then-valid");

        for (int i = 0; i < 15; i++) {
            mvc.perform(chat(user, json(""))).andExpect(status().isBadRequest());
        }
        for (int i = 0; i < 10; i++) {
            mvc.perform(chat(user, json("hello " + i))).andExpect(status().isOk());
        }
    }

    private org.springframework.test.web.servlet.RequestBuilder chat(User user, String body) {
        return post("/api/chat").with(loginAs(user)).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(body);
    }

    private static String json(String message) {
        return "{\"message\":\"" + message + "\"}";
    }
}
