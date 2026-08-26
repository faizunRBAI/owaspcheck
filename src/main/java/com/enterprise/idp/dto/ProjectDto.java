package com.enterprise.idp.dto;

import com.enterprise.idp.domain.Project;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

/** API representation of a catalog project. */
public record ProjectDto(
        Long id,
        @NotBlank @Size(max = 150) String name,
        @Size(max = 1000) String description,
        @Size(max = 500) String repositoryUrl,
        Project.Lifecycle lifecycle,
        Long teamId,
        String teamName,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
