package com.ohmyproject.attachment;

import com.ohmyproject.common.ApiException;
import com.ohmyproject.common.IdGenerator;
import com.ohmyproject.config.AppProperties;
import com.ohmyproject.session.ChatSession;
import com.ohmyproject.session.SessionDeletedEvent;
import com.ohmyproject.session.SessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Service
public class AttachmentService {

    private static final Logger log = LoggerFactory.getLogger(AttachmentService.class);
    private static final Pattern ID_PATTERN = Pattern.compile("^[a-zA-Z0-9-]+$");
    private static final Pattern NAME_SAFE = Pattern.compile("[^\\p{L}\\p{N}_.\\- ()\\[\\]]");

    private final AppProperties props;
    private final AttachmentRepository repo;
    private final SessionService sessions;
    private final Path uploadsRoot;

    public AttachmentService(AppProperties props, AttachmentRepository repo, SessionService sessions) {
        this.props = props;
        this.repo = repo;
        this.sessions = sessions;
        this.uploadsRoot = Paths.get(props.uploads().dir()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(uploadsRoot);
        } catch (IOException e) {
            throw new IllegalStateException("cannot create uploads dir: " + uploadsRoot, e);
        }
    }

    public Attachment upload(String sessionId, String userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(400, "file is required");
        }
        if (!ID_PATTERN.matcher(sessionId == null ? "" : sessionId).matches()) {
            throw new ApiException(400, "invalid sessionId");
        }
        if (file.getSize() > props.uploads().maxFileBytes()) {
            throw new ApiException(413, "file too large");
        }
        ChatSession session = sessions.get(sessionId, userId, false);

        String originalName = sanitizeName(file.getOriginalFilename());
        String ext = extractExt(originalName).toLowerCase(Locale.ROOT);
        AttachmentKind kind = classifyByExt(ext);

        byte[] head = readHead(file, 8192);
        String mimeType;
        if (kind == AttachmentKind.IMAGE) {
            mimeType = sniffImageMime(head);
            if (mimeType == null || !props.uploads().imageMimeWhitelist().contains(mimeType)) {
                throw new ApiException(400, "image magic bytes do not match a supported format");
            }
        } else {
            for (int i = 0; i < Math.min(head.length, 8192); i++) {
                if (head[i] == 0) {
                    throw new ApiException(400, "binary content detected; text files only");
                }
            }
            mimeType = probeTextMime(originalName, head);
            if (!isAllowedTextMime(mimeType)) {
                mimeType = "text/plain";
            }
        }

        String id = IdGenerator.newId();
        String storedFilename = id + ext;
        Path sessionDir = uploadsRoot.resolve(session.sessionId());
        Path target = sessionDir.resolve(storedFilename).toAbsolutePath().normalize();
        if (!target.startsWith(uploadsRoot) || !target.startsWith(sessionDir.toAbsolutePath().normalize())) {
            throw new ApiException(400, "invalid target path");
        }
        try {
            Files.createDirectories(sessionDir);
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new ApiException(500, "failed to save file");
        }

        long actualSize;
        try {
            actualSize = Files.size(target);
        } catch (IOException e) {
            actualSize = file.getSize();
        }

