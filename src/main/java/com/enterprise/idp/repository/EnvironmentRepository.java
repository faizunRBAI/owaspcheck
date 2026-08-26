package com.enterprise.idp.repository;

import com.enterprise.idp.domain.Environment;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Data access for {@link Environment}. */
public interface EnvironmentRepository extends JpaRepository<Environment, Long> {

    List<Environment> findByProjectId(Long projectId);

    Optional<Environment> findByProjectIdAndName(Long projectId, String name);
}
