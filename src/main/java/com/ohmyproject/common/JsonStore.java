package com.ohmyproject.common;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 基于本地文件系统的 JSON 存储。每个实体一个文件；写入采用
 * 先写临时文件再原子 move，避免半写状态。调用方负责外部同步。
 */
public class JsonStore<T> {

    private final Path baseDir;
    private final ObjectMapper mapper;
    private final Class<T> type;

    public JsonStore(Path baseDir, ObjectMapper mapper, Class<T> type) {
        this.baseDir = baseDir;
        this.mapper = mapper;
        this.type = type;
        try {
            Files.createDirectories(baseDir);
        } catch (IOException e) {
            throw new IllegalStateException("cannot create " + baseDir, e);
        }
    }

    public Optional<T> findById(String id) {
        Path p = filePath(id);
        if (!Files.exists(p)) return Optional.empty();
        try {
            return Optional.of(mapper.readValue(p.toFile(), type));
        } catch (IOException e) {
            throw new IllegalStateException("read failed: " + p, e);
        }
    }

    public List<T> listAll() {
        List<T> out = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(baseDir, "*.json")) {
            for (Path p : stream) {
                out.add(mapper.readValue(p.toFile(), type));
            }
        } catch (IOException e) {
            throw new IllegalStateException("list failed: " + baseDir, e);
        }
        return out;
    }

    public void save(String id, T value) {
        Path target = filePath(id);
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        try {
            mapper.writerWithDefaultPrettyPrinter().writeValue(tmp.toFile(), value);
            Files.move(tmp, target,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new IllegalStateException("save failed: " + target, e);
        }
    }

    public boolean delete(String id) {
        try {
            return Files.deleteIfExists(filePath(id));
        } catch (IOException e) {
            throw new IllegalStateException("delete failed", e);
        }
    }

    public boolean exists(String id) {
        return Files.exists(filePath(id));
    }

    private Path filePath(String id) {
        if (id == null || id.contains("/") || id.contains("\\") || id.contains("..")) {
            throw new IllegalArgumentException("invalid id: " + id);
        }
        return baseDir.resolve(id + ".json");
    }

    // 便于偶尔需要的泛型读取（例如列表内的 Map）
    public <V> V readAs(Path p, TypeReference<V> ref) {
        try {
            return mapper.readValue(p.toFile(), ref);
        } catch (IOException e) {
            throw new IllegalStateException("read failed: " + p, e);
        }
    }
}
