# 开发暂停与接续说明

暂停日期：2026-10-22，用户明确要求“先停下，把开发进度、待处理问题、成果等信息记录”。状态快照为北京时间06:49:14（UTC2026-10-21 22:49:14）。**开发已暂停；没有继续实施、测试、迁移或部署的后台任务。只有用户之后明确要求恢复，才继续开发。** 本次暂停之后仅整理和提交记录，不推进功能、不合入main。

## 1. 从哪里继续

正式接续入口是本文件；完整协调历史在[暂停时协调台账](../../artifacts/checkpoints/2026-10-22-development-pause/progress.md)，原台账仍位于集成工作树 `.superpowers/sdd/2026-10-21-ama-phase-b/progress.md`。全部39份封存载荷见[证据清单](../../artifacts/checkpoints/2026-10-22-development-pause/manifest.json)，清单SHA256为 `eaa50bc9ead19f39f604ba3b13761735766e56efd25a299e64f23749ad407aa6`；自哈希未包含在清单内。

可在后续会话直接说明：

> 继续会议助手开发。先阅读 DEVELOPMENT_HANDOFF.md 及其链接的2026-10-22接续说明和暂停台账，恢复B07纯S1的F1/F2/F3修复、D01设计R1修正；保留现有工作树、提交、迁移和原始证据。继续按原作者修复、独立复审和阶段记录推进，不能把已完成任务从头重做。

## 2. 工作区与提交：不要混淆main和集成树

|工作区|分支|暂停时HEAD|状态/用途|
|---|---|---|---|
|`/home/tanren/ama`|main|`15b0a6bc4872cbed209c38474c3b1e425397e972`|阶段A已本地合入。用户更新的总计划、B计划仍为未提交修改，必须保留。主目录另增加本次接续入口，不把B代码视为已合入main。|
|`/home/tanren/ama/.worktrees/phase-a`|codex/phase-b-foundation|业务代码到`b938cb14275955cc860c806a7fb9de95565f5b65`|集成工作树名称仍为phase-a，实际承担阶段B；本次记录将产生后续文档提交。暂停前tracked clean。|
|`.../.worktrees/phase-b-file-registry`|codex/phase-b-file-registry|`45dc8ed27449de191400c858b88413a518842f21`|文件登记完整交付+修复已评审，干净。|
|`.../.worktrees/phase-b-minutes-pure`|codex/phase-b-minutes-pure|`7e052a88fcd03d60523d013839701dd35a7f2b48`|纯S1待修，未集成，干净。fix1已派发但暂停时未改文件。|
|`.../.worktrees/phase-b-provenance-design`|codex/phase-b-provenance-design|`214a63d978e4c04c0b3aec1c99d8d0ce3c093d9d`|设计补充待修，未采用，干净。fix1已派发但暂停时未改文件。|

[原始状态快照](../../artifacts/checkpoints/2026-10-22-development-pause/pause-2026-10-22-state.json)保留精确分支、路径和状态。两名活动作者 `/root/b07_pure_s1_implement`、`/root/d01_provenance_design` 已中断；扫描未发现cwd位于这两处作者工作树内的活动进程。原有数据库/S3测试容器、卷、私有测试环境、PKI、预览进程和其他历史工作树均未重置、删除或擅自停服；本次没有操作Docker守护进程/WSL服务。

Windows访问项目：`\\wsl.localhost\Ubuntu-24.04\home\tanren\ama`。终端用PowerShell调用WSL，例如：

```powershell
wsl.exe -d Ubuntu-24.04 --cd /home/tanren/ama/.worktrees/phase-a --exec git status --short
```

工具环境为集成树 `.tools/env.sh`，其中可能关联私有环境，不能归档/输出凭证。不要reset/clean/rebase其他任务，不要按 `stash@{0}` 弹出共享stash；历史B05保留stash精确OID为 `bc12e825246c60c12752129c4d512ef9c545758c`。

## 3. 已完成与可复用成果

