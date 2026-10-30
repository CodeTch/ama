# 本地开发环境 Implementation Plan

> **2026-10-28 Demo 优先 R2 补充**：本计划已完成的本地登录/MySQL/会议数据及验证保持完成。完整文件服务、Worker、模型和发布提供方尚未装配；后续沿现有启动器，在[调整计划](2026-10-28-ama-demo-first-adjustment.md) DEMO-01 复核入口、02 接文件/Worker、03 接模型、04 接发布，各片段同步页面联调，05 交付演示，B-QUAL 完成深入验证。不重做本计划，不建第二套启动体系或 S3/双 MySQL/双活专用配置/测试。日常归属环境按实际授权复用，旧一次性 B-L5 环境不可重用；下文为历史实际结果，本次未重跑。

> 使用 executing-plans 内联执行；本轮主 Agent 实现，不分派并行任务。不提交或推送。

**Goal:** 接通本地登录和 MySQL 中持久化的会议数据。

**Architecture:** Vite `/api` 同源代理；只在专用启动器类路径中提供开发登录；独立 Docker MySQL；复用现有迁移、会话及会议服务。

**Tech Stack:** Java 21、Spring、MySQL 8.4.6、Python 3、Vue、Vite、Playwright。

**Spec:** `docs/superpowers/specs/2026-10-28-local-development-design.md`

## Global Constraints

- 仅新建本地容器和持久卷；不访问或复用历史受限测试库。
- 开发代码不进入生产 JAR；不关闭业务会话鉴权、Origin 或 CSRF。
- 重启不覆盖示例数据或用户新建会议；随机密码不入 Git。

## Review Focus

- 真实 Chromium 是否接受现有 Secure/Host cookie。
- 跨源登录及业务写入必须被拒绝。
- 不同开发账号之间保持会议权限边界。
- 重启不能重置数据库或覆盖已经编辑的会议。
- 停止命令只处理带本实例归属标识的进程和容器。

## Task 1: 代理与独立本地运行环境

- [x] 先写 `tools/dev/local_app_smoke.mjs`，针对当前服务器运行并确认代理/登录缺失导致失败。
- [x] 更新 `web/vite.config.ts`，保留 Cookie、Origin、CSRF 和 `/api` 路径，允许 `AMA_API_TARGET` 配置后端。
- [x] 在 `tools/dev/java/ama/localdev/` 添加独立开发装配及登录控制器，复用真实 DB 会话、人员目录、类别快照和会议服务。
- [x] 实现 `tools/dev/local_app.py start|stop|status`，管理专属容器、持久卷、迁移、单独编译和进程归属。
- [x] 仅在显式本地开发模式下显示登录和切换账号链接，补充启动说明。
- [x] 运行真实 HTTP/浏览器冒烟、相关前端测试、类型/构建检查；重启后再次检查已保存数据。

## 验证结果与审查

2026-10-28，实际执行于指定 `phase-b-completion` 工作树，仅使用本轮新建的本地环境。

- 原代理冒烟先失败：会话请求返回 HTML/200，而应为后端 JSON/401。
- 后端 Maven package、独立开发 Java 编译、前端类型检查与生产构建通过。
- 36 项相关前端单测、4 项启动器回归通过，0 跳过。新增环境隔离与 MySQL 快捷恢复两项先观察到失败再修正。
- 真实 Chromium 16 项检查通过：代理、错误密码、登录 Origin、Secure/HttpOnly Cookie、真实会话、目录、类别、CSRF、负责/参与列表、详情、新建、信息保存、刷新、账号隔离及零浏览器运行错误。
- 完整停止并重启 API/Vite/MySQL 后，原会话、新建会议、已保存标题、会议集合/种子去重四项通过。首次执行 16 个迁移，重启重复迁移为 0。
- 真实启动注入伪造的 Spring 数据源、JSON、配置文件与 provider 设置；独立 loopback TCP 拦截器记录目标连接次数 0，启动与后续会话/数据读取通过。
- 生产 JAR 不含 `ama/localdev/` 开发登录类；Windows 访问页面 200、未登录会话 JSON/401，原有 8080 服务的存活状态 UP。
- 唯一替代独立审查者报告 1 Important（继承 Spring 配置可改变实际数据源）及 1 Minor（进程存活时 start 不恢复 MySQL）；两项修复后同一审查者复核关闭。实际数据源明确绑定启动器的 DATABASE_*，并清理外部 Spring/JVM/适配器变量。新建进程等待改用所属 Popen.poll，停止归属保护保持不变，最终复核无新增 Important。

运行证据、日志和受保护会话只保存在本轮 `.tools/local-app/`。开发账号与数据为本地示例；未执行企业登录、模型、录音、文件发布或 Phase B 整体验收。未提交、合并或推送。

## 用户更新：开发账号不校验密码

2026-10-28 用户明确要求“改成任意密码皆可登录，不验证”。本地专用登录已移除密码参数读取和比较，HTML 可留空，启动器不再生成或依赖开发密码文件；账号白名单、loopback/Origin、会话及业务 CSRF/权限保持有效。此要求替代上文初始密码方案。

定向回归 `node tools/dev/local_login_smoke.mjs` 先在旧服务上失败（任意密码 401，期望 303），修改并重启后 12 项检查通过，包含任意、空、缺省、长 Unicode 密码及真实浏览器登录和会议数据读取。4 项启动器回归通过，同一独立审查者复核无 Critical/Important。

额外使用旧 `smoke.json` 整表快照执行 persistence 检查时，因现场已新增一条会议导致集合严格相等断言失败。未改写旧快照或删除新增会议；另做只读检查确认旧会话、原已保存标题和快照中所有原会议仍保留。
