# 任务接续记录：2026-10-31 23:28

## 停止指令与执行边界

用户最新指令：**“完成本次调度之后，就停下，记录接续文档以及任务状态信息，以便后续通过‘继续任务’指令接续任务开发。”**

当前个人模板初始化事件修复批次已经完成定向回归，开发与调度停止。收到用户新的“继续任务”之前，不自动开始实现、测试、部署、CAS 联调或压测。本记录覆盖 `DEVELOPMENT_HANDOFF.md` 及既有 ledger 中更早的“继续所有任务”或“长测运行中”指令。

没有运行中的 Maven/测试 JVM、个人模板/原生录音 CAS 验收脚本或录音负载驱动。没有新建自动调度。保留现有开发应用和兄弟 harness 运行，未停止它们。没有提交、重置、清理业务数据、覆盖失败报告或切换分支。

机器状态快照：`.tools/local-app/task-continuation-status-20261031-2328.json`（0600；无口令、Cookie、CSRF 或 bearer）。

## 必须保留的开发约束

- AMA 所有开发、计划、测试与验证从 `/home/tanren/ama` 根目录的 `main` 进行；单 agent；不创建分支、子 agent 或 worktree。
- 兄弟项目 `/home/tanren/ama-integration-harness` 保持原 `master`，用户允许同步修复兼容问题；不自行切换它的分支。
- 所有实际外部业务接口通过兄弟 harness 联调。固定 HTTP **8792**、SMTP **2525** 的设计不修改。模拟器不代表企业真实协议权威或模型质量。
- 保留工作区全部既有修改、数据库、原始音频、部分夹具及历史失败证据；不因测试失败重建/清空环境。
- 私有环境文件 `.tools/local-app/resume-c-it-env.json` 仅供进程环境注入，不打印内容。

## 当前源码与部署

| 项目 | 核实结果 |
| --- | --- |
| 工作区 / 分支 | `/home/tanren/ama` / `main` |
| 工作区源码签名 | `ad062c02d605eb4cdb49fbc4e6791ebba485ff5701e87db96fac2f1bdcc2c4ef` |
| 运行版本签名 | `744bfad00d97dd7b828c901d332cc4c96393908e6d3e5ad3fefb20a117816e3a` |
| 差异 | **最新初始化事件引用校验修复及新增事件回归尚未部署**；运行版本已含此前 append/录音鉴权/个人模板和初始化回执修复 |
| Web / API / 管理 | 5174 / 8182 / 8183；管理 readiness 实际 `200 / UP` |
| 主 MySQL | 46443；`ama_local_dev`；V045，35 条成功，失败 0 |
| 自有 C 测试库 | `ama_c_recording_1ef37ff530a7`；V045 |
| API 连接池 / 文件线程 | 32 / 32；本轮未放宽超时、队列、fsync 或权限规则 |
| 外部 origins | cas / knowledge / model 均为 `http://127.0.0.1:8792` |

进程仅供下次核实，不能未经核对直接信号：API 436212、worker 436211、files 436152、model 436183、knowledge 436184、frontend 436348。

Harness PID195659；本次只读核实同时监听 127.0.0.1:8792 / :2525；原库 `/home/tanren/ama-integration-harness/data/mock.db`。原 bridge：knowledge→46969、minutes→47091。运行记录 `.tools/local-app/resume-harness-both-bridges-process.json`；旧 `resume-harness-bridge-process.json` 的 PID157192 已失效，不要使用。

## 本次最后完成的批次

真实 CAS 报告 `personal-templates/current-v2-harness-01/report.json` 首先完成个人模板 CRUD、八主题、原键重放、非法输入/过期冲突、独立 foreign403、真实 TXT→worker RAW→WORKING→确认，然后初始化返回503。

实际 JFR 异常采样定位 `JdbcMinutesSettingsReceipts.restore` 仍强制 owner=null / revision=1。已补实际 MySQL RED→GREEN：原受理个人 revision 绑定当前 actor 与父子 owner，编辑/删除后仍可回放原受理结果；新选择的生命周期门槛保持。首次组合19项中18通过、一个既有录音夹具 JDBC 登录超时，原失败保留。夹具在 Hikari 活跃期间改用应用已建立的池，未修改生产超时；19项重跑通过，独立登录预算恢复1项通过。

回执修复部署后，新真实 CAS 报告 `personal-templates/current-v2-receipt-fixed-02/report.json` 仍在首次初始化失败，此次为400 `INVALID_ARGUMENT`。第二次 JFR 精确定位 `MinutesEventReferences` 的初始化事件引用仍限定内置模板；事务回滚，未进入模型请求。

