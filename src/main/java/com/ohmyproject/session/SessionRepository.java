package com.ohmyproject.session;

import com.ohmyproject.common.JsonStore;
import com.ohmyproject.config.AppProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Repository;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

@Repository
public class SessionRepository {

    private final JsonStore<ChatSession> store;

    public SessionRepository(AppProperties props, ObjectMapper mapper) {
        Path dir = Paths.get(props.data().dir(), "sessions");
        this.store = new JsonStore<>(dir, mapper, ChatSession.class);
    }

    public Optional<ChatSession> findById(String id) { return store.findById(id); }
    public List<ChatSession> listAll() { return store.listAll(); }
    public void save(ChatSession s) { store.save(s.sessionId(), s); }
    public boolean delete(String id) { return store.delete(id); }
}
