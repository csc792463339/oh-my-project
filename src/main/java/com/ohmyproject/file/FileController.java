package com.ohmyproject.file;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/files")
public class FileController {

    private final FileService service;

    public FileController(FileService service) {
        this.service = service;
    }

    @GetMapping
    public FileService.FileContent read(
            @RequestParam("sessionId") String sessionId,
            @RequestParam("path") String path) {
        return service.read(sessionId, path);
    }
}
