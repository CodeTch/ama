> **2026-10-28 当前计划（AMA-DEMO-FIRST-20261028 R2）**：按[Demo 优先调整计划](2026-10-28-ama-demo-first-adjustment.md)执行。前期本地 MySQL、本地文件、单机；按用户操作片段同步复刻原型、接通功能和联调。先交付 M1-Demo，再以 B-QUAL 完成 M1-B 正式本地验收；真实平台另验。S3、双 MySQL、双活未完适配及专属依赖全部最后 F。保留现有功能/完成状态，不重做 B-L1～4；本轮仅更新文档。

---

# 智能会议助手分阶段交付 Implementation Plan

> **For agentic workers:** 使用既有 executing-plans 内联流程与完整业务批次；当前调度和审查规则见 AMA-WORKFLOW-20261027 与 Demo 优先 R2。旧逐步骤模板不自动触发新代理、重复开发或提交；本轮仅文档。

**Goal:** 优先在本地 MySQL、本地文件存储和单机环境完成原型页面复刻与可演示核心闭环，再补齐一期业务，最后集中适配 S3、双 MySQL、双活及生产资格。原 100 场实时、300 在线和两台服务器自动恢复目标保留为相应后续验收要求。

**Architecture:** 保留已确认 v0.2 的现有实现和领域契约；模块化 Java 后端、Vue 前端。此次只调整实施顺序与验收环境；前期复用现有配置和本地实现，不为后移部署场景新增专用实现、抽象、配置体系、测试任务或架构改造。

**Tech Stack:** Java 21、Spring Boot 4.x、MySQL 8.4 LTS、Vue 3/TypeScript/Vite、local/S3、Podman；JUnit、Maven Surefire/Failsafe、Node 原生测试/tsx、Playwright。精确补丁版本在 A-01 锁定。

**Spec:** [已确认架构 v0.2](../specs/2026-10-21-ama-architecture-design.md)、[需求对应](../specs/2026-10-21-ama-requirements-matrix.md)、[部署前置条件](../specs/2026-10-21-ama-deployment-prerequisites.md)。

状态核对（2026-10-28）：A 本地工程已交付；B remaining Tasks 1–10、B-L1～4 已按各自限定范围采纳，local/S3 已有完整 API 链证据，完整浏览器与部分核心验证仍开放，B 为 INCOMPLETE。当前本地启动器已实现登录/会议数据，完整文件/Worker/模型/发布装配仍缺。详见调整计划第 2、6 节与 B 摘要；下列原始任务/接口约定不单独代表实现状态。

## Global Constraints

- 前期固定本地 MySQL＋本地文件＋单机；以下多环境/双机/HA 条款是最终需求，未完部分在 F 处理，不成为 Demo 开发、联调或验收前置。
- 已完成继续完成、已实现全部保留；仅对 Demo/核心串联实际阻塞作必要修复和最小修改。禁止借此重构、替换、删除、清理或提前适配部署。
- “100 场同时在线录音并实时转写，每场一个采集端，合计 300 名在线用户”；300 包含 100 名录音用户。
- “两台 RHEL 8.10 x86_64 虚拟机，每台 16 核、32GB 内存”；不新增第三台项目节点。
- “业务数据仍使用 MySQL”；`database.mode=private-ha|enterprise-platform` 与 `storage.type=local|s3` 独立。
- “local 模式启动校验不得强制要求 S3 地址、Bucket 或凭证”；新上传/录音固定后端 ID，历史读取按原记录路由。
- “只有一个节点挂载普通 XFS 文件系统并启动数据服务”；隔离成功且副本可用后接管，隔离失败不强制提升。
- 现有模型、知识库、SSO/人员目录均采用适配器；不新建公司知识库，不用模拟结果冒充真实集成。
- 未确认转写不得生成；待采用候选不得通过最终确认；发布成功只读；撤回/废止不得恢复或再次发布。
- 负责人名单草稿、AI/声纹姓名、行动项负责人不产生访问授权；所有文件读取再次检查会议权限。
- 原文 P0/P1 均交付，历史成果不自动清理；不擅加产品名称字数上限；UTC 存储，实际会议日期按 Asia/Shanghai 筛选。
- Redis/MQ/Kubernetes/搜索集群不作为必需依赖；生产凭证不进仓库；原型/需求原件不修改。

