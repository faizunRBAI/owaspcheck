package com.enterprise.idp.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

/** API representation of a team. */
public record TeamDto(
        Long id,
        @NotBlank @Size(max = 150) String name,
        @Size(max = 1000) String description,
        @NotBlank @Email String ownerEmail,
        @Size(max = 120) String slackChannel,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
