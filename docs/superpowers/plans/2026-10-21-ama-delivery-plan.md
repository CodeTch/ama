# 智能会议助手分阶段交付 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 按六个阶段交付完整一期会议助手，覆盖本地/S3 文件后端、100 场实时会议、300 在线用户和两台服务器自动恢复。

**Architecture:** 以已确认的 v0.2 为唯一总体架构基线；模块化 Java 后端，API/实时/Worker 隔离，Vue 前端复刻原型。数据库来源和文件后端独立配置，领域状态与版本统一，不为部署方式复制业务代码。

**Tech Stack:** Java 21、Spring Boot 4.x、MySQL 8.4 LTS、Vue 3/TypeScript/Vite、local/S3、Podman；JUnit、Maven Surefire/Failsafe、Vitest、Playwright。精确补丁版本在 A-01 锁定。

**Spec:** [已确认架构 v0.2](../specs/2026-10-21-ama-architecture-design.md)、[需求对应](../specs/2026-10-21-ama-requirements-matrix.md)、[部署前置条件](../specs/2026-10-21-ama-deployment-prerequisites.md)。

状态：2026-10-21，实施计划待用户评审并选择执行方式。本轮只制定计划；以下路径、接口和命令是待实现的交付约定，不代表文件或程序已经存在。全部任务初始未开始，不提交完成勾选。工作量按真实完成速度再估算，不在未知团队投入及接口交付时间时承诺上线日期。

## Global Constraints

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

1. 存储切换时尚在录音，用户应能继续补传并读到旧文件：B-04、C-04、F-03 测后端固定与迁移中断。
2. 外部已发布成功但本地超时，重试应查回同一文档：B-08、D-05、F-02 测响应丢失及并发重试。
3. 人员被移除时回听仍在进行，应终止访问且管理员不能绕过：B-02、D-06、F-05 测实时撤权。
4. 人工编辑期间迟到的模型结果不得覆盖输入：B-07、C-03、D-04 测版本/指纹冲突。
5. 文件落盘或隔离失败时不能显示已保存或双写：B-04、C-04、F-02 测 fsync 失败、缺口与网络分区。

## 1. 阶段与交付门槛

| 阶段 / 子计划 | 可演示交付物 | 阶段通过标准 | 前置依赖 |
| --- | --- | --- | --- |
| [A：工程与关键验证](2026-10-21-ama-phase-a.md) | 可启动的受保护健康接口、原型基准、模型探针、HA 前置报告 | A-01…A-05；本地检查通过；外部未到位明确记录为待验证 | 架构已批准；执行方式待选 |
| [B：基础与首个闭环](2026-10-21-ama-phase-b.md) | 转写文件导入→人工确认→纪要→测试知识库发布 | local/s3 分别跑通；越权/冲突/发布未知均有测试；真实平台联调单列 | A-01、A-02、A-05；B-07/08 真联调依赖模型/知识库 |
| [C：音频与实时](2026-10-21-ama-phase-c.md) | 音视频处理、工作台、在线录音、断线补传与实时字 | 100 路应用协议测试；真实模型满足实时指标才算实时验收通过 | B-01…B-06；真实模型依赖 A-03 |
| [D：完整业务与 UI](2026-10-21-ama-phase-d.md) | 材料、要素/来源、模板/历史、发布治理、回看、导出、列表/专题/删除 | 所有已定义一期业务规则与原型页面有验收证据；未保存/撤权/删除全链路覆盖 | B 的领域/文件/任务契约；音文定位依赖 C |
| [E：运营治理与邮件](2026-10-21-ama-phase-e.md) | 反馈审核、声纹、评测、配置、邮件 | 补充业务细则通过后实现；未审核数据不生效；邮件未知不盲目重复发送 | A-02 的专项规则；D 权限/有效成果 |
| [F：生产资格与交接](2026-10-21-ama-phase-f.md) | 六种部署组合、负载报告、故障恢复证据、备份恢复和运维手册 | NFR-01…10、HA-01…14、安全/恢复/回滚与交接通过 | 全部功能；实际环境和平台配额到位 |

