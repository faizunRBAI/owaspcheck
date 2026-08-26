package com.enterprise.idp.service;

import com.enterprise.idp.domain.Environment;
import com.enterprise.idp.domain.Project;
import com.enterprise.idp.dto.EnvironmentDto;
import com.enterprise.idp.exception.DuplicateResourceException;
import com.enterprise.idp.exception.ResourceNotFoundException;
import com.enterprise.idp.mapper.CatalogMapper;
import com.enterprise.idp.repository.EnvironmentRepository;
import com.enterprise.idp.repository.ProjectRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Business operations for project environments. */
@Service
@Transactional(readOnly = true)
public class EnvironmentService {

    private final EnvironmentRepository repository;
    private final ProjectRepository projectRepository;
    private final CatalogMapper mapper;

    public EnvironmentService(EnvironmentRepository repository, ProjectRepository projectRepository,
                              CatalogMapper mapper) {
        this.repository = repository;
        this.projectRepository = projectRepository;
        this.mapper = mapper;
    }

    public List<EnvironmentDto> findAll() {
        return repository.findAll().stream().map(mapper::toDto).toList();
    }

    public List<EnvironmentDto> findByProject(Long projectId) {
        return repository.findByProjectId(projectId).stream().map(mapper::toDto).toList();
    }

    public EnvironmentDto findById(Long id) {
        return mapper.toDto(getEntity(id));
    }

    @Transactional
    public EnvironmentDto create(EnvironmentDto dto) {
        Project project = resolveProject(dto.projectId());
        Optional<Environment> existing = repository.findByProjectIdAndName(project.getId(), dto.name());
        if (existing.isPresent()) {
            throw new DuplicateResourceException(
                    "Environment '" + dto.name() + "' already exists for project " + project.getName());
        }
        Environment environment = new Environment();
        environment.setProject(project);
        apply(environment, dto);
        return mapper.toDto(repository.save(environment));
    }

    @Transactional
    public EnvironmentDto update(Long id, EnvironmentDto dto) {
        Environment environment = getEntity(id);
        Project project = resolveProject(dto.projectId());
        Optional<Environment> clash = repository.findByProjectIdAndName(project.getId(), dto.name());
        if (clash.isPresent() && !clash.get().getId().equals(id)) {
            throw new DuplicateResourceException(
                    "Environment '" + dto.name() + "' already exists for project " + project.getName());
        }
        environment.setProject(project);
        apply(environment, dto);
        return mapper.toDto(repository.save(environment));
    }

    @Transactional
    public void delete(Long id) {
        repository.delete(getEntity(id));
    }

    private Environment getEntity(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Environment", id));
    }

    private Project resolveProject(Long projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));
    }

    private void apply(Environment environment, EnvironmentDto dto) {
        environment.setName(dto.name());
        if (dto.type() != null) {
            environment.setType(dto.type());
        }
        environment.setRegion(dto.region());
        environment.setEndpointUrl(dto.endpointUrl());
    }
}
