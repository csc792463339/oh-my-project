# Oh My Project

Codex CLI 的 Web 外壳，面向非开发人员（产品 / 运营 / 测试）的代码问答工具。

## 先决条件
- JDK 21+
- Maven 3.9+
- Node.js 20+（仅前端开发 / 打包时需要）
- Codex CLI 已安装并登录（`codex --version` 可用）

## 一键打包（前端 + 后端单 JAR）
```bash
mvn -Pfrontend -DskipTests package
java -jar target/oh-my-project-0.1.0-SNAPSHOT.jar
```
浏览器打开 http://localhost:8080 。

默认管理员口令 `change-me`，在 `src/main/resources/application.yml` 的 `app.admin.password` 修改。

## 开发模式
```bash
# 后端
mvn spring-boot:run

# 前端（另一个终端）
cd frontend && npm install && npm run dev
```
前端开发服务器运行在 5173，`/api/*` 代理到 8080。

## 配置（application.yml）
```yaml
app:
  admin:
    password: "change-me"       # 管理员口令（明文）
  codex:
    executable: "codex"         # codex 可执行文件；Windows 可写 codex.cmd / codex.exe 的绝对路径
    sandbox: "read-only"        # codex --sandbox
    color: "never"              # codex --color
    skip-git-repo-check: true
  data:
    dir: "./data"               # 会话与项目 JSON 存储目录
```

## 路由
- `/` 聊天页（匿名访问，localStorage UUID 区分用户）
- `/admin/login` 管理员登录
- `/admin/projects` 项目 CRUD
- `/admin/sessions` 查看所有会话

## 运行测试
```bash
mvn test -Dtest='*IT,*Test'             # 单测 + Spring 上下文 + MockMvc
mvn -Dcodex.it=true -Dtest=CodexRunnerIT test  # 真调 Codex CLI 的集成测试
```
