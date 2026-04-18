# Oh My Project PRD（修订版 v2 / Codex exec 驱动）

> 本版本基于多轮澄清结论重写，取代 v1。关键改动：
> - 采用 `codex exec` 非交互式 + `--json` 流式事件 + `codex exec resume` 恢复会话
> - 前端不再使用 xterm.js，Markdown 渲染即可
> - 由 Codex 自身管理会话上下文，后端不再手动拼接 10 轮历史
> - 新增管理员后台（项目管理 + 口令登录）
> - 技术栈锁定：Spring Boot 3.x + Java 21 + React + Vite，单 JAR 发布

---

# 一、产品概述

## 1.1 产品名称
Oh My Project（代码理解助手）

## 1.2 产品定位
基于 Codex CLI 的 Web 化代码问答工具，面向非开发人员（产品 / 运营 / 测试），用于理解代码逻辑和排查问题。

## 1.3 核心特点
- 本地 JSON 文件存储，不引入数据库
- 不做 RAG / 向量检索 / 代码结构解析
- 完全依赖 Codex CLI 进行代码理解
- 类 ChatGPT 的对话体验
- 基于 SSE 的流式输出
- 会话上下文由 Codex 自身维护（通过 `codex exec resume`）
- 单 JAR 部署，Mac / Windows 11 同时支持

## 1.4 产品本质
> Codex `exec` 的 Web 外壳 + Prompt 控制 + 会话 / 项目管理

---

# 二、目标用户

| 用户角色 | 使用目的 |
|----------|--------|
| 产品经理 | 理解功能实现 |
| 运营人员 | 排查业务问题 |
| 测试人员 | 定位异常原因 |
| 新人开发 | 快速熟悉项目 |
| 管理员   | 维护项目配置、查看全部会话 |

---

# 三、使用场景

示例问题：
- 用户注册流程是怎么实现的？
- 订单支付失败一般是什么原因？
- 为什么接口返回 500？
- 定时任务什么时候执行？

---

# 四、功能范围

## 4.1 支持功能
- 多会话管理（新建 / 切换 / 继续 / 删除）
- 基于项目的问答（单会话绑定单项目）
- SSE 流式输出
- 会话上下文记忆（由 Codex 自身维护，不受"10 轮"硬限制）
- 项目管理后台（管理员维护项目列表与项目 Prompt）
- 匿名用户（浏览器 localStorage UUID）
- 管理员视图（查看所有会话）

## 4.2 不支持功能（强约束）
- ❌ 代码生成
- ❌ 代码修改
- ❌ 在回答中返回源码
- ❌ 代码下载
- ❌ 多项目上下文融合
- ❌ 用户注册 / 权限隔离（仅管理员有口令）

---

# 五、系统架构

```
[ React + Vite 前端 ]
        ↓  HTTP / SSE
[ Spring Boot 3.x 后端 ]
        ↓
[ Prompt 组装层 ]
        ↓  ProcessBuilder
[ codex exec --json (+ resume) ]
        ↓
[ 本地代码仓库 ]

本地文件存储：
./data/sessions/{sessionId}.json
./data/projects/{projectId}.json
```

---

# 六、核心流程

## 6.1 首次提问（新会话）
1. 用户选择项目、输入问题
2. 后端生成 `sessionId`
3. 组装 Prompt：`[系统规则] + [项目 Prompt] + [用户问题]`
4. 调用：`codex exec --json --cd <projectPath> --skip-git-repo-check "<组装后的 prompt>"`
5. 从 Codex 返回的 JSON 事件流中提取 `session_id` 存入会话元数据
6. 逐事件转换为 SSE 推送给前端
7. 流结束后将 user / assistant 消息落盘

## 6.2 继续提问（已有会话）
1. 用户在已有会话中输入新问题
2. 后端读取会话记录，取出 Codex `session_id`
3. 调用：`codex exec resume <session_id> --json --cd <projectPath> "<用户问题>"`
   - 项目 Prompt 不再重复注入（已在首次调用中注入）
   - 系统规则每次都拼在用户问题前（稳妥）
4. 流式推送 → 落盘

## 6.3 会话超时
- 后端记录每个会话 `lastActiveAt`
- 有流式输出进行中时视为"活跃"，不计入超时
- 10 分钟无任何请求 → 仅清理内存中可能残留的 SSE 连接
- 由于每次请求都是 `codex exec` 短进程，不存在常驻进程需要杀死
- 会话数据保留在 JSON，用户下次进入可继续（Codex session 也仍可 resume）

---

# 七、Prompt 设计

## 7.1 系统规则（每次请求都拼在最前）

