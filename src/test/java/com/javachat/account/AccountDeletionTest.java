package com.javachat.account;

import com.javachat.chat.ConversationSummary;
import com.javachat.support.IntegrationTest;
import com.javachat.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Deleting an account removes the user and everything tied to them, including memories that have
 * no foreign key, while leaving every other user untouched. The session ends with it.
 */
class AccountDeletionTest extends IntegrationTest {

    @Test
    void deletesUserMessagesSummaryAndMemoriesButNothingOfOtherUsers() throws Exception {
        User leaving = newUser("leaving");
        User staying = newUser("staying");
        for (User user : new User[] {leaving, staying}) {
            seedConversation(user, 4);
            summaryRepository.save(new ConversationSummary(user.getId(), "summary", 1L));
            memoryService.remember(user.getId(), "a fact about " + user.getName());
        }
        MockHttpSession session = new MockHttpSession();

        mvc.perform(delete("/api/me").with(loginAs(leaving)).with(csrf()).session(session))
            .andExpect(status().isNoContent());

        assertThat(userRepository.findById(leaving.getId())).isEmpty();
        assertThat(messagesOf(leaving)).isEmpty();
        assertThat(summaryRepository.findById(leaving.getId())).isEmpty();
        assertThat(memoryCount(leaving)).isZero();
        assertThat(session.isInvalid()).as("the session must end with the account").isTrue();

        assertThat(userRepository.findById(staying.getId())).isPresent();
        assertThat(messagesOf(staying)).hasSize(4);
        assertThat(summaryRepository.findById(staying.getId())).isPresent();
        assertThat(memoryCount(staying)).isEqualTo(1);
    }

    @Test
    void withoutCsrfTokenNothingIsDeleted() throws Exception {
        User user = newUser("protected");
        seedConversation(user, 2);
        memoryService.remember(user.getId(), "a fact");

        mvc.perform(delete("/api/me").with(loginAs(user)))
            .andExpect(status().isForbidden());

        assertThat(userRepository.findById(user.getId())).isPresent();
        assertThat(messagesOf(user)).hasSize(2);
        assertThat(memoryCount(user)).isEqualTo(1);
    }
}
