package com.enterprise.idp.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import javax.crypto.SecretKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Issues and validates the portal's JWT access tokens. */
@Component
public class JwtTokenProvider {

    private static final Logger LOG = LoggerFactory.getLogger(JwtTokenProvider.class);

    private final JwtProperties properties;
    private final SecretKey signingKey;

    public JwtTokenProvider(JwtProperties properties) {
        this.properties = properties;
        byte[] keyBytes = properties.getSecret().getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalStateException(
                    "idp.jwt.secret must be at least 32 characters to sign HS256 tokens");
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Creates a signed token for the given user.
     *
     * @param username subject of the token
     * @param role     granted authority written as the {@code role} claim
     * @return a compact serialized JWT
     */
    public String generateToken(String username, String role) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + properties.getExpirationMs());
        return Jwts.builder()
                .subject(username)
                .claim("role", role)
                .issuer(properties.getIssuer())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey)
                .compact();
    }

    /** Returns the subject of a valid token, or {@code null} when the token is unusable. */
    public String extractUsername(String token) {
        Claims claims = parse(token);
        return claims == null ? null : claims.getSubject();
    }

    /** Returns the role claim of a valid token, or {@code null}. */
    public String extractRole(String token) {
        Claims claims = parse(token);
        return claims == null ? null : claims.get("role", String.class);
    }

    /** Returns true when the token's signature and expiry are both valid. */
    public boolean isValid(String token) {
        return parse(token) != null;
    }

    /** Token lifetime in seconds, for the client's benefit. */
    public long getExpiresInSeconds() {
        return properties.getExpirationMs() / 1000L;
    }

    private Claims parse(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(signingKey)
                    .requireIssuer(properties.getIssuer())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException ex) {
            LOG.debug("Rejected JWT: {}", ex.getMessage());
            return null;
        }
    }
}
