package com.enterprise.idp.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.enterprise.idp.dto.AuthDtos;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

/** API-level tests for authentication and access control. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AuthControllerApiTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc() {
        return MockMvcBuilders.webAppContextSetup(context)
                .apply(org.springframework.security.test.web.servlet.setup
                        .SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    @Test
    @DisplayName("registration returns 201 with a usable bearer token")
    void registersUser() throws Exception {
        AuthDtos.RegisterRequest request =
                new AuthDtos.RegisterRequest("apitester", "apitester@enterprise.example", "Str0ngPassw0rd");

        mockMvc().perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.username").value("apitester"));
    }

    @Test
    @DisplayName("registration rejects an invalid payload with 400")
    void rejectsInvalidRegistration() throws Exception {
        AuthDtos.RegisterRequest request =
                new AuthDtos.RegisterRequest("ab", "not-an-email", "short");

        mockMvc().perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("login with unknown credentials returns 401")
    void rejectsUnknownLogin() throws Exception {
        AuthDtos.LoginRequest request = new AuthDtos.LoginRequest("nobody", "whatever123");

        mockMvc().perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("catalog endpoints reject anonymous access with 401")
    void protectsCatalog() throws Exception {
        mockMvc().perform(get("/api/v1/projects"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("the health endpoint is publicly reachable")
    void healthIsPublic() throws Exception {
        mockMvc().perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("the OpenAPI document is publicly reachable")
    void openApiIsPublic() throws Exception {
        mockMvc().perform(get("/v3/api-docs"))
                .andExpect(status().isOk());
    }
}
