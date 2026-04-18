package com.ohmyproject;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "app.data.dir=${java.io.tmpdir}/oh-my-project-web-" + "${random.uuid}",
        "app.admin.password=it-password",
        "app.codex.executable=codex",
        "app.codex.sandbox=read-only",
        "app.codex.color=never",
        "app.codex.skip-git-repo-check=true"
})
class WebEndpointsIT {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @Test
    void admin_endpoints_require_auth() throws Exception {
        mvc.perform(get("/api/admin/projects"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void user_must_provide_user_id_header_to_list_sessions() throws Exception {
        mvc.perform(get("/api/sessions"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void admin_login_and_full_project_session_flow() throws Exception {
        // 1. 管理员登录
        MvcResult login = mvc.perform(post("/api/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"it-password\"}"))
                .andExpect(status().isOk())
                .andReturn();
        Cookie[] cookies = login.getResponse().getCookies();
        Cookie authCookie = null;
        for (Cookie c : cookies) {
            if ("admin_token".equals(c.getName())) {
                authCookie = c;
                break;
            }
        }
        assertThat(authCookie).isNotNull();
        assertThat(authCookie.getValue()).isNotBlank();

        // 2. 新建项目
        MvcResult create = mvc.perform(post("/api/admin/projects")
                        .cookie(authCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"demo","path":"/tmp/demo","techStack":"Java",
                                 "dirStructure":"src","coreModules":"core"}"""))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode proj = mapper.readTree(create.getResponse().getContentAsString());
        String projectId = proj.get("id").asText();

        // 3. 普通用户接口能看到该项目
        mvc.perform(get("/api/projects").header("X-User-Id", "user-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(projectId))
                .andExpect(jsonPath("$[0].name").value("demo"))
                // 关键：路径不对普通用户暴露
                .andExpect(jsonPath("$[0].path").doesNotExist());

        // 4. 用户创建会话
        MvcResult sess = mvc.perform(post("/api/sessions")
                        .header("X-User-Id", "user-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":\"" + projectId + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String sessionId = mapper.readTree(sess.getResponse().getContentAsString())
                .get("sessionId").asText();

        // 5. 自己的会话列表能看到
        mvc.perform(get("/api/sessions").header("X-User-Id", "user-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sessionId").value(sessionId));

        // 6. 他人看不到（不同 userId）
        mvc.perform(get("/api/sessions").header("X-User-Id", "user-2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        // 7. 管理员能看到全部
        mvc.perform(get("/api/admin/sessions").cookie(authCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sessionId").value(sessionId));

        // 8. 删除会话
        mvc.perform(delete("/api/sessions/" + sessionId).header("X-User-Id", "user-1"))
                .andExpect(status().isNoContent());

        // 9. 删除项目
        mvc.perform(delete("/api/admin/projects/" + projectId).cookie(authCookie))
                .andExpect(status().isNoContent());
    }

    @Test
    void wrong_password_returns_401() throws Exception {
        mvc.perform(post("/api/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized());
    }
}
