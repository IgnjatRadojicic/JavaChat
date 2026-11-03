package com.javachat.user;

import com.javachat.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * Login runs after Google has already been verified. Users are identified by Google's permanent
 * sub, never by email, and an unverified email must not create or touch any account.
 */
class GoogleLoginTest extends IntegrationTest {

    @Autowired
    GoogleOidcUserService googleOidcUserService;

    @Test
    void unverifiedEmailIsRejectedAndNoUserIsCreated() {
        String sub = "unverified-" + UUID.randomUUID();

        OAuth2AuthenticationException rejected = catchThrowableOfType(OAuth2AuthenticationException.class,
            () -> googleOidcUserService.loadUser(request(sub, "a@example.com", "A", false)));

        assertThat(rejected.getError().getErrorCode()).isEqualTo("email_not_verified");
        assertThat(userRepository.findByGoogleSub(sub)).isEmpty();
    }

    @Test
    void missingEmailVerifiedClaimIsTreatedAsUnverified() {
        String sub = "no-claim-" + UUID.randomUUID();

        OAuth2AuthenticationException rejected = catchThrowableOfType(OAuth2AuthenticationException.class,
            () -> googleOidcUserService.loadUser(request(sub, "b@example.com", "B", null)));

        assertThat(rejected).isNotNull();
        assertThat(userRepository.findByGoogleSub(sub)).isEmpty();
    }

    @Test
    void returningUserIsUpdatedInPlaceNeverDuplicated() {
        String sub = "returning-" + UUID.randomUUID();
        googleOidcUserService.loadUser(request(sub, "old@example.com", "Old Name", true));
        User first = userRepository.findByGoogleSub(sub).orElseThrow();

        googleOidcUserService.loadUser(request(sub, "new@example.com", "New Name", true));

        User second = userRepository.findByGoogleSub(sub).orElseThrow();
        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(second.getEmail()).isEqualTo("new@example.com");
        assertThat(second.getName()).isEqualTo("New Name");
        assertThat(second.getCreatedAt()).isEqualTo(first.getCreatedAt());
        assertThat(second.getLastLoginAt()).isAfterOrEqualTo(first.getLastLoginAt());
    }

    @Test
    void sameEmailWithDifferentGoogleAccountsMeansDifferentUsers() {
        String firstSub = "first-" + UUID.randomUUID();
        String secondSub = "second-" + UUID.randomUUID();

        googleOidcUserService.loadUser(request(firstSub, "shared@example.com", "One", true));
        googleOidcUserService.loadUser(request(secondSub, "shared@example.com", "Two", true));

        User one = userRepository.findByGoogleSub(firstSub).orElseThrow();
        User two = userRepository.findByGoogleSub(secondSub).orElseThrow();
        assertThat(one.getId()).isNotEqualTo(two.getId());
    }

    private static OidcUserRequest request(String sub, String email, String name, Boolean emailVerified) {
        ClientRegistration google = ClientRegistration.withRegistrationId("google")
            .clientId("test-client-id")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
            .scope("openid", "profile", "email")
            .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
            .tokenUri("https://oauth2.googleapis.com/token")
            .jwkSetUri("https://www.googleapis.com/oauth2/v3/certs")
            .userNameAttributeName("sub")
            .build();

        Instant now = Instant.now();
        OAuth2AccessToken accessToken = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER,
            "access-token", now, now.plusSeconds(300), Set.of("openid", "profile", "email"));

        OidcIdToken.Builder idToken = OidcIdToken.withTokenValue("id-token")
            .subject(sub)
            .issuedAt(now)
            .expiresAt(now.plusSeconds(300))
            .claim("email", email)
            .claim("name", name);
        if (emailVerified != null) {
            idToken.claim("email_verified", emailVerified);
        }

        return new OidcUserRequest(google, accessToken, idToken.build());
    }
}
