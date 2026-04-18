package com.ohmyproject.attachment;

public record Attachment(
        String id,
        String sessionId,
        String originalName,
        String storedFilename,
        String mimeType,
        AttachmentKind kind,
        long size,
        long createdAt,
        Boolean boundToMessage
) {
}
