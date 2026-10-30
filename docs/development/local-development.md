# 本地开发：登录与会议数据

> **2026-10-28 R2 能力边界**：以下命令目前只证明本地登录、MySQL 与会议数据，不是完整 Demo 启动说明。按[Demo 优先计划](../superpowers/plans/2026-10-28-ama-demo-first-adjustment.md)后续沿本入口逐段装配 local 文件/Worker（DEMO-02）、模型（03）、发布（04），实际实现并验证后再更新本说明；本次仅文档，没有安装这些能力。旧 `tools/bl5_launch.py` 即使 local 模式也依赖 S3 和已关闭批次，不能作为日常 Demo 入口。

在 Linux / WSL 中，从本工作树根目录运行：

固定端口启动联调 harness（另开一个终端保持进程运行）：

```sh
cd /home/tanren/ama-integration-harness
FIXED_PORTS=1 CAS_ALLOWED_SERVICES=http://127.0.0.1:5173/auth/cas/callback,http://127.0.0.1:5173/meetings OPEN_BROWSER=0 ./scripts/start_latest.sh
```

再在 AMA 工作树启动本地应用：

```sh
cd /home/tanren/ama
AMA_CAS_ORIGIN=http://127.0.0.1:8792 python3 tools/dev/local_app.py start
```


```sh
python3 tools/dev/local_app.py start
python3 tools/dev/local_app.py status
```

首次启动需要 Docker、项目的 Java 21 / Node 工具链以及已缓存的 Maven 依赖。启动器会构建后端、为本工作树创建专属 MySQL 容器和持久卷、显式执行迁移、编译独立开发装配，再启动前后端。当前工作树可复用项目 `phase-a/.tools` 中已安装的工具链二进制；不读取旧数据库环境或凭证。

访问 [会议页面](http://127.0.0.1:5173/meetings)，点击“企业登录”。本地入口会跳转到联调辅助项目提供的 CAS 登录页，再回到 `/auth/cas/callback`；开发和生产都走这条 CAS 2.0 服务端校验链。

| 账号 | 用途 |
| --- | --- |
| `dev-owner` / `dev-member` / `dev-viewer` | 联调辅助项目中的 CAS 测试账号，分别用于负责人、参会人和账号隔离验证 |

CAS 测试账号密码由联调辅助项目管理，当前种子账号使用 `dev-password`。AMA 不接收或保存 CAS 密码，只接收一次性 `ST-*` Ticket，并由服务端调用 `/cas/p3/serviceValidate` 后签发自己的 HttpOnly 会话。示例数据存储于真实 MySQL。

前端为 `127.0.0.1:5173`，本地开发 API 为 `127.0.0.1:8082`，管理端口为 `127.0.0.1:8083`。Vite 将 `/api` 和 `/auth` 原路径转发至 `AMA_API_TARGET`；没有设置该变量时默认 `http://127.0.0.1:8080`。CAS 地址由 `AMA_CAS_ORIGIN` 控制；桌面联调启动器固定使用 `http://127.0.0.1:8792`，启动 harness 时传入 `FIXED_PORTS=1 CAS_ALLOWED_SERVICES=...`，AMA 启动时传入 `AMA_CAS_ORIGIN=http://127.0.0.1:8792`。联调辅助项目应把 `CAS_ALLOWED_SERVICES` 注册为 `http://127.0.0.1:5173/auth/cas/callback,http://127.0.0.1:5173/meetings`，分别覆盖登录回调和 CAS 登出后的回跳。Cookie、Origin 和 CSRF 均保留；管理路径不经过代理。使用相同的 `127.0.0.1:5173` 地址登录和访问页面，避免混用主机名。

开发身份、目录、类别和登录入口的 Java 源码仅位于 `tools/dev/java`，由启动器在额外类路径单独加载，不打进生产 API JAR。生产企业身份适配器、业务权限校验、会话撤销、Secure/HttpOnly Cookie 和 CSRF 校验均保留。信息保存需要安装真实会议/纪要状态转换组件；启动器为其提供明确的读取预算。未安装模型、Worker、文件服务、企业 SSO 或发布提供方，不会伪造这些能力可用。

开发装配显式创建底层数据源，与迁移使用同一份启动器生成的 `DATABASE_*` 参数。启动器也会剔除终端继承的 Spring、JVM 和其他部署/提供方配置，避免旧环境变量将本地操作导向其他数据库。前后端仍存活时重新执行 `start` 也会检查并恢复专属 MySQL；若其端口发生变化，会重启受管应用以更新连接。

停止本地环境，保留会议数据：

```sh
python3 tools/dev/local_app.py stop
```

再次运行 `start` 使用同一容器和持久卷，不重置数据库，也不覆盖已经初始化的示例会议。停止操作核验 PID 启动时间、实例标识及 Docker 归属标签，只处理本启动器管理的资源。原有 8080 服务不受影响。

日常本地环境在实际授权和归属范围内可重复使用，不要求每次创建新数据库或全套封存。此规则不扩展到旧 B-L5 受限资源、凭据、PKI 或已消费的一次性窗口。后续 Demo 沿用上述 loopback 同源入口及 Cookie/Origin/CSRF；内部文件服务原有 mTLS 保留，确需 HTTPS 的能力另以受信任配置验证。

运行真实浏览器冒烟检查：

```sh
node tools/dev/local_app_smoke.mjs
node tools/dev/local_login_smoke.mjs
python3 -m unittest discover -s tools/dev/tests -p test_local_app.py -v
```

浏览器检查使用真实登录和业务 API，会新增一条“联通验证会议（本地示例）”。报告在 `.tools/local-app/smoke.json`；受保护的浏览器会话文件仅供本机验证重启持久化。服务日志位于同目录的 `frontend.log`、`backend.log`、`migration.log`、`build.log` 和 `compile.log`。

验证完整停止及重启后的持久化：

```sh
python3 tools/dev/local_app.py stop
python3 tools/dev/local_app.py start
node tools/dev/local_app_persistence.mjs
```

该检查复用上一轮浏览器会话，确认登录会话、新建会议、已保存标题仍存在，且会议集合没有因为重跑示例初始化而变化。
