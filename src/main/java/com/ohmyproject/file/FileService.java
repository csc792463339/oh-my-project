package com.ohmyproject.file;

import com.ohmyproject.common.ApiException;
import com.ohmyproject.session.ChatSession;
import com.ohmyproject.session.SessionService;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class FileService {

    private static final long MAX_BYTES = 512L * 1024L;

    private final SessionService sessions;

    public FileService(SessionService sessions) {
        this.sessions = sessions;
    }

    public FileContent read(String sessionId, String relative) {
        String userId = SessionService.currentUserId();
        ChatSession s = sessions.get(sessionId, userId, SessionService.isAdmin());

        Path root = safeRoot(s.projectPath());
        Path target = resolveWithin(root, relative);

        if (!Files.exists(target)) {
            throw new ApiException(404, "file not found: " + relative);
        }
        if (Files.isDirectory(target)) {
            throw new ApiException(400, "path is a directory: " + relative);
        }

        long size;
        try {
            size = Files.size(target);
        } catch (IOException e) {
            throw new ApiException(500, "failed to stat file");
        }

        boolean truncated = size > MAX_BYTES;
        byte[] bytes;
        try {
            if (truncated) {
                bytes = new byte[(int) MAX_BYTES];
                try (var in = Files.newInputStream(target)) {
                    int read = in.readNBytes(bytes, 0, (int) MAX_BYTES);
                    if (read < MAX_BYTES) {
                        byte[] shrunk = new byte[read];
                        System.arraycopy(bytes, 0, shrunk, 0, read);
                        bytes = shrunk;
                    }
                }
            } else {
                bytes = Files.readAllBytes(target);
            }
        } catch (IOException e) {
            throw new ApiException(500, "failed to read file");
        }

        String content = new String(bytes, StandardCharsets.UTF_8);
        return new FileContent(relative, content, size, truncated);
    }

    private static Path safeRoot(String projectPath) {
        if (projectPath == null || projectPath.isBlank()) {
            throw new ApiException(400, "session has no project path");
        }
        return Path.of(projectPath).toAbsolutePath().normalize();
    }

    private static Path resolveWithin(Path root, String relative) {
        if (relative == null || relative.isBlank()) {
            throw new ApiException(400, "path required");
        }
        String cleaned = relative.trim();
        while (cleaned.startsWith("/")) cleaned = cleaned.substring(1);
        Path resolved = root.resolve(cleaned).toAbsolutePath().normalize();
        if (!resolved.startsWith(root)) {
            throw new ApiException(403, "path outside project");
        }
        return resolved;
    }

    public record FileContent(String path, String content, long size, boolean truncated) {}
}
