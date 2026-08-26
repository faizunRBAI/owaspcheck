package com.enterprise.idp.dto;

import com.enterprise.idp.domain.Deployment;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

/** API representation of a deployment record. */
public record DeploymentDto(
        Long id,
        @NotBlank @Size(max = 120) String version,
        Deployment.DeploymentStatus status,
        @Size(max = 64) String commitSha,
        @Size(max = 150) String triggeredBy,
        @Size(max = 1000) String notes,
        @NotNull Long environmentId,
        String environmentName,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
