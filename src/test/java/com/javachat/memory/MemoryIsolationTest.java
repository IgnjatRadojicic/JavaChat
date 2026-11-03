package com.javachat.memory;

import com.jayway.jsonpath.JsonPath;
import com.javachat.support.IntegrationTest;
import com.javachat.user.User;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The wall between users. Search, listing and deletion must only ever touch the signed in user's
 * memories, and asking about someone else's memory must look exactly like asking about one that
 * does not exist.
 */
class MemoryIsolationTest extends IntegrationTest {

    @Test
    void identicalMemoriesOfTwoUsersNeverMixInSearch() {
        User me = newUser("twin-me");
        User other = newUser("twin-other");
        memoryService.remember(me.getId(), "Loves hiking in the Alps");
        memoryService.remember(other.getId(), "Loves hiking in the Alps");

        List<String> recalled = memoryService.recall(me.getId(), "hiking in the Alps");

        assertThat(recalled).containsExactly("Loves hiking in the Alps");
    }

    @Test
    void userWithoutMemoriesRecallsNothingEvenWhenOthersMatchPerfectly() {
        User empty = newUser("no-memories");
        User other = newUser("has-memories");
        memoryService.remember(other.getId(), "Prefers Kotlin over Java");

        assertThat(memoryService.recall(empty.getId(), "Prefers Kotlin over Java")).isEmpty();
    }

    @Test
    void searchingByMeaningFindsTheRelevantMemoryFirst() {
        User user = newUser("ranking");
        memoryService.remember(user.getId(), "Has a cat named Luna");
        memoryService.remember(user.getId(), "Is preparing for a Levi9 interview");

        assertThat(memoryService.recall(user.getId(), "interview preparation for Levi9").getFirst())
            .isEqualTo("Is preparing for a Levi9 interview");
    }

    @Test
    void listingShowsOnlyYourOwnMemories() throws Exception {
        User me = newUser("list-me");
        User other = newUser("list-other");
        memoryService.remember(me.getId(), "mine");
        memoryService.remember(other.getId(), "theirs");

        String body = mvc.perform(get("/api/memories").with(loginAs(me)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        List<String> contents = JsonPath.read(body, "$[*].content");
        assertThat(contents).containsExactly("mine");
    }

    @Test
    void deletingSomeoneElsesMemoryIs404AndTheMemorySurvives() throws Exception {
        User me = newUser("delete-me");
        User other = newUser("delete-other");
        memoryService.remember(other.getId(), "their secret");
        UUID theirMemory = memoryService.listMemories(other.getId()).getFirst().id();

        mvc.perform(delete("/api/memories/{id}", theirMemory).with(loginAs(me)).with(csrf()))
            .andExpect(status().isNotFound());

        assertThat(memoryCount(other)).isEqualTo(1);
    }

    @Test
    void deletingYourOwnMemoryWorksOnceThenIs404() throws Exception {
        User user = newUser("delete-own");
        memoryService.remember(user.getId(), "temporary fact");
        UUID id = memoryService.listMemories(user.getId()).getFirst().id();

        mvc.perform(delete("/api/memories/{id}", id).with(loginAs(user)).with(csrf()))
            .andExpect(status().isNoContent());
        mvc.perform(delete("/api/memories/{id}", id).with(loginAs(user)).with(csrf()))
            .andExpect(status().isNotFound());

        assertThat(memoryCount(user)).isZero();
    }

    @Test
    void malformedMemoryIdIsA400NotA500() throws Exception {
        User user = newUser("bad-uuid");

        mvc.perform(delete("/api/memories/{id}", "not-a-uuid").with(loginAs(user)).with(csrf()))
            .andExpect(status().isBadRequest());
    }
}
