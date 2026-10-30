# 背景材料后端：暂停接续检查点（2026-10-29）

用户最新指令要求完成当前批次调度后立即停下。本文件是保留现场的接续记录，不是完成报告、设计采用记录或迁移冻结记录。主会话负责汇总总接续文档及关闭所有 agent。

工作树：`/home/tanren/ama/.worktrees/phase-b-completion`。本批源码已直接写在共享树中；没有 commit/reset/clean、回滚他人修改或派生 agent。当前运行服务仍为旧版本。本 agent 未执行 Maven、javac、其他构建、测试、迁移应用或服务启停；没有读取秘密/私有 launcher 状态。用户发出暂停指令后，只确认下列文件存在并写本记录，没有继续实现或审查。

## 当前边界

**未完成、未编译、未验收，不可声称已可运行。** 已写入的代码意图实现真实来源接入，并非前端内存假数据；但持久化、权限、幂等、解析和完整分钟流水线均尚无本批测试证据。

已开始并写入：

- 新 `MaterialParser` 及独立文档解析适配器；TXT 段落、PDF 页、DOCX 段落、PPTX presentation 顺序；字节 SHA、输入/文本/单元界限、取消与诚实失败。扫描 PDF 无 OCR 不伪装成功。
- `JdbcMaterials` 使用真实 `FileUploads` 保存知识库原件和解析文本，使用 `FileRegistry` 验证原用途/原回执并绑定材料引用；关联、追加修订重试、软移除、快照、持久化幂等回执。失败材料保留并置 `materials_pending`，不应静默进入 EMPTY。
- `TranscriptFilePurposePolicy` 添加 `MATERIAL_SOURCE`；authority assembly 注册 `MATERIAL_CONTENT`/`MATERIAL_TEXT` 引用策略。
- 知识库 provider 新签名目的 `MATERIAL_READ`，真实读取当前 `can_read` 授权可见的库、文档及精确 payload SHA 版本；保留既有 bearer、Ed25519 delegation、TLS/pins 和 grant 路径。没有造文档、造授予、改运行凭证或重初始化服务。
- V017 候选：材料七张目标表/FK、parser profile、命令回执、source context、source_context_material、目标/证据直接 source_context_id，以及为兼容旧 FullRow hash 的两张只读投影视图。**候选未检查、未冻结、未应用。** 中央 migration register 未改。
- 核心配置/材料 catalog/generation fingerprint/StoredSourceGraph、JDBC 图发现/捕获/输入冻结、renderer、controlled wire 和候选/保存复制路径已有接线修改，但整体尚未收尾和验证。
- 已写 `MaterialDocumentParserTest` 三个基本用例；未执行。其他计划中的材料测试尚未写。

没有修改前端、OpenAPI YAML、`MediaTranscriptParser`/local_asr/media config、import draft/discard 实现、`tools/dev/local_app.py`、中央 migration register。共享文件原先已有他人修改，本批对这些文件进行了局部增量；不要把当前整个 diff 归为本 agent 所有。

## 已写 API 契约（源码草案，服务仍旧版本）

`GET /api/v1/meetings/{meetingId}/materials`

```json
{
  "items": [],
  "materialSnapshotId": null,
  "materialsPending": false,
  "meetingRevision": "12"
}
```

每个 item：

```json
{
  "id": "material UUID",
  "materialRevisionId": "revision UUID",
  "parsedDocumentId": "parse-result UUID",
  "fileId": "original-content file UUID",
  "name": "original receipt display name",
  "source": "LOCAL",
  "sourceVersion": null,
  "sizeBytesToken": "1234",
  "sha256": "64 lowercase hex",
  "state": "READY",
  "error": null
}
```

`source` 为 `LOCAL|KNOWLEDGE`；knowledge 的 sourceVersion 是 provider 的精确不可变 payload SHA；`state` 实际本稿返回 `READY|FAILED`；失败返回安全错误码字符串，绝不把失败当作无材料。ID 都是服务器真实持久化 ID。

`POST /api/v1/meetings/{meetingId}/materials`：必需一个 `Idempotency-Key`，请求 JSON 恰好一种：