```
你是一个代码讲解助手。

规则：
- 不输出代码、不生成代码、不返回源码片段
- 只用自然语言解释
- 重点解释：功能逻辑、调用流程、异常原因
- 回答面向非开发人员，尽量避免术语堆砌
- 不确定时必须明确说明"不确定"
```

## 7.2 项目 Prompt（由管理员在后台维护）

字段：
- `name` 项目名称
- `path` 本地绝对路径
- `techStack` 技术栈描述
- `dirStructure` 目录结构说明
- `coreModules` 核心模块说明

示例文本（拼接到首次请求）：
```
项目名称：Order System
技术栈：Java + Spring Boot
目录结构：
- controller：接口层
- service：业务逻辑
- repository：数据访问
核心模块：订单、支付、用户

代码路径：/projects/order-system
请基于该路径下代码进行分析。
```

## 7.3 最终 Prompt 结构

**首次请求**：
```
[系统规则]
[项目 Prompt]
用户问题：<text>
```

**后续 resume 请求**：
```
[系统规则]
用户问题：<text>
```

## 7.4 上下文说明
- 原 v1 的"最近 10 轮手动拼接 + 滑动窗口"**废弃**
- 上下文由 Codex exec resume 自行维护
- 后端 JSON 中保存完整对话历史仅用于前端展示

---

# 八、会话设计

## 8.1 会话结构（`./data/sessions/{sessionId}.json`）

```json
{
  "sessionId": "uuid",
  "userId": "browser-uuid",
  "projectId": "project-uuid",
  "projectPath": "/projects/order-system",
  "codexSessionId": "codex-returned-session-id",
  "title": "订单支付问题",
  "messages": [
    { "role": "user", "content": "..." , "ts": 1710000000 },
    { "role": "assistant", "content": "...", "ts": 1710000001 }
  ],
  "createdAt": 1710000000,
  "lastActiveAt": 1710000000
}
```

## 8.2 会话能力
- 新建会话（自动绑定用户选择的项目）
- 会话列表（普通用户看自己 `userId` 的；管理员看全部）
- 继续提问（resume）
- 删除会话

## 8.3 标题生成
- 取第一条用户问题前 20 字作为标题
- 不再额外调模型生成

## 8.4 并发控制
- 同一会话：串行，前一个请求未结束时拒绝新请求
- 不同会话：完全并发，不限制 Codex CLI 子进程数量（风险自担）

---

# 九、项目管理模块（新增）

## 9.1 管理员登录
- 口令写死在配置文件（`application.yml` 的 `admin.password`）
- 前端登录后颁发一个简单 token（如 JWT 或随机串 + 内存 Map），写入 httpOnly Cookie
- token 过期时间：24 小时

## 9.2 项目数据结构（`./data/projects/{projectId}.json`）

```json
{
  "projectId": "uuid",
  "name": "Order System",
  "path": "/projects/order-system",
  "techStack": "Java + Spring Boot",
  "dirStructure": "controller: 接口层\nservice: 业务逻辑\n...",
  "coreModules": "订单、支付、用户",
  "createdAt": 1710000000,
  "updatedAt": 1710000000
}
```

## 9.3 功能
- 管理员后台页：列表 / 新增 / 编辑 / 删除项目
- 路径**不做校验**（已确认，管理员自负）
- 普通用户端：只读下拉列表（仅暴露 `projectId` 和 `name`）

---

# 十、前端设计

## 10.1 页面结构
- 左栏：会话列表 + "新建会话" 按钮
- 中栏：Chat 窗口（Markdown 渲染，**不使用 xterm.js**）
- 顶部：项目选择下拉 + 管理员入口

## 10.2 管理员视图
- 额外 `/admin` 路由（需登录）
- 左栏切换为"所有会话"视图
- 额外 Tab：项目管理

## 10.3 匿名身份
- 首次打开在 `localStorage` 生成 UUID
- 所有请求携带 `X-User-Id: <uuid>` 头

---

# 十一、流式输出设计

## 11.1 事件流转

```
codex exec --json  →  Java 按行读 stdout
                   →  解析 JSON 事件
                   →  转为 SSE data: 帧
                   →  前端累积渲染
```

## 11.2 Codex JSON 事件处理
- 只提取文本输出类事件转发给前端
- 其他事件（工具调用、思考过程等）按需丢弃或折叠
- 首次请求时从事件中捕获 `session_id` 存入会话

## 11.3 SSE 帧格式
```
event: message
data: {"delta": "这段"}

event: message
data: {"delta": "代码的作用是..."}

event: done
data: {"sessionId": "xxx"}
```

