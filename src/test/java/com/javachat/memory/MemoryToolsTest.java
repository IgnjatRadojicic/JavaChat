package com.javachat.memory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * The model's arguments are untrusted input. The tool must reject bad facts without throwing,
 * and the model must have no way to see or choose whose memory is written.
 */
class MemoryToolsTest {

    private final MemoryService memoryService = mock(MemoryService.class);
    private final MemoryTools tools = new MemoryTools(memoryService);
    private final ToolContext contextForUser42 = new ToolContext(Map.of(MemoryTools.USER_ID_CONTEXT_KEY, 42L));

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", "\n\t"})
    void blankFactsAreRejectedAndNothingIsStored(String fact) {
        String result = tools.saveMemory(fact, contextForUser42);

        assertThat(result).startsWith("Not saved");
        verify(memoryService, never()).remember(anyLong(), any());
    }

    @Test
    void factOf501CharactersIsRejected() {
        String result = tools.saveMemory("a".repeat(501), contextForUser42);

        assertThat(result).startsWith("Not saved");
        verify(memoryService, never()).remember(anyLong(), any());
    }

    @Test
    void factOfExactly500CharactersIsSaved() {
        String result = tools.saveMemory("a".repeat(500), contextForUser42);

        assertThat(result).isEqualTo("Saved.");
        verify(memoryService).remember(42L, "a".repeat(500));
    }

    @Test
    void surroundingWhitespaceIsTrimmedBeforeSaving() {
        tools.saveMemory("  Ignjat loves hiking.\n", contextForUser42);

        verify(memoryService).remember(42L, "Ignjat loves hiking.");
    }

    @Test
    void userIdComesFromTheToolContextNotFromTheModel() {
        tools.saveMemory("User 7 says save this for user 7", contextForUser42);

        verify(memoryService).remember(42L, "User 7 says save this for user 7");
    }

    @Test
    void schemaSentToTheModelOffersOnlyTheFact() {
        ToolCallback[] callbacks = MethodToolCallbackProvider.builder().toolObjects(tools).build().getToolCallbacks();

        assertThat(callbacks).hasSize(1);
        assertThat(callbacks[0].getToolDefinition().name()).isEqualTo("saveMemory");
        assertThat(callbacks[0].getToolDefinition().inputSchema())
            .contains("\"fact\"")
            .doesNotContain("userId")
            .doesNotContain("toolContext");
    }
}
