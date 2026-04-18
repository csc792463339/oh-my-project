package com.ohmyproject.auth;

import com.ohmyproject.common.ApiException;
import com.ohmyproject.config.AppProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminAuthController {

    private final AppProperties props;
    private final AdminTokenStore tokens;

    public AdminAuthController(AppProperties props, AdminTokenStore tokens) {
        this.props = props;
        this.tokens = tokens;
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody LoginRequest req, HttpServletResponse resp) {
        if (req == null || req.password == null
                || !req.password.equals(props.admin().password())) {
            throw new ApiException(401, "invalid password");
        }
        String token = tokens.issue();
        Cookie c = new Cookie(AdminAuthFilter.COOKIE_NAME, token);
        c.setHttpOnly(true);
        c.setPath("/");
        c.setMaxAge((int) (AdminTokenStore.DEFAULT_TTL_MILLIS / 1000));
        resp.addCookie(c);
        return Map.of("ok", true);
    }

    @PostMapping("/logout")
    public Map<String, Object> logout(@CookieValue(value = AdminAuthFilter.COOKIE_NAME, required = false) String token,
                                      HttpServletResponse resp) {
        tokens.revoke(token);
        Cookie c = new Cookie(AdminAuthFilter.COOKIE_NAME, "");
        c.setHttpOnly(true);
        c.setPath("/");
        c.setMaxAge(0);
        resp.addCookie(c);
        return Map.of("ok", true);
    }

    @GetMapping("/me")
    public Map<String, Object> me() {
        return Map.of("admin", true);
    }

    public record LoginRequest(String password) {}
}
