package com.enterprise.idp.service;

import com.enterprise.idp.domain.Project;
import com.enterprise.idp.domain.Team;
import com.enterprise.idp.dto.ProjectDto;
import com.enterprise.idp.exception.DuplicateResourceException;
import com.enterprise.idp.exception.ResourceNotFoundException;
import com.enterprise.idp.mapper.CatalogMapper;
import com.enterprise.idp.repository.ProjectRepository;
import com.enterprise.idp.repository.TeamRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Business operations for catalog projects. */
@Service
@Transactional(readOnly = true)
public class ProjectService {

    private final ProjectRepository repository;
    private final TeamRepository teamRepository;
    private final CatalogMapper mapper;

    public ProjectService(ProjectRepository repository, TeamRepository teamRepository, CatalogMapper mapper) {
        this.repository = repository;
        this.teamRepository = teamRepository;
        this.mapper = mapper;
    }

    public List<ProjectDto> findAll() {
        return repository.findAll().stream().map(mapper::toDto).toList();
    }

    public List<ProjectDto> findByTeam(Long teamId) {
        return repository.findByTeamId(teamId).stream().map(mapper::toDto).toList();
    }

    public ProjectDto findById(Long id) {
        return mapper.toDto(getEntity(id));
    }

    @Transactional
    public ProjectDto create(ProjectDto dto) {
        if (repository.existsByName(dto.name())) {
            throw new DuplicateResourceException("A project named '" + dto.name() + "' already exists");
        }
        Project project = new Project();
        apply(project, dto);
        return mapper.toDto(repository.save(project));
    }

    @Transactional
    public ProjectDto update(Long id, ProjectDto dto) {
        Project project = getEntity(id);
        if (!project.getName().equals(dto.name()) && repository.existsByName(dto.name())) {
            throw new DuplicateResourceException("A project named '" + dto.name() + "' already exists");
        }
        apply(project, dto);
        return mapper.toDto(repository.save(project));
    }

    @Transactional
    public void delete(Long id) {
        repository.delete(getEntity(id));
    }

    private Project getEntity(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Project", id));
    }

    private void apply(Project project, ProjectDto dto) {
        project.setName(dto.name());
        project.setDescription(dto.description());
        project.setRepositoryUrl(dto.repositoryUrl());
        if (dto.lifecycle() != null) {
            project.setLifecycle(dto.lifecycle());
        }
        if (dto.teamId() == null) {
            project.setTeam(null);
        } else {
            Team team = teamRepository.findById(dto.teamId())
                    .orElseThrow(() -> new ResourceNotFoundException("Team", dto.teamId()));
            project.setTeam(team);
        }
    }
}
