package com.enterprise.idp.controller;

import com.enterprise.idp.dto.ProjectDto;
import com.enterprise.idp.service.ProjectService;
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

/** CRUD endpoints for catalog projects. */
@RestController
@RequestMapping("/api/v1/projects")
@Tag(name = "Projects", description = "Software projects registered in the portal catalog")
@SecurityRequirement(name = "bearerAuth")
public class ProjectController {

    private final ProjectService service;

    public ProjectController(ProjectService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List projects, optionally filtered by owning team")
    public List<ProjectDto> list(@RequestParam(required = false) Long teamId) {
        return teamId == null ? service.findAll() : service.findByTeam(teamId);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a project by id")
    public ProjectDto get(@PathVariable Long id) {
        return service.findById(id);
    }

    @PostMapping
    @Operation(summary = "Create a project")
    public ResponseEntity<ProjectDto> create(@Valid @RequestBody ProjectDto dto) {
        ProjectDto created = service.create(dto);
        return ResponseEntity.created(URI.create("/api/v1/projects/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a project")
    public ProjectDto update(@PathVariable Long id, @Valid @RequestBody ProjectDto dto) {
        return service.update(id, dto);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a project")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