```json
{"fileId":"completed MATERIAL_SOURCE upload file UUID"}
```

或者：

```json
{"libraryId":"controlled-library","documentId":"doc-...","version":"64 lowercase payload SHA"}
```

返回 201 + item；有界同步解析失败仍保存关联和失败结果，返回 FAILED item。格式支持草案：本地 PDF/PPTX/DOCX/TXT；原型 accept 为 PDF/PPTX/DOCX，TXT 为复用技术能力。上传继续复用现有 begin/part/completion 端点与真实原回执，purpose=`MATERIAL_SOURCE`。

`POST /api/v1/meetings/{meetingId}/materials/{materialId}/retry`：无请求体，必需 `Idempotency-Key`，返回 200 + 新修订 item；只有 FAILED/UNSUPPORTED 可开始新解析，历史修订保留。`DELETE` 同一路径返回 204；软移除，历史来源和文件引用保留，重复删除原意幂等。

`GET /api/v1/material-libraries?meetingId=...&search=...&cursor=...&limit=20`：

```json
{"items":[{"id":"controlled-library","name":"Controlled library"}],"cursor":null}
```

`GET /api/v1/material-libraries/{libraryId}/documents`，同样 query：

```json
{"items":[{"id":"doc-...","libraryId":"controlled-library","name":"真实文档标题","version":"64 lowercase SHA","sha256":"same SHA","sizeBytesToken":"1234"}],"cursor":null}
```

`meetingId` 必需；search 可选最长128 UTF16，limit 1..50；cursor 绑定 actor/meeting/library/search/provider/store。原文预览/下载使用已有文件下载端点和 item.fileId；未新增专门 preview 端点。

私有读响应设置 `Cache-Control: private, no-store`。新增 API 错误 handler 已写；身份 filter 对新增根路径/认证失败响应的 private/no-store 分类尚未补查。

## processing 与既有任务的明确限制

- 本稿没有引入新的 transcript enhancement provider、producer 或 ENHANCE task。原本 transcript IMPORT 的 frozen config/manifest 仍是 EMPTY 的原件抽取；没有把背景材料伪装成主转写输入。
- 新材料自动保存/应用材料 selection snapshot，修改 meeting business revision；这与主会话 main-files-only import draft 的独立 selection revision 不同。前端 mutation 后应 GET 材料列表拿最新 meetingRevision，再刷新所需会议/processing 状态。
- subsequent minutes generation 已开始接 server 的 `material_snapshot_id`，包括所有单位文本、来源 alias、精确 parser profile、source context 和 snapshot fingerprint；这一路尚未证实能完整跑通。
- **“应用材料并重新增强”仍未实现**。现有 task footer 不能调用本材料 API 后就显示转写增强成功。应在继续任务时由主会话明确完成真实 enhancement 路径或保留明确不可用状态；不能把 minutes generation 冒充 transcript enhancement。对 READY/RUNNING/RECONCILING 活跃任务，本稿材料 mutation 选择返回 `409 MATERIAL_PROCESSING_ACTIVE`。
- 没有给 processing submit 增加材料请求字段。此前向主会话说明：保持主文件 extraction submit 原形，minutes 在服务端读取精确 snapshot；main 不应仅将 `materialMode` 宣称 PINNED 就认为 transcript import 已消费背景。

## 编译/SQL/流水线风险与已知未收尾点

以下是作者在暂停时已知的风险，不是已完成检查的结论；不准用本记录当验证证据。

