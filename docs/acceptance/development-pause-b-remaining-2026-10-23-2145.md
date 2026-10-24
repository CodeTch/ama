# B 阶段暂停与精准接续记录（2026-10-23 21:45）

用户明确要求暂停，已中断 Task5 修复作者及 Task9/10 只读预检子代理；只完成交接、封存和记录提交。**本记录优先于 18:20 暂停记录、此前 ACTIVE/继续任务段落及旧 PREPARED 状态。** 下次用户说“继续任务”时恢复全部剩余本地受控 B 阶段目标，不能只完成 Task5 后结束，也不重问暂无的公司资料。

## 位置、提交和恢复入口

- 唯一产品开发树：`/home/tanren/ama/.worktrees/phase-b-completion`；分支 `codex/phase-b-completion-20261023`。
- 暂停前产品 HEAD：`857102b917bbef77c38fae3211e3a65207a98c6c`（Task5 原始实现，**尚未采用**）。本次随后提交仅包含新暂停记录/封存；用 `git log -1` 核对 `docs: pause phase B at task 5 fix01 with exact resume records`，不要把该纯记录提交当产品修复。
- 主目录 `/home/tanren/ama` 为 `main`，本次核对 HEAD `c71a44adb679a78d4c28dde7a4223aad1a23b40f`，产品树干净。该 main 集成来自另一次用户授权；本轮未合并或推送。仅为下次发现记录，可在主目录保存未提交的文档接续入口，不动产品代码/HEAD。
- 规范计划：`docs/superpowers/plans/2026-10-23-ama-phase-b-remaining.md`；主规范：`docs/superpowers/specs/2026-10-23-ama-phase-b-controlled-runtime.md` 及各已采用 wrapper。无需重新规划或重复已通过的设计审查。
- W=`.superpowers/sdd/2026-10-23-ama-phase-b-remaining`。先读最新 `current-state.json`、`resume-20261023-active-context.md` 和 `progress.md` 最后 USER PAUSE，再读 Task5fix01 交接。旧 pause/resume 字段保留历史，最新明确暂停优先。
- 完整本次 W 原件及两个 fix01 starter 文件封存到 `artifacts/phase-b/b-remaining/controller-pause-20261023-2145/`，有逐文件大小/SHA、归档 SHA 和恢复说明。W 仍在原位；只有 W 缺失才从该归档经校验后恢复。不要覆盖较新 W 或解出至主目录。私有 env、PKI 和运行数据留在私有 `.tools`，不进入归档。

## 完成边界和全部剩余顺序

|计划任务|本次暂停真实状态|
|---|---|
|1 原子队列/Worker 基础|已实现、双 PASS 并采用 `a3b2933ef83035e858d2d3336decba4617f22a1f`，不重派。|
|2 profile/schema/editor 基础|已实现、双 PASS 并采用 `3e393dbc9485b187b89bfc03abd88821a0aabddb`，不重派。|
|3 真实来源权威|原实现及 fix01 已采用 `2ec38c0350f466fc4292d588051c5694cc099cb5`，不重派。|
|4 processing intake/API|原 `59c8b080` + fix `1ce4a2e6405fe380a5a7f7a18873d903c71528b7`，限定复审双 PASS，采用 `35b2325aee8e6c09aa1bdebc880c4f09bd37837b`。不重复 Task4 审查。|
|5 TXT import Worker|原实现提交 `857102b`；独立源码复审 2 Important，fix round1 中断，未封存修复/未采用，下一首要工作。|
|6 保存转写/marks/确认|设计 R1 已采用 `27143b3`；只读源码、预算、Task5 候选接口预检已完成；实现未开始。|
|8 reconciliation 公平性|设计已采用 `1834ebb`；精确未编号 DDL 提案已独立静态双 PASS；未分配/冻结/ACK/应用，未实现，FUP 仍 OPEN。|
|7 外部 prepared-output/权威|设计 `efcbf78` 已采用，只读 source preflight 已完成；实现未开始，必须在 8 之后。|
|9 generation|设计/R1/共享锁序 `af331c7e2eaff1af7ca2d743e717c3e31fff43d9` 已采用；本次只读源码预检刚开始即暂停，**最终报告不存在**；实现未开始。|
|10 editor/review|设计 `875047cdbcd6ddd17f5c267f8998b7a5a4378b6c` 已采用；本次只读源码预检未完成，**最终报告不存在**；实现未开始。|
|11 final/publication|设计/R1/closure/限定双 PASS 已采用 `f176382618924d9a00fd5fbabf93bbc30c78dd71`；源码未开始，publication_submission 仅逻辑提案，无 DDL 资格。|
|12 web/browser|设计/R1/closure/限定双 PASS 已采用 `8e7a82e237a9552ace2d7491adadbf08416feaee`，T12DR01 设计级关闭；源码和真实浏览器验收未开始。|
|13 integrated review/delivery|未开始，完成 1–12 后再执行新鲜整体验证和最强 fresh whole-branch review，补全交付记录。|

