package com.enterprise.idp.repository;

import com.enterprise.idp.domain.Project;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Data access for {@link Project}. */
public interface ProjectRepository extends JpaRepository<Project, Long> {

    Optional<Project> findByName(String name);

    boolean existsByName(String name);

    List<Project> findByTeamId(Long teamId);
}
