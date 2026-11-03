package com.javachat.chat;

import com.javachat.support.IntegrationTest;
import com.javachat.user.User;
import com.openai.errors.OpenAIIoException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Streaming must deliver every token exactly as produced, finish with the saved message, and on
 * failure end with an error event while saving nothing.
 */
class StreamingTest extends IntegrationTest {

    @Autowired
    ChatService chatService;

    @Test
    void tokensArriveInOrderThenDoneCarriesTheSavedFullText() {
        User user = newUser("stream");
        chatModel.streamTokens("Spring", " Boot", " is\nnice", "!");

        List<ServerSentEvent<Object>> events = stream(user, "what is spring boot");

        assertThat(events).extracting(ServerSentEvent::event)
            .containsExactly("token", "token", "token", "token", "done");
        assertThat(events.subList(0, 4)).extracting(event -> ((ChatStreamToken) event.data()).text())
            .containsExactly("Spring", " Boot", " is\nnice", "!");

        ChatMessageResponse done = (ChatMessageResponse) events.getLast().data();
        assertThat(done.content()).isEqualTo("Spring Boot is\nnice!");
        assertThat(done.role()).isEqualTo(MessageRole.ASSISTANT);
        assertThat(chatMessageRepository.findById(done.id()))
            .get().extracting(ChatMessage::getContent).isEqualTo("Spring Boot is\nnice!");
        assertThat(messagesOf(user)).extracting(ChatMessage::getContent)
            .containsExactly("what is spring boot", "Spring Boot is\nnice!");
    }

    @Test
    void failureMidStreamEndsWithOneErrorEventAndSavesNothing() {
        User user = newUser("stream-fails");
        chatModel.streamTokens("Half", " an");
        chatModel.failStreamAfterTokensWith(new OpenAIIoException("connection dropped"));

        List<ServerSentEvent<Object>> events = stream(user, "question");

        assertThat(events).extracting(ServerSentEvent::event).containsExactly("token", "token", "error");
        ProblemDetail problem = (ProblemDetail) events.getLast().data();
        assertThat(problem.getStatus()).isEqualTo(503);
        assertThat(messagesOf(user)).isEmpty();
    }

    @Test
    void cancelledStreamSavesNothing() {
        User user = newUser("stream-cancelled");
        chatModel.streamTokens("one", " two", " three");

        chatService.replyStream(user, "question").take(1).collectList().block(Duration.ofSeconds(10));

        assertThat(messagesOf(user)).isEmpty();
    }

    @Test
    void overHttpTokensKeepTheirLeadingSpacesAsJson() throws Exception {
        User user = newUser("stream-http");
        chatModel.streamTokens("Spring", " Boot");

        MvcResult started = mvc.perform(post("/api/chat/stream").with(loginAs(user)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"hi\"}"))
            .andExpect(request().asyncStarted())
            .andReturn();

        MvcResult finished = mvc.perform(asyncDispatch(started))
            .andExpect(status().isOk())
            .andReturn();

        String body = finished.getResponse().getContentAsString();
        assertThat(finished.getResponse().getContentType()).startsWith(MediaType.TEXT_EVENT_STREAM_VALUE);
        assertThat(body)
            .contains("event:token")
            .contains("{\"text\":\" Boot\"}")
            .contains("event:done");
        assertThat(body.indexOf("event:done")).isGreaterThan(body.lastIndexOf("event:token"));
    }

    private List<ServerSentEvent<Object>> stream(User user, String message) {
        return chatService.replyStream(user, message).collectList().block(Duration.ofSeconds(10));
    }
}
