package com.ohmyproject.codex;

import com.ohmyproject.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 以子进程方式运行 codex exec / codex exec resume。
 *
 * - stdout 按行解析为 {@link CodexEvent} 并回调
 * - stderr 单独用一个虚拟线程 drain，避免阻塞管道
 * - prompt 经由 stdin 写入（命令行末尾用 "-"），避免 shell 引号转义
 */
@Component
public class CodexRunner {

    private static final Logger log = LoggerFactory.getLogger(CodexRunner.class);

    private final AppProperties props;
    private final CodexEventParser parser;

    public CodexRunner(AppProperties props, CodexEventParser parser) {
        this.props = props;
        this.parser = parser;
    }

    public void run(CodexRunRequest req, CodexEventHandler handler) {
        List<String> cmd = buildCommand(req);

        Process proc;
        try {
            proc = new ProcessBuilder(cmd).redirectErrorStream(false).start();
        } catch (IOException e) {
            log.error("[codex.launch.failed] cmd={}", cmd, e);
            handler.onComplete(-1, e);
            return;
        }

        Thread stderrPump = Thread.ofVirtual()
                .name("codex-stderr-" + proc.pid())
                .start(() -> drainStderr(proc.getErrorStream(), handler));

        try (OutputStream stdin = proc.getOutputStream()) {
            stdin.write(req.prompt().getBytes(StandardCharsets.UTF_8));
            stdin.flush();
        } catch (IOException e) {
            log.error("[codex.stdin.failed] pid={}", proc.pid(), e);
            proc.destroy();
            handler.onComplete(-1, e);
            return;
        }

        Throwable failure = null;
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                parser.parse(line).ifPresent(handler::onEvent);
            }
        } catch (IOException e) {
            failure = e;
        }

        int exitCode = -1;
        try {
            exitCode = proc.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            proc.destroy();
            if (failure == null) failure = e;
        }

        try {
            stderrPump.join(2_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        handler.onComplete(exitCode, failure);
    }

    private List<String> buildCommand(CodexRunRequest req) {
        List<String> cmd = new ArrayList<>();
        cmd.add(props.codex().executable());
        cmd.add("exec");
        boolean resume = req.resumeThreadId() != null && !req.resumeThreadId().isBlank();
        if (resume) {
            cmd.add("resume");
        }
        cmd.add("--json");
        if (props.codex().skipGitRepoCheck()) {
            cmd.add("--skip-git-repo-check");
        }
        // resume 子命令不支持 --sandbox / --color / --cd，已记录在会话中由原进程继承
        if (!resume) {
            cmd.add("--sandbox");
            cmd.add(props.codex().sandbox());
            cmd.add("--color");
            cmd.add(props.codex().color());
            cmd.add("--cd");
            cmd.add(req.projectPath());
        }
        if (resume) {
            cmd.add(req.resumeThreadId());
        }
        if (req.images() != null) {
            for (var img : req.images()) {
                cmd.add("-i");
                cmd.add(img.toAbsolutePath().toString());
            }
        }
        cmd.add("-");
        return cmd;
    }

    private void drainStderr(InputStream err, CodexEventHandler handler) {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(err, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                handler.onStderrLine(line);
            }
        } catch (IOException ignored) {}
    }
}
