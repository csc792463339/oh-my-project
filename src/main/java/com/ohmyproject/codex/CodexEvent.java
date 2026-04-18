package com.ohmyproject.codex;

/**
 * 从 Codex CLI `exec --json` 输出解析得到的事件。
 * 只保留上游消费需要的字段；command_execution 的命令与输出被刻意丢弃，
 * 因为 PRD 规定不得把 Codex 自发执行的 shell 命令/输出透传给前端。
 */
public sealed interface CodexEvent {

    record ThreadStarted(String threadId) implements CodexEvent {}

    record TurnStarted() implements CodexEvent {}

    record AgentMessage(String itemId, String text) implements CodexEvent {}

    record CommandExecutionStatus(String itemId, String status) implements CodexEvent {}

    record TurnCompleted(long inputTokens, long outputTokens) implements CodexEvent {}

    /** codex exec 在发生业务错误时（如达到配额上限）会发此事件，之后紧跟 turn.failed。 */
    record ErrorEvent(String message) implements CodexEvent {}

    /** 一轮对话失败，携带错误原因。 */
    record TurnFailed(String message) implements CodexEvent {}

    record Unknown(String rawType) implements CodexEvent {}
}
