package com.ohmyproject.chat;

import com.ohmyproject.common.RequestContext;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatStreamService streamService;

    public ChatController(ChatStreamService streamService) {
        this.streamService = streamService;
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestBody ChatRequest req) {
        return streamService.stream(RequestContext.getUserId(), req.sessionId(), req.message(),
                req.attachmentIds());
    }

    public record ChatRequest(String sessionId, String message, List<String> attachmentIds) {}
}
