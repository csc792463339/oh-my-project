package com.ohmyproject.session;

import com.ohmyproject.attachment.Attachment;

import java.util.List;

public record Message(String role, String content, long ts, List<Attachment> attachments) {
    public static Message user(String content) {
        return new Message("user", content, System.currentTimeMillis(), null);
    }
    public static Message user(String content, List<Attachment> attachments) {
        List<Attachment> copy = (attachments == null || attachments.isEmpty())
                ? null
                : List.copyOf(attachments);
        return new Message("user", content, System.currentTimeMillis(), copy);
    }
    public static Message assistant(String content) {
        return new Message("assistant", content, System.currentTimeMillis(), null);
    }
}
