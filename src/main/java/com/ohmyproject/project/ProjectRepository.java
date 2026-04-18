package com.ohmyproject.project;

import com.ohmyproject.common.JsonStore;
import com.ohmyproject.config.AppProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Repository;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

@Repository
public class ProjectRepository {
    private final JsonStore<Project> store;

    public ProjectRepository(AppProperties props, ObjectMapper mapper) {
        Path dir = Paths.get(props.data().dir(), "projects");
        this.store = new JsonStore<>(dir, mapper, Project.class);
    }

    public Optional<Project> findById(String id) { return store.findById(id); }
    public List<Project> listAll() { return store.listAll(); }
    public void save(Project p) { store.save(p.id(), p); }
    public boolean delete(String id) { return store.delete(id); }
    public boolean exists(String id) { return store.exists(id); }
}