        Attachment a = new Attachment(
                id, session.sessionId(), originalName, storedFilename,
                mimeType, kind, actualSize, System.currentTimeMillis(), null
        );
        repo.save(a);
        return a;
    }

    public List<Attachment> resolveForUse(String sessionId, String userId, List<String> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        if (ids.size() > props.uploads().maxFilesPerMessage()) {
            throw new ApiException(400, "too many attachments");
        }
        sessions.get(sessionId, userId, false);
        List<Attachment> out = new ArrayList<>(ids.size());
        for (String id : ids) {
            if (id == null || !ID_PATTERN.matcher(id).matches()) {
                throw new ApiException(400, "invalid attachmentId");
            }
            Attachment a = repo.findById(id)
                    .orElseThrow(() -> new ApiException(404, "attachment not found: " + id));
            if (!sessionId.equals(a.sessionId())) {
                throw new ApiException(403, "attachment does not belong to session");
            }
            out.add(a);
        }
        return out;
    }

    public Path physicalPath(Attachment a) {
        Path p = uploadsRoot.resolve(a.sessionId()).resolve(a.storedFilename()).toAbsolutePath().normalize();
        if (!p.startsWith(uploadsRoot)) {
            throw new ApiException(500, "attachment path escapes root");
        }
        if (!Files.exists(p)) {
            throw new ApiException(410, "attachment file missing");
        }
        return p;
    }

    public void markBound(Collection<Attachment> atts) {
        if (atts == null) return;
        for (Attachment a : atts) {
            if (Boolean.TRUE.equals(a.boundToMessage())) continue;
            Attachment updated = new Attachment(
                    a.id(), a.sessionId(), a.originalName(), a.storedFilename(),
                    a.mimeType(), a.kind(), a.size(), a.createdAt(), Boolean.TRUE
            );
            repo.save(updated);
        }
    }

    public Attachment getForRead(String id, String userId, boolean admin) {
        if (id == null || !ID_PATTERN.matcher(id).matches()) {
            throw new ApiException(400, "invalid attachmentId");
        }
        Attachment a = repo.findById(id)
                .orElseThrow(() -> new ApiException(404, "attachment not found"));
        sessions.get(a.sessionId(), userId, admin);
        return a;
    }

    public Resource openRaw(Attachment a) {
        Path p = physicalPath(a);
        try {
            return new ByteArrayResource(Files.readAllBytes(p));
        } catch (IOException e) {
            throw new ApiException(500, "failed to read attachment");
        }
    }

    public record TextSlice(String originalName, String mimeType, long size, String text, boolean truncated) {}

    public List<TextSlice> readAsSlices(List<Attachment> texts) {
        List<TextSlice> out = new ArrayList<>();
        int budget = props.uploads().maxPromptAttachmentChars();
        int used = 0;
        long perFileMax = props.uploads().maxTextFileBytes();
        for (Attachment a : texts) {
            if (a.kind() != AttachmentKind.TEXT) continue;
            if (used >= budget) {
                out.add(new TextSlice(a.originalName(), a.mimeType(), a.size(),
                        "[omitted due to prompt length budget]", true));
                continue;
            }
            Path p = physicalPath(a);
            long size;
            try {
                size = Files.size(p);
            } catch (IOException e) {
                throw new ApiException(500, "failed to stat attachment");
            }
            boolean truncated = size > perFileMax;
            long toRead = Math.min(size, perFileMax);
            byte[] bytes;
            try (InputStream in = Files.newInputStream(p)) {
                bytes = in.readNBytes((int) toRead);
            } catch (IOException e) {
                throw new ApiException(500, "failed to read attachment");
            }
            String content = new String(bytes, StandardCharsets.UTF_8);
            int remaining = budget - used;
            if (content.length() > remaining) {
                content = content.substring(0, Math.max(0, remaining));
                truncated = true;
            }
            used += content.length();
            out.add(new TextSlice(a.originalName(), a.mimeType(), a.size(), content, truncated));
        }
        return out;
    }

    @EventListener
    public void onSessionDeleted(SessionDeletedEvent e) {
        deleteBySession(e.sessionId());
    }

    public void deleteBySession(String sessionId) {
        if (sessionId == null || !ID_PATTERN.matcher(sessionId).matches()) return;
        List<Attachment> all = repo.listAll();
        for (Attachment a : all) {
            if (sessionId.equals(a.sessionId())) {
                repo.delete(a.id());
            }
        }
        Path sessionDir = uploadsRoot.resolve(sessionId).toAbsolutePath().normalize();
        if (!sessionDir.startsWith(uploadsRoot)) return;
        if (!Files.exists(sessionDir)) return;
        try (Stream<Path> walk = Files.walk(sessionDir)) {
            walk.sorted((p1, p2) -> p2.getNameCount() - p1.getNameCount())
                    .forEach(p -> {
                        try { Files.deleteIfExists(p); } catch (IOException ignore) {}
                    });
        } catch (IOException e) {
            log.warn("failed to clean upload dir for session {}", sessionId, e);
        }
    }

    private AttachmentKind classifyByExt(String ext) {
        Set<String> imageExts = Set.of(".png", ".jpg", ".jpeg", ".webp", ".gif");
        if (imageExts.contains(ext)) return AttachmentKind.IMAGE;
        if (props.uploads().textExtensionWhitelist().contains(ext)) return AttachmentKind.TEXT;
        throw new ApiException(400, "unsupported file type: " + (ext.isEmpty() ? "(no ext)" : ext));
    }

    private static String sniffImageMime(byte[] head) {
        if (head.length >= 8
                && (head[0] & 0xff) == 0x89 && head[1] == 0x50 && head[2] == 0x4E && head[3] == 0x47) {
            return "image/png";
        }
        if (head.length >= 3
                && (head[0] & 0xff) == 0xFF && (head[1] & 0xff) == 0xD8 && (head[2] & 0xff) == 0xFF) {
            return "image/jpeg";
        }
        if (head.length >= 6
                && head[0] == 0x47 && head[1] == 0x49 && head[2] == 0x46 && head[3] == 0x38
                && (head[4] == 0x39 || head[4] == 0x37) && head[5] == 0x61) {
            return "image/gif";
        }
        if (head.length >= 12
                && head[0] == 0x52 && head[1] == 0x49 && head[2] == 0x46 && head[3] == 0x46
                && head[8] == 0x57 && head[9] == 0x45 && head[10] == 0x42 && head[11] == 0x50) {
            return "image/webp";
        }
        return null;
    }

    private static byte[] readHead(MultipartFile file, int max) {
        try (InputStream in = file.getInputStream()) {
            return in.readNBytes(max);
        } catch (IOException e) {
            throw new ApiException(500, "failed to read file head");
        }
    }

    private static String probeTextMime(String name, byte[] head) {
        try {
            Path tmp = Paths.get("probe" + extractExt(name));
            String probed = Files.probeContentType(tmp);
            if (probed != null) return probed;
        } catch (IOException ignore) {}
        return "text/plain";
    }

    private boolean isAllowedTextMime(String mime) {
        if (mime == null) return false;
        for (String prefix : props.uploads().textMimePrefixes()) {
            if (mime.startsWith(prefix)) return true;
        }
        return false;
    }

    private static String sanitizeName(String raw) {
        if (raw == null || raw.isBlank()) return "file";
        String base;
        try {
            base = Paths.get(raw).getFileName().toString();
        } catch (Exception e) {
            base = raw;
        }
        base = base.replace('\u0000', ' ').replaceAll("[\\r\\n\\t]", " ").trim();
        base = NAME_SAFE.matcher(base).replaceAll("_");
        if (base.isEmpty()) base = "file";
        if (base.length() > 100) base = base.substring(0, 100);
        return base;
    }

    private static String extractExt(String name) {
        if (name == null) return "";
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) return "";
        String ext = name.substring(dot);
        if (ext.length() > 10) return "";
        return ext;
    }
}