|范围|已交付证据/结果|剩余边界|
|---|---|---|
|阶段A|工程骨架、技术与架构基线、需求追踪、运行/检查工具、前端原型骨架，已合main|骨架不是完整业务UI。|
|B00当前正式采用设计|B00-1.3，93表709列；ER、字典、审阅DDL、真实MySQL元数据与独立评审|1.4材料来源补充未通过最终评审；实现差异仍逐任务关闭，阶段B最终验收未通过。|
|B01/B02/B03|事务/审计、跨节点身份会话与撤销、会议草稿/幂等/CAS/名单草稿与有效授权分离，独立评审和实际回归已采用|真实SSO/目录协议、停用传播与企业字典待公司提供。|
|B04基础与完整文件登记|local原生文件服务、Java S3、受管后端身份、历史路由、上传FINALIZING/登记/业务引用/鉴权读取，含真实两后端测试和完整审查；本次修复长Unicode下载响应头|早期Q401仍未知；D06长流撤权、D07清理、真实平台及断电/HA资格未闭环。|
|B05|可靠任务/租约/独立外部调用marker/额度/outbox及并发修复，经完整审查和复审采用|领域Worker处理器尚未接线；真实模型quota/cutoff能力待提供。|
|B06前置|严格UTF8纯TXT解析器19项测试及独立评审；V011和V011.1结构采用|真实上传→导入任务→保存/标记→确认快照运行时尚未实施。|
|B07结构与技术契约|V012结构及纪要/配置归档精确格式已独立采用|纯S1代码虽97项测试通过，独立审查仍有3项阻塞；不得接入或称B07完成。|
|B08结构|V013五表及双物理FK，实际10项IT、元数据及独立审查已采用|最终确认、知识库发布/UNKNOWN查回完整运行时未实施。|
|B09/C—F|原型最终覆盖定位、阶段任务和验收规则已准备|完整UI闭环、音频/实时录音、材料/编辑/治理/导出/邮件/生产部署与容量等仍待开发/资格验证。|

现有公开证据以集成树 `docs/acceptance/b/`、`artifacts/phase-b/`、`docs/database/migration-register.md` 为准；不要把下面的测试数量相加冒充独立总数。

### B04最近成果：已集成，协调归档说明尚待补齐

完整作者范围 `8da440d..45dc8ed` 的三次提交已经逐次集成：

|作者提交|集成提交|
|---|---|
|`5e7022c20c9389ad319157320408901f3df089ac`|`b6d5930eb1f74e3311cd34f8bf730d5c99063ef4`|
|`d1e47ca69e68caa6bd51fee1706998b3e6cd2506`|`a1545a26ddcbaabb20d24d9e839b762d6544dc86`|
|`45dc8ed27449de191400c858b88413a518842f21`|`b938cb14275955cc860c806a7fb9de95565f5b65`|

[集成字节凭据](../../artifacts/checkpoints/2026-10-22-development-pause/task-B-04-registry-integration-byte-receipt.json)：227文件、3,608,418字节与作者最终Git对象完全相同；12份正式SQL不变。此处仅做无冲突的精确代码采用，没有为了归档重跑重型测试。

[完整初审](../../artifacts/checkpoints/2026-10-22-development-pause/task-B-04-registry-review.md)唯一P2为长文件名产生过大的Content-Disposition，导致实际Tomcat GET/Range500。[修复报告](../../artifacts/checkpoints/2026-10-22-development-pause/task-B-04-registry-fix1-report.md)与[独立复审](../../artifacts/checkpoints/2026-10-22-development-pause/task-B-04-registry-fix1-review.md)确认R1 ADDRESSED、限定SPEC/quality PASS，0新增问题。完整元数据不截断，只限制下载响应头为1024 ASCII字节，按完整Unicode标量截取传输名称。

local/S3各4个方法均保留RED3fail→GREEN4pass，合计16次实际GREEN下载；67单测是每次规范入口重复运行的同一组，不是268个独立测试。原144份artifact和原报告/manifest均保留。RED-local测试文件只有旧哈希、无旧源码前像，不能声称它与最终测试字节一致；RED-S3最终测试源码及基线控制器均有精确因果证据。暂停时尚未另写B04公开协调adoption汇总；恢复后补记即可，不重做原实现。

## 4. 必须先处理的评审问题

### B07纯S1：未采用，先修F1/F2/F3

完整范围 `8da440d39e1b8e8dcdfc34722908d29b5aa3aa8a..7e052a88fcd03d60523d013839701dd35a7f2b48`，273路径，271份封存载荷，32个FullRow描述。13套/97个独立单测通过；规范入口13项是子集。独立审查实际构造反例，结论SPEC FAIL、quality Needs fixes。

