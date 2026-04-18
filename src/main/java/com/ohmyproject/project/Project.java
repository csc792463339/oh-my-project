package com.ohmyproject.project;

public record Project(
        String id,
        String name,
        String path,
        String description,
        long createdAt,
        long updatedAt
) {}
