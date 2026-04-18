package com.ohmyproject.session;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/sessions")
public class AdminSessionController {

    private final SessionService service;

    public AdminSessionController(SessionService service) {
        this.service = service;
    }

    @GetMapping
    public List<AdminSummary> list() {
        return service.listAllForAdmin().stream().map(AdminSummary::from).toList();
    }

    @GetMapping("/{id}")
    public ChatSession get(@PathVariable String id) {
        return service.get(id, null, true);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        service.delete(id, null, true);
        return ResponseEntity.noContent().build();
    }

    public record AdminSummary(String sessionId, String title, String userId, String projectId, long lastActiveAt) {
        static AdminSummary from(ChatSession s) {
            return new AdminSummary(s.sessionId(), s.title(), s.userId(), s.projectId(), s.lastActiveAt());
        }
    }
}