1. **Java 全部未编译。** 新代码使用 Java21 records、pattern switch、现有档案和 JDBC API；存在括号/类型/方法签名风险。`MaterialCatalog.manifest` 一行构造表达式尤其需要先核对括号；新增 Table enum 对其他 exhaustive switch 的影响未检查。批量字符串替换过的现有类需要在继续时逐处确认，不能假定所有替换命中且正确。
2. **V017 不能直接应用。** 初稿从目标 schema 筛取涉及 material_snapshot/material_revision 的所有 FK，再追加 source_context CREATE；因此存在把 source_context/source_context_material 的 ALTER 排在 CREATE 之前、重复或命名冲突的具体风险。主会话需先整理 create/FK 顺序与增量字典，确认后登记/冻结精确字节。旧迁移原件未改，V028 归 main。
3. **预算未端到端贯通。** 文档 parser 有独立正值技术界限，图 capture 继续用既有 cumulative ledger；但是物料列表 metadata/LONGTEXT admission、知识库 text aggregation、PDF writer 异常映射、多个 ZIP 条目的内存/时间边界等尚未充分收尾或测试。不得因为 parser 有界就宣称所有 domain budget 已证明。
4. **source context 收尾未完成。** 已有证据 context projection 和复制接线，但非 ACTION、零证据 element basis root、历史混合 parser、候选 restore、copy/remap、readback 比较和完整 graph closure 尚未通测；有的路径仍可能拒绝材料或漏捕获新投影。`MaterialBinding` 对 persisted context 的 catalog/ref/confirmation 三者精确相等校验需继续补齐；当前不是完整 D01 addendum 验收。
5. **runtime archives 未安装。** 新 helper `ControlledMaterialArchives.enable(freshMinutesArchives)` 仅准备档案字节，没有接 local admin 配置或重启。恢复时需用全新 release 名调用 `ControlledMinutesArchives.build(...)` 后 enable，安装该 release 并选择其 prompt/model refs，保留所有旧 archive/index 历史；绝不能拿旧 release 原地替换。worker 与 API 必须一致读取新 retained index。现有 provider 主业务仍是原先受控模型行为；本稿只接背景 context，未伪造新的智能增强。
6. **真实知识库测试缺失。** 新浏览/精确版本接口源码已写，但未执行签名/TLS/grant 撤销、搜索/cursor、跨 actor/meeting/library、restart persistence 等测试；未假定现有 store 已有任何具体文档或授权。
7. **材料服务事务/幂等尚未证明。** 新写 guarded transaction 使用 PublicationWriteGuard、真实 completed-source acquisition/bind，但 root fence、task mutex/锁顺序、重复 add/retry/delete、删除后回放、失败后追加 revision、generated protective uploads 的保留/清理均未验证。不得把“调用 guard 的代码存在”当成正确性的证据。
8. 本次完整生成之前遇到两次脚本失败：默认 `python` 不存在；后改 python3 的迁移生成脚本曾有括号语法错。两次均未写对应 Python 批次的目标；后续成功批次已经写出迁移和 purpose/authority 修改。Java heredoc 在最初脚本之前已经成功保留，不需要重新生成。没有因此撤销任何他人文件。

## 测试状态与恢复顺序

未运行任何 Maven/Java/Python 测试或 build；未运行迁移、API、浏览器或端到端验收。暂停前曾执行一次 `git diff --check -- backend tools/providers/controlled_knowledge.py`（当时无输出），它早于最后几轮新增源码，只能说明那一时刻的 whitespace，不能证明当前 checkpoint 或编译结果。

已经写入但未执行的类：`ama.material.MaterialDocumentParserTest`（3 tests：TXT 段落与原 SHA；hash/size 拒绝；PDF 格式不猜测及取消）。尚未写入持久化/权限/幂等/知识服务/材料 fingerprint 和 context 的专用测试。

仅在用户说“继续任务”并由主会话重新协调后：

1. 先读本文件与总接续文档，核对下面 manifest，避免重新实现同样类或丢掉其他工人的代码。明确 enhancement 未完、材料分钟 path 未验收两个边界。
2. 修复当前已有源码的 Java/SQL/graph 连接问题，再补材料专用测试；不要以新模块覆盖或重建现有文件。SQL 冻结/应用、Maven 和服务启停仍由 main 串行。
3. 主会话可在准备好时执行 focused unit command：`./mvnw -pl backend/adapters -am -Dtest=MaterialDocumentParserTest -Dsurefire.failIfNoSpecifiedTests=false test`；wrapper 是否存在和完整仓库构建约定尚未本批核对，请以主会话已采用命令为准。现有 empty-material raw hash chain、controlled protocol、saved sources/copy/final/publication 相关套件必须回归；新增材料持久化集成测试写完再列精确类名，不能假称已有。
4. 档案、V017、API/worker serialization 完成后，验证真实上传/刷新恢复/知识版本/parse fail-retry-remove/来源证据与预算/授权撤销；此之前不要展示“功能完成”。