执行顺序：A 的工程契约先行→B→C/D→E→F。A 的模型/运维验证是并行工作线，不要求等待所有运维反馈才开始 B。F 的部署模板可在 B 稳定后制作，生产资格必须等真实功能及环境。这里的并行表示依赖允许，并不自动派发子代理。

每任务分三种证据：`实现验证`（单元/受控集成）、`平台联调`（公司测试接口）、`部署验收`（实际 RHEL/网络/存储）。只完成第一种不得将后两种标绿。允许继续不依赖缺失条件的任务；禁止用跳过测试、模拟模型或单机运行替代目标验收。

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
| `backend/adapters/src/main/resources/db/migration/` | Flyway 不可变迁移；B-01 起按领域编号追加 |
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
- `web`：`npm --prefix web run test -- <selector>`，脚本为 `vitest run`；`e2e/visual` 调用 Playwright 并保留 trace/截图差异。[Vitest CLI](https://vitest.dev/guide/cli.html) · [Playwright 断言](https://playwright.dev/docs/test-assertions)
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
| CROSS-01、CROSS-02 | B-01、B-03、B-05、D-04 | F-05 |
| CROSS-03 | E-04 | F-05 |
| CROSS-04 | E-01、E-02、E-03 | F-05 |
| CROSS-05 | A-05、B-09、C-03、C-05、D-08、E-01…E-04 | F-05 |
| CROSS-06、ARC-03、ARC-10 | B-04 | F-01、F-02、F-03、F-04 |
| ARC-01、ARC-05 | A-03、C-02、C-06、C-07 | F-04 |
| ARC-02、ARC-04、ARC-09 | A-04、F-01 | F-02、F-04 |
| ARC-06、ARC-07 | A-02、B-02、B-08、D-01、D-05 | F-05 |
| ARC-08 | 全阶段横切目标 | F-02…F-06 |

## 5. 外部条件和业务缺口的收口

A-02 创建一份带责任方、请求字段、证据和受影响任务的决定台账。D-01…D-19 不重新逐项问已确认的问题：新导入抽屉、只读发布、七类主体、local/S3 按已确认方案执行；尚未明确的输入组合/限额、日期入口、降级确认、OCR、邮件及治理细则集中在各自任务开始前形成可审阅补充规格。

SSO/人员接口阻塞 B-02 真联调；模型容量阻塞 A-03/C-06/F-04 实测；知识库契约阻塞 B-08/D-05 真发布验收；邮件/治理规则阻塞 E 对应功能定型；虚拟化/fencing 阻塞 F-02 私有自动恢复验收。均不阻塞不依赖它们的代码/接口测试；mock 或测试账号只用于非生产配置。

## 6. 统一完成定义与交付记录

- [ ] 每任务关联需求编号、提交、测试命令、结果和遗留条件；无实现的按钮不得标成功。
- [ ] 每阶段交付 `docs/acceptance/<phase>/summary.md`，包含演示步骤、失败/越权案例、真实/模拟标识和未通过项目。
- [ ] 性能/故障报告包含环境、版本、样本、请求分布、原始指标和时间线，不只写结论。
- [ ] 每阶段复核本表覆盖；变更影响接口、数据或安全边界时先修订相关规格，不重新打开无关已批准范围。
- [ ] 执行入口为 A-01；初次执行前读取 using-git-worktrees，建立/复用合适隔离工作区。本轮不创建实现分支或安装依赖。

计划评审后选择执行方式：建议按任务由实现代理与独立审查代理推进，因为跨模块版本、文件持久化和外部副作用的错误会影响真实会议数据；也可由当前会话顺序实现，阶段结束统一独立审查。此处只是建议，尚未派发代理，也未启动编码。
