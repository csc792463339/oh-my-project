package com.ohmyproject.project;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/projects")
public class AdminProjectController {

    private final ProjectService service;

    public AdminProjectController(ProjectService service) {
        this.service = service;
    }

    @GetMapping
    public List<Project> list() { return service.list(); }

    @GetMapping("/{id}")
    public Project get(@PathVariable String id) { return service.get(id); }

    @PostMapping
    public ResponseEntity<Project> create(@RequestBody ProjectService.ProjectInput in) {
        return ResponseEntity.status(201).body(service.create(in));
    }

    @PutMapping("/{id}")
    public Project update(@PathVariable String id, @RequestBody ProjectService.ProjectInput in) {
        return service.update(id, in);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
