package com.ohmyproject.codex;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class CodexEventParser {

    private static final Logger log = LoggerFactory.getLogger(CodexEventParser.class);

    private final ObjectMapper mapper = new ObjectMapper();

    public Optional<CodexEvent> parse(String line) {
        if (line == null || line.isBlank()) {
            return Optional.empty();
        }
        JsonNode root;
        try {
            root = mapper.readTree(line);
        } catch (Exception e) {
            log.warn("codex non-json line ignored: {}", line);
            return Optional.empty();
        }
        String type = text(root, "type");
        if (type == null) return Optional.empty();

        return switch (type) {
            case "thread.started" -> Optional.of(new CodexEvent.ThreadStarted(text(root, "thread_id")));
            case "turn.started"   -> Optional.of(new CodexEvent.TurnStarted());
            case "turn.completed" -> parseTurnCompleted(root);
            case "item.started"   -> parseItem(root, false);
            case "item.completed" -> parseItem(root, true);
            case "error"          -> Optional.of(new CodexEvent.ErrorEvent(text(root, "message")));
            case "turn.failed"    -> Optional.of(new CodexEvent.TurnFailed(nestedMessage(root)));
            default -> Optional.of(new CodexEvent.Unknown(type));
        };
    }

    private Optional<CodexEvent> parseItem(JsonNode root, boolean completed) {
        JsonNode item = root.get("item");
        if (item == null || !item.isObject()) return Optional.empty();
        String itemType = text(item, "type");
        String itemId = text(item, "id");
        if (itemType == null) return Optional.empty();

        return switch (itemType) {
            case "agent_message" -> {
                if (!completed) yield Optional.empty();
                String textVal = text(item, "text");
                yield textVal == null ? Optional.empty()
                        : Optional.of(new CodexEvent.AgentMessage(itemId, textVal));
            }
            case "command_execution" -> Optional.of(new CodexEvent.CommandExecutionStatus(
                    itemId, text(item, "status")));
            default -> Optional.empty();
        };
    }

    private Optional<CodexEvent> parseTurnCompleted(JsonNode root) {
        JsonNode usage = root.get("usage");
        long in = usage != null ? usage.path("input_tokens").asLong(0) : 0;
        long out = usage != null ? usage.path("output_tokens").asLong(0) : 0;
        return Optional.of(new CodexEvent.TurnCompleted(in, out));
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return (v == null || v.isNull()) ? null : v.asText();
    }

    /** turn.failed 的错误消息在 error.message 下。 */
    private static String nestedMessage(JsonNode root) {
        JsonNode err = root.get("error");
        if (err == null || !err.isObject()) return null;
        return text(err, "message");
    }
}
