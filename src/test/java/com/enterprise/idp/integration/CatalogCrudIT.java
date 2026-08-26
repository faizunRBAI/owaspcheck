package com.enterprise.idp.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.enterprise.idp.domain.Deployment;
import com.enterprise.idp.domain.Environment;
import com.enterprise.idp.domain.Project;
import com.enterprise.idp.dto.AuthDtos;
import com.enterprise.idp.dto.DeploymentDto;
import com.enterprise.idp.dto.EnvironmentDto;
import com.enterprise.idp.dto.ProjectDto;
import com.enterprise.idp.dto.TeamDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * End-to-end CRUD flow against a real PostgreSQL instance, with Flyway
 * migrations applied exactly as they are in production.
 */
@SpringBootTest
@Testcontainers
class CatalogCrudIT {

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("idp")
                    .withUsername("idp")
                    .withPassword("idpTestPassword123");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("idp.jwt.secret",
                () -> "integration-test-signing-secret-key-1234567890abcdef");
    }

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;
    private String bearer;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(org.springframework.security.test.web.servlet.setup
                        .SecurityMockMvcConfigurers.springSecurity())
                .build();
        bearer = "Bearer " + authenticate();
    }

    private String authenticate() throws Exception {
        String username = "it-user-" + System.nanoTime();
        AuthDtos.RegisterRequest request = new AuthDtos.RegisterRequest(
                username, username + "@enterprise.example", "IntegrationPass123");
        String body = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("accessToken").asText();
    }

    @Test
    @DisplayName("flyway seed data is present after migration")
    void seedDataMigrated() throws Exception {
        mockMvc.perform(get("/api/v1/teams").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name=='Platform Engineering')]").exists());
    }

    @Test
    @DisplayName("full team -> project -> environment -> deployment lifecycle")
    void fullCatalogLifecycle() throws Exception {
        long teamId = createTeam();
        long projectId = createProject(teamId);
        long environmentId = createEnvironment(projectId);
        long deploymentId = createDeployment(environmentId);

        // Read back the deployment with its environment name resolved.
        mockMvc.perform(get("/api/v1/deployments/" + deploymentId).header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value("1.4.2"))
                .andExpect(jsonPath("$.environmentName").value("staging"));

        // Filter deployments by environment.
        mockMvc.perform(get("/api/v1/deployments?environmentId=" + environmentId)
                        .header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        // Update the deployment status.
        DeploymentDto update = new DeploymentDto(deploymentId, "1.4.2",
                Deployment.DeploymentStatus.SUCCEEDED, "abc1234", "ci-bot",
                "promoted", environmentId, null, null, null);
        mockMvc.perform(put("/api/v1/deployments/" + deploymentId)
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"));

        // Cascade: deleting the project removes environments and deployments.
        mockMvc.perform(delete("/api/v1/projects/" + projectId).header("Authorization", bearer))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/deployments/" + deploymentId).header("Authorization", bearer))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("duplicate project names are rejected with 409")
    void duplicateProjectRejected() throws Exception {
        long teamId = createTeam();
        String name = "dup-project-" + System.nanoTime();
        ProjectDto dto = new ProjectDto(null, name, "first", null,
                Project.Lifecycle.EXPERIMENTAL, teamId, null, null, null);

        mockMvc.perform(post("/api/v1/projects").header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/projects").header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    @DisplayName("an unknown resource id returns 404 with the error payload")
    void unknownResourceReturns404() throws Exception {
        mockMvc.perform(get("/api/v1/projects/99999").header("Authorization", bearer))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    @DisplayName("requests without a token are rejected")
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/projects"))
                .andExpect(status().isUnauthorized());
    }

    private long createTeam() throws Exception {
        TeamDto dto = new TeamDto(null, "it-team-" + System.nanoTime(), "integration",
                "it@enterprise.example", "#it", null, null);
        return idOf(mockMvc.perform(post("/api/v1/teams").header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
    }

    private long createProject(long teamId) throws Exception {
        ProjectDto dto = new ProjectDto(null, "it-project-" + System.nanoTime(), "integration",
                "https://github.com/enterprise/it", Project.Lifecycle.PRODUCTION, teamId, null, null, null);
        return idOf(mockMvc.perform(post("/api/v1/projects").header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.teamId").value(teamId))
                .andReturn().getResponse().getContentAsString());
    }

    private long createEnvironment(long projectId) throws Exception {
        EnvironmentDto dto = new EnvironmentDto(null, "staging", Environment.EnvironmentType.STAGING,
                "us-east-1", "https://staging.enterprise.example", projectId, null, null, null);
        return idOf(mockMvc.perform(post("/api/v1/environments").header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
    }

    private long createDeployment(long environmentId) throws Exception {
        DeploymentDto dto = new DeploymentDto(null, "1.4.2", Deployment.DeploymentStatus.IN_PROGRESS,
                "abc1234", "ci-bot", "initial", environmentId, null, null, null);
        return idOf(mockMvc.perform(post("/api/v1/deployments").header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
    }

    private long idOf(String json) throws Exception {
        JsonNode node = objectMapper.readTree(json);
        return node.get("id").asLong();
    }
}
