package com.ohmyproject.codex;

import java.nio.file.Path;
import java.util.List;

/**
 * 一次 Codex 调用的请求参数。
 *
 * @param prompt            要发送给模型的完整提示文本（系统规则 + 项目 Prompt + 用户问题）
 * @param projectPath       代码仓库路径，映射到 codex exec --cd
 * @param resumeThreadId    非 null 时触发 `codex exec resume <id>` 以延续上下文
 * @param images            可附加的图片绝对路径列表，映射到可重复的 `-i/--image` 参数
 */
public record CodexRunRequest(
        String prompt,
        String projectPath,
        String resumeThreadId,
        List<Path> images
) {
    public CodexRunRequest {
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("prompt must not be blank");
        }
        if (projectPath == null || projectPath.isBlank()) {
            throw new IllegalArgumentException("projectPath must not be blank");
        }
        images = images == null ? List.of() : List.copyOf(images);
    }

    public CodexRunRequest(String prompt, String projectPath, String resumeThreadId) {
        this(prompt, projectPath, resumeThreadId, List.of());
    }
}
