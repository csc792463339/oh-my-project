package com.ohmyproject.project;

import com.ohmyproject.common.ApiException;
import com.ohmyproject.common.IdGenerator;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
public class ProjectService {

    private final ProjectRepository repo;

    public ProjectService(ProjectRepository repo) {
        this.repo = repo;
    }

    public List<Project> list() {
        return repo.listAll().stream()
                .sorted(Comparator.comparingLong(Project::updatedAt).reversed())
                .toList();
    }

    public Project get(String id) {
        return repo.findById(id).orElseThrow(() -> new ApiException(404, "project not found"));
    }

    public Project create(ProjectInput in) {
        validate(in);
        long now = System.currentTimeMillis();
        Project p = new Project(
                IdGenerator.newId(),
                in.name().trim(),
                in.path().trim(),
                nullToEmpty(in.description()),
                now, now
        );
        repo.save(p);
        return p;
    }

    public Project update(String id, ProjectInput in) {
        validate(in);
        Project old = get(id);
        Project updated = new Project(
                old.id(),
                in.name().trim(),
                in.path().trim(),
                nullToEmpty(in.description()),
                old.createdAt(),
                System.currentTimeMillis()
        );
        repo.save(updated);
        return updated;
    }

    public void delete(String id) {
        if (!repo.delete(id)) {
            throw new ApiException(404, "project not found");
        }
    }

    private static String nullToEmpty(String s) { return s == null ? "" : s; }

    private static void validate(ProjectInput in) {
        if (in == null) throw new ApiException(400, "body required");
        if (in.name() == null || in.name().isBlank()) throw new ApiException(400, "name required");
        if (in.path() == null || in.path().isBlank()) throw new ApiException(400, "path required");
    }

    public record ProjectInput(
            String name,
            String path,
            String description
    ) {}
}
