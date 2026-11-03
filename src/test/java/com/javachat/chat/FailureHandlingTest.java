package com.javachat.chat;

import com.javachat.support.IntegrationTest;
import com.javachat.user.User;
import com.openai.errors.OpenAIIoException;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * When the model fails, the user gets a clean problem response, internal details stay in the log,
 * and nothing half finished is stored. A question is never saved without its answer.
 */
class FailureHandlingTest extends IntegrationTest {

    @Test
    void azureFailureIs503AndNothingIsSaved() throws Exception {
        User user = newUser("azure-down");
        chatModel.failCallsWith(new OpenAIIoException("connection reset"));

        mvc.perform(chat(user))
            .andExpect(status().isServiceUnavailable())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.detail").value("The assistant is unavailable right now. Please try again shortly."));

        assertThat(messagesOf(user)).isEmpty();
    }

    @Test
    void unexpectedFailureIs500WithoutLeakingTheCause() throws Exception {
        User user = newUser("bug");
        chatModel.failCallsWith(new IllegalStateException("db password is hunter2"));

        String body = mvc.perform(chat(user))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.detail").value("Something went wrong."))
            .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("hunter2").doesNotContain("IllegalStateException");
        assertThat(messagesOf(user)).isEmpty();
    }

    @Test
    void summaryFailureInTheBackgroundDoesNotBreakTheReply() throws Exception {
        User user = newUser("summary-fails");
        seedConversation(user, 30);
        chatModel.replyWith(prompt -> {
            if (com.javachat.support.FakeChatModel.systemText(prompt).contains("running summary")) {
                throw new IllegalStateException("summary model down");
            }
            return "still answering";
        });

        mvc.perform(chat(user))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").value("still answering"));

        assertThat(summaryRepository.findById(user.getId())).isEmpty();
        assertThat(messagesOf(user)).hasSize(32);
    }

    private org.springframework.test.web.servlet.RequestBuilder chat(User user) {
        return post("/api/chat").with(loginAs(user)).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"message\":\"hi\"}");
    }
}
