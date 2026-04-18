package com.ohmyproject.auth;

import com.ohmyproject.common.RequestContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 从 X-User-Id 头提取匿名用户 ID 写入 RequestContext。
 * 缺失时按空串处理——普通用户接口会据此判定"未识别身份"。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class UserIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-User-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse resp, FilterChain chain)
            throws ServletException, IOException {
        try {
            String uid = req.getHeader(HEADER);
            RequestContext.setUserId(uid == null ? "" : uid.trim());
            chain.doFilter(req, resp);
        } finally {
            RequestContext.clear();
        }
    }
}
