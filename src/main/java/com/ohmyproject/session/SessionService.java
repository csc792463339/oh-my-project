package com.ohmyproject.session;

import com.ohmyproject.common.ApiException;
import com.ohmyproject.common.IdGenerator;
import com.ohmyproject.common.RequestContext;
import com.ohmyproject.project.Project;
import com.ohmyproject.project.ProjectService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
public class SessionService {

    private final SessionRepository repo;
    private final ProjectService projects;
    private final ApplicationEventPublisher publisher;

    public SessionService(SessionRepository repo, ProjectService projects,
                          ApplicationEventPublisher publisher) {
        this.repo = repo;
        this.projects = projects;
        this.publisher = publisher;
    }

    public ChatSession create(String userId, String projectId, String title) {
        requireUserId(userId);
        Project p = projects.get(projectId);
        String finalTitle = (title == null || title.isBlank())
                ? "新会话"
                : truncate(title, 40);
        long now = System.currentTimeMillis();
        ChatSession s = new ChatSession(
                IdGenerator.newId(), userId, p.id(), p.path(),
                null, finalTitle, List.of(), now, now
        );
        repo.save(s);
        return s;
    }

    public ChatSession get(String sessionId, String userId, boolean admin) {
        ChatSession s = repo.findById(sessionId)
                .orElseThrow(() -> new ApiException(404, "session not found"));
        if (!admin && !s.userId().equals(userId)) {
            throw new ApiException(404, "session not found");
        }
        return s;
    }

    public List<ChatSession> listForUser(String userId) {
        requireUserId(userId);
        return repo.listAll().stream()
                .filter(s -> userId.equals(s.userId()))
                .sorted(Comparator.comparingLong(ChatSession::lastActiveAt).reversed())
                .toList();
    }

    public List<ChatSession> listAllForAdmin() {
        return repo.listAll().stream()
                .sorted(Comparator.comparingLong(ChatSession::lastActiveAt).reversed())
                .toList();
    }

    public void delete(String sessionId, String userId, boolean admin) {
        ChatSession s = get(sessionId, userId, admin);
        repo.delete(s.sessionId());
        publisher.publishEvent(new SessionDeletedEvent(s.sessionId()));
    }

    public ChatSession appendMessage(ChatSession s, Message m) {
        ChatSession updated = s.withMessage(m);
        // 首条 user 消息作为自动标题（如果当前还是"新会话"）
        if ("新会话".equals(updated.title())
                && "user".equals(m.role())
                && updated.messages().size() == 1) {
            updated = new ChatSession(
                    updated.sessionId(), updated.userId(), updated.projectId(),
                    updated.projectPath(), updated.codexThreadId(),
                    truncate(m.content(), 20),
                    updated.messages(),
                    updated.createdAt(), updated.lastActiveAt()
            );
        }
        repo.save(updated);
        return updated;
    }

    public ChatSession updateThreadId(ChatSession s, String threadId) {
        if (s.codexThreadId() != null || threadId == null) return s;
        ChatSession updated = s.withThreadId(threadId);
        repo.save(updated);
        return updated;
    }

    public void touch(ChatSession s) {
        repo.save(s.touched());
    }

    public void requireUserId(String uid) {
        if (uid == null || uid.isBlank()) {
            throw new ApiException(400, "X-User-Id header required");
        }
    }

    public static String currentUserId() {
        return RequestContext.getUserId();
    }

    public static boolean isAdmin() {
        return RequestContext.isAdmin();
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        String t = s.replaceAll("\\s+", " ").trim();
        return t.length() <= max ? t : t.substring(0, max);
    }
}