**本批最后修复**：初始事件可引用当前会议 owner 的个人模板；父/子/生命周期 owner 精确匹配，当前 revision、command revision 一致且未删除。内置模板第一版保留，空排除、真实 meeting/subject 版本及取消代次条件保留。新增真实 MySQL `PersonalTemplateInitialEventIT` 先 RED，随后全部 `PersonalTemplate*IT` **21项 PASS / 0失败 / 0跳过**。其中含继承用例，不能称21个独立模板功能。事件和回执的新测试是生产组件的元数据夹具；完整业务生产链仍以真实 CAS 报告签收。

当前验证日志：

- 最新定向21项：`.tools/local-app/resume-personal-template-initial-event-green.log`。
- 事件 RED：`resume-personal-template-initial-event-red.log`；JFR/异常栈：`resume-personal-template-initial-02.jfr`、`resume-personal-template-initial-exceptions-02.json`。
- 回执 RED：`resume-personal-template-initial-receipt-red.log`；组合超时：`resume-personal-template-initial-receipt-green.log`；19项重跑：`resume-personal-template-initial-receipt-green2.log`。
- 最近完整生产 Java **734 PASS**：`resume-personal-template-receipt-current-java.log`，**发生在最新事件修复之前**，不得写成最新源码全量通过。
- 最近 Web **445 PASS**：`resume-personal-template-current-final-web.log`；typecheck、build、API字节一致已通过。本次最后批次未改 Web；原 >500k bundle 提示保留。
- 录音集成 **78 PASS**：`resume-recording-all-current-it.log`；随后夹具池接线验证1项：`resume-recording-fixture-pool-isolation-green.log`。
- 录音驱动 Python **18 PASS**：`resume-recording-load-final-unit.log`；runtime 配置 Python21项通过：`resume-personal-template-runtime-config-green.log`。

## 录音失败长测与修复状态

Case：`.tools/local-app/recording-load/resume-harness-100-7200s/`。

- 原计划100路录音 / 300用户 / 7200秒；实际在 **5317.307秒** 确认不可恢复失败后，仅 SIGINT 驱动 PID282411。应用角色未被此中止动作信号，全部证据和业务数据保留。
- 中止前230264 ACK、89错误；最终230354 ACK。89路实际生产队列溢出；后台用户 fatal=0。实际API故障在 duration/2=3600秒注入 SIGKILL，6秒后同一二进制恢复。
- 原 `report.json` 明确 `passed=false`、未完成两小时。其未归档 ACK 计数不能解释成已经证实音频丢失。
- 单独只读物理回读 **230354分片 / 14742656000字节全部原 SHA 匹配**；报告 `physical-prefix-readback-01/report.json`。此证明限定物理 ACK 前缀，不替代归档、权限/API或两小时验收。
- JFR / thread / SQL plans 均保留于原case和 `resume-recording-append-sql-plans.txt`。逐块全历史读在3600分片时传回3600行；新增数据库事务内聚合全量合法性和总量，仅读尾部+重试邻接证明；未引入进程缓存授权。
- 初版投影33ms比旧23ms慢，失败测量保留。实际计划识别逐行相关查询后，窗口前沿+已捕获分段集合降低成本；后续15次复用连接测量54,000→45行，中位旧21.50ms / 新15.83ms。此为组件测量，**尚未证明新版100路/两小时吞吐**。
- 大小写 collation 可漏校验远处异常分段，真实 RED→BINARY 精确比较修复。append15项、全部录音78项已通过。
- 驱动 QueueFull 无法写 sentinel 导致 consumer 挂起，RED→GREEN18项；现在直接观察 producer 完成/失败，队列仍300，不将取消计成完整采集。

新版 native 故障、100/300短测和完整7200秒尚未启动。旧短测100/300/60秒与 native10项的报告属于旧签名，不冒充新版结果。下一轮使用全新目录，不能覆盖旧失败。

## B / C / D 任务状态

三个阶段均 **NOT ACCEPTED**，不填推算百分比。

