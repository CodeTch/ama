# 本地开发：会议、转写、受控纪要与发布

> **2026-10-29 R2 当前能力**：统一本地入口已接通会议、TXT 上传/转写、受控模型生成、候选、编辑/审阅、冻结、签名知识库发布与正式文档双层权限。模型输出为固定合成示例，不能作为真实 AI 质量证据。功能证据与原型验收、稳定性资格分开记录；M1-Demo 已按本地受控流程验收；B 正式验收仍未完成。

在 Linux / WSL 中，从本工作树根目录运行：

```sh
python3 tools/dev/local_app.py start
python3 tools/dev/local_app.py status
```

首次启动需要 Docker、项目的 Java 21 / Node 工具链以及已缓存的 Maven 依赖。启动器会构建后端、为本工作树创建专属 MySQL 容器和持久卷、显式执行迁移、编译独立开发装配，再启动前端、API、Worker、文件服务、受控模型和知识库提供方。当前工作树可复用项目 `phase-a/.tools` 中已安装的工具链二进制；不读取旧数据库环境或凭证。

本地登录通过兄弟联调项目 `\\wsl.localhost\Ubuntu-24.04\home\tanren\ama-integration-harness` 提供的 CAS 接口完成，不再把本地直签页面作为产品入口。先在 harness 项目启动 CAS 服务，再启动本工作树：

```sh
cd /home/tanren/ama-integration-harness
CAS_ALLOWED_SERVICES=http://127.0.0.1:5173/auth/cas/callback,http://127.0.0.1:5173/auth/cas/logout/callback,http://127.0.0.1:5174/auth/cas/callback,http://127.0.0.1:5174/auth/cas/logout/callback \
MOCK_HTTP_HOST=127.0.0.1 MOCK_HTTP_PORT=8792 \
MOCK_SMTP_HOST=127.0.0.1 MOCK_SMTP_PORT=2525 \
MOCK_DATA_DIR=/home/tanren/ama-integration-harness/data/live-8792 \
MOCK_DB_PATH=/home/tanren/ama-integration-harness/data/live-8792/mock.db \
./backend/start.sh

cd /home/tanren/ama/.worktrees/phase-b-completion
AMA_INTEGRATION_KNOWLEDGE_ORIGIN=http://127.0.0.1:8792 \
AMA_CAS_ORIGIN=http://127.0.0.1:8792 \
python3 tools/dev/local_app.py start --web-port 5174 --api-port 8182 --management-port 8183
```

