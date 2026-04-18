package com.ohmyproject.session;

import java.util.ArrayList;
import java.util.List;

public record ChatSession(
        String sessionId,
        String userId,
        String projectId,
        String projectPath,
        String codexThreadId,
        String title,
        List<Message> messages,
        long createdAt,
        long lastActiveAt
) {
    public ChatSession {
        messages = messages == null ? new ArrayList<>() : new ArrayList<>(messages);
    }

    public ChatSession withMessage(Message m) {
        List<Message> next = new ArrayList<>(messages);
        next.add(m);
        return new ChatSession(sessionId, userId, projectId, projectPath, codexThreadId,
                title, next, createdAt, System.currentTimeMillis());
    }

    public ChatSession withThreadId(String threadId) {
        return new ChatSession(sessionId, userId, projectId, projectPath, threadId,
                title, messages, createdAt, System.currentTimeMillis());
    }

    public ChatSession touched() {
        return new ChatSession(sessionId, userId, projectId, projectPath, codexThreadId,
                title, messages, createdAt, System.currentTimeMillis());
    }
}
