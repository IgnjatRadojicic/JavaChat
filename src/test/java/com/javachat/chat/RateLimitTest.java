package com.javachat.chat;

import com.javachat.support.IntegrationTest;
import com.javachat.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The HTTP side of rate limiting: 10 messages, then 429 with a usable Retry-After, per user. */
class RateLimitTest extends IntegrationTest {

    @Test
    void eleventhMessageGets429WithRetryAfterAndNeverReachesTheModel() throws Exception {
        User user = newUser("spammer");
        sendMessages(user, 10);
        int modelCallsBefore = chatModel.chatPrompts().size();

        MvcResult rejected = mvc.perform(chat("/api/chat", user))
            .andExpect(status().isTooManyRequests())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andReturn();

        long retryAfter = Long.parseLong(rejected.getResponse().getHeader("Retry-After"));
        assertThat(retryAfter).isBetween(1L, 6L);
        assertThat(rejected.getResponse().getContentAsString()).contains("\"retryAfterSeconds\":" + retryAfter);
        assertThat(chatModel.chatPrompts()).hasSize(modelCallsBefore);
        assertThat(messagesOf(user)).hasSize(20);
    }

    @Test
    void oneUserHittingTheLimitDoesNotAffectAnother() throws Exception {
        User noisy = newUser("noisy");
        User quiet = newUser("quiet");
        sendMessages(noisy, 10);
        mvc.perform(chat("/api/chat", noisy)).andExpect(status().isTooManyRequests());

        mvc.perform(chat("/api/chat", quiet)).andExpect(status().isOk());
    }

    @Test
    void streamingAndPlainChatShareTheSameBucket() throws Exception {
        User user = newUser("both-endpoints");
        sendMessages(user, 10);

        mvc.perform(chat("/api/chat/stream", user))
            .andExpect(status().isTooManyRequests())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    private void sendMessages(User user, int count) throws Exception {
        for (int i = 0; i < count; i++) {
            mvc.perform(chat("/api/chat", user)).andExpect(status().isOk());
        }
    }

    private org.springframework.test.web.servlet.RequestBuilder chat(String path, User user) {
        return post(path).with(loginAs(user)).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"message\":\"hi\"}");
    }
}