**继续顺序固定：5fix/复核/采用 →6→8→7→9→10→11→12→13。** 任务数不是加权进度，不宣称 B 阶段百分比或已完成。Task11/12 设计不是实现或运行资格。

## 下一步：同一 Task5 作者继续同一 fix01

**作者交接已完整核对，SHA `0b8d3f8a6e32c2617d5bfa11fde587e7408760c92a27f0fc55272fbbc8f5a59d`：fix01 尚未改任何产品/测试/POM/报告或 tracked 文件，未运行任何测试、无 RED/GREEN、无新进程/资源。仅创建 W runner 与 raw 两个 starter 文件，原现场保留。下一具体动作是写关闭路径的 focused unit RED。旧 Task05 pipeline/native helper 会写旧 raw 路径，恢复必须先提取/调整为新的 fix01 输出，不能原样运行。**

1. 恢复 `/root/b05_worker_implementation`，先读 `W/task-05-fix-01-pause-handoff-20261023.md` 和 `task-05-fix-01-brief.md`。本轮 FIX_BASE 为 **857102b**；不要重新实现原 Task5，不增加修复轮数，不启动另一产品作者。用户暂停仅放开原 HEAD/index 冻结以做纯文档 checkpoint，恢复需核对纯记录后重新建立 sole-author freeze。
2. 原始审查者 `/root/b05_source_review` 已完成初审；报告 `task-05-source-review.md`，14922B，SHA `7da8043023ceb21053bafa5ae0157c80b93c9fc687c8bb6549f8b1cb1df319a9`，root 已完整读并核对源码。只在修复报告、实际 commit 和覆盖命令/输出完成后交该原审查者限定复审。原句柄失效才明确接续身份，不能默认重做全审。
3. 开放项 **T5SR01 Important**：`TranscriptImportRuntime` 在 source.open 返回之后才登记 abort/deadline；LOCAL 503 部分错误体、S3 control/HEAD 等流返回前窗口仍能等到 transport timeout。须在 transport/open/headers/control/error 之前安装 deny-only 生命周期，批次截止或真实续租失败须中止实际请求/读取并有界退出，保持原授权先提交再 I/O，不能只降低全局超时或遗弃线程。
4. 开放项 **T5SR02 Important**：一个 abort 回调抛 unchecked 会跳过其余 abort、执行器 shutdown/join 和 definitions/backend close；`closed=true` 又令重试失效。须 best-effort 取消所有执行、保证所有清理/有界 join、保留首个错误/中断和 repeated-close 行为。真实两个活动 preparation，其中第一个 abort 抛错，仍须取消第二个且尝试后端关闭一次，无 post-close writer。
5. **CV1 必需证据缺口**：原证据没有“两份真实 production carrier 不同 plan 交叉替换”和“真实 RAW 插入后 body/source/order 被破坏由私有 validator 拒绝”的证明，作者已确认缺失，随 fix01 加真实探针；不能用假 policy 或公开新 grant 替代。
6. 原作者拟定方案尚未证明：每次 preparation 在 open 前有 deny-only read lifetime；LOCAL actual async exchange/body 和 S3 已有 Apache5 实际 request/body 绑定取消；关闭路径 best-effort/finally。先真实 RED，再最小修复，覆盖 Task05PipelineIT/TransportIT/CancellationIT/S3DeadlineIT、WorkerLoop/provider close、真实 carrier/validator。**暂停不产生任何 RED/GREEN 通过结论。** 精确已执行情况以 pause handoff 为准。
7. 新原始输出只写 `artifacts/phase-b/b-remaining/task-05-fix-01/` 新子目录；旧 task-05 原始 evidence 全字节不改。原 `W/task-05-report.md` 只能保留原完整前缀后追加，并另写 fix01 report/public report/source seals/commands/XML。已有两个 starter 文件已封存，不能当运行结果。
8. 针对改动的覆盖和相关旧回归、final source 全 units 一次即可；不为整齐计数重复全部旧 cohort。每个 XML 必须新鲜、有实际匹配、绑定最终源码窗口。fix 后独立有限复审/证据处置双 PASS 才能采用 Task5 和激活6；若需新 round2，先重读实际 7.0.1 `subagent-driven-development/references/fix-loop.md`，仍由同作者修复，root 不写产品源码。

