package com.ohmyproject.codex;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class CodexEventParserTest {

    private final CodexEventParser parser = new CodexEventParser();

    @Test
    void parses_thread_started() {
        var ev = parser.parse("{\"type\":\"thread.started\",\"thread_id\":\"abc-123\"}");
        assertThat(ev).containsInstanceOf(CodexEvent.ThreadStarted.class);
        assertThat(((CodexEvent.ThreadStarted) ev.orElseThrow()).threadId()).isEqualTo("abc-123");
    }

    @Test
    void parses_turn_started() {
        var ev = parser.parse("{\"type\":\"turn.started\"}");
        assertThat(ev).containsInstanceOf(CodexEvent.TurnStarted.class);
    }

    @Test
    void parses_agent_message_on_completed() {
        String line = "{\"type\":\"item.completed\",\"item\":{\"id\":\"item_3\",\"type\":\"agent_message\",\"text\":\"1+1等于2。\"}}";
        var ev = parser.parse(line).orElseThrow();
        assertThat(ev).isInstanceOf(CodexEvent.AgentMessage.class);
        var msg = (CodexEvent.AgentMessage) ev;
        assertThat(msg.itemId()).isEqualTo("item_3");
        assertThat(msg.text()).isEqualTo("1+1等于2。");
    }

    @Test
    void skips_agent_message_on_started() {
        String line = "{\"type\":\"item.started\",\"item\":{\"id\":\"item_1\",\"type\":\"agent_message\",\"text\":\"partial\"}}";
        assertThat(parser.parse(line)).isEmpty();
    }

    @Test
    void command_execution_only_exposes_status_not_command() {
        String line = "{\"type\":\"item.started\",\"item\":{\"id\":\"item_1\",\"type\":\"command_execution\","
                + "\"command\":\"rm -rf /\",\"aggregated_output\":\"danger\",\"exit_code\":null,\"status\":\"in_progress\"}}";
        var ev = parser.parse(line).orElseThrow();
        assertThat(ev).isInstanceOf(CodexEvent.CommandExecutionStatus.class);
        var ce = (CodexEvent.CommandExecutionStatus) ev;
        assertThat(ce.status()).isEqualTo("in_progress");
        // 关键：command / output 不在事件里，无法被外层发往前端
    }

    @Test
    void parses_turn_completed_with_usage() {
        String line = "{\"type\":\"turn.completed\",\"usage\":{\"input_tokens\":100,\"cached_input_tokens\":40,\"output_tokens\":20}}";
        var ev = parser.parse(line).orElseThrow();
        assertThat(ev).isInstanceOf(CodexEvent.TurnCompleted.class);
        var tc = (CodexEvent.TurnCompleted) ev;
        assertThat(tc.inputTokens()).isEqualTo(100);
        assertThat(tc.outputTokens()).isEqualTo(20);
    }

    @Test
    void unknown_type_returns_unknown_event() {
        var ev = parser.parse("{\"type\":\"some.future.event\"}").orElseThrow();
        assertThat(ev).isInstanceOf(CodexEvent.Unknown.class);
        assertThat(((CodexEvent.Unknown) ev).rawType()).isEqualTo("some.future.event");
    }

    @Test
    void blank_and_non_json_lines_are_skipped() {
        assertThat(parser.parse("")).isEmpty();
        assertThat(parser.parse(null)).isEmpty();
        assertThat(parser.parse("not json at all")).isEmpty();
    }

    @Test
    void missing_type_returns_empty() {
        assertThat(parser.parse("{\"foo\":\"bar\"}")).isEmpty();
    }

    @Test
    void item_without_type_is_skipped() {
        Optional<CodexEvent> ev = parser.parse("{\"type\":\"item.completed\",\"item\":{\"id\":\"x\"}}");
        assertThat(ev).isEmpty();
    }

    @Test
    void parses_error_event() {
        var ev = parser.parse("{\"type\":\"error\",\"message\":\"usage limit hit\"}").orElseThrow();
        assertThat(ev).isInstanceOf(CodexEvent.ErrorEvent.class);
        assertThat(((CodexEvent.ErrorEvent) ev).message()).isEqualTo("usage limit hit");
    }

    @Test
    void parses_turn_failed_with_nested_message() {
        String line = "{\"type\":\"turn.failed\",\"error\":{\"message\":\"quota exceeded\"}}";
        var ev = parser.parse(line).orElseThrow();
        assertThat(ev).isInstanceOf(CodexEvent.TurnFailed.class);
        assertThat(((CodexEvent.TurnFailed) ev).message()).isEqualTo("quota exceeded");
    }
}
