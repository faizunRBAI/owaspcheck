package com.enterprise.idp.controller;

import com.enterprise.idp.dto.DeploymentDto;
import com.enterprise.idp.service.DeploymentService;
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

/** CRUD endpoints for deployment records. */
@RestController
@RequestMapping("/api/v1/deployments")
@Tag(name = "Deployments", description = "Recorded deployments of project versions into environments")
@SecurityRequirement(name = "bearerAuth")
public class DeploymentController {

    private final DeploymentService service;

    public DeploymentController(DeploymentService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List deployments, optionally filtered by environment")
    public List<DeploymentDto> list(@RequestParam(required = false) Long environmentId) {
        return environmentId == null ? service.findAll() : service.findByEnvironment(environmentId);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a deployment by id")
    public DeploymentDto get(@PathVariable Long id) {
        return service.findById(id);
    }

    @PostMapping
    @Operation(summary = "Record a new deployment")
    public ResponseEntity<DeploymentDto> create(@Valid @RequestBody DeploymentDto dto) {
        DeploymentDto created = service.create(dto);
        return ResponseEntity.created(URI.create("/api/v1/deployments/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a deployment record")
    public DeploymentDto update(@PathVariable Long id, @Valid @RequestBody DeploymentDto dto) {
        return service.update(id, dto);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a deployment record")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
