# A：工程基础与关键能力验证 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** 交付可运行工程和可复现验证工具，尽早暴露模型、存储及自动恢复条件的缺口。

**Architecture:** 为已批准的模块化应用建立工程契约与 UI 基准。外部探针默认不执行生产调用；运维验证与业务开发分开推进。

**Tech Stack:** Java 21、Spring Boot 4.x、Maven、Vue 3/TypeScript、Python 3 标准库探针、JUnit/Vitest/Playwright。

**Spec:** [架构 v0.2](../specs/2026-10-21-ama-architecture-design.md) 第 1—5、11、13—15 节；[总计划](2026-10-21-ama-delivery-plan.md)。

## Global Constraints

- 继承总计划全部约束；本阶段不改变“两台 RHEL 8.10 x86_64 虚拟机，每台 16 核、32GB 内存”。
- 模型输入为 16kHz、单声道、16bit PCM WAV；VAD/SPK 为完整 WAV 的 Base64，ASR/embed 为 WAV 二进制。
- 100 场、2 秒窗口约 50 ASR 请求/秒；该数字是探针负载目标，不能据此认定模型有此吞吐。
- 具体公司协议、目录、容量和隔离能力未提供的项目标记为阻塞相应联调，不制造成功报告。

## Review Focus

1. local 配置没有 Bucket/密钥仍能启动：A-01 的 `localStartsWithoutS3Credentials`。
2. 缺少平台接口清单被误认为验证通过：A-02 的 `missing_contract_is_blocked`。
3. HTTP 成功但模型响应字段缺失：A-03 的 `missing_text_is_invalid_response`。
4. 两 VM 实际同一宿主或隔离不可达：A-04 的 `unsafe_topology_cannot_pass`。
5. 原型旧脚本与最终显示不同：A-05 以最终浏览器渲染截图作基准。

## Task A-01：配置边界、运行探针与测试入口

**Files:** Create `pom.xml`、`mvnw`、`.mvn/wrapper/maven-wrapper.properties`、`backend/contracts/pom.xml`、`backend/core/pom.xml`、`backend/adapters/pom.xml`、`backend/api/pom.xml`、`backend/integration-tests/pom.xml`；`backend/api/src/main/java/ama/api/ApiApplication.java`、`backend/core/src/main/java/ama/config/DeploymentConfig.java`、`backend/api/src/main/resources/application.yml`、`contracts/openapi/api.yaml`、`tools/checks/run.py`、`tools/checks/tests/test_run.py`。Test `backend/api/src/test/java/ama/api/DeploymentConfigTest.java`、`backend/integration-tests/src/test/java/ama/support/ApiReply.java`、`backend/integration-tests/src/test/java/ama/support/ContractClient.java`。

**Interfaces:** Produces `DeploymentConfig.parse(Map<String,String>): DeploymentConfig`，字段 `databaseMode/storageType/defaultBackendId`；`GET /internal/health/live`、`GET /internal/health/ready`；测试 `ContractClient.request(String method, String path, JsonNode body, Map<String,String> headers): ApiReply`。健康接口由内部网络保护，不输出密码、文件根路径或内部地址。

- [x] 搭建本任务需要的最小构建/测试入口，锁定兼容补丁、Node/npm 版本和 wrapper 校验信息；创建 `.gitignore` 排除密钥、构建产物和测试音频私密样本。
- [x] 写 `localStartsWithoutS3Credentials`、`s3MissingEndpointRejected`、`readinessFailsWhenDatabaseUnavailable`；断言核心为 `assertEquals("local", config.storageType()); assertEquals(503, reply.status());`。Python 测试验证零匹配和子命令失败不会返回成功。
- [x] 运行 `./mvnw -pl backend/api -am -Dtest=DeploymentConfigTest -Dsurefire.failIfNoSpecifiedTests=false test` 和 `python3 -m unittest discover -s tools/checks/tests`；应在未实现行为上失败，不能只因网络/依赖下载失败记 RED。
- [x] 实现配置验证、分离存活/就绪、统一错误返回和测试入口；新增依赖只允许已选技术范围，OpenAPI 合并检查纳入 runner。
- [x] 运行 `python3 tools/checks/run.py unit DeploymentConfigTest`；PASS 且有真实执行数；启动后检查存活不因短时数据库失败退出、就绪正确拒流。
- [x] 提交本任务路径，提交说明 `build: establish application configuration and verification entrypoints`；交付可启动 API 和环境能力报告。

## Task A-02：外部契约和业务决定台账

**Files:** Create `docs/contracts/integration-catalog.md`、`docs/contracts/product-decisions.md`、`config/contracts.test.example.json`、`tools/probes/check_contracts.py`、`tools/probes/tests/test_contracts.py`。

**Interfaces:** Produces `check_contracts(manifest: dict) -> dict`，结果包含 `checks[{id,status,owner,blockedTasks,evidence}]`，status 限 `verified|pending|failed`；命令 `python3 tools/probes/check_contracts.py --manifest <path> --offline`。公司接口原始格式不预先伪造。

- [x] 写 `missing_contract_is_blocked`、`secret_fields_are_redacted`；给缺失 SSO 协议的清单，断言 `assert report['checks'][0]['status'] == 'pending'`；日志不含测试 token。
- [x] 运行 `python3 -m unittest discover -s tools/probes/tests -p test_contracts.py`，确认失败对应台账校验行为。
- [x] 实现离线清单校验；逐项收口 INT-01…10、PR-01…15、PL-01…05，映射总计划任务；为 D-01…19 分类已采纳/需产品规格/需平台证据。邮件、治理、输入组合等缺口形成具体待决定字段，不用空白占位。
- [x] 重跑测试并审阅清单：未知项保持 pending，真实认证材料只记受控引用；给出 B/C/D/E/F 哪些工作可继续的结果。
- [x] 提交 `docs: define integration contracts and decision gates`；未取得接口不阻塞本工具交付，但不视为接口联调通过。