|问题|位置/原因|修复与验收|
|---|---|---|
|F1 P1|OriginChainVerifier.java:11–20，仅收集初始图的intrinsic确认；递归继承的旧要素集确认哈希可能漏验。直接验证G2失败，外层draft反而成功。|完整递归使用项共享操作状态，所有到达确认/历史链都验证；直接/外层compute/review/final一致拒绝损坏确认。|
|F2 P2|StoredSourceGraph.java:153–159，已完成节点缓存先于深度检查，复用子树绕过最长路径预算。|对共享DAG任意顺序检查最长可达路径，含非叶缓存子树；精确上限/超一、循环、取消与节点预算。|
|F3 P2|MinutesContracts.Artifact:270–272及消费者，在可检查预算前复制原始byte数组。|有界、可先查大小、不可变的原始字节载体；首次复制前检查容量，消费者不先复制后判长；小规模或可观测测试，不做大内存压力实验。|

阅读[完整审查](../../artifacts/checkpoints/2026-10-22-development-pause/task-B-07-pure-s1-review.md)、[fix1任务书](../../artifacts/checkpoints/2026-10-22-development-pause/task-B-07-pure-s1-fix1-brief.md)与封存的Java反例/日志。作者 `/root/b07_pure_s1_implement` 应在同树继续；原独立审查者 `/root/b07_pure_s1_review` 负责限定修复复审。不要新写一套S1或重新派发原任务。fix1暂停时尚未写代码、无新提交。

### D01 / B00-1.4-proposed：设计R1，未采用

范围 `91774bd..214a63d`，65路径；评审专用真实MySQL验证96表727列，24关系断言及7次独立进程/连接重开检查，250设计检查。独立审查另387断言通过，但唯一P2尚未关闭：完整上下文闭包必须包含**所有被纳入要素的basis context，不受其证据数量影响**。要素保留C1而全部证据改绑C2时，当前“仅零证据basis根”的精确规则遗漏C1，与所有到达行的规则冲突。

阅读[完整审查](../../artifacts/checkpoints/2026-10-22-development-pause/task-D-01-provenance-design-review.md)和[fix1任务书](../../artifacts/checkpoints/2026-10-22-development-pause/task-D-01-provenance-design-fix1-brief.md)。作者 `/root/d01_provenance_design` 同树修订各域准确根规则，增加C1+全部证据C2的离线向量，再由 `/root/d01_provenance_review` 限定复审。没有实际bad final/hash碰撞证明，也不需要为文字/向量修正重跑不变DDL或改正式表。暂停时未写修正。

当前1.4只有设计与评审专用库；三张来源表/两列尚未分配正式新迁移、未写运行时代码。历史1.3、原提案、原始元数据和所有已应用/共享迁移必须保持不变。

### 另外保留的风险/前置条件

- **B04-Q401原因未明**：原 `orphanIsNotReadableAsBusinessFile` 在首次upload begin预期201实际401，trace `2b235500-5f04-43b7-a742-8fb19a63f7ac`。原日志缺精确session/cookie/resolver快照；后续GREEN仅为未复现，不能关闭。若重现，先留有限非秘密关联证据，不盲目改身份模块或加重试。
- **B01-IMM-01历史偏差永久PARTIAL**：早期私有应用后的V006曾被改字节，旧原件已保全；不能以当前功能通过消除此事实。当前V006仅用于独立确认空的新库，旧checksum/失败/未知库必须独立恢复评审，不能repair/reset。
- **B06队列锁序集成只是待验证假设**：当前queue先subject再domain，而MeetingAccess需要identity→subject→root；不存在生产meeting policy。必须先用最小实际MySQL用例证明缺口，再做窄接口修正；不能在claim外包事务或晚锁identity。完整要求在[准备任务书](../../artifacts/checkpoints/2026-10-22-development-pause/task-B-06-runtime-prepared-brief.md)。
- 公司SSO/目录、分类字典、模型流式协议/吞吐/实名/额度截止、知识库权限/发布幂等查回、邮件接口、治理角色/用途/保留，以及虚拟化隔离/fencing接口仍是前置条件；没有任何真实企业联调或真实发布通过声明。
- 输入格式/大小/时长、OCR和浏览器录音边界仍依能力矩阵及决定台账；纯TXT与合成测试不等于其他格式生产资格。100路实时录音/300在线、双节点RHEL、故障自动恢复/断电持久性均尚未验收。