## 11.4 异常帧
```
event: error
data: {"message": "Codex CLI 退出，code=1"}
```

---

# 十二、接口设计

## 12.1 用户接口

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/projects` | 获取项目列表（只读，仅 name 与 id） |
| POST | `/api/sessions` | 创建会话 `{projectId, firstMessage}` |
| GET | `/api/sessions` | 当前用户会话列表 |
| GET | `/api/sessions/{id}` | 会话详情 |
| DELETE | `/api/sessions/{id}` | 删除会话 |
| POST | `/api/chat/stream` | SSE 流式提问 `{sessionId, message}` |

## 12.2 管理员接口（Cookie token 鉴权）

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/admin/login` | 口令登录 |
| POST | `/api/admin/logout` | 登出 |
| GET  | `/api/admin/sessions` | 所有会话 |
| GET  | `/api/admin/projects` | 项目列表 |
| POST | `/api/admin/projects` | 新增项目 |
| PUT  | `/api/admin/projects/{id}` | 修改项目 |
| DELETE | `/api/admin/projects/{id}` | 删除项目 |

---

# 十三、技术栈与部署

## 13.1 后端
- Java 21
- Spring Boot 3.x（Web + WebFlux for SSE 或 Spring MVC SseEmitter）
- Jackson JSON
- 进程调用：`ProcessBuilder`，逐行读取 stdout

## 13.2 前端
- React 18 + Vite
- 状态：Zustand 或 React Context（极简即可）
- Markdown 渲染：`react-markdown` + `remark-gfm`
- SSE：原生 `EventSource` 或 `@microsoft/fetch-event-source`（支持 POST）

## 13.3 构建与部署
- 前端构建产物拷贝到 `src/main/resources/static/`
- Maven 单 JAR 打包
- 启动：`java -jar oh-my-project.jar`
- 启动目录下自动创建 `./data/sessions` 和 `./data/projects`

## 13.4 配置文件（`application.yml`）
```yaml
server:
  port: 8080
admin:
  password: "change-me"
codex:
  executable: "codex"   # Mac 默认；Windows 可改为绝对路径
data:
  dir: "./data"
```

## 13.5 跨平台注意事项
- `codex` 可执行文件在 Mac / Win 路径不同，支持在配置中覆盖
- 文件路径使用 `java.nio.file.Path`，避免手拼分隔符
- `ProcessBuilder` 的 `redirectErrorStream(true)` 合并 stderr
- Windows 下 `codex` 可能为 `codex.cmd` / `codex.exe`，需在启动时探测或由配置指定

---

# 十四、约束与限制

## 14.1 安全
- 系统规则强制"不输出代码"（Prompt 层约束，不做后处理过滤）
- 不做路径校验（管理员自负，已确认）
- 管理员口令明文存配置

## 14.2 产品
- 单会话绑定单项目
- 不支持跨项目对话
- 同一会话禁止并发请求

---

# 十五、异常处理

| 场景 | 处理 |
|---|---|
| Codex 进程异常退出 | 发送 SSE `error` 帧，落盘错误消息 |
| 会话切换 | 前端中断 EventSource；后端子进程在下次生命周期内自然结束 |
| 同一会话并发 | 返回 409 |
| 管理员 token 过期 | 返回 401 |
| Codex resume 失败（session 丢失） | 降级：作为新 session 重发首次 Prompt |

---

# 十六、风险分析

## 16.1 精度风险
- 无 RAG，Codex 可能产生幻觉
- 接受"持续迭代改进"

## 16.2 性能风险
- `codex exec` 每次冷启动有开销
- 不限制并发子进程，大量同时请求可能拖垮机器（风险自担）

## 16.3 上下文风险
- 依赖 Codex 自身 session 存储，若其存储机制变更需同步调整

## 16.4 运行环境
- 需提前安装 Codex CLI 并登录
- 需保证 `codex` 在 PATH 或配置中指定

---

# 十七、MVP 范围

- Spring Boot 3.x + Java 21 后端
- React + Vite 前端，打包进单 JAR
- 用户端：项目选择、多会话、SSE 流式问答
- 管理员端：口令登录、项目 CRUD、查看所有会话
- JSON 文件存储
- 基于 `codex exec --json` + `codex exec resume`
- Mac / Windows 11 双平台可运行

---

# 十八、成功标准
- 非开发人员可用自然语言理解代码逻辑
- 回答可读性高，面向非开发
- 平均首字响应时间 < 5s（取决于 Codex CLI）
- 约 70% 问题可被有效回答，持续迭代
