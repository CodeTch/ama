# B/C/D 暂停与精准接续（2026-11-01 17:05，Asia/Shanghai）

**状态：当前批次已收尾，按用户最新指令暂停。B/C/D 均未整阶段签收。** 用户发送“继续任务”时，直接从本文的接续顺序执行，无需重新询问任务范围。此前“D完成前不停”的指令已被本次暂停覆盖；收到继续指令后再恢复推进剩余B/C/D。

## 工作区和运行现场

- 根目录 `/home/tanren/ama`，分支 `main`；全程单agent，没有创建分支、worktree或子agent，没有提交/重置/删除旧业务与失败证据。
- 六角色本地服务保留运行：Web5174、API8182、management8183、MySQL映射46431，数据库连接池32、文件线程32、standard场景。
- **实际运行代码：** `d3cf2d7e8cf07ba1ea9e2a13b69a2ff1cd4f9516da4873d38b8c3f4563f22c4b`，主库V056。C02五阶段已部署。
- **当前工作区代码：** `825dc9b3d18781b1935894d1502bb6bb3a3894854088ad0d72140c551fd79037`。新增C03后端尚未部署，不能把当前运行界面当成C03验收。不要手改state伪造源码对齐。
- 新独占测试库 `ama_c_recording_1737f5dfd77a` 已完整迁移47个脚本至V057；主库仍V056。V057原文件SHA-256：`01cac8bcebfe72faf9a08b898ac88323bc82fdf8d24c9eb60e3745ca1866ca47`，已经应用到该测试库，后续不能直接改写该迁移。
- 测试连接配置在 `.tools/local-app/bcd-completion/test-database-speech-current.json`，仅让工具读取，不在聊天或日志打印密码。创建库工具保留每个schema的独立私有配置。
- 兄弟项目 `/home/tanren/ama-integration-harness` 仍原分支 `master`，固定HTTP8792/SMTP2525，同一进程PID1893722。保留原provider环境/材料配置，新增精确二进制摘要场景与原执行证据已实际重启验证。
- 当前没有仍在运行的测试、浏览器验收或负载采集。现场快照：`.tools/local-app/bcd-completion/pause-runtime-snapshot.json`。本地常驻服务保留运行。

## 当前批次已经完成的后端范围

1. `TranscriptContracts.Command` 新增CORRECTION，仅接受服务端candidateId和ACCEPT/REJECT，不接受客户端模型文字。原请求摘要绑定候选ID与决定，旧SAVE请求hash golden保持。
2. V057存储真实增强候选与原键批次。来源绑定RAW、创建时的WORKING、input、segment、file、clip、engine和ASR/PUNC快照摘要，文本与状态分开；接受/拒绝/撤销记录实际版本。新表已纳入meeting物理删除清单。
3. `SpeechReviewRepository` 在真实SavedScope/EDIT根中先核算元数据、每字段文本和模型字节预算，再读取；模型JSON解析/摘要核验在事务外，F重读比较完整快照。模型clip文件、范围、原SHA/PCM/engine和原RAW正文均核验。人工已经改过的片段不产生覆盖建议。
4. `JdbcSavedTranscripts` 已实现Java服务方法 `proposeCorrections`、`readCorrections`，CORRECTION继续走原稿件S/F写入通道。接受生成新WORKING；拒绝保留人工正文；RAW不变；其他未改片段的PENDING保留，人工改过的建议置STALE；立即UNDO生成新版本并记UNDONE。
5. 当前实现针对**显式请求创建的增强候选**检查真实PENDING：S和F均阻断带待处理项的确认，确认后拒绝新建议。技术ENHANCEMENT快照没有自动替换正文。完整C03产品流程、可见性及确认语义仍要在API/UI接线时核验，不能由本批五项测试推断整阶段完成。
6. 原键批次利用现有无FK的idempotency_record顺序，在身份/根之前claim；接受结果重放只返回原batch/ID和当前observation，不重新生成、不回挂旧稿，优先于新模型/档案准入。
7. `CorrectionInstallation` 新增显式 `CONTROLLED_SPEECH/READY` 模式供该后端使用；**当前Spring配置仍只选择原DISABLED_NOT_INSTALLED，未添加新HTTP路由、OpenAPI、SDK、页面或本地运行启用配置。不能只改环境变量强行启用。**

主要源码：

- `backend/adapters/src/main/java/ama/transcript/JdbcSavedTranscripts.java`
- `backend/adapters/src/main/java/ama/transcript/SpeechReviewRepository.java`
- `backend/adapters/src/main/java/ama/transcript/SpeechReviewFacts.java`
- `backend/adapters/src/main/java/ama/transcript/SpeechReviewReceipts.java`
- `backend/adapters/src/main/java/ama/transcript/SavedTranscriptGraph.java`
- `backend/adapters/src/main/resources/db/migration/V057__speech_review_candidates.sql`
- `backend/integration-tests/src/test/java/ama/storage/SpeechReviewIT.java`

更细的实现选择见 `.superpowers/sdd/2026-10-21-ama-phase-c/c03-speech-review-next.md`。其中早先“尚未接线”的段落属于历史，以本文和最终测试为准。

## 验证与保留的失败

| 范围 | 最终结果 | 证据（相对 `.tools/local-app/bcd-completion/`） |
| --- | --- | --- |
| C03原请求契约与旧SAVE hash | 4项PASS | `c03-correction-contract-green2.log` |
| C03真实后端业务 | 5项PASS，0失败/错误/跳过 | `c03-backend-batch-final-it1.log` |
| 生产Java模块全回归 | 865项PASS，0失败/错误/跳过 | `c03-backend-batch-production-java.log`、`c03-backend-batch-production-java-counts.json` |
| 兄弟harness全量 | 64项PASS | `c03-harness-binary-full-green.log` |
| 固定端口实际重启/执行证明 | PASS：COMPLETED摘要、FENCED、迟到409 | `c03-harness-binary-live-restart.log` |