## 5. 用户已确认的决定：不要重复询问

架构v0.2及实施计划已批准。MySQL支持两机私有或公司平台；文件支持服务器local或S3，与数据库来源独立。两台RHEL8.10 x86_64、各16核32GB；100场同时录音实时转写，每场一采集端，300总在线包含100采集者。不增项目第三节点。私有HA自动恢复依赖真实隔离/fencing条件，未提供前不强制提升。

知识库对接现有平台，只做材料引用和纪要发布；统一登录/人员目录对接公司平台。已有编辑/输入、受控链接邮件、用途授权与独立审批/回滚治理规则继续有效。

本轮新增明确回答已在集成提交 `ba5d199c8134a8425ed0ef8f5112f8ade4e7fd91` 记录：

1. 首版仅在尚无有效正文、无人编辑且版本未变化时自动保存；已有正文的重新生成始终候选并须负责人明确采用。
2. 重要结论/行动项先标“待核对”，负责人核对原文后确认；依据不足可转待确认事项。

见[精确补充规格](../superpowers/specs/2026-10-22-ama-minutes-ux-decisions.md)及[原始用户回答](../../artifacts/phase-b/minutes-ux-decisions/user-answers.json)。规则批准不等于代码已经实现。

## 6. 恢复后的顺序

1. 先核对本文件、暂停台账、五工作区HEAD/状态与主计划原字节，保留所有证据。不要重跑已通过的庞大基础设施cohort。
2. 原作者继续B07 fix1（P1优先）和D01设计fix1；分别保留真实RED/静态修正/GREEN证据，不覆盖旧档案。按原审查者限定复审，通过后才采用。
3. 补齐B04公开协调采用/问题记录；源码已精确集成，不重新实现、不把Q401标fixed。
4. B07纯S1通过采用后，按准备任务书启用新的隔离B06运行时任务：真实文件登记→有界不可变配置归档索引→导入Worker→保存/标记→确认快照。先提交批次指纹/归档恢复/队列锁序技术说明；不能用DTO替代真实档案来源或填写 `{}` 输入。
5. 继续B07真实事实解析/模型适配/候选与守护首版、B08最终确认/测试知识库发布、B09完整UI及local/S3实际闭环。另有[信息组件准备任务书](../../artifacts/checkpoints/2026-10-22-development-pause/task-B-09-info-components-prepared-brief.md)，只是准备，尚未建工作树/派代理/写组件。
6. 按已批准总计划继续C/D/E/F，所有P0/P1保留。每任务独立作者+独立审查、记录证据；公司/运维未知项保持前置，不阻塞其他可做任务。阶段验收后才按已授权规则本地合main；无push、真实邮件/发布、生产/fencing操作授权。

## 7. 迁移、计划和证据保护

正式SQL通过V013（含V011.1）；V003/V004为永久空号。所有已应用/共享SQL不改字节、不改编号，不repair/outOfOrder/伪造READY/重置旧库。发现差异先增量设计/独立评审，再由根协调人统一登记、提交SHA并单独ACK，最后首次正式应用；不要自行猜新号。已有审阅DDL不等于正式Flyway脚本。

主工作区两份用户修改未提交且保留，主树和集成树字节一致：

- 总计划SHA256 `a532dec6e94c53f0e81ae92f14b4f84b6a56cf9d68c37bd6e27d7903f911ffa9`。
- B计划SHA256 `bfa789f77b5ca90725b2c0f32977993c260ac79ab22c2fff76cd775cc104c862`。

原字节另封存于checkpoint的plans子目录。原型/需求设计原件没有改动。所有旧作者工作树、原始失败/成功日志、测试XML、Git提交和私有测试夹具保持原状；本次checkpoint只按显式允许清单复制公开的报告、修复任务书、评审反例/审计和状态，不复制私钥、环境变量、口令、DB内容或Docker Config.Env。大量原始作者测试证据已在各自Git提交/现有artifact目录中，本次没有重复导出或运行。

这份说明是暂停记录，**不是B阶段最终验收、全项目完成或生产部署通过声明**。