## Review Focus

1. 存储切换时尚在录音，用户应能继续补传并读到旧文件：B-04/C-04 保留现有固定后端行为及 local 恢复；尚未完成的 S3/跨后端迁移证明集中 F-03。
2. 外部已发布成功但本地超时，重试应查回同一文档：B-08、D-05、F-02 测响应丢失及并发重试。
3. 人员被移除时回听仍在进行，应终止访问且管理员不能绕过：B-02、D-06、F-05 测实时撤权。
4. 人工编辑期间迟到的模型结果不得覆盖输入：B-07、C-03、D-04 测版本/指纹冲突。
5. 文件落盘或隔离失败时不能显示已保存或双写：B-04/C-04 保留 local 持久化与失败处理；双机隔离、网络分区和生产断电资格集中 F-02。

## 1. 阶段与交付门槛

| 阶段 / 子计划 | 可演示交付物 | 阶段通过标准 | 前置依赖 |
| --- | --- | --- | --- |
| [A：已交付基础（M0）](2026-10-21-ama-phase-a.md) | 复用工程、原型基准及现有验证工具 | 已完成项保持完成；部署专属 pending 归 F | 既有实现与有效证据，不重做 |
| [B：首次 Demo（M1-Demo，优先）](2026-10-21-ama-phase-b.md) | 会议/信息→上传/转写→生成/审阅→发布/正式文档，每段同步页面与接线，最后完整演示 | 真实浏览器链、逐页原型标准、基本权限/冲突/幂等/未知/保存恢复通过，无重要缺陷；深度证明逐项列 B-QUAL | 复用 A/B 已采纳能力；一个本地 MySQL/local/单机；不依赖 S3、双 MySQL、双活 |
| [B：正式本地验收（M1-B）](2026-10-21-ama-phase-b.md) | B-QUAL 汇合 B-L5/Task13 深度核心证明与正式交付 | B 摘要最晚 M1-B 的断言全部满足、B-00 差异/适用回归/全分支审查收口；真实平台能力另列 | M1-Demo 和本地实际证据；深层证明缺口不能变成删除要求；通过后停止交接 |
| [C：音频与实时（M2）](2026-10-21-ama-phase-c.md) | 音视频处理、工作台、在线录音、断线补传与实时字 | 本地链路可演示；C-07 本地协议容量及真实模型实时指标按范围另验，均不前置到 M1 | B 的文件/任务/权限契约；真实模型依赖 A-03；S3 部分归 F |
| [D：完整业务与 UI（M2）](2026-10-21-ama-phase-d.md) | 材料、模板/历史、发布治理、回看导出、列表/专题/删除 | 本地业务与原型页面有证据；Demo 页面复刻已拆入 B，完整三视口汇合在 D-08 | B 契约；音文定位依赖 C，其他独立业务/页面先做；S3 部分归 F |
| [E：运营治理与邮件（M3）](2026-10-21-ama-phase-e.md) | 反馈审核、声纹、评测、配置、邮件 | 本地单机完成业务细则；未审核不生效、邮件未知不盲重发；真实平台联调分列 | A-02 专项规则、D 权限/有效成果，不新增部署前置 |
| [F：最后部署适配与交接（M4）](2026-10-21-ama-phase-f.md) | 确认需求后集中 S3/双 MySQL/双活实现配置、集成回归、性能恢复与运维交接 | 原六组合、NFR-01…10、HA-01…14 按确认范围逐项验收；待环境/未完成如实保留 | B/C/D/E 业务完成；实际需求、环境和配额到位；接收 DEP-S3/MYSQL/HA/MIGRATE/MATRIX |