## Task5 已保全的原始证据和资格限制

- 原报告 `task-05-report.md` SHA `9eff9b704479536b218cd3a764f4f0ece22742cb2380384741a9aa540e6eb360`，原前缀副本 `task-05-original-report-857102b.md`。原作者 commit 的 source/test/POM51 路、backend seal481 路、manifest551 项，root 已核对 1033 个 FS/Git blob 路径、4727 protected 原件、现 model 和冻结 SQL。
- 原 exact final source **227 units PASS**；IT 是 **116 distinct composed passing**，其中 **35 项 exact final production source**，不是一次 clean116。final-owned-integration102 为101PASS/1 upload401；保留失败。正向 forward-only fixture issuedAt-30s 后39PASS，不改 production/原 fixture/auth negative。实际观测 issued_age_us=-3234085，原因未证明为时钟回退。原102/重跑/各窗口不得相加当全新测试总量。
- root 已根据作者实际 retained edit 精确逆转最后 generic Runnable deny insertion，旧 queue 哈希 `bb51773d2d5af8b37604b5da818c023b9c735927709e062e8029a46e54fba57e` 与4个旧 seal 一致；这是标明的派生重建，不是独立存档的旧完整源码。见 `task-05-final-queue-delta-custody.md`。
- root 最后只读核对 exact75 个真实 intake 链：50SUCCEEDED/19 fixture-CANCELLED/4FAILED/2STALE，50 typed RAW/producer/input/businessVersion/segments，13 migration history、AIO OFF。19 个只作 fixture 维护的 cancellation 跨3轮有真实前后记录，不是业务 cancellation/success 资格；前17项的强链接是后验核验，错误 package /proc scan、S3backend attribution/corrections 和时间线仍保留真实限制。root 观测无 active import 不代表随后一直为空。详见 `task-05-resource-db-secret-rootproof.json`、`task-05-fixture-cleanup-round1-rootproof.json`、`task-05-custody-disposition.md` 和原 cleanup chronology。
- **CV2 历史执行资格**：原作者 resource guard 曾在内存读取完整 docker inspect 再挑字段；sanitized published JSON 不等于 selected-only execution。root 另以真正 `--format` 选择字段核对资源，已观察公开603文件/3个已知私密值+PEM/credential patterns 为0，作者4needle/601的独立声明不混为root4。该历史偏差如实保留，不回溯许可，不再复用旧 broad inspect helper。未来只准 selected-only --format，绝不 Config.Env。
- 原 Task4 组合测试、初始 native startup 错误原因未知、Task4 三个 minor/followups 等旧限制保留到最终 triage；不为暂停或修复改写旧记录。

## 已完成的只读准备：保留，不重做

以下均 root FULLREAD/哈希和实际 baseline 校验完成，仅提供实现准备，不等于新的设计/运行资格。Task5fix 后只重钉真正改变的接口，不重做整个设计。

