package com.enterprise.idp.service;

import com.enterprise.idp.domain.Deployment;
import com.enterprise.idp.domain.Environment;
import com.enterprise.idp.dto.DeploymentDto;
import com.enterprise.idp.exception.ResourceNotFoundException;
import com.enterprise.idp.mapper.CatalogMapper;
import com.enterprise.idp.repository.DeploymentRepository;
import com.enterprise.idp.repository.EnvironmentRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Business operations for deployment records. */
@Service
@Transactional(readOnly = true)
public class DeploymentService {

    private final DeploymentRepository repository;
    private final EnvironmentRepository environmentRepository;
    private final CatalogMapper mapper;

    public DeploymentService(DeploymentRepository repository, EnvironmentRepository environmentRepository,
                             CatalogMapper mapper) {
        this.repository = repository;
        this.environmentRepository = environmentRepository;
        this.mapper = mapper;
    }

    public List<DeploymentDto> findAll() {
        return repository.findAll().stream().map(mapper::toDto).toList();
    }

    public List<DeploymentDto> findByEnvironment(Long environmentId) {
        return repository.findByEnvironmentId(environmentId).stream().map(mapper::toDto).toList();
    }

    public DeploymentDto findById(Long id) {
        return mapper.toDto(getEntity(id));
    }

    @Transactional
    public DeploymentDto create(DeploymentDto dto) {
        Deployment deployment = new Deployment();
        deployment.setEnvironment(resolveEnvironment(dto.environmentId()));
        apply(deployment, dto);
        return mapper.toDto(repository.save(deployment));
    }

    @Transactional
    public DeploymentDto update(Long id, DeploymentDto dto) {
        Deployment deployment = getEntity(id);
        deployment.setEnvironment(resolveEnvironment(dto.environmentId()));
        apply(deployment, dto);
        return mapper.toDto(repository.save(deployment));
    }

    @Transactional
    public void delete(Long id) {
        repository.delete(getEntity(id));
    }

    private Deployment getEntity(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Deployment", id));
    }

    private Environment resolveEnvironment(Long environmentId) {
        return environmentRepository.findById(environmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Environment", environmentId));
    }

    private void apply(Deployment deployment, DeploymentDto dto) {
        deployment.setVersion(dto.version());
        if (dto.status() != null) {
            deployment.setStatus(dto.status());
        }
        deployment.setCommitSha(dto.commitSha());
        deployment.setTriggeredBy(dto.triggeredBy());
        deployment.setNotes(dto.notes());
    }
}
