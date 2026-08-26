package com.enterprise.idp.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for JWT issuing and validation.
 *
 * <p>The signing material below is a throwaway literal generated for these
 * tests only. It is never used by any environment, and the production key
 * arrives from the JWT_SECRET environment variable at runtime.
 */
class JwtTokenProviderTest {

    private static final String TEST_SIGNING_MATERIAL =
            "unit-test-signing-material-not-used-anywhere-1234567890";

    private JwtTokenProvider provider;

    @BeforeEach
    void setUp() {
        provider = new JwtTokenProvider(propertiesWith(TEST_SIGNING_MATERIAL, 3600000L));
    }

    private JwtProperties propertiesWith(String material, long expiryMs) {
        JwtProperties properties = new JwtProperties();
        properties.setSecret(material);
        properties.setExpirationMs(expiryMs);
        properties.setIssuer("owaspcheck-idp");
        return properties;
    }

    @Test
    @DisplayName("issued token round-trips the subject and role claims")
    void generatesReadableToken() {
        String token = provider.generateToken("alice", "ROLE_ADMIN");

        assertThat(token).isNotBlank();
        assertThat(provider.isValid(token)).isTrue();
        assertThat(provider.extractUsername(token)).isEqualTo("alice");
        assertThat(provider.extractRole(token)).isEqualTo("ROLE_ADMIN");
    }

    @Test
    @DisplayName("expiry is reported in seconds")
    void reportsExpirySeconds() {
        assertThat(provider.getExpiresInSeconds()).isEqualTo(3600L);
    }

    @Test
    @DisplayName("a tampered token is rejected")
    void rejectsTamperedToken() {
        String token = provider.generateToken("alice", "ROLE_USER");
        String tampered = token.substring(0, token.length() - 3) + "abc";

        assertThat(provider.isValid(tampered)).isFalse();
        assertThat(provider.extractUsername(tampered)).isNull();
    }

    @Test
    @DisplayName("garbage input is rejected rather than throwing")
    void rejectsGarbage() {
        assertThat(provider.isValid("not-a-jwt")).isFalse();
    }

    @Test
    @DisplayName("a token signed with different material is rejected")
    void rejectsForeignSignature() {
        JwtTokenProvider other = new JwtTokenProvider(
                propertiesWith("a-completely-different-signing-material-0987654321", 3600000L));
        String foreign = other.generateToken("mallory", "ROLE_ADMIN");

        assertThat(provider.isValid(foreign)).isFalse();
    }

    @Test
    @DisplayName("an expired token is rejected")
    void rejectsExpiredToken() throws InterruptedException {
        JwtTokenProvider shortProvider =
                new JwtTokenProvider(propertiesWith(TEST_SIGNING_MATERIAL, 1L));
        String token = shortProvider.generateToken("bob", "ROLE_USER");

        Thread.sleep(50L);

        assertThat(shortProvider.isValid(token)).isFalse();
    }

    @Test
    @DisplayName("signing material shorter than 32 bytes is refused at startup")
    void refusesWeakMaterial() {
        JwtProperties weak = propertiesWith("too-short", 3600000L);

        assertThatThrownBy(() -> new JwtTokenProvider(weak))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32 characters");
    }
}
