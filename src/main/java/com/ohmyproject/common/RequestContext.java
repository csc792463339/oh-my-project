package com.ohmyproject.common;

/**
 * 线程局部的请求上下文。由 filter 写入，controller / service 读取。
 * SSE 场景下我们会在进入异步前把 userId 显式传给下层，避免跨线程取值。
 */
public final class RequestContext {
    private static final ThreadLocal<String> USER_ID = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> ADMIN = ThreadLocal.withInitial(() -> false);

    private RequestContext() {}

    public static void setUserId(String userId) { USER_ID.set(userId); }
    public static String getUserId() { return USER_ID.get(); }
    public static void setAdmin(boolean admin) { ADMIN.set(admin); }
    public static boolean isAdmin() { return Boolean.TRUE.equals(ADMIN.get()); }

    public static void clear() {
        USER_ID.remove();
        ADMIN.remove();
    }
}
