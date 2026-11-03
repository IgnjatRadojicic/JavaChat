package com.javachat.support;

import com.javachat.chat.ChatMessage;
import com.javachat.chat.ChatMessageRepository;
import com.javachat.chat.ConversationSummaryRepository;
import com.javachat.chat.MessageRole;
import com.javachat.memory.MemoryService;
import com.javachat.user.User;
import com.javachat.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;

/**
 * Base for tests that run the whole app against a real database. Every test creates its own
 * users with random Google subs, so tests never share data or rate limit buckets.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, TestAiConfiguration.class})
public abstract class IntegrationTest {

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected FakeChatModel chatModel;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected ChatMessageRepository chatMessageRepository;

    @Autowired
    protected ConversationSummaryRepository summaryRepository;

    @Autowired
    protected MemoryService memoryService;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetFakeModel() {
        chatModel.reset();
    }

    protected User newUser(String name) {
        String sub = name + "-" + UUID.randomUUID();
        return userRepository.save(new User(sub, name + "@example.com", name, null));
    }

    protected RequestPostProcessor loginAs(User user) {
        return oidcLogin().idToken(token -> token.subject(user.getGoogleSub()));
    }

    /** Saves messages alternating USER and ASSISTANT, oldest first, and returns them in order. */
    protected List<ChatMessage> seedConversation(User user, int count) {
        List<ChatMessage> messages = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            MessageRole role = i % 2 == 1 ? MessageRole.USER : MessageRole.ASSISTANT;
            messages.add(new ChatMessage(user, role, String.format("message %03d", i)));
        }
        return chatMessageRepository.saveAll(messages);
    }

    protected ChatMessage seedMessage(User user, MessageRole role, String content) {
        return chatMessageRepository.save(new ChatMessage(user, role, content));
    }

    protected List<ChatMessage> messagesOf(User user) {
        return chatMessageRepository.findAll().stream()
            .filter(message -> message.getUser().getId().equals(user.getId()))
            .toList();
    }

    protected int memoryCount(User user) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM vector_store WHERE (metadata ->> 'userId')::bigint = ?", Integer.class, user.getId());
        return count == null ? 0 : count;
    }
}