## Task A-03：实时模型可行性探针

**Files:** Create `tools/probes/model_probe.py`、`tools/probes/tests/test_model_probe.py`、`tools/probes/samples/README.md`、`docs/acceptance/a/model-capacity.md`。

**Interfaces:** Produces `probe(config: dict, wav_paths: list[str]) -> dict`；结果 `audioSeconds/requestCount/inflight/queueMs/serviceMs/endToEndMs/errors/observedFields`，时延带分位数和原始样本路径。配置需显式测试地址、授权标识和最大并发。

- [x] 写 `wav_encoding_matches_endpoint`、`missing_text_is_invalid_response`、`timeout_keeps_request_accounted`；本地假 HTTP 服务返回缺 text/超时/429，断言 `assert report['errors']['INVALID_RESPONSE'] == 1`，Base64 解码后包含 WAV 头且时间单位映射正确。
- [x] 运行 `python3 -m unittest discover -s tools/probes/tests -p test_model_probe.py`，确认协议/统计行为失败。
- [x] 实现 VAD/SPK/ASR/embed/punc/LLM 探针，区分接口文档已定义字段与实测可选字段；不将 600/1800 秒客户端等待策略当 SLA。
- [x] 重跑本地模型协议、统计、限流及断流回归测试。
- [ ] 在平台提供授权测试环境后，以 1/10/50/100 路逐级比较 2 秒、较长窗口与重叠策略，记录重复词/数字/术语/交叠质量和约 50 请求/秒承载情况；未获得环境只交付探针并标记真实容量 pending。
- [x] 提交 `test: add model protocol and capacity probes`；报告必须给“可用方案/未满足指标/需要的平台能力”，不能只列 HTTP 200 数量。

## Task A-04：HA 前置检查与演练输入

**Files:** Create `tools/probes/ha_preflight.py`、`tools/probes/tests/test_ha_preflight.py`、`deploy/inventory.example.json`、`docs/acceptance/a/ha-prerequisites.md`。

**Interfaces:** Produces `validate_topology(inventory: dict) -> dict`，包含 `canDeploy/canClaimAutomaticRecovery/blockers`；默认只读。输入指定两 VM 标识、故障域、磁盘/挂载、同步网络、fencing 提供方及最近验证证据。

- [x] 写 `unsafe_topology_cannot_pass`、`localStillNeedsFencingWithExternalDatabase`；给无隔离或相同未隔离宿主，断言 `assert result['canClaimAutomaticRecovery'] is False`；没有配置不得执行远程命令。
- [x] 运行 `python3 -m unittest discover -s tools/probes/tests -p test_ha_preflight.py`，确认失败对应判定。
- [x] 实现清单检查及 PR/PL 对应输出；提交 HA-01…14 的注入前提和证据模板，区分 local、私有 S3、公司 S3 的适用条件。
- [x] 重跑两节点 HA 清单及证据一致性测试。
- [ ] 取得运维资料后只读核验内核/订阅/磁盘/管理接口。隔离动作留至 F-02 已安排的测试演练，不在预检中自动断电。
- [x] 提交 `ops: add two-node HA prerequisite validation`；运维未确认项目保留 pending，不拖延 B 的独立开发。

## Task A-05：原型视觉基准与可运行前端外壳

**Files:** Create `web/package.json`、`web/package-lock.json`、`web/vite.config.ts`、`web/playwright.config.ts`、`web/src/main.ts`、`web/src/App.vue`、`web/src/shared/styles/tokens.css`、`web/src/shared/api/client.ts`、`web/src/shared/api/generated.ts`、`web/tests/visual/shell.spec.ts`、`docs/acceptance/a/prototype-index.md`。

**Interfaces:** Consumes A-01 OpenAPI；Produces `request<T>(path: string, init?: RequestInit): Promise<T>`，Cookie 会话、统一错误处理；路由 `/meetings`，后续页面按功能增加。

- [x] 在固定浏览器/字体、1440×900 与 1024×768 下记录原型最终渲染的页面/抽屉/弹窗入口；人工遮罩只限时间等动态内容，不遮罩布局差异。
- [x] 写 `shellMatchesPrototypeAndKeepsNarrowActionsVisible`；核心断言 `await expect(page).toHaveScreenshot('meeting-shell.png'); await expect(page.getByRole('button', { name: '导入会议' })).toBeVisible();`，基准来源标记为原型，不用新实现自我批准。
- [x] 运行 `python3 tools/checks/run.py visual shell.spec.ts`，确认布局/缺组件导致失败。
- [x] 实现设计令牌、导航、页面容器、空态与 API 客户端；保留真实未连接状态，不把静态演示按钮显示为操作成功。
- [x] 重跑视觉和 `npm --prefix web run typecheck`、`npm --prefix web run build`；审阅差异截图和原型索引后提交 `feat: establish prototype-aligned application shell`。

## 阶段验收与移交

- [x] A-01/A-05 有可运行产物；无公司依赖也能明确展示未就绪状态。
- [x] A-02…A-04 工具测试通过；公司接口和 HA 证据的 pending/failed 如实列出。
- [x] `docs/acceptance/a/summary.md` 指明 B 可启动的任务和 C/F 尚缺条件；没有真实模型负载不得宣布 100 路实时已达标。

本地工程和验证工具已交付，组合测试及独立审查记录见[阶段 A 验收](../../acceptance/a/summary.md)。上面保留未勾选的真实平台/运维验证不阻塞独立业务开发，也不计为已验收能力。
