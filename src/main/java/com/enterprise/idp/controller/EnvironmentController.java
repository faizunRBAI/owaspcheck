package com.enterprise.idp.controller;

import com.enterprise.idp.dto.EnvironmentDto;
import com.enterprise.idp.service.EnvironmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** CRUD endpoints for project environments. */
@RestController
@RequestMapping("/api/v1/environments")
@Tag(name = "Environments", description = "Deployable environments belonging to a project")
@SecurityRequirement(name = "bearerAuth")
public class EnvironmentController {

    private final EnvironmentService service;

    public EnvironmentController(EnvironmentService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List environments, optionally filtered by project")
    public List<EnvironmentDto> list(@RequestParam(required = false) Long projectId) {
        return projectId == null ? service.findAll() : service.findByProject(projectId);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get an environment by id")
    public EnvironmentDto get(@PathVariable Long id) {
        return service.findById(id);
    }

    @PostMapping
    @Operation(summary = "Create an environment")
    public ResponseEntity<EnvironmentDto> create(@Valid @RequestBody EnvironmentDto dto) {
        EnvironmentDto created = service.create(dto);
        return ResponseEntity.created(URI.create("/api/v1/environments/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an environment")
    public EnvironmentDto update(@PathVariable Long id, @Valid @RequestBody EnvironmentDto dto) {
        return service.update(id, dto);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete an environment")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
