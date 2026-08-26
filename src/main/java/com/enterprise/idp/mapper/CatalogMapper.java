package com.enterprise.idp.mapper;

import com.enterprise.idp.domain.Deployment;
import com.enterprise.idp.domain.Environment;
import com.enterprise.idp.domain.Project;
import com.enterprise.idp.domain.Team;
import com.enterprise.idp.dto.DeploymentDto;
import com.enterprise.idp.dto.EnvironmentDto;
import com.enterprise.idp.dto.ProjectDto;
import com.enterprise.idp.dto.TeamDto;
import org.springframework.stereotype.Component;

/** Converts between JPA entities and API DTOs. */
@Component
public class CatalogMapper {

    public TeamDto toDto(Team team) {
        return new TeamDto(
                team.getId(),
                team.getName(),
                team.getDescription(),
                team.getOwnerEmail(),
                team.getSlackChannel(),
                team.getCreatedAt(),
                team.getUpdatedAt());
    }

    public ProjectDto toDto(Project project) {
        Team team = project.getTeam();
        return new ProjectDto(
                project.getId(),
                project.getName(),
                project.getDescription(),
                project.getRepositoryUrl(),
                project.getLifecycle(),
                team == null ? null : team.getId(),
                team == null ? null : team.getName(),
                project.getCreatedAt(),
                project.getUpdatedAt());
    }

    public EnvironmentDto toDto(Environment environment) {
        Project project = environment.getProject();
        return new EnvironmentDto(
                environment.getId(),
                environment.getName(),
                environment.getType(),
                environment.getRegion(),
                environment.getEndpointUrl(),
                project.getId(),
                project.getName(),
                environment.getCreatedAt(),
                environment.getUpdatedAt());
    }

    public DeploymentDto toDto(Deployment deployment) {
        Environment environment = deployment.getEnvironment();
        return new DeploymentDto(
                deployment.getId(),
                deployment.getVersion(),
                deployment.getStatus(),
                deployment.getCommitSha(),
                deployment.getTriggeredBy(),
                deployment.getNotes(),
                environment.getId(),
                environment.getName(),
                deployment.getCreatedAt(),
                deployment.getUpdatedAt());
    }
}
