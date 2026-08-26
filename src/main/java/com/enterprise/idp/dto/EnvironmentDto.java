package com.enterprise.idp.dto;

import com.enterprise.idp.domain.Environment;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

/** API representation of a project environment. */
public record EnvironmentDto(
        Long id,
        @NotBlank @Size(max = 100) String name,
        Environment.EnvironmentType type,
        @Size(max = 64) String region,
        @Size(max = 500) String endpointUrl,
        @NotNull Long projectId,
        String projectName,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
