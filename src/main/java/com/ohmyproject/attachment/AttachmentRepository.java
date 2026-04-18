package com.ohmyproject.attachment;

import com.ohmyproject.common.JsonStore;
import com.ohmyproject.config.AppProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Repository;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

@Repository
public class AttachmentRepository {
    private final JsonStore<Attachment> store;

    public AttachmentRepository(AppProperties props, ObjectMapper mapper) {
        Path dir = Paths.get(props.uploads().metaDir());
        this.store = new JsonStore<>(dir, mapper, Attachment.class);
    }

    public Optional<Attachment> findById(String id) { return store.findById(id); }
    public List<Attachment> listAll() { return store.listAll(); }
    public void save(Attachment a) { store.save(a.id(), a); }
    public boolean delete(String id) { return store.delete(id); }
}
