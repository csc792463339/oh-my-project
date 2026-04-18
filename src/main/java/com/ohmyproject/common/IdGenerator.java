package com.ohmyproject.common;

import java.util.UUID;

public final class IdGenerator {
    private IdGenerator() {}
    public static String newId() { return UUID.randomUUID().toString(); }
}