访问 [会议页面](http://127.0.0.1:5174/meetings)，点击“企业 CAS 登录”，使用 harness 中的测试账号登录。当前联调账号为 `dev-owner` / `dev-password`；它只存在于 harness 的联调数据库，不能作为生产凭证。生产环境必须通过 `CAS_ENABLED=true` 和正式 CAS 地址配置使用同一服务端流程。

| 账号 | 用途 |
| --- | --- |
| `dev-owner` | 开发负责人，负责“产品周会（本地示例）”，参与“需求评审（本地示例）” |
| `dev-member` | 开发参会人，负责需求评审，参与产品周会 |
| `dev-viewer` | 无上述会议权限，用于验证账号隔离，也可创建自己的会议 |
| `dev-admin` | 仅 PLATFORM_ADMIN；平台角色不会自动授予会议或正式文档权限 |

CAS 登录页会校验 harness 中的账号密码并返回一次性 ticket；AMA 服务端验证 ticket 后再创建本地数据库会话。示例身份仍存储于当前工作树的真实 MySQL，CAS 用户和企业身份必须使用同一个稳定 ID 才能建立会话。

本轮导入页联调使用 harness 的固定端口启动方式，不覆盖其既有端口设计：

```sh
cd /home/tanren/ama-integration-harness
FIXED_PORTS=1 OPEN_BROWSER=0 ./scripts/start_latest.sh

cd /home/tanren/ama
AMA_INTEGRATION_KNOWLEDGE_ORIGIN=http://127.0.0.1:8792 \
AMA_CAS_ORIGIN=http://127.0.0.1:8792 \
python3 tools/dev/local_app.py start --web-port 5173 --api-port 8184 --management-port 8185

AMA_LOCAL_URL=http://127.0.0.1:5173 \
AMA_CAS_ORIGIN=http://127.0.0.1:8792 \
node tools/dev/import_cas_integration.mjs
```

`import_cas_integration.mjs` 使用真实浏览器和真实请求验证匿名拒绝、CAS ticket/会话、会议创建、TXT 上传、保存后刷新恢复、harness 知识库版本读取、导入受理、处理页导航和登出撤销；它只记录请求与响应，不伪造产品成功或处理完成。报告写入 `.tools/local-app/integration-import-cas/report.json`。

2026-10-30 的固定端口实跑结果为 10/10 checks 通过：AMA 会话 actor 为 `dev-owner`，TXT 上传产生真实 `201` 上传初始化和分块/完成请求，刷新未重复上传，知识库读取了 harness 的不透明版本 token；随后将 harness 的知识库读取临时切换为 `403`，确认 AMA 显示权限错误且没有伪造成功；导入返回真实 `202` 并导航到处理页，登出后会话返回 `401`。报告还保留 harness 的 CAS/知识库请求日志，所有浏览器请求均有明确响应或失败分类，页面无 `pageerror`。

前端为 `127.0.0.1:5174`，本地开发 API 为 `127.0.0.1:8182`，管理端口为 `127.0.0.1:8183`。Vite 将 `/api` 和 `/auth` 原路径转发至 `AMA_API_TARGET`；启动器会为 CAS 回调生成当前前端地址的精确 service URL。Cookie、Origin 和 CSRF 均保留；管理路径不经过代理。使用相同的 `127.0.0.1:5174` 地址登录和访问页面，避免混用主机名。

本地目录与类别开发装配的 Java 源码仅位于 `tools/dev/java`，由启动器在额外类路径单独加载，不打进生产 API JAR。CAS 登录、生产企业身份适配器、业务权限校验、会话撤销、Secure/HttpOnly Cookie 和 CSRF 校验均由正式 API 代码提供。信息保存需要安装真实会议/纪要状态转换组件；启动器为其提供明确的读取预算。独立 Worker 使用现有真实处理、纪要、最终确认和发布组件。本地模型/知识库通过已有受控适配器显式安装；企业 SSO 通过 harness 完成协议联调，公司真实模型/知识库访问尚未验证。

开发装配显式创建底层数据源，与迁移使用同一份启动器生成的 `DATABASE_*` 参数。启动器也会剔除终端继承的 Spring、JVM 和其他部署/提供方配置，避免旧环境变量将本地操作导向其他数据库。前后端仍存活时重新执行 `start` 也会检查并恢复专属 MySQL；若其端口发生变化，会重启受管应用以更新连接。

停止本地环境，保留会议数据：

```sh
python3 tools/dev/local_app.py stop
```

需要单独暂停和恢复 Worker 时，可使用 `python3 tools/dev/local_app.py stop-worker` 和 `python3 tools/dev/local_app.py start-worker`。恢复前会核对源码、其他五个进程、既有配置和端口；源码已变化时须使用完整 `start` 更新装配。

再次运行 `start` 使用同一容器和持久卷，不重置数据库，也不覆盖已经初始化的示例会议。停止操作依次处理前端、API、Worker、本地文件服务、模型/知识库提供方及 MySQL，核验 PID 启动时间、实例标识及 Docker 归属标签。原有 8080 服务不受影响。

日常本地环境在实际授权和归属范围内可重复使用，不要求每次创建新数据库或全套封存。此规则不扩展到旧 B-L5 受限资源、凭据、PKI 或已消费的一次性窗口。后续 Demo 沿用上述 loopback 同源入口及 Cookie/Origin/CSRF；内部文件服务原有 mTLS 保留，确需 HTTPS 的能力另以受信任配置验证。

运行真实浏览器冒烟检查：

```sh
node tools/dev/local_app_smoke.mjs
AMA_LOCAL_URL=http://127.0.0.1:5174 node tools/dev/local_login_smoke.mjs
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


## TXT 转写操作

1. 在列表点击“导入会议”，等待原创建命令确认后进入上传弹窗；也可“新建会议”后从工作台打开“导入会议”。
2. 添加两份 UTF-8 TXT；按队列中的“上移/下移”确定顺序，再点击“按列表顺序提交导入”。任务受理与处理成功分开显示。
3. 通过“处理任务”查看真实结果；完成后刷新工作台，在“转写历史”中读取 RAW，选择“恢复为新的工作版本”。
4. 修改正文与段落标记，点击“保存正文与标记”；确认按钮只使用已保存结果。点击“确认当前保存版本”后，刷新可读到同一确认稿。

此开发档案使用已有严格 UTF-8 行解析器：每个文件最多 64 KiB/2048 段，每批最多 1 MiB/4096 段；这比浏览器通用上传上限更小。音频/视频/DOCX 与背景材料属于后续阶段。无效 UTF-8 会使整个导入任务失败，不能生成部分 RAW。确认只完成转写确认，不会自动生成纪要。

本地文件、配置档案与私有服务证书位于忽略的 `.tools/local-app/`，目录保留于停止/重开之间。文件服务使用内核挂载/目录/卷标识和角色文件检查，API/Worker 通过受信任的本地 CA 和 mTLS 访问，内部路由不经前端代理。服务证书有效期一年，仅用于本地开发。凭据配置权限为 0600，不将私钥或会话文件提交到仓库。

修改后端或开发装配源码后再次执行 `start` 会核对源码指纹，重新构建并重启归属进程；六个服务的状态见 `status`。日志新增 `files.log`、`worker.log`、`pki.log`、`model.log` 和 `knowledge.log`。与此前相同，原 8080 服务不受影响。

真实浏览器 TXT 链路与坏文件检查：

```sh
node tools/dev/local_transcript_smoke.mjs
```

报告和实际页面截图位于 `.tools/local-app/transcript-smoke/`。此检查新增两场专用会议（成功和坏文件）；所有操作使用真实业务 API，不通过 SQL 注入业务结果。


## 受控纪要与发布

确认转写后，进入“纪要与审阅”，读取并选择 Controlled minutes 初始模板，保存初始设置，再点击“生成完整纪要”。无已保存正文时首次结果自动保存；之后重新生成的候选需检查后采用。已保存正文、要素和行动字段通过“开始编辑”获得当前页编辑确认，保存后再做来源审阅。

最终确认要求当前 CONCLUSION/ACTION 的全文来源审阅，以及 ACTION 已接受字段的确认。部分支持不会满足必需条件；正文与其他要素的可选审阅不替代必需项。当前行动项支持显示名/日期编辑及从本地真实目录选择企业负责人；企业身份的姓名由服务端精确核验，清除该身份保留显示名/日期后可手写名称。选为负责人不授予会议读取权限。受控输出和脚本化审阅只用于验证流程，不代替人工判断。

满足条件后点击“确认并冻结已保存版本”，读取知识库、选择 Controlled library / Controlled root 并发布。Worker 完成与本地应用成功后才显示“查看正式文档”。同源文档入口每次复查当前会议权限及知识库读权限。初始本地授权为 dev-owner 可发布/读、dev-member 只读；只有出现在某场会议已确认名单中才同时具备该场会议的读取权限。正常重启不恢复后续撤销的知识库授权。

模型配置、模板 IDs、签名密钥、端口和数据存储保留在 `.tools/local-app/`。首次知识库初始化先写未发布临时存储，完整关闭后发布；中断重试复用同一身份与密钥。不得提交私钥、Bearer、登录会话或配置密码。

按顺序运行本地完整链路（会新增专用会议；使用固定合成结果和明确的测试审阅决策）：

```sh
node tools/dev/local_transcript_smoke.mjs
node tools/dev/local_minutes_smoke.mjs
node tools/dev/local_minutes_review_smoke.mjs
node tools/dev/local_publication_smoke.mjs
```

发布检查会暂时撤销并恢复 dev-member 的知识库读取权限，并暂时给非参会 dev-viewer 知识库读权限，验证会议侧仍拒绝，然后恢复原授权。仅对本工作树专用本地提供方执行。

已发布会议重启核对：

```sh
node tools/dev/local_demo_restart_check.mjs before
python3 tools/dev/local_app.py stop
python3 tools/dev/local_app.py start
node tools/dev/local_demo_restart_check.mjs after
```

检查保留两位用户的原浏览器会话，比较会议、纪要、冻结版本与发布状态的完整 JSON 值，以及正式文档 HTML 的逐字节 SHA-256。JSON 对象字段顺序不参与业务值比较。报告和 1440/1024 截图位于 `.tools/local-app/demo-restart/`。首次发布曾在 45 秒等待结束后由第二次任务尝试成功，其原因和稳定性资格仍待 B-QUAL；延长检查期限不等于关闭该问题。


2026-10-29 浏览器补充：真实双标签 409/草稿重新应用、IME 输入及视图切换、浏览器发布 ACK 丢失后只查询恢复均已有日常样本证据。候选以原生两列弹窗比较已保存稿与所选候选，底部决定区保持可见；当前进度从服务端保存/冻结观察更新，成功刷新不会卸载活动编辑器。原型逐页记录见 `docs/acceptance/b/demo-browser-prototype-2026-10-29.md`。企业行动负责人选择已在 B-R13 通过本地限定验收，详见 `docs/acceptance/b/action-owner-local-2026-10-29.md`；真实公司来源仍待 PLATFORM-B。
