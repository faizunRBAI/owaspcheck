package com.enterprise.idp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.idp.domain.Team;
import com.enterprise.idp.dto.TeamDto;
import com.enterprise.idp.exception.DuplicateResourceException;
import com.enterprise.idp.exception.ResourceNotFoundException;
import com.enterprise.idp.mapper.CatalogMapper;
import com.enterprise.idp.repository.TeamRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unit tests for {@link TeamService}. */
@ExtendWith(MockitoExtension.class)
class TeamServiceTest {

    @Mock
    private TeamRepository repository;

    private TeamService service;

    private Team team;

    @BeforeEach
    void setUp() {
        service = new TeamService(repository, new CatalogMapper());
        team = new Team();
        team.setId(1L);
        team.setName("Platform Engineering");
        team.setOwnerEmail("platform@enterprise.example");
    }

    @Test
    @DisplayName("findAll maps every entity")
    void listsTeams() {
        when(repository.findAll()).thenReturn(List.of(team));

        List<TeamDto> result = service.findAll();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("Platform Engineering");
    }

    @Test
    @DisplayName("findById returns the mapped team")
    void findsById() {
        when(repository.findById(1L)).thenReturn(Optional.of(team));

        assertThat(service.findById(1L).ownerEmail()).isEqualTo("platform@enterprise.example");
    }

    @Test
    @DisplayName("findById on a missing id raises ResourceNotFoundException")
    void missingTeamRaises() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    @DisplayName("create rejects a duplicate team name")
    void rejectsDuplicateName() {
        when(repository.existsByName("Platform Engineering")).thenReturn(true);
        TeamDto dto = new TeamDto(null, "Platform Engineering", null,
                "platform@enterprise.example", null, null, null);

        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(DuplicateResourceException.class);
        verify(repository, never()).save(any(Team.class));
    }

    @Test
    @DisplayName("create persists a new team")
    void createsTeam() {
        when(repository.existsByName("Payments")).thenReturn(false);
        when(repository.save(any(Team.class))).thenAnswer(invocation -> {
            Team saved = invocation.getArgument(0);
            saved.setId(2L);
            return saved;
        });
        TeamDto dto = new TeamDto(null, "Payments", "Payment services",
                "payments@enterprise.example", "#payments", null, null);

        TeamDto created = service.create(dto);

        assertThat(created.id()).isEqualTo(2L);
        assertThat(created.slackChannel()).isEqualTo("#payments");
    }

    @Test
    @DisplayName("update rejects renaming onto an existing team name")
    void rejectsRenameCollision() {
        when(repository.findById(1L)).thenReturn(Optional.of(team));
        when(repository.existsByName("Payments")).thenReturn(true);
        TeamDto dto = new TeamDto(1L, "Payments", null,
                "platform@enterprise.example", null, null, null);

        assertThatThrownBy(() -> service.update(1L, dto))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    @DisplayName("delete removes an existing team")
    void deletesTeam() {
        when(repository.findById(1L)).thenReturn(Optional.of(team));

        service.delete(1L);

        verify(repository).delete(team);
    }
}