## 精确文件清单与暂停时 SHA256

这些是本 agent 实际写入过的39个源码/测试/迁移文件；新增本接续文档另计。共享已有文件的 SHA 表示记录时整文件字节，不意味着其中全部修改归本 agent。其他 agent 仍可能随后编辑共享文件；继续前若 hash 不同，请先看主会话接续记录，不能强行覆盖。

|工作树相对路径|SHA256|
|---|---|
|`backend/core/src/main/java/ama/material/MaterialParser.java`|`af7c0ceec7e49b9cf076f9d8ee040a41d621debb306ebb0ef20f2725017af58e`|
|`backend/core/src/main/java/ama/material/MaterialProfiles.java`|`5c93223e2dce27726c28232ceca6e954640fd178d9313e274df06b4cca602291`|
|`backend/core/src/main/java/ama/material/MaterialArchives.java`|`5899e59f9fe37b14d4e5f594649557578cd49ac8c3adfb37bbe7ded982afb6cc`|
|`backend/core/src/main/java/ama/material/MaterialBinding.java`|`90e9fcdd50daecf0927aa7934e568a700aeb22cb5a739eccb06edeadb15e0690`|
|`backend/core/src/main/java/ama/minutes/contract/MaterialCatalog.java`|`5ba87e8604998ab5f8e6a9c2351105f26e81e835c98def7079874235e2b6de6f`|
|`backend/core/src/main/java/ama/config/ConfigSchema.java`|`4cff884761daddee30cb8f30acfe29acc714abb042133053fa816d54664be583`|
|`backend/core/src/main/java/ama/config/ConfigBindings.java`|`fdd19c8f89a4541ce1a0a458086307294d41be2ebc6ad19c48b422842f1db4da`|
|`backend/core/src/main/java/ama/minutes/contract/GenerationFingerprint.java`|`6f195b63f928447bf7a9936b6387797a17e16a96f35ab79b51a4557c72da01c8`|
|`backend/core/src/main/java/ama/minutes/contract/StoredSourceGraph.java`|`2304c01634bb36d84d9a85b4559efd9d5fd284bc018bd86fa49bed0a77ee2955`|
|`backend/contracts/src/main/java/ama/contract/MinutesContracts.java`|`e3bdda4b59697d9a055f0fb4f821c6ac11c4ca3121c69449e59ee534fb4d8706`|
|`backend/adapters/src/main/java/ama/material/MaterialDocumentParser.java`|`955f00beb3117bb011eae243dd1f2870a6623b4a937df0abff2713d1c0c09c79`|
|`backend/adapters/src/main/java/ama/minutes/ControlledMaterialArchives.java`|`fca0981c099c610e045833f24c2f5184658282e238f3d3443d146866079e28d0`|
|`backend/adapters/src/main/java/ama/transcript/MaterialFileReferencePolicy.java`|`01973359a73e6fd8d8d647e5b91f359239df91835fc4f813a2c77b994945e859`|
|`backend/adapters/src/main/java/ama/transcript/JdbcMaterials.java`|`fe442735c67156bd1f57f1b2d62159f593c05e68e40a894c3c744e04424089ff`|
|`backend/adapters/src/main/java/ama/transcript/MaterialKnowledge.java`|`ea046d67923b289e971838afe3b2807f88dcff69fa8dd28420865f03cacaba0a`|
|`backend/adapters/src/main/java/ama/transcript/MaterialSourceContexts.java`|`a1d0925968556f9ed0f47421c20ae9f24c8a748c7a6f82811bad5659c33c7a3e`|
|`backend/adapters/src/main/java/ama/transcript/MaterialMinutesContext.java`|`e9cea7331f6bacc172a034022edc199eafb861dca32f67f99663f5ba160e9dfe`|
|`backend/adapters/src/main/java/ama/transcript/TranscriptFilePurposePolicy.java`|`f617e9ac78748cb70c85f84ad760e52064fe299b195f03748b1ed1f2bfe4a2ee`|
|`backend/adapters/src/main/java/ama/transcript/JdbcTranscriptAuthority.java`|`7078eb5a1f873732a64b6ebb229e5b31b7b3fcd2285ea25e7b9db1762bf3f7ff`|
|`backend/adapters/src/main/java/ama/transcript/ControlledKnowledgeClient.java`|`054a63e8d75926b14fb02c21f96124fe427d4071dd5488842c1f828e84297786`|
|`backend/adapters/src/main/java/ama/transcript/MinutesGraphDiscovery.java`|`5a70f0a482a89c33e904bae36472e1c075f264e43776d62f534c902334fbc3cd`|
|`backend/adapters/src/main/java/ama/transcript/MinutesGraphCapture.java`|`50ae2db3be49030d2cf44ad2e59c83648b794c0e95ddc0ab5b65f2706af2110d`|
|`backend/adapters/src/main/java/ama/transcript/JdbcMinutesGenerationPreparation.java`|`95a8d6ea08992d3a31a1687d6e4f676249a90a04e59e7b1f7dba8828e6530d9e`|
|`backend/adapters/src/main/java/ama/transcript/ControlledMinutesRenderer.java`|`f7e7c1e81d8701fd8e8fc11c4e263430ea30173899a65a76070122645e6d661b`|
|`backend/adapters/src/main/java/ama/transcript/MinutesCandidateGraphPlan.java`|`294d9f3a14d8dc70f3396e0ebe1ecef939669160fa2527d963603a5982fd63b5`|
|`backend/adapters/src/main/java/ama/transcript/MinutesCandidateWriter.java`|`769b52ca9afaa4ef5ab97e8805859ba7627ce0c413894bc5c3df9b6aeaa17fb6`|
|`backend/adapters/src/main/java/ama/transcript/MinutesSavedGraphPlan.java`|`094986b278fee7d93b0d0d4efdc962a575d391809c1009f5c12e38bdeece9053`|
|`backend/adapters/src/main/java/ama/transcript/MinutesSavedWriter.java`|`77970a90362becae9460554762893ecbe9066d3e88287f936e324165b81f59d6`|
|`backend/adapters/src/main/java/ama/transcript/MinutesMetadataPages.java`|`591d6de4b615ed974ee9aa0846517313ebdb5209106b763e56a32d7b2bac8aa9`|
|`backend/adapters/src/main/java/ama/transcript/MinutesRecordedLookup.java`|`6f8b8abe0c5090864e5fbfdcdbe9e845669c3e28054f758fa05cb6b967436831`|
|`backend/adapters/src/main/java/ama/model/MaterialMinutesWire.java`|`c6ea573097c22088448e7d2754a71e57904523c6aea5dde6aa894414ce875deb`|
|`backend/adapters/src/main/java/ama/model/ControlledMinutesProtocol.java`|`41a4ca9234a6bb545d5b4de5a0f968d50335b9a4b23cd20d8ded71111933c337`|
|`backend/adapters/src/main/java/ama/model/ControlledMinutesProvider.java`|`c503683f0e4d004d6956576dcbdc195aa61c508b6d4c25ed4c0a83a50c933a88`|
|`backend/adapters/src/main/resources/db/migration/V017__materials.sql`|`9687e819578d2bd8ee84131ae7f0aef3516914a4bc4da89495dc46006b36c3af`|
|`backend/adapters/src/test/java/ama/material/MaterialDocumentParserTest.java`|`d8e0a9f90f6493291181542a964aaa1f9a858a731f6e9953c21ae1b4abab58b1`|
|`backend/api/src/main/java/ama/api/MaterialConfiguration.java`|`4356d09f9c150760c2a303325909def0ded56e5b666d21297c0e6669c3a2ce63`|
|`backend/api/src/main/java/ama/api/MaterialController.java`|`b65e725d4d2d9c0782c4c857d6a003442a7dbf13546ca6db09e4024f48ac216f`|
|`backend/api/src/main/java/ama/api/MaterialErrors.java`|`ef5065c2f18d3642dbea211ec25de5a95f8124770a7edef1b1f6b4addf9abbd2`|
|`tools/providers/controlled_knowledge.py`|`d606471d266f9dbd7d7262dd1a92b8e9670e6b5734a8a72028df6f0de00087c1`|
