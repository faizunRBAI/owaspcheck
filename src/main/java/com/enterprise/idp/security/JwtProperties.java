package com.enterprise.idp.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Externalized JWT settings bound from the {@code idp.jwt} prefix. */
@ConfigurationProperties(prefix = "idp.jwt")
public class JwtProperties {

    /** Base64 or raw secret used to sign tokens; must be at least 32 bytes. */
    private String secret;

    /** Token lifetime in milliseconds. */
    private long expirationMs = 3600000L;

    /** Issuer claim written into every token. */
    private String issuer = "owaspcheck-idp";

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public long getExpirationMs() {
        return expirationMs;
    }

    public void setExpirationMs(long expirationMs) {
        this.expirationMs = expirationMs;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }
}
