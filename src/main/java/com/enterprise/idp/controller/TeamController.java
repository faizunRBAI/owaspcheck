package com.enterprise.idp.controller;

import com.enterprise.idp.dto.TeamDto;
import com.enterprise.idp.service.TeamService;
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
import org.springframework.web.bind.annotation.RestController;

/** CRUD endpoints for teams. */
@RestController
@RequestMapping("/api/v1/teams")
@Tag(name = "Teams", description = "Organizational teams that own projects")
@SecurityRequirement(name = "bearerAuth")
public class TeamController {

    private final TeamService service;

    public TeamController(TeamService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List all teams")
    public List<TeamDto> list() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a team by id")
    public TeamDto get(@PathVariable Long id) {
        return service.findById(id);
    }

    @PostMapping
    @Operation(summary = "Create a team")
    public ResponseEntity<TeamDto> create(@Valid @RequestBody TeamDto dto) {
        TeamDto created = service.create(dto);
        return ResponseEntity.created(URI.create("/api/v1/teams/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a team")
    public TeamDto update(@PathVariable Long id, @Valid @RequestBody TeamDto dto) {
        return service.update(id, dto);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a team")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
