package com.ohmyproject.attachment;

import com.ohmyproject.session.SessionService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/uploads")
public class AttachmentController {

    private final AttachmentService service;

    public AttachmentController(AttachmentService service) {
        this.service = service;
    }

    @PostMapping
    public Attachment upload(@RequestParam("sessionId") String sessionId,
                             @RequestParam("file") MultipartFile file) {
        String userId = SessionService.currentUserId();
        return service.upload(sessionId, userId, file);
    }

    @GetMapping("/{id}/raw")
    public ResponseEntity<Resource> raw(@PathVariable("id") String id) {
        String userId = SessionService.currentUserId();
        boolean admin = SessionService.isAdmin();
        Attachment a = service.getForRead(id, userId, admin);
        Resource resource = service.openRaw(a);

        String filename = URLEncoder.encode(a.originalName(), StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(a.mimeType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + filename + "\"; filename*=UTF-8''" + filename)
                .contentLength(a.size())
                .body(resource);
    }
}
