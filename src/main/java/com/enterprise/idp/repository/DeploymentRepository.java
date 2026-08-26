package com.enterprise.idp.repository;

import com.enterprise.idp.domain.Deployment;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Data access for {@link Deployment}. */
public interface DeploymentRepository extends JpaRepository<Deployment, Long> {

    List<Deployment> findByEnvironmentId(Long environmentId);

    List<Deployment> findByStatus(Deployment.DeploymentStatus status);
}
