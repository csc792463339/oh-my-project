package com.ohmyproject.chat;

import com.ohmyproject.attachment.Attachment;
import com.ohmyproject.attachment.AttachmentKind;
import com.ohmyproject.attachment.AttachmentService;
import com.ohmyproject.codex.CodexEvent;
import com.ohmyproject.codex.CodexEventHandler;
import com.ohmyproject.codex.CodexRunRequest;
import com.ohmyproject.codex.CodexRunner;
import com.ohmyproject.common.ApiException;
import com.ohmyproject.project.Project;
import com.ohmyproject.project.ProjectService;
import com.ohmyproject.session.ChatSession;
import com.ohmyproject.session.Message;
import com.ohmyproject.session.SessionLockRegistry;
import com.ohmyproject.session.SessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class ChatStreamService {

    private static final Logger log = LoggerFactory.getLogger(ChatStreamService.class);

    private final SessionService sessions;
    private final ProjectService projects;
    private final SessionLockRegistry lockRegistry;
    private final PromptBuilder promptBuilder;
    private final CodexRunner runner;
    private final AttachmentService attachments;
    private final ExecutorService executor =
            Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("chat-", 0).factory());

    public ChatStreamService(SessionService sessions, ProjectService projects,
                             SessionLockRegistry lockRegistry, PromptBuilder promptBuilder,
                             CodexRunner runner, AttachmentService attachments) {
        this.sessions = sessions;
        this.projects = projects;
        this.lockRegistry = lockRegistry;
        this.promptBuilder = promptBuilder;
        this.runner = runner;
        this.attachments = attachments;
    }

    public SseEmitter stream(String userId, String sessionId, String userMessage,
                             List<String> attachmentIds) {
        if (userMessage == null || userMessage.isBlank()) {
            throw new ApiException(400, "message required");
        }
        sessions.requireUserId(userId);

        ChatSession session = sessions.get(sessionId, userId, false);
        SessionLockRegistry.Handle lock = lockRegistry.tryAcquire(sessionId)
                .orElseThrow(() -> new ApiException(409, "session busy"));

        try {
            List<Attachment> resolved = attachments.resolveForUse(sessionId, userId, attachmentIds);
            List<Attachment> imageAtts = new ArrayList<>();
            List<Attachment> textAtts = new ArrayList<>();
            for (Attachment a : resolved) {
                if (a.kind() == AttachmentKind.IMAGE) imageAtts.add(a);
                else if (a.kind() == AttachmentKind.TEXT) textAtts.add(a);
            }
            List<AttachmentService.TextSlice> textSlices = attachments.readAsSlices(textAtts);
            List<Path> imagePaths = new ArrayList<>();
            for (Attachment a : imageAtts) {
                imagePaths.add(attachments.physicalPath(a));
            }

            ChatSession afterUser = sessions.appendMessage(session, Message.user(userMessage, resolved));
            attachments.markBound(resolved);

            boolean firstTurn = afterUser.codexThreadId() == null;
            Project project = firstTurn ? projects.get(afterUser.projectId()) : null;
            String prompt = promptBuilder.build(afterUser, project, userMessage, firstTurn,
                    textSlices, imageAtts.size());

            log.info("[user.input] session={} message={} attachments={}",
                    sessionId, userMessage, resolved.size());
            log.info("[prompt] session={}\n{}", sessionId, prompt);

            SseEmitter emitter = new SseEmitter(0L);
            AtomicReference<String> capturedThreadId = new AtomicReference<>();
            StringBuilder assistantBuf = new StringBuilder();

            executor.execute(() -> runInBackground(
                    sessionId, afterUser, prompt, imagePaths, emitter, capturedThreadId,
                    assistantBuf, lock));

            return emitter;
        } catch (RuntimeException e) {
            lock.close();
            throw e;
        }
    }

    private void runInBackground(String sessionId, ChatSession afterUser, String prompt,
                                 List<Path> images,
                                 SseEmitter emitter, AtomicReference<String> capturedThreadId,
                                 StringBuilder assistantBuf, SessionLockRegistry.Handle lock) {
        AtomicReference<String> codexErrorMsg = new AtomicReference<>();
        try {
            runner.run(new CodexRunRequest(prompt, afterUser.projectPath(),
                            afterUser.codexThreadId(), images),
                    new CodexEventHandler() {
                        @Override public void onEvent(CodexEvent event) {
                            if (event instanceof CodexEvent.ErrorEvent ee && ee.message() != null) {
                                codexErrorMsg.compareAndSet(null, ee.message());
                            } else if (event instanceof CodexEvent.TurnFailed tf && tf.message() != null) {
                                codexErrorMsg.compareAndSet(null, tf.message());
                            }
                            dispatch(emitter, event, assistantBuf, capturedThreadId);
                        }
                        @Override public void onStderrLine(String line) {
                            log.warn("[codex.stderr] session={} {}", sessionId, line);
                        }
                        @Override public void onComplete(int exitCode, Throwable error) {
                            Throwable effective = error;
                            if (effective == null && codexErrorMsg.get() != null) {
                                effective = new CodexReportedError(codexErrorMsg.get());
                            }
                            finish(emitter, sessionId, afterUser, assistantBuf, capturedThreadId,
                                    exitCode, effective, lock);
                        }
                    });
        } catch (Throwable t) {
            finish(emitter, sessionId, afterUser, assistantBuf, capturedThreadId, -1, t, lock);
        }
    }

    public static final class CodexReportedError extends RuntimeException {
        public CodexReportedError(String msg) { super(msg); }
    }

    private void dispatch(SseEmitter emitter, CodexEvent event,
                          StringBuilder assistantBuf, AtomicReference<String> threadId) {
        try {
            switch (event) {
                case CodexEvent.ThreadStarted ts -> threadId.set(ts.threadId());
                case CodexEvent.TurnStarted ignored ->
                        emitter.send(SseEmitter.event().name("status")
                                .data(Map.of("phase", "started")));
                case CodexEvent.AgentMessage am -> {
                    String text = am.text() == null ? "" : am.text();
                    if (assistantBuf.length() > 0) assistantBuf.append("\n\n");
                    assistantBuf.append(text);
                    emitter.send(SseEmitter.event().name("message")
                            .data(mapOfNullable("delta", text)));
                }
                case CodexEvent.CommandExecutionStatus ce ->
                        emitter.send(SseEmitter.event().name("status")
                                .data(mapOfNullable(
                                        "phase", "analyzing",
                                        "status", ce.status())));
                case CodexEvent.TurnCompleted tc ->
                        emitter.send(SseEmitter.event().name("status")
                                .data(Map.of("phase", "turn_completed",
                                        "inputTokens", tc.inputTokens(),
                                        "outputTokens", tc.outputTokens())));
                case CodexEvent.ErrorEvent ignored -> {}
                case CodexEvent.TurnFailed ignored -> {}
                case CodexEvent.Unknown ignored -> {}
            }
        } catch (IOException ignored) {
            // 客户端断开：不再尝试发送，交给 emitter 的生命周期处理
        }
    }

    private void finish(SseEmitter emitter, String sessionId, ChatSession base,
                        StringBuilder assistantBuf, AtomicReference<String> threadId,
                        int exitCode, Throwable error, SessionLockRegistry.Handle lock) {
        try {
            ChatSession current = base;
            String tid = threadId.get();
            if (tid != null && current.codexThreadId() == null) {
                current = sessions.updateThreadId(current, tid);
            }
            String assistantText = assistantBuf.toString().trim();
            if (!assistantText.isEmpty()) {
                current = sessions.appendMessage(current, Message.assistant(assistantText));
                log.info("[assistant.output] session={}\n{}", sessionId, assistantText);
            } else {
                sessions.touch(current);
            }

            if (error != null || exitCode != 0) {
                String msg = describeError(error, exitCode);
                log.warn("[chat.error] session={} msg={}", sessionId, msg);
                try {
                    emitter.send(SseEmitter.event().name("error")
                            .data(mapOfNullable("code", 500, "message", msg)));
                } catch (IOException ignored) {}
            } else {
                try {
                    emitter.send(SseEmitter.event().name("done")
                            .data(Map.of("sessionId", current.sessionId())));
                } catch (IOException ignored) {}
            }
        } catch (Throwable t) {
            log.error("[chat.finish.failed] session={}", sessionId, t);
        } finally {
            try { emitter.complete(); } catch (Exception ignored) {}
            lock.close();
        }
    }

    private static String describeError(Throwable error, int exitCode) {
        if (error != null) {
            String m = error.getMessage();
            if (m != null && !m.isBlank()) return m;
            return error.getClass().getSimpleName();
        }
        return "codex exited with code " + exitCode;
    }

    private static Map<String, Object> mapOfNullable(Object... kv) {
        if (kv.length % 2 != 0) throw new IllegalArgumentException("odd kv length");
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }
}
