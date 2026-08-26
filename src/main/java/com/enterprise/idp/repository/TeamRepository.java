package com.enterprise.idp.repository;

import com.enterprise.idp.domain.Team;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Data access for {@link Team}. */
public interface TeamRepository extends JpaRepository<Team, Long> {

    Optional<Team> findByName(String name);

    boolean existsByName(String name);
}
