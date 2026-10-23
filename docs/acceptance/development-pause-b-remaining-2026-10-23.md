# B 阶段暂停与精准接续说明（2026-10-23 18:20）

用户明确要求暂停开发。已停止运行中的子代理；无构建/测试进程。仅完成记录和封存，不继续产品实现。下次收到 **“继续任务”** 即恢复本记录所述目标：使用子代理完成全部剩余本地受控 B 阶段实现、测试、独立复审和交付记录；不重新询问公司接口或是否继续。

## 位置和可信状态

- 开发树：`/home/tanren/ama/.worktrees/phase-b-completion`；分支 `codex/phase-b-completion-20261023`。不要在主目录的旧接续树写产品代码。
- 暂停时产品源码 HEAD：`59c8b0801398123b1c0190ecbfeba8513f645abd`。其后只有暂停文档/封存提交，提交标题 `docs: checkpoint paused phase B remaining work for exact resume`；先用 git log/status 确认最终文档 HEAD。
- 主目录 `/home/tanren/ama` 分支 `codex/phase-b-resume-20261023`，HEAD 仍 `34cb226aefe99c67d3681af68d748b0aeea4e073`。为让下一会话发现本记录，仅在该树保留未提交的接续入口追加和本说明副本，不提交、不快进产品代码。最终快进前先确认这些仅文档内容已保存在完成树提交，再按精确路径处理这两项，不能 reset/clean 整树。
- main 仍 `0676a21311f2b2193f73a5e096f4a5605ee9b41f`，原 stash `bc12e825246c60c12752129c4d512ef9c545758c` 保留；无 push/main merge。
- 正式计划：`docs/superpowers/plans/2026-10-23-ama-phase-b-remaining.md`；规范：`docs/superpowers/specs/2026-10-23-ama-phase-b-controlled-runtime.md`。
- W：完成树 `.superpowers/sdd/2026-10-23-ama-phase-b-remaining`。恢复先读 `current-state.json` 和 `progress.md`，以最后的 USER PAUSE 为准。旧段落是历史，不能重派已完成任务。
- 本次 W 的完整原始文件归档、逐文件 SHA 和选定 MySQL 元数据位于 `artifacts/phase-b/b-remaining/controller-pause-20261023-1820/`。工作区仍完整保留；若 W 缺失，先核对归档 manifest/hash 再还原至同一 W。归档不含私有 env/口令/PKI。

## 已完成与未完成的准确边界

|计划任务|暂停状态|
|---|---|
|1 队列/Worker 基础|实现、测试、独立双 PASS、采用完成，采用 `a3b2933ef83035e858d2d3336decba4617f22a1f`。|
|2 profile/schema/edit guard|实现、测试、独立双 PASS、采用完成，采用 `3e393dbc9485b187b89bfc03abd88821a0aabddb`。|
|3 真实来源权威|实现及一次修复完成、独立双 PASS、采用 `2ec38c0350f466fc4292d588051c5694cc099cb5`。|
|4 真实 processing HTTP intake|作者完成并提交 `59c8b080`，root 原始证据核对完成；**独立源码复审中断，尚未采用**。|
|5 / 6 / 8 / 7|设计已采用，准备书已写；源码均未开始。执行顺序为 5→6→8→7，不能跳过 8 的公平性修正。|
|9 / 10|设计采用分别 `af331c7e2eaff1af7ca2d743e717c3e31fff43d9`、`875047cdbcd6ddd17f5c267f8998b7a5a4378b6c`；源码未开始。|
|11 发布|R1 设计限定复审双 PASS，root 完整读取实际 delta、closure、最终报告并核对哈希；**还没做正式设计采用**，无源码/DDL/运行资格。|
|12 网页业务闭环|完整设计及独立复审已读；1 Important，原设计作者 fix round1 刚启动即暂停，**尚无 R1/closure**。|
|13 整体交付|未开始：最终全量门禁、fresh whole-branch 独立复审、演示/测试矩阵/接续更新和安全主目录 FF 均待完成。|

任务数不是加权进度，不据此宣称 B 阶段百分比或完成。完整 B 阶段未验收；设计批准、单项测试不能替代完整业务闭环。

## 下次第一优先：完成 Task4 独立审查和修复

