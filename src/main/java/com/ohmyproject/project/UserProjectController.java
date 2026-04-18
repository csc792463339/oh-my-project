package com.ohmyproject.project;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 普通用户只能看到 id/name，其他字段（路径等）不暴露。
 */
@RestController
@RequestMapping("/api/projects")
public class UserProjectController {

    private final ProjectService service;

    public UserProjectController(ProjectService service) {
        this.service = service;
    }

    @GetMapping
    public List<PublicProject> list() {
        return service.list().stream()
                .map(p -> new PublicProject(p.id(), p.name()))
                .toList();
    }

    public record PublicProject(String id, String name) {}
}