| 阶段 | 已有范围证据 | 未完成的收口 |
| --- | --- | --- |
| B | 逐页矩阵5项PASS：AUTH-01、INFO-01、TOPIC-01、LIB-01、FEEDBACK-01；专题当前批次真实CAS10及三视口72零差异、只读知识库CAS6及三视口12零差异已有独立报告 | 矩阵13项OPEN、2项DEFERRED_VOICE；会议/名单/导入材料/转写/纪要候选/审阅最终发布/跨页负例及像素仍需继续。MIN-01保持OPEN |
| C | 录音文件持久化、租约、WS、浏览器PCM/恢复/归档已有实际链路与回归；手工替换/撤销和部分模型协议组件已有证据 | C-07旧两小时失败，新版未验；C-02生产分阶段任务/额度/RAW闭环、C-03自动纠错/说话人、C-06持久字幕与回放仍未完成；模拟器不签收真实模型质量/延迟 |
| D | 材料/纪要/审阅/最终发布基础与个人专题已有部分实现；D-04当前新增个人模板持久化、协议v2、UI及配置追加安装 | D-04真实生成/候选/历史/模板UI/像素尚未完成；D-05治理移除/失效传播，D-06三格式授权导出，D-07可恢复物理清理，D-08全页交互视觉等仍待收口 |

B状态权威矩阵：`docs/acceptance/b/b-followup-page-crud-matrix-2026-10-30.md`。既有B/C执行 ledger 与 brief 保留在 `.superpowers/sdd/2026-10-30-ama-b-followup-pixel-crud-cas/`、`.superpowers/sdd/2026-10-21-ama-phase-c/`。D规格在 `docs/superpowers/plans/2026-10-21-ama-phase-d.md`；没有新建一个虚报已执行的D ledger。

个人模板追加归档独立检查通过：旧74文件逐字节保留、新增35文件、第二次输出一致、live archive未改，报告 `resume-personal-template-local-setup-01/report.json`。v1原协议/hash/lookup pin 保留，v2独立请求hash/header，但底层模型身份和全局额度不分裂。

## “继续任务”后的顺序

1. 先读本记录与机器快照，确认根目录/main、进程与签名、数据库和所有既有修改。不重做已经完成的CRUD、SDK、append修复；不清理部分业务夹具。
2. 最新事件修复已有21项定向GREEN；补当前源码需要的全量验证后正常本地部署。保持 API池32/文件32，三个外部 origin 均8792。通常从根目录 `source .tools/env.sh`，然后：

   ```bash
   ./mvnw -B -ntp -pl backend/contracts,backend/core,backend/adapters,backend/api,backend/worker,backend/realtime,backend/file-service -am test
   AMA_LOCAL_DB_POOL_MAXIMUM=32 AMA_LOCAL_FILE_WORKERS=32 AMA_CAS_ORIGIN=http://127.0.0.1:8792 AMA_INTEGRATION_KNOWLEDGE_ORIGIN=http://127.0.0.1:8792 AMA_INTEGRATION_MODEL_ORIGIN=http://127.0.0.1:8792 python3 tools/dev/local_app.py start --web-port 5174 --api-port 8182 --management-port 8183
   ```

3. 新目录跑 `node tools/dev/personal_templates_cas_integration.mjs .tools/local-app/personal-templates/current-v2-event-fixed-03`。旧01/02失败报告不覆盖；脚本已包含模板→实际TXT/RAW/确认→初始选择/生成→删除保留历史→最终/发布。原先两次都止于初始化，**尚无此批v2模型请求成功/失败证据**。
4. Harness `backend/app/main.py` minutes bridge的白名单尚缺 `X-Ama-Minutes-Protocol`。这是已知源码缺口，**还未通过实际v2模型请求证明、未修复**；必须先用真实8792请求/对应回归证明，再做最小兼容修复、兄弟测试，保持原库/双bridge/固定8792与2525。不要绕过harness或当成企业权威。
5. 继续真实模板UI创建/编辑/删除、原请求丢ACK/刷新、会议选择、候选采用/拒绝、最终/发布及三视口精确原型验收。脚本目前打开弹窗截图，不等于这些UI流程或零像素验收已经完成；MIN-01不能提前关闭。材料存在时的模板设置分支仍需真实复核。
6. 新版 native实际SIGKILL→100/300短测→新完整7200秒，均新证据目录。原生必须同时开启 `AMA_NATIVE_API_FAULT=1 AMA_NATIVE_API_HARD_CRASH=1`；load venv用词法绝对路径 `.tools/recording-load-venv/bin/python`，不要 `.resolve()` 丢失venv。长测开始前冻结角色、记录签名和容量；完整结束前不正常部署/改harness。不得仅增大超时/队列或弱化fsync/证明来通过。
7. 再按既有brief继续C-02/C-03/C-06和B/D剩余任务。所有真实外部调用仍通过harness；不以组件或模拟质量冒充生产闭环。

私有Java properties的键和值均为完整 `\uXXXX` 转义，诊断读取时须两侧解码，不能直接按明文键查找。禁止递归枚举 `.tools/local-app` 音频对象树，历史对象很多；日志/runner文件用明确路径或非递归glob。
