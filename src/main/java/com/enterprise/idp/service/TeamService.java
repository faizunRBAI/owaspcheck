package com.enterprise.idp.service;

import com.enterprise.idp.domain.Team;
import com.enterprise.idp.dto.TeamDto;
import com.enterprise.idp.exception.DuplicateResourceException;
import com.enterprise.idp.exception.ResourceNotFoundException;
import com.enterprise.idp.mapper.CatalogMapper;
import com.enterprise.idp.repository.TeamRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Business operations for teams. */
@Service
@Transactional(readOnly = true)
public class TeamService {

    private final TeamRepository repository;
    private final CatalogMapper mapper;

    public TeamService(TeamRepository repository, CatalogMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public List<TeamDto> findAll() {
        return repository.findAll().stream().map(mapper::toDto).toList();
    }

    public TeamDto findById(Long id) {
        return mapper.toDto(getEntity(id));
    }

    @Transactional
    public TeamDto create(TeamDto dto) {
        if (repository.existsByName(dto.name())) {
            throw new DuplicateResourceException("A team named '" + dto.name() + "' already exists");
        }
        Team team = new Team();
        apply(team, dto);
        return mapper.toDto(repository.save(team));
    }

    @Transactional
    public TeamDto update(Long id, TeamDto dto) {
        Team team = getEntity(id);
        if (!team.getName().equals(dto.name()) && repository.existsByName(dto.name())) {
            throw new DuplicateResourceException("A team named '" + dto.name() + "' already exists");
        }
        apply(team, dto);
        return mapper.toDto(repository.save(team));
    }

    @Transactional
    public void delete(Long id) {
        Team team = getEntity(id);
        repository.delete(team);
    }

    private Team getEntity(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Team", id));
    }

    private void apply(Team team, TeamDto dto) {
        team.setName(dto.name());
        team.setDescription(dto.description());
        team.setOwnerEmail(dto.ownerEmail());
        team.setSlackChannel(dto.slackChannel());
    }
}
