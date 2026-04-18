package com.ohmyproject.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        Admin admin,
        Codex codex,
        Data data,
        Uploads uploads
) {
    public record Admin(String password) {}

    public record Codex(
            String executable,
            String sandbox,
            String color,
            boolean skipGitRepoCheck
    ) {}

    public record Data(String dir) {}

    public record Uploads(
            String dir,
            String metaDir,
            long maxFileBytes,
            long maxTextFileBytes,
            int maxFilesPerMessage,
            List<String> imageMimeWhitelist,
            List<String> textExtensionWhitelist,
            List<String> textMimePrefixes,
            int maxPromptAttachmentChars
    ) {}
}
