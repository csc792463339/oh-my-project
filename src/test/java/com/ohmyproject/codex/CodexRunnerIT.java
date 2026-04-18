package com.ohmyproject.codex;

import com.ohmyproject.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 需要本地存在可用的 codex CLI 并已登录。默认不启用，通过
 *   mvn -Dcodex.it=true test
 * 触发。
 */
@EnabledIfSystemProperty(named = "codex.it", matches = "true")
class CodexRunnerIT {

    @Test
    void real_exec_returns_agent_message_and_thread_id() throws Exception {
        Path tmp = Files.createTempDirectory("codex-it-");
        try {
            AppProperties props = new AppProperties(
                    new AppProperties.Admin("x"),
                    new AppProperties.Codex("codex", "read-only", "never", true),
                    new AppProperties.Data("./data"),
                    null
            );
            CodexRunner runner = new CodexRunner(props, new CodexEventParser());

            CountDownLatch done = new CountDownLatch(1);
            AtomicReference<String> threadId = new AtomicReference<>();
            AtomicReference<String> lastAssistant = new AtomicReference<>();
            AtomicInteger exit = new AtomicInteger(-99);
            List<String> unexpected = new CopyOnWriteArrayList<>();

            runner.run(
                    new CodexRunRequest("用一句话回答：1+1等于几？", tmp.toString(), null),
                    new CodexEventHandler() {
                        @Override public void onEvent(CodexEvent event) {
                            switch (event) {
                                case CodexEvent.ThreadStarted ts -> threadId.set(ts.threadId());
                                case CodexEvent.AgentMessage am -> lastAssistant.set(am.text());
                                case CodexEvent.CommandExecutionStatus ignored -> {}
                                case CodexEvent.TurnStarted ignored -> {}
                                case CodexEvent.TurnCompleted ignored -> {}
                                case CodexEvent.ErrorEvent ignored -> {}
                                case CodexEvent.TurnFailed ignored -> {}
                                case CodexEvent.Unknown u -> unexpected.add(u.rawType());
                            }
                        }
                        @Override public void onComplete(int code, Throwable err) {
                            exit.set(code);
                            done.countDown();
                        }
                    });

            assertThat(done.await(90, TimeUnit.SECONDS)).as("codex finished in time").isTrue();
            assertThat(exit.get()).isEqualTo(0);
            assertThat(threadId.get()).as("thread id captured").isNotBlank();
            assertThat(lastAssistant.get()).as("got an agent message").isNotBlank();
        } finally {
            Files.walk(tmp).sorted((a, b) -> b.compareTo(a)).forEach(p -> p.toFile().delete());
        }
    }
}
