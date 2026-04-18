package com.ohmyproject.session;

import com.ohmyproject.common.RequestContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/sessions")
public class UserSessionController {

    private final SessionService service;

    public UserSessionController(SessionService service) {
        this.service = service;
    }

    @GetMapping
    public List<Summary> list() {
        String uid = RequestContext.getUserId();
        return service.listForUser(uid).stream().map(Summary::from).toList();
    }

    @PostMapping
    public ChatSession create(@RequestBody CreateRequest req) {
        return service.create(RequestContext.getUserId(), req.projectId(), req.title());
    }

    @GetMapping("/{id}")
    public ChatSession get(@PathVariable String id) {
        return service.get(id, RequestContext.getUserId(), false);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        service.delete(id, RequestContext.getUserId(), false);
        return ResponseEntity.noContent().build();
    }

    public record CreateRequest(String projectId, String title) {}

    public record Summary(String sessionId, String title, String projectId, long lastActiveAt) {
        static Summary from(ChatSession s) {
            return new Summary(s.sessionId(), s.title(), s.projectId(), s.lastActiveAt());
        }
    }
}