- Task6 source alignment `task-06-source-alignment-preflight-20261023.md` SHA `5b94c4a0c6a71bb51b26e093e0260c88c2d79e19b74dd211b4124e9163be21e4`；budget `task-06-budget-activation-preflight-20261023.md` SHA `1e1f6a325fe1d7321cc17edde963f92072d7edaed94e9d1db14a05a0be97dd85`；候选857 repin `task-06-task5-candidate-repin-857102b.md` SHA `208c0d59f3aa79e1e495710ee439948b0487fa27c2c52197db12c3a07c295329`，51路径/18范围/12未变接口 rootproof 已存。最终 fix 源码变化接口还要重钉。
- Task6 owns producer OpenAPI **和 mechanical generated.ts refresh/check**，Task12 owns client/UI。实际 positive budget/effective original minima/correction DISABLED_NOT_INSTALLED composition qualification 是激活门禁，预检候选数值不是已安装能力。Root 不要求运行时读 Git/hash，也不把 nonce/digest 建议强造新公共 API/批准链。未知/enabled无真实 reader 不能冒充 disabled；Task6 保存和确认语义按已采用 R1。
- Task8 未编号 proposal `task-08-forward-ddl-proposal.sql`，164B，SHA `023fe875efd2be0bbd9d59b2d22a9b6429ea88eb6fc7a7b30d8541a53c0d46ee`：仅 external_operation 增 signed `reconcile_scan_seq BIGINT NOT NULL AUTO_INCREMENT` 和 unique `external_reconcile_scan`。静态独立双 PASS review SHA `d74af5405484d87d1789f9cc1943bc4f54e7d40d91755b5235642d832f31b29b`。无正式版本/header/freeze/ACK/apply；待 Task6 后真实模型/history/engine 重核，由 root 单独分配/冻结/ACK。不是 durable_task 调度公平性。>64 held/unregistered、fixed highwater bounded scan 等真实证明未执行，FUP 未关闭。
- Task7 source preflight `task-07-source-alignment-preflight-20261023.md` SHA `1a991429e02c7dcbba4605425408a28fbd66e7ff1cbbf7be8df01b4ecaf23bb9`，61源路径+10采用文档 rootproof。待实际5/6/8接口重钉。外部 observation atomic 与 permit aftercommit separate bounded recovery 不可混写。
- Task13 gate preflight `task-13-gate-custody-preflight-20261023.md` SHA `6ef4353b65e8201e105a67971b0dfbdf6bc8e02b9a3bd14c4aeaa81ec5098067`，21路径 rootproof；Task12 prepared ruling `task-12-final-gate-output-alignment-prepared.md`：Task12 最小修复规范 runner/root bundle/Playwright 全新输出目录转发，保留 discovery/counts/failclosed，不另造runner。Task13 最终一次 affected all gates，不重复计数，不运行会改旧 raw/DB 的 generic database_design verify、不重拍旧基线。

## 新启动但未完成的预检：从各交接继续

- `/root/b09_source_preflight`：`task-09-preflight-pause-handoff-20261023.md` SHA `82ae27289fbfb2f1318114f222cb25d5a1a199cec8d516a40d0505429063765c`。只读 wrapper/runtime R1/shared-rank，原设计完整阅读和实际源码映射未做，最终 source-alignment report 不存在。下一步按 handoff 补 exact design/R1/closure/reviews 和 focused source；不得称完成。
- `/root/b10_source_preflight`：`task-10-preflight-pause-handoff-20261023.md` SHA `71ed93a14eeaf0ebbdfc42238e94b8f7c9a1b51f8ccd1765c23557a3b23d0e27`。只读 wrapper/runtime R1/shared-rank，设计原件和实际源码映射未做，最终报告不存在。
- 两者基线只用 committed857 `git show`，只写新W report、不碰活跃产品树/运行时；恢复可继续只读以节约等待，但真正激活时须再核对 Task6/8/7/9 已采用接口，不把早期候选当当前接口。不得并行启动产品作者。

## 留存资源和保护规则

