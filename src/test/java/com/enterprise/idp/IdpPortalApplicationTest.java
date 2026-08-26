package com.enterprise.idp;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.idp.controller.DeploymentController;
import com.enterprise.idp.controller.EnvironmentController;
import com.enterprise.idp.controller.ProjectController;
import com.enterprise.idp.controller.TeamController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** Verifies the application context wires all catalog controllers. */
@SpringBootTest
@ActiveProfiles("test")
class IdpPortalApplicationTest {

    @Autowired
    private ProjectController projectController;

    @Autowired
    private TeamController teamController;

    @Autowired
    private EnvironmentController environmentController;

    @Autowired
    private DeploymentController deploymentController;

    @Test
    @DisplayName("context loads with every catalog controller present")
    void contextLoads() {
        assertThat(projectController).isNotNull();
        assertThat(teamController).isNotNull();
        assertThat(environmentController).isNotNull();
        assertThat(deploymentController).isNotNull();
    }
}
