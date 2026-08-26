package com.enterprise.idp.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request/response payloads for authentication endpoints. */
public final class AuthDtos {

    private AuthDtos() {
    }

    /** Registration request. */
    public record RegisterRequest(
            @NotBlank @Size(min = 3, max = 100) String username,
            @NotBlank @Email String email,
            @NotBlank @Size(min = 8, max = 100) String password) {
    }

    /** Login request. */
    public record LoginRequest(
            @NotBlank String username,
            @NotBlank String password) {
    }

    /** Issued token response. */
    public record TokenResponse(
            String accessToken,
            String tokenType,
            long expiresInSeconds,
            String username,
            String role) {
    }
}
