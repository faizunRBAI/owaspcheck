package com.enterprise.idp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.enterprise.idp.domain.Project;
import com.enterprise.idp.domain.Team;
import com.enterprise.idp.dto.ProjectDto;
import com.enterprise.idp.exception.DuplicateResourceException;
import com.enterprise.idp.exception.ResourceNotFoundException;
import com.enterprise.idp.mapper.CatalogMapper;
import com.enterprise.idp.repository.ProjectRepository;
import com.enterprise.idp.repository.TeamRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unit tests for {@link ProjectService}. */
@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

    @Mock
    private ProjectRepository repository;

    @Mock
    private TeamRepository teamRepository;

    private ProjectService service;

    private Team team;
    private Project project;

    @BeforeEach
    void setUp() {
        service = new ProjectService(repository, teamRepository, new CatalogMapper());

        team = new Team();
        team.setId(10L);
        team.setName("Platform Engineering");

        project = new Project();
        project.setId(1L);
        project.setName("developer-portal");
        project.setLifecycle(Project.Lifecycle.PRODUCTION);
        project.setTeam(team);
    }

    @Test
    @DisplayName("findAll includes the owning team name")
    void listsProjectsWithTeam() {
        when(repository.findAll()).thenReturn(List.of(project));

        List<ProjectDto> result = service.findAll();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).teamName()).isEqualTo("Platform Engineering");
        assertThat(result.get(0).lifecycle()).isEqualTo(Project.Lifecycle.PRODUCTION);
    }

    @Test
    @DisplayName("findByTeam delegates to the team-scoped query")
    void listsByTeam() {
        when(repository.findByTeamId(10L)).thenReturn(List.of(project));

        assertThat(service.findByTeam(10L)).hasSize(1);
    }

    @Test
    @DisplayName("create resolves and attaches the owning team")
    void createsWithTeam() {
        when(repository.existsByName("payments-api")).thenReturn(false);
        when(teamRepository.findById(10L)).thenReturn(Optional.of(team));
        when(repository.save(any(Project.class))).thenAnswer(invocation -> {
            Project saved = invocation.getArgument(0);
            saved.setId(2L);
            return saved;
        });

        ProjectDto dto = new ProjectDto(null, "payments-api", "Payments", null,
                Project.Lifecycle.EXPERIMENTAL, 10L, null, null, null);

        ProjectDto created = service.create(dto);

        assertThat(created.id()).isEqualTo(2L);
        assertThat(created.teamId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("create fails when the referenced team does not exist")
    void createFailsOnUnknownTeam() {
        when(repository.existsByName("orphan")).thenReturn(false);
        when(teamRepository.findById(404L)).thenReturn(Optional.empty());

        ProjectDto dto = new ProjectDto(null, "orphan", null, null,
                Project.Lifecycle.EXPERIMENTAL, 404L, null, null, null);

        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Team");
    }

    @Test
    @DisplayName("create rejects a duplicate project name")
    void rejectsDuplicate() {
        when(repository.existsByName("developer-portal")).thenReturn(true);

        ProjectDto dto = new ProjectDto(null, "developer-portal", null, null,
                Project.Lifecycle.EXPERIMENTAL, null, null, null, null);

        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    @DisplayName("update can detach a project from its team")
    void detachesTeam() {
        when(repository.findById(1L)).thenReturn(Optional.of(project));
        when(repository.save(any(Project.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProjectDto dto = new ProjectDto(1L, "developer-portal", "Updated", null,
                Project.Lifecycle.DEPRECATED, null, null, null, null);

        ProjectDto updated = service.update(1L, dto);

        assertThat(updated.teamId()).isNull();
        assertThat(updated.lifecycle()).isEqualTo(Project.Lifecycle.DEPRECATED);
    }
}
