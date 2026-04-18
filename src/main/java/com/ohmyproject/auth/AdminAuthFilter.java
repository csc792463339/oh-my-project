package com.ohmyproject.auth;

import com.ohmyproject.common.RequestContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 拦截 /api/admin/** 且路径不是 /api/admin/login。
 * 无有效 token 时直接返回 401，Controller 不会被执行。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class AdminAuthFilter extends OncePerRequestFilter {

    public static final String COOKIE_NAME = "admin_token";

    private final AdminTokenStore tokens;

    public AdminAuthFilter(AdminTokenStore tokens) {
        this.tokens = tokens;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (!path.startsWith("/api/admin/")) return true;
        return path.equals("/api/admin/login");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse resp, FilterChain chain)
            throws ServletException, IOException {
        String token = extractToken(req);
        if (!tokens.isValid(token)) {
            resp.setStatus(401);
            resp.setContentType("application/json;charset=UTF-8");
            resp.getWriter().write("{\"code\":401,\"message\":\"admin auth required\"}");
            return;
        }
        RequestContext.setAdmin(true);
        try {
            chain.doFilter(req, resp);
        } finally {
            RequestContext.setAdmin(false);
        }
    }

    private String extractToken(HttpServletRequest req) {
        Cookie[] cookies = req.getCookies();
        if (cookies == null) return null;
        for (Cookie c : cookies) {
            if (COOKIE_NAME.equals(c.getName())) return c.getValue();
        }
        return null;
    }
}