1. 使用原审查者 `/root/b_task04_intake_source_review` 的 `followup_task`，从中断位置完成报告 `W/task-04-review.md`；保留原审查阅读记录。原作者 `/root/b_task04_intake_implementation` 处于待命，不要重新实现 Task4。原 handle 失效才明确接续身份。
2. 原任务范围 BASE `2ec38c0350f466fc4292d588051c5694cc099cb5` 到 `59c8b0801398123b1c0190ecbfeba8513f645abd`，不能用 HEAD~1；其中并行根代理 9/10 设计文档不是 Task4 产品实现。
3. `W/task-04-report.md` 已完整读；root 独立核对 623 作者路径、32 source（28 Java +4 OpenAPI）、589 manifest 条目、FS+Git blob、XML。最终不同测试 **223 unit +151 IT =374，0 failures/errors/skips**。其中 150 final regressions +1 configuration restart，后者 5 fresh child JVM；不是 DB 重启、Import Worker 或浏览器资格。Java 最终窗口一致，随后仅 OpenAPI partSizeToken 范围修正并 lint/bundle 通过；早期失败、setup、未完成 unit 窗口全部保留。
4. 原始证据 `artifacts/phase-b/b-remaining/task-04/`；rootproof `W/task-04-rootproof.json`。bounded review package `task-04-review-package.diff` SHA `89fa6f8b6377ddf6a4273aeda14b982cf0b1da0c2ab9d48171d152d00dcde592`；完整 audit SHA `0f10b2f7ab24d4f6f25329695c5ae400881e20a680643bf1cb457cf0097ba668`。
5. **初步线索，非最终裁决**已存 `W/task-04-review-interrupted-notes.md`：restore 只核对 ordinal 等，缺原请求 ordered files/revision/full input binding；正常 ELIGIBLE reason=null 与 OpenAPI enum 不符。先拿完整独立报告再核对/修复，不把聊天线索当双 PASS。
6. 每轮先重读实际 7.0.1 `subagent-driven-development/references/fix-loop.md`；前 3 轮恢复同作者，root 不写产品修复。覆盖改动的真实测试 + actual fix diff +限定复审。原 374 已有证据，不为重复确认而重跑全部。
7. 双 PASS 并完成采用后，才从当时 clean HEAD 冻结 Task5 实际 BASE，校准真实 Task4 facade；`W/task-05-brief-prepared.md` 仅准备书，不是已激活。

## 可并行的设计收尾（仍不写产品代码）

**Task11**：原设计 SHA `a8ec962bfee7ed1025a8d0dc113750447d9a8cc95b006b758cc544de900981e8`；R1 `task-11-final-publication-design-r1.md` SHA `f18ae4734603709284d91345b2909eca9687627c825444fd8cfa06dd49d36ffc`（102624B/405行）；closure SHA `b906213c0ff8af494f54495b3dbd337cbf3283994dfabe2e6798255438808c51`；最终限定复审 `task-11-final-publication-design-r1-review.md` SHA `247c16e565986ebb61c8b60589240fd0fbd6bf82153826cb57bde72db42d3241`（11506B/65行）。T11DR01/02 均 ADDRESSED、无新增问题。root 已完整读取，不必重复广泛复审。下一步精确原件采用 wrapper/plan/raw manifest。承接到 Task7 私有 queue terminal hook，保留真实 marker、fact-only 无 domain writer、UNKNOWN 不当 NOT_SENT。Caller bearer+签名 actor delegation、双 ACL 同源官方文档 relay、`publication_submission` 不可变接受绑定/单向本地决议均是后续实现门禁。

`publication_submission` 只是审过的逻辑提案：**未分配版本、未生成 SQL、未冻结、未 ACK、未应用**；在 Task8 实际 schema 后 root 独立审精确 DDL，再 allocation/freeze/独立 ACK/application；不能提前造迁移。原 fixed-input retry 不放宽；首发前真实零效果/未标记失败可经完整协议恢复编辑/改目标/重新 final，不能永远锁死 FAILED。

**Task12**：原设计 `task-12-web-business-loop-design.md` SHA `2ce7e7457f07314b7ce967f433c716764d64f97f890576755ee61f7208e9253a`（57259B/175行）；独立 review SHA `b9bbedb4fa71cac0ccf3c65c62d00703b947c9e10c6031586702308260f43566`。root 已完整读取并验证 V008 LONGTEXT / JdbcMeetingRepository.read 原 SELECT *。T12DR01：新 list/workspace/category/composed info/roster reader 必须在文本进 JDBC 前进行受保护的 metadata 预算和完整集合检查，分页/DTO 大小不够。已写 `task-12-design-fix-01-brief.md`，恢复原设计者 `/root/b_task12_web_loop_design` 执行 round1，原始文件不改；暂停时 R1/closure 均不存在。完成后交原独立审查者 `/root/b_task12_web_design_review` 限定复审。无浏览器/运行时/新资源启动。