当前顺序：DEMO-01 复核会议/信息及原型 → DEMO-02 上传/转写 → DEMO-03 生成/审阅 → DEMO-04 发布/正式文档 → DEMO-05 交付 M1-Demo → B-QUAL 完成 M1-B 后停止。模型和发布提供方随对应片段增量接入；不等全部环境/页面/深度测试齐备才展示。真实平台接口可用时嵌入 B-02/07/08，不能一概后移 F。后续另行启动 C/D 独立业务及页面，音文按 C 依赖 → E 各任务按实际依赖 → 最后 F。具体验收见[调整计划第 3～6 节](2026-10-28-ama-demo-first-adjustment.md)及[B 开放义务表](../../acceptance/b/summary.md#current-open-obligations-r2)。F 专用模板、配置、架构和测试不提前开展；可交错推进不自动派发代理。

B-00 交付 ER 图、逐表数据字典、审阅用 DDL、事务/版本/删除与迁移策略及独立评审记录，具体路径和通过标准见 B 阶段计划。覆盖一期已确认的数据需求，并将实体/表映射到 B…E 的实现任务；未定业务规则记录责任方、影响表和补充评审条件，不冻结为已批准结构。B 阶段所需表的字段、约束和索引必须完整，无阻塞设计问题，且设计、DDL、迁移分工一致，才可通过 B-00。

本次插入任务的过渡规则：保留已开展的 B-01 及其他任务的代码、提交和测试证据，不因编号顺序追溯重做。B-00 以执行工作树中的现有迁移和实现为输入，记录基线提交/迁移版本并形成差异清单；通用迁移、事务、审计、存储端口等不依赖未定表结构的工作可继续收尾，依赖未评审结构的新建表/改表及相关任务最终验收须等对应评审通过。已应用或共享的 Flyway 脚本保持不可变，修正使用无冲突的新版本迁移；新增编号由执行者统一登记。评审通过后按差异补充修复和回归，再继续依赖任务。修改主工作区计划不会自动更新其他工作树或已运行代理的上下文，执行者须先同步这两份计划并确认采用的版本。

每任务分别记录 `源码/实现验证`、`M1-Demo 可演示`、`M1-B 本地正式验收`、`平台联调`、`部署验收`。注明单元/API/浏览器及受控提供方范围；受控合成纪要不代表真实 AI 质量。Demo 不等待全部深度 B 证明，也不替代正式 B、公司能力或生产资格。后移部署用例登记 F 待办，不能作为前期必需测试的跳过通过记录；没有实际证据的状态保持未通过。

## 2. 代码与目录边界

| 待创建路径 | 职责 / 首个拥有任务 |
| --- | --- |
| `pom.xml`、`mvnw`、`.mvn/wrapper/maven-wrapper.properties` | Maven 聚合、Java 21、版本锁定；A-01 |
| `backend/contracts/src/main/java/ama/contract/` | 跨进程请求/响应类型；由各功能任务新增自己的契约文件 |
| `backend/core/src/main/java/ama/` | meeting/capture/transcript/minutes/publication 等领域与端口；不依赖 Web 层 |
| `backend/adapters/src/main/java/ama/` | JDBC、公司集成、local/S3 客户端与模型实现 |
| `backend/api/src/main/java/ama/api/` | 业务 HTTP、会话、文件代理与鉴权 |
| `backend/realtime/src/main/java/ama/realtime/` | WSS、录音分片、字幕和重连；C-04 |
| `backend/worker/src/main/java/ama/worker/` | 任务轮询、模型/解析/导出执行；B-05 |
| `backend/file-service/src/main/java/ama/file/` | local 活动文件服务；B-04；s3 不启动 |
| `docs/superpowers/specs/2026-10-21-ama-database-design.md` | ER 图、事务/版本/生命周期、索引依据与迁移分工；B-00 |
| `docs/database/ama-data-dictionary.md`、`docs/database/ama-schema-draft.sql` | 逐表字段/约束定义与审阅用 DDL；B-00；DDL 草案不直接充当生产迁移 |
| `docs/acceptance/b/database-design-review.md` | 设计版本、独立评审、阻塞项与现有实现差异闭环证据；B-00 |
| `backend/adapters/src/main/resources/db/migration/` | 以 B-00 评审基线为依据；B-01 起按领域追加 Flyway 不可变迁移，修正迁移统一登记版本避免冲突 |
| `backend/integration-tests/src/test/java/ama/` | 真实 MySQL/文件、协议和副作用测试；A-01 提供测试基础 |
| `web/src/features/`、`web/src/shared/` | 按功能的 Vue 页面与通用控件；A-05 起 |
| `web/tests/e2e/`、`web/tests/visual/` | 用户流程与原型截图对比 |
| `tools/probes/`、`tools/load/` | 不进入生产服务的能力探针、录音负载和报告分析 |
| `deploy/`、`docs/runbooks/`、`docs/acceptance/` | 部署配置、操作手册、可审计的验证报告；A/F |

包根为 `ama`。模块之间只依赖 contracts/core 的公开端口；adapters 实现端口，启动进程负责装配。首个任务创建所需模块 POM，未来模块随其首个任务加入根模块列表，不预生成空模块冒充进展。API 按 `/api/v1`，错误含 `code/message/traceId`；并发冲突 409、未登录 401、已登录越权 403、无效输入 422、暂不可用 503。

所有 ID 在接口中使用字符串，revision/epoch/seq 使用非负整数且约定安全范围；二进制/超长内容不塞入普通 JSON。可空日期保持 null。每个任务定义的新类型放在对应 `*Contracts.java`，TypeScript 由 OpenAPI 生成到 `web/src/shared/api/generated.ts`，不手写两份结构。OpenAPI 分片放 `contracts/openapi/`，A-01 定义合并/校验命令。

## 3. 测试命令与任务执行约定

A-01 建立 `tools/checks/run.py`，后续任务的命令均从仓库根目录在 Linux/WSL 执行。接口固定为 `python3 tools/checks/run.py <unit|it|web|e2e|visual|contract|load|ops> <selector>`，原样传递子命令退出码，零匹配、缺环境或跳过必需用例均非成功；报告实际测试数量和证据路径。

- `unit`：Maven Surefire 按类选择 `*Test`；聚合模块允许无同名测试，但最终报告必须有匹配用例。
- `it`：Failsafe 的 `-Dit.test=<Class> verify`，真实测试 MySQL；`contract` 另读取 `AMA_TEST_*` 公司测试配置，不向生产地址默认发送请求。[Failsafe 官方命令](https://maven.apache.org/surefire/maven-failsafe-plugin/examples/single-test.html)
- `web`：统一入口选择当前已注册的 `web/tests/unit/*.test.ts`，通过锁定的 `tsx --test --test-reporter=junit` 执行 Node 原生传输单测并检查新鲜 JUnit 结果；`e2e/visual` 调用 Playwright，正常运行禁止写入缺失基准，保留 trace/截图差异。后续组件单测按需要补充，外部 runner 接口不变。[Playwright 断言](https://playwright.dev/docs/test-assertions)
- `load/ops`：在 C-07/F-01 注册明确的场景，不允许 selector 作为任意 shell 命令执行。破坏性演练只接收已批准的测试环境清单。

每任务的测试步骤写出名称、输入和关键断言；代码片段是测试核心断言，数据准备在同一步描述。A-01 提供 `ApiReply(int status, JsonNode body)` HTTP 测试工具和测试故障注入设施，故障控制只存在测试进程，不暴露生产后门。领域测试使用任务内创建的夹具，禁止生产测试共享状态。

每任务先写行为测试并确认失败原因正确，再实现、运行本任务和受影响回归、审阅、提交。纯文档/配置清单不强制写镜像式测试，以内容检查和实际配置校验验证。提交只包含本任务文件，不使用无差别 `git add .`，不自动推送、合并或执行生产部署。

## 4. 需求到任务的追踪

| 需求编号 | 主实现任务 | 最终验收 |
| --- | --- | --- |
| CAP-01、CAP-03、CAP-06 | B-04、B-06、B-09、C-01 | D-08、F-04 |
| CAP-02 | C-01、C-02 | C-07、F-04 |
| CAP-04、CAP-05、CAP-07 | D-01 | D-03、F-05 |
| CAP-08、CAP-09、CAP-10 | C-04、C-05、C-06 | C-07、F-02、F-04 |
| CAP-11、CAP-12、CAP-13、CAP-14 | B-02、B-03、D-02 | D-05、F-05 |
| TX-01、TX-02、TX-03 | B-05、C-02、C-03 | C-07、D-06 |
| TX-04、TX-05、TX-06 | C-03、D-01 | D-08、F-05 |
| TX-07、TX-08、TX-09 | B-06、C-03 | B-09、D-06 |
| TX-10 | E-01 | E-03、F-05 |
| MIN-01 | B-07 | D-03、F-04 |
| MIN-02、MIN-03、MIN-04、MIN-05、MIN-06、MIN-12 | D-03 | D-05、F-05 |
| MIN-07、MIN-08、MIN-09、MIN-10、MIN-11 | D-04 | D-08、F-05 |
| MIN-13、MIN-14 | B-08、D-05 | F-05 |
| MIN-15 | D-06 | F-04、F-05 |
| PUB-01、PUB-02、PUB-03、PUB-04、PUB-05 | B-08、D-05 | B-09、F-02 |
| PUB-06、PUB-07、PUB-08 | D-05、D-06 | F-02、F-05 |
| LIST-01、LIST-02、LIST-03、LIST-05 | D-02、D-08 | F-04、F-05 |
| LIST-04 | D-07 | F-02、F-03 |
| LIST-06、LIST-07 | D-06 | F-05 |
| CROSS-01、CROSS-02 | B-00、B-01、B-03、B-05、D-04 | F-05 |
| CROSS-03 | E-04 | F-05 |
| CROSS-04 | E-01、E-02、E-03 | F-05 |
| CROSS-05 | A-05、B-09、C-03、C-05、D-08、E-01…E-04 | F-05 |
| CROSS-06、ARC-03、ARC-10 | B-04 | F-01、F-02、F-03、F-04 |
| ARC-01、ARC-05 | A-03、C-02、C-06、C-07 | F-04 |
| ARC-02、ARC-04、ARC-09 | A-04、F-01 | F-02、F-04 |
| ARC-06、ARC-07 | A-02、B-02、B-08、D-01、D-05 | F-05 |
| ARC-08 | 全阶段横切目标 | F-02…F-06 |

## 5. 外部条件和业务缺口的收口

A-02 已有决定台账继续使用。D-01…D-19 不重新逐项问已确认的问题：新导入抽屉、只读发布、七类主体等业务基线不变；local/S3 已实现保留，S3 未完适配在 F。尚未明确的输入组合/限额、日期入口、降级确认、OCR、邮件及治理细则在各自任务前补充；双 MySQL 实际语义和部署拓扑到 F-00 集中确认。

SSO/人员接口阻塞 B-02 真联调；模型容量阻塞 A-03/C-06/F-04 实测；知识库契约阻塞 B-08/D-05 真发布验收；邮件/治理规则阻塞 E 对应功能定型；虚拟化/fencing 阻塞 F-02 私有自动恢复验收。均不阻塞不依赖它们的代码/接口测试；mock 或测试账号只用于非生产配置。

## 6. 统一完成定义与交付记录

- [ ] 每任务关联需求编号、提交、测试命令、结果和遗留条件；无实现的按钮不得标成功。
- [ ] 每阶段交付 `docs/acceptance/<phase>/summary.md`，包含演示步骤、失败/越权案例、真实/模拟标识和未通过项目。
- [ ] 性能/故障报告包含环境、版本、样本、请求分布、原始指标和时间线，不只写结论。
- [ ] 每阶段复核本表覆盖；变更影响接口、数据或安全边界时先修订相关规格，不重新打开无关已批准范围。
- [ ] 数据库相关任务引用 B-00 评审基线与迁移版本；阶段 B 验收前关闭现有实现差异。C…E 新增或调整表结构时同步数据字典/ER 图并完成对应增量评审，F 验证旧新应用兼容迁移与回滚边界。
- [ ] 当前从最新接续入口定位已存在开发树，按 DEMO-01～05 交付首次 Demo，再由 B-QUAL 正式收口；A-01 及已采纳 B 能力不重新开发，不重建工作树。

执行组织沿用 AMA-WORKFLOW-20261027 的主 Agent 内联、完整功能批次和集中审查；本次文档调整无需派发实现或运行批次，也不自动提交、推送或合并。

阶段 A 本地交付和后续前置条件见[验收报告](../../acceptance/a/summary.md)；公司真实联调及部署资格仍按各阶段门禁收口。
