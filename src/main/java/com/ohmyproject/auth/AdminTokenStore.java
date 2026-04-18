package com.ohmyproject.auth;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存中的管理员 token 存储。进程重启后 token 失效（MVP 可接受）。
 */
@Component
public class AdminTokenStore {

    public static final long DEFAULT_TTL_MILLIS = 24L * 60 * 60 * 1000;

    private final ConcurrentHashMap<String, Long> tokens = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    public String issue() {
        byte[] buf = new byte[24];
        random.nextBytes(buf);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
        tokens.put(token, System.currentTimeMillis() + DEFAULT_TTL_MILLIS);
        return token;
    }

    public boolean isValid(String token) {
        if (token == null) return false;
        Long exp = tokens.get(token);
        if (exp == null) return false;
        if (exp < System.currentTimeMillis()) {
            tokens.remove(token);
            return false;
        }
        return true;
    }

    public void revoke(String token) {
        if (token != null) tokens.remove(token);
    }
}
