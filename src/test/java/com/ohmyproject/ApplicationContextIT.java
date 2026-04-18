package com.ohmyproject;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
        "app.data.dir=${java.io.tmpdir}/oh-my-project-it-" + "${random.uuid}",
        "app.admin.password=it-password",
        "app.codex.executable=codex",
        "app.codex.sandbox=read-only",
        "app.codex.color=never",
        "app.codex.skip-git-repo-check=true"
})
class ApplicationContextIT {
    @Test void contextLoads() {}
}
