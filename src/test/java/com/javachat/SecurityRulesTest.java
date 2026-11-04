package com.javachat;

import com.javachat.chat.MessageRole;
import com.javachat.support.IntegrationTest;
import com.javachat.user.User;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The access rules from the spec: APIs answer 401 instead of redirecting, the page shell is public,
 * state changing requests need a CSRF token, and identity never comes from the request body.
 */
class SecurityRulesTest extends IntegrationTest {

    @ParameterizedTest
    @ValueSource(strings = {"/api/me", "/api/chat/history", "/api/memories"})
    void apiCallsWithoutLoginGet401AndNoRedirect(String path) throws Exception {
        mvc.perform(get(path).accept(MediaType.TEXT_HTML))
            .andExpect(status().isUnauthorized())
            .andExpect(header().doesNotExist("Location"));
    }

    // The signed out landing page needs its scripts, styles, logo and background before anyone logs in.
    // Tests run without the built frontend, so a missing file answers 404, which still proves it was let through.
    @ParameterizedTest
    @ValueSource(strings = {"/", "/index.html", "/assets/index.js", "/images/logo-512.png", "/images/landing.webp"})
    void frontendFilesArePublic(String path) throws Exception {
        mvc.perform(get(path).accept(MediaType.TEXT_HTML))
            .andExpect(result -> assertThat(result.getResponse().getStatus())
                .as("%s must not ask for a login", path)
                .isIn(200, 404));
    }

    @Test
    void apiCallsAreNotRememberedAsThePlaceToReturnToAfterLogin() throws Exception {
        MockHttpSession session = new MockHttpSession();

        mvc.perform(get("/api/me").session(session)).andExpect(status().isUnauthorized());

        assertThat(session.getAttribute("SPRING_SECURITY_SAVED_REQUEST"))
            .as("a saved /api request would send the user to raw JSON after Google login")
            .isNull();
    }

    @Test
    void browserPagesAreStillRememberedForAfterLogin() throws Exception {
        MockHttpSession session = new MockHttpSession();

        mvc.perform(get("/swagger-ui.html").session(session).accept(MediaType.TEXT_HTML))
            .andExpect(status().is3xxRedirection());

        assertThat(session.getAttribute("SPRING_SECURITY_SAVED_REQUEST")).isNotNull();
    }

    @Test
    void browserPageWithoutLoginIsSentToGoogle() throws Exception {
        mvc.perform(get("/swagger-ui.html").accept(MediaType.TEXT_HTML))
            .andExpect(status().is3xxRedirection())
            .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("/oauth2/authorization/google")));
    }

    @Test
    void healthCheckIsPublic() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void chatWithoutCsrfTokenIsForbiddenAndNeverReachesTheModel() throws Exception {
        User user = newUser("csrf");

        mvc.perform(post("/api/chat").with(loginAs(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"hi\"}"))
            .andExpect(status().isForbidden());

        assertThat(chatModel.chatPrompts()).isEmpty();
        assertThat(messagesOf(user)).isEmpty();
    }

    @Test
    void clearingHistoryWithoutCsrfTokenChangesNothing() throws Exception {
        User user = newUser("csrf-delete");
        seedMessage(user, MessageRole.USER, "keep me");

        mvc.perform(delete("/api/chat/history").with(loginAs(user)))
            .andExpect(status().isForbidden());

        assertThat(messagesOf(user)).hasSize(1);
    }

    @Test
    void logoutAnswers204InsteadOfRedirecting() throws Exception {
        User user = newUser("logout");

        mvc.perform(post("/logout").with(loginAs(user)).with(csrf()))
            .andExpect(status().isNoContent())
            .andExpect(header().doesNotExist("Location"));
    }

    @Test
    void userIdSmuggledIntoTheBodyIsIgnored() throws Exception {
        User attacker = newUser("attacker");
        User victim = newUser("victim");

        mvc.perform(post("/api/chat").with(loginAs(attacker)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"hi\",\"userId\":" + victim.getId() + "}"))
            .andExpect(status().isOk());

        assertThat(messagesOf(attacker)).hasSize(2);
        assertThat(messagesOf(victim)).isEmpty();
    }

    @Test
    void meDoesNotExposeTheInternalId() throws Exception {
        User user = newUser("me");

        String body = mvc.perform(get("/api/me").with(loginAs(user)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("me@example.com").doesNotContain("\"id\"").doesNotContain(user.getGoogleSub());
    }
}
