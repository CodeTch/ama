# 本地开发：登录与会议数据

在 Linux / WSL 中，从本工作树根目录运行：

```sh
python3 tools/dev/local_app.py start
python3 tools/dev/local_app.py status
```

首次启动需要 Docker、项目的 Java 21 / Node 工具链以及已缓存的 Maven 依赖。启动器会构建后端、为本工作树创建专属 MySQL 容器和持久卷、显式执行迁移、编译独立开发装配，再启动前后端。当前工作树可复用项目 `phase-a/.tools` 中已安装的工具链二进制；不读取旧数据库环境或凭证。

访问 [会议页面](http://127.0.0.1:5173/meetings)，点击“开发账号登录”。也可直接访问 [开发登录页](http://127.0.0.1:5173/api/dev/login)。

| 账号 | 用途 |
| --- | --- |
| `dev-owner` | 开发负责人，负责“产品周会（本地示例）”，参与“需求评审（本地示例）” |
| `dev-member` | 开发参会人，负责需求评审，参与产品周会 |
| `dev-viewer` | 无上述会议权限，用于验证账号隔离，也可创建自己的会议 |

本地开发账号不校验密码：任意填写或留空均可，选择账号后直接点击“登录本地环境”。不再需要密码文件。开发页面清楚标识本地环境，示例数据存储于真实 MySQL。

前端为 `127.0.0.1:5173`，本地开发 API 为 `127.0.0.1:8082`，管理端口为 `127.0.0.1:8083`。Vite 将 `/api` 原路径转发至 `AMA_API_TARGET`；没有设置该变量时默认 `http://127.0.0.1:8080`。Cookie、Origin 和 CSRF 均保留；管理路径不经过代理。使用相同的 `127.0.0.1:5173` 地址登录和访问页面，避免混用主机名。

开发身份、目录、类别和登录入口的 Java 源码仅位于 `tools/dev/java`，由启动器在额外类路径单独加载，不打进生产 API JAR。生产企业身份适配器、业务权限校验、会话撤销、Secure/HttpOnly Cookie 和 CSRF 校验均保留。信息保存需要安装真实会议/纪要状态转换组件；启动器为其提供明确的读取预算。未安装模型、Worker、文件服务、企业 SSO 或发布提供方，不会伪造这些能力可用。

开发装配显式创建底层数据源，与迁移使用同一份启动器生成的 `DATABASE_*` 参数。启动器也会剔除终端继承的 Spring、JVM 和其他部署/提供方配置，避免旧环境变量将本地操作导向其他数据库。前后端仍存活时重新执行 `start` 也会检查并恢复专属 MySQL；若其端口发生变化，会重启受管应用以更新连接。

停止本地环境，保留会议数据：

```sh
python3 tools/dev/local_app.py stop
```

再次运行 `start` 使用同一容器和持久卷，不重置数据库，也不覆盖已经初始化的示例会议。停止操作核验 PID 启动时间、实例标识及 Docker 归属标签，只处理本启动器管理的资源。原有 8080 服务不受影响。

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