## 数据库、工具与保全

- Docker Desktop 已启动，WSL integration 正常。仅允许复用本轮 owned MySQL：`ama-test-b01-ad9f1af29eff`，ID `eefdd9a984ee00fe6ca886168b29a6ce4f3b405467b8d3204240d4e1a8dc7502`，label `ama.remaining.owner=remaining-20261023`，pinned mysql digest `869218921e61d6c3c89820955d63cca42971f0e3e6c1e2792247bbd944ebc6e9`，768MiB/1CPU/256PID/nativeAIO0。
- 暂停只读核对仍 RUNNING、OOM false，loopback **127.0.0.1:45157**。保留容器/数据，未停止、清空或重建。历史测试端口 58062 是 Docker 中断前的动态映射，原证据不重写。下次重新 inspect 同 ID 的实际 loopback 映射后再连接，不假定端口永远固定。
- 本轮唯一私有 env：完成树 `.tools/phase-b-remaining/env.sh`；URL 已从 58062 更新为 45157，口令未变，0600 历史 backup 保留。不要输出或公开 env、Config.Env、口令、私钥。原 restore receipts 位于 W/post-user-interruption-*。此次恢复不是 MySQL server restart qualification。
- 最后只读 audit 13 行 migration history 不变、100 条 Task4 owned task/attempt 全 terminal/ended。不要重新执行只认可 12 SQL 的旧 fixture 创建器，不能 reset/drop/repair/outOfOrder 或修改旧 oracle。
- 冻结已应用 `V013_1__b_runtime_metadata.sql`：1817B，SHA `9280baea017014f3322af901373a7f8d69dfaa396fdd49f3c5d872535c43a520`，Flyway checksum -1898004857。原 12 SQL 不改；003/004 gaps、014..027 reserved，下个 integer028；Task8 patch 未分配。
- 4728 原保护路径中 4727 不变。唯一已授权 current-generated 例外 `docs/database/ama-schema-draft.sql` 当前 SHA `d3094336b4d55d9389b2fd1d852d627b93e0ac0394b9b1801e258a154b0cb445`；原快照不改，Task2 prior blob 保留。两份用户计划原 SHA/SQL/旧 artifact/树/stash 都保留。
- 实际技能缓存 `C:/Users/LING/.codex/plugins/cache/openai-curated-remote/superpowers/7.0.1/skills/`，旧 6.4.2 已失效。当前权限 danger-full-access / approval never：exec_command **完全省略 sandbox_permissions**，普通 WSL 可用；不要恢复旧 require_escalated 调用。
- shell 为 PowerShell；用 literal here-string 管道至 WSL python3，避免变量/反引号误插值。JDK 只读 `/home/tanren/ama/.worktrees/phase-a/.tools/zulu21.52.203-ca-jdk21.0.12.1-linux_x64`；Node `/home/tanren/ama/.tools/node-v22.23.4-linux-x64/bin`，规范检查 `tools/checks/run.py`，输出必须新目录、不覆盖历史。
- 新 S3 尚未创建。后续5/12启动必须原计划资源/所有权 guard、新路径/PKI/受控凭据，不执行旧 helper 默认值。只一名产品作者；独立 readonly 设计/审查可以并行；root 只协调/记录，不代写产品修复。

## 完整剩余顺序和限制

继续顺序：4 复审/修复/采用 →5 Worker 原子输出和真实 LOCAL/S3 →6 保存转写/marks/确认 →8 对账公平性及经审查 forward schema →7 外部原子事实/权威 →9 生成 →10 编辑/人审 →11 final/发布 →12 真实 HTTPS 浏览器闭环 →13 fresh 整体独立复审/记录/安全主目录 FF/交付。准备书 5/6/8/7 和设计不是已执行；每项新实际接口/BASE 必须激活时重钉。

公司 SSO/目录/模型/知识库暂无资料，用户已授权继续本地受控业务闭环，不重问、不伪造公司协议。B01 IMM 永久 PARTIAL，B04 Q401 UNKNOWN；HA/容量/native I/O/生产发布/邮件资格未取得。无 production publish/mail/push/main merge。本地任务未完成前不要交付成 B 阶段已完成。