- MySQL exact CID `eefdd9a984ee00fe6ca886168b29a6ce4f3b405467b8d3204240d4e1a8dc7502`，name `ama-test-b01-ad9f1af29eff`，label `ama.remaining.owner=remaining-20261023`，pinned mysql sha `869218921e61d6c3c89820955d63cca42971f0e3e6c1e2792247bbd944ebc6e9`。暂停 fresh selected inspect RUNNING/OOMfalse，loopback **46653**，768MiB memory+swap/1CPU/256PID。保留数据，不停止/重建/reset。旧58062/45157是历史，不改原 evidence。
- RustFS exact CID `a91f5946d5f619335d5cfb6bf2295b1df46abbb4a75b2db8feccaf637bcb7d51`，name `ama-task05-s3-5237a2db-e8d5-4cbe-b357-b35df5cfcba5`，label `ama.owner=ama-task05-5237a2db-e8d5-4cbe-b357-b35df5cfcba5`，pinned sha `128220dc9382bac81940a1a9616ee8c274a3f3b8f647eedbeb50df5bb8b49922`，loopback **45501**，2GiB memory+swap/2CPU/256PID。暂停 RUNNING/OOMfalse；现有 data/logs exact mounts 保留。资源证明 `pause-20261023-2145-resource-process-proof.json`，只看选定字段、无 Config.Env。
- 当前唯一 DB env `.tools/phase-b-remaining/env.sh`，Task5 S3/private PKI 根 `.tools/task05-5237a2db-e8d5-4cbe-b357-b35df5cfcba5`，client.env 0600，PKI 可在同一轮 Task5fix 复用；新测试用该根下 exclusive UUID child。不要新容器/新凭据/新PKI/privileged install；先选定 inspect 同CID/labels/digest/mounts/bounds/真实端口再复用。数据和私钥不进公开文档/归档。无 secret 输出。
- 所有父测试/Worker 512MiB/2 active CPU，native/probe64MiB/2CPU；现有 reserve/release/exactbind 机制 accepted，collision fail+retain、不可静默重试。LOCAL genuine mTLS、受控 loopback S3 HTTP 资格不等于 productionS3HTTPS/公司协议资格。
- 暂停 `/proc` 只读按 completion cwd + java/mvn/node/python comm 观测为空，非全系统无进程声明。原作者自己的 pause handoff 记录确切 owned PID/测试启动情况。共享容器保留运行，停止业务推进不等于删除 fixture。
- 原保护 baseline4728路径：**4727 immutable**，sole已授权 generated当前模型 `docs/database/ama-schema-draft.sql` SHA `d3094336b4d55d9389b2fd1d852d627b93e0ac0394b9b1801e258a154b0cb445`。旧模型快照/SQL/计划/raw/stash/树不改。
- 13条已应用迁移至 `V013_1__b_runtime_metadata.sql`，1817B，SHA `9280baea017014f3322af901373a7f8d69dfaa396fdd49f3c5d872535c43a520`，Flywaychecksum -1898004857。实际 version 是001...013.1，不能误写13.1找不到后重跑。003/004 gaps，014..027reserved，下个integer028，patch未分配。禁止 reset/drop/repair/outOfOrder/旧fixture创建器，root 独占 DDL allocation/freeze/separateACK/application。
- 实际技能7.0.1缓存，6.4.2已失效；Windows PowerShell用 literal here-string | WSLpython3可靠。approvalnever **省略 sandbox_permissions**。JDK `/home/tanren/ama/.worktrees/phase-a/.tools/zulu21.52.203-ca-jdk21.0.12.1-linux_x64`；Node `/home/tanren/ama/.tools/node-v22.23.4-linux-x64/bin`。规范 `tools/checks/run.py`，新输出路径，不能盲用旧 helper 触及历史 artifacts。

## 后续各任务不可遗漏的交接门禁

Task6实际保存/marks/restore/确认需真实完整集/零marks保护、LONGTEXT传输前预算、same saved/draft算法和authentic correction qualifier；Task8真实新forwardDDL+model/dictionary/ER+fairness proof后才激活7；Task7真实private prepared observer/单owner/strict generic fences/generation-only immutable stale retention/aftercommit permit recovery；Task9真实 firstsettings/template-origin 无fakeSQL、十五profile original provenance/durable provider/UNKNOWN；Task10意图ACK在typing前、全部PENDING贡献/图重映射/精确reviews；Task11 actual publication_submission DDL必须postTask8另审另ACK，真实marker/未知不重发/零效果合法重试/成功read-only；Task12真实producer projection/OpenAPI和HTTPS LOCAL+S3+四身份+refresh/restart/lostresponse browser；Task13最终一轮规范门禁/fresh whole独立复审/有限修复及演示资格/summary/完整handoff/delivery。每项以最终真实源码 BASE 激活，不拿设计/准备常量作运行证明。

公司 SSO/目录/模型/知识库暂无，已获授权继续本地受控闭环，勿重问。B01IMM永久 PARTIAL、B04Q401 UNKNOWN；HA/容量/nativeI/O/production publish/mail未资格。**无真实生产发布/邮件/新push/main merge。** 用户此次只是暂停，不能自行恢复或自动调度。