五项后端业务测试实际走固定harness五阶段、MySQL、受管mTLS文件和合法导入任务，覆盖：待处理阻断确认、显式接受/原RAW不变/原键回放；拒绝；立即撤销与旧接受ACK不回挂；人工改动使建议过期及外人拒绝；meeting删除清除候选、批次与checkpoint。**没有覆盖新HTTP入站协议、浏览器纠错操作、说话人或C06。**

失败均保留：

- `c03-correction-contract-red.log` 缺新契约的RED；第一轮green因测试误把既有INVALID_ID异常期待成IllegalArgumentException而失败，修正断言后green2通过。
- `c03-binary-scenario-red.log` 是依赖PYTHONPATH未载入，不算产品RED；真正的两项匹配器RED在 `c03-binary-scenario-red2.log`。新增binary_sha256仅精确匹配原二进制，不会将其他会议一起改成测试响应。
- 首次新库 `ama_c_recording_00db7fa0cf2e` 迁移启动失败、未创建schema_history；随后的green1被schema前置检查拦住，没有候选业务写入。原库保留。迁移工具已将当前classes与外部依赖隔离，排除可变的内部旧JAR；新1737库迁移成功。首次异常被MigrationMain隐藏，**未将其具体根因写成已经证实的DDL错误**。
- `c03-real-review-production-green2.log` 真正暴露预算计数错误：把多行元数据总量当成单个文本字段，触发65536限制。修复为聚合capture和逐字段text核算，原限额未增加；green3单例通过，再最终五项全通过。

本批没有前端源码修改，未把上次Web492/type/build结果冒充本批重跑；当前全量Java不包含整个历史integration-tests集合。

## 接续时按此顺序推进

1. 确认根目录/main，读取本文、根 `DEVELOPMENT_HANDOFF.md` 首段和 `.tools/local-app/task-current-status.json`。保留全部原脏修改、业务与失败报告。不要重做C02或把工作区当前代码当作已部署代码。
2. 先集中审查当前C03后端：补多候选“接受一条后其余仍待处理”、并发建议/确认、原件损坏/预算边界、冻结治理/原键跨部署等必要负例，明确技术增强快照与显式业务建议的产品语义。
3. 完成C03 HTTP/契约/客户端。建议对应服务：GET corrections（sourceRevisionId），POST correction-proposals（expectedRevisionToken/sourceRevisionId/原键），POST corrections/{id}/decisions（绑定path/body候选ID、source/version与accept/reject）。严格拒绝任意模型文字和未知字段；服务不可用时，已接受原batch也需可通过稳定receipt恢复。
4. 扩展 `contracts/openapi/transcripts.yaml` 及minutes中的SavedCorrectionQualification、新请求/响应；同步生成SDK、运行时schema及前端严格枚举/解析，保持旧SAVE/REPLACE/UNDO字节契约。当前客户端仍不认识CONTROLLED_SPEECH/READY。
5. 在现有“纠错建议与候选”工作台接真实panel和原请求journal，先落盘原键/原体，ACK后新鲜回读再清理；绑定actor/meeting/source，处理晚到、冲突、撤权与脏稿。完成后才改本地/Spring安装声明并正常部署做真实CAS验收。
6. 继续C03说话人持久化、人工实名/合并、音文工作台。现有内存SpeakerService/CorrectionService和未挂载组件不能算生产交付；不得通过标签或合并改变meeting ACL，也不能去掉当前SourceGraph对speaker/source metadata的保护后直接接受任意ID。
7. 继续C06持久窗口/字幕修订、afterEventId回放、实时保留额度，以及两小时录音归档的独立ASR来源权限/有界流。普通上传32MiB限制保留。
8. 完成B/D真实可达控件及原型差异矩阵、最终回归，再执行冻结源码的实际容量资格。C07目前600秒协议通过但ACK延迟不达标，7200秒尚未完成；不能直接记整阶段PASS。

## 可直接复用的命令与注意点

从项目根目录执行当前后端业务回归：

```bash
source .tools/env.sh
AMA_BCD_TEST_DATABASE_FILE=.tools/local-app/bcd-completion/test-database-speech-current.json \
AMA_SPEECH_HARNESS_ORIGIN=http://127.0.0.1:8792 \
AMA_LOCAL_SPEECH_RUNTIME=/home/tanren/ama/.tools/local-models/speech-runtime-v1.json \
python3 .tools/local-app/bcd-completion/run_append_it.py SpeechReviewIT <新的日志文件名>
```

生产模块回归：

```bash
source .tools/env.sh
./mvnw -B -ntp -pl backend/api,backend/file-service,backend/worker,backend/realtime -am test
```

不要并行运行Maven；不要在冻结容量采集/ACK期间构建或改运行配置。新测试schema工具为 `.tools/local-app/bcd-completion/create_owned_speech_schema.py`，以拥有者root做DDL，业务仍ama_local，不授SUPER、不改binlog信任。所有外部接口继续经兄弟固定8792/2525，修复其兼容行为也不能将其视作公司权威或模型质量证明。

旧容量报告、原型基准与完整义务仍在 `docs/superpowers/plans/2026-11-01-bcd-completion.md`。原型SHA保持 `ab21d0b73f4e48bbf701ee8bca5e958940b61f6e6f680d4ced02de0ce4bab867`；D08桌面零像素结果有明确旧部署范围，390溢出及无音频时钟差异仍开放。静态60组件/473按钮清单不是业务签收。S3/HA等未完成专用义务按既定R2留在F。
