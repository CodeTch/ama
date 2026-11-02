# B/C/D 开发暂停与精准接续（2026-11-01 22:55）

**当前状态：PAUSED_BY_USER_AFTER_CURRENT_BATCH，B/C/D 总体 NOT_ACCEPTED。** 用户最新要求“完成当前批次调度之后，就停下，记录当前任务状态信息以及接续文档”。该要求覆盖此前“D完成前不许停”的持续执行要求。当前批次已结束，未启动下一批；仅在用户再次发出“继续任务”等明确继续指令后恢复。

本文件优先于旧暂停记录、旧交接中的ACTIVE/禁止停描述和旧容量要求。机器状态为 `.tools/local-app/task-current-status.json`，暂停时运行快照为 `.tools/local-app/bcd-completion/pause-runtime-snapshot-20261101.json`。

## 约束与当前验收口径

- 项目根 `/home/tanren/ama`，main，单agent；禁止子agent、切分支或使用worktree；不自动提交、不重置业务数据、不覆盖旧失败报告。已有大量未提交/未跟踪代码是本任务成果，应保留。
- 所有外部联调走兄弟 `/home/tanren/ama-integration-harness` 的固定HTTP8792/SMTP2525。兄弟master，模拟器可以同步修复，不是公司权威实现；固定端口设计不修改。
- C并发：10场会议并发录音、50人同时在线（10录音+40后台），持续不间断1800秒，每2秒分片；完整ACK/SHA/归档，temporary字幕p95≤3秒、stable≤5秒，按源结束到客户端收到事件测量。正式连续场景不计划断ACK/杀进程；故障恢复单列。
- C长录音文件：30分钟，28800000样本/57600044字节/900分片/15个120秒窗口/75阶段checkpoint，完整归档→独立受管来源→RAW。
- 50录音/200在线验证，以及两小时长文件验证，均已后移F，不阻塞本轮BCD。D保留业务、页面、控件、权限和原型验收义务，不另设该容量门槛。详见 [当前阶段验收调整](2026-11-01-current-phase-acceptance.md)。

## 已部署运行状态

- 部署与工作区签名一致：`63f0089a45c840a748c302d33da42196033ed6975553433f31fc755a30580fee`。
- 主库 `ama_local_dev`，本机MySQL端口46431，迁移V058/V059/V060全部成功。测试配置仍指向独占 `ama_c_recording_327536fe2506`，V060；私有连接参数在 `.tools/local-app/bcd-completion/test-database-speech-current.json`，不要打印。
- 页面 `http://127.0.0.1:5174/meetings`；API8182，管理8183；API DB pool32/file32。
- 六角色全部存活。暂停快照PID：frontend2617057、API2624469、worker2616620、files2616502、model2616558、knowledge2616559。以后操作前重新核对state与真实PID，不盲用本页旧PID。
- 没有Maven、负载或浏览器验证批次继续运行。应用和兄弟联调服务保留运行，未关闭服务或数据资源。
- 已应用迁移不可改：V058 SHA `d062609f8e7c2218b682a01477509cce0a759af29d12d07f86afdb5fe97f52d4`；V059 `8afe136f26fa896f358bb3703cde543983c25a865c4d57a859b0a9649759760e`；V060 `54fd3408912dbe5bd3c9d73fce68277696be4e0d58ace17ce09ae652cb9743cf`。

## 本批实现和验证

录音归档的独立转写来源、专用不可变processing/parser配置、V060技术任务、真实HTTP来源准备/只读状态、普通公开上传32MiB限制，以及页面“保存并开始转写”已部署。归档副本只能从真实当前READY archive与成功任务产生，不能用任意文件或普通同字节上传冒充。原始归档、实时字幕、会后RAW分别保存；RAW仍经原封闭intake/worker/S/F，源SHA与字节完整性保持。

修复归档准备阶段与真实续租之间的完整图竞态，仅TASK_PREPARATION可完整回滚后重捕获；最终写入仍要求真实TASK_MUTATION/WRITING。无配置历史后端任务在非阻塞claim时跳过，保留原任务，不回退后端或清理旧失败。Archive重试夹具改为数据库期限与真实有界poll，未改生产backoff/租约/权限。

普通导入处理期限仍60秒；显式录音profile在真实任务facts下使用独立600秒有界期限。全量档案追加遇到预算失败，JFR定位为258条依赖超过256；仅本地辅助安装与运行配置统一依赖预算384，保留64档案/1MiB/原深度等其他限额，通用ControlledMinutesAdmin.READ仍256。最终本地catalog为58档案/258依赖，pin `f28a63e481c989b7a649477cd9ac0ded6da4dd5e01fc48da1bb8cda30d4b4ff0`。

浏览器实际发现并修复应用级journal在session hydration时误把未解析的meetingScope=null当显式离开，导致刷新丢原转写请求的问题。新增应用级真实RED→GREEN回归；现在身份初次同步不传meeting，路由提交后再同步会议，显式退出/跨身份仍清理。

| 检查 | 结果 | 证据（以下短路径均相对 `.tools/local-app/bcd-completion/`） |
| --- | --- | --- |
| 生产Java | 878通过，0失败 | `recording-source-production-java-final1.log` |
| 实际MySQL/mTLS/固定harness组合 | 69总：68通过/1跳过，0失败/0错误 | `recording-thirty-minute-source-current-it2.log`、`-exit.json`（exit0） |
| 30分钟长文件实际用例 | 900分片/57600044字节/15窗/75checkpoint/RAW1800000ms，elapsed243秒、86次真实续租 | 上述it2中的THIRTY_MINUTE_SOURCE_PASS；这是长文件处理，不是持续并发容量 |
| 两小时F用例 | 默认跳过，不计通过 | 显式 `AMA_RUN_PHASE_F_TWO_HOUR_SOURCE=1` 才启用 |
| Web | 521通过；type/build通过；OpenAPI83运行schema字节一致 | `recording-import-session-hydration-{full,type,build,api}-green.log` |
| 本地工具 | 53总：49通过/4跳过 | `local-recording-catalog-budget-full-green.log` |
| 负载工具 | 26通过 | `load-c-freeze-full-green.log`；只证明工具，未执行容量 |

此前两小时源文件it1失败、it2因用户缩小范围中止，及30分钟组合前台it1收到SIGTERM143，均保留日志/JFR/数据，不改为成功。当前组合it2通过来自独立detached批次，不与旧报告混合计数。

## 当前批次最终结果：native-cas-04 整例失败，11个检查点通过

运行脚本 `.tools/local-app/bcd-completion/recording_archive_transcription_cas.mjs`，执行副本和完整报告在 `.tools/local-app/bcd-completion/recording-archive-transcription-native-cas-04/`。**报告pass=false，不得直接升级为PASS。**

已通过11个实际检查点：真实CAS与原生AudioWorklet PCM/WS持久ACK、temporary/stable持久字幕、真实API SIGKILL期间IndexedDB保留未确认音频、恢复后重发原字节、刷新不自动采集且新设备段、暂停/续录、跨路由继续采集、结束后实际归档/来源准备，以及intake已提交但浏览器丢响应后，刷新恢复原命令并收到相同key/body的真实202原回执。case04已修正验证脚本：必须等真实重放202及待核对提示消失，不能用SENDING时按钮暂时隐藏冒充完成。

后续失败：脚本读取 `transcript.saved.segments`，但尚未建立人工工作稿的RAW-only状态允许 `saved=null`，抛 `TypeError: Cannot read properties of null (reading 'segments')`。这是下一步验证消费端的准确修复入口，不应重新受理intake或伪造人工稿来满足错误断言。**`tools/load/transport.py` 的 `transcribe_archive` 有同一假设，也必须修复后才能跑容量。**

本批结束时只读SQL已确认（原始证据 `native-cas-04-final-readonly-db.tsv`）：

- meeting `b11bbfcb-1cfd-433b-8b95-e1f7bbb6e1ac`，recording session `497f9972-8fa0-44ee-8b28-9daf7e80976f`，FINISHED。
- archive READY，原文件 `912d7b52-9503-4821-b34c-1b00411e4e57`，SHA `9f3f0ea3246660dc32d2bae5cac4e54bda5fa8d4b6a93a1578d0910c61d1d8ce`，1188898字节、594427样本。
- source READY，独立copy文件 `766d0453-c860-4809-80a8-83a57a982629`。
- import task `e519a93d-1d12-46cb-91ce-5392656a8222`，SUCCEEDED，attempt1。
- RAW revision `3586e924-c532-4a43-8039-7a9ddc501215`，1段，时间0—37152ms，1个源文件。
- 原生报告含21个去重分片、重发帧原SHA及ACK；可继续授权下载整WAV逐帧核验，不能只以数据库SHA代替实际字节读回。

case01登录白页超时（未完成业务步骤）、case02旧hydration缺陷、case03验证脚本使用APIRequestContext访问HTTP loopback时未带浏览器Secure cookie导致401及过早观察重放状态，均原样保留。case04已切回浏览器同源fetch并等待真实202，但其最终RAW读取断言尚待修复。不要重造旧sessionStorage，不覆盖失败目录，不反复新建会议或重提旧intake来绕过验证。

## 用户“继续任务”后的精确顺序

1. 确认根目录/main，读本文件、机器状态、当前验收调整、[BCD统一计划](../superpowers/plans/2026-11-01-bcd-completion.md)和[录音归档接线ledger](../superpowers/plans/2026-11-01-recording-archive-transcription.md)。核对实际source signature与六角色，不默认重建环境。
2. 先修复验证消费者的RAW-only处理：从history取得上述RAW revision，真实GET `/api/v1/meetings/{meeting}/transcript/revisions/{rawRevision}`，按契约核对 `selected.segments` 与source；不要依赖nullable `saved`。补负载探针回归：saved=null仍可通过真实RAW revision读取，错来源/缺RAW/时间不全必须失败，未知受理仍重放原key/body。根因已定位，尚未修改该处；暂停后不自动执行。
3. 对原native-cas-04做新的**只读接续报告**：原WAV完整下载、每帧SHA/样本/完整SHA、RAW来源与时间、回听、持久字幕DONE与尾部/刷新/三视口、第二真实CAS身份403。原pass=false不改；新报告明确链接旧11个检查点和本次只读范围。仅确需验证产品改动时再开新原生录音整例，避免重做已验证的SIGKILL。
4. 修复后的load工具单元通过，再实际短预检：`.tools/local-app/bcd-completion/c-10-50-preflight-30s.json`（30秒，仅预检），使用全新输出目录。尚未运行，不能称通过。
5. 预检合格后冻结部署、负载driver和兄弟配置，跑 `tools/load/scenarios/realtime-10-30m.json`：10/50、连续1800秒、observeSubtitles=true/dropAck=false/longFileRecorderCount=1。全部9000分片ACK、10个归档实际字节和SHA、逐路字幕覆盖及p95达标；采集后至少一份完整30分钟归档经实际HTTP来源准备/intake/生产worker得到RAW，排空和会后处理耗时另列。冻结期间不跑构建/部署/其他验证、不修改产品或兄弟运行配置。
6. 继续完成B与D逐页/逐控件/业务负例/原型证据。矩阵为 `docs/acceptance/b/b-followup-page-crud-matrix-2026-10-30.md` 与 `docs/acceptance/d/page-matrix.md`，仍NOT_ACCEPTED。最新AST清单 `.tools/local-app/d08-page-matrix/controls-audit-04/`：62组件/487按钮，其中425生产候选、62演示/未来候选，**不是487个成功操作**。FuturePanels等仍需按实际路由和分支审查。已存在的桌面8项零像素与390原型自身464溢出的差异须如实处理，不能直接把旧失败签成通过。
7. 全部当前BCD义务有真实证据后再集中单agent自查、更新阶段摘要和机器状态；不等待F50/200或两小时标准，不自动提交/合并/推送。

## 执行方式与避免已知工具问题

- 不并行Maven。普通前台长命令曾在约600秒收到SIGTERM143；长IT与30分钟负载应使用自有detached runner并保存退出文件，不把工具会话结束当测试通过。
- IT辅助：`.tools/local-app/bcd-completion/owned_it_runner.py`；真实连接环境来自上述私有test-database-speech-current.json，harness origin必须8792；已有完整it2无需无变化重复。
- 负载辅助：`.tools/local-app/bcd-completion/owned_load_runner.py SCENARIO NEW_OUTPUT RUN_NAME`，由 `subprocess.Popen(..., start_new_session=True, stdin=DEVNULL, stdout=新runner日志)` 启动；它写本批log和`RUN_NAME-exit.json`。当前没有该类后台批次运行。
- 负载Python必须用 `.tools/recording-load-venv/bin/python` 的词法路径，不能resolve掉venv symlink。真正执行需 `--execute --output-root 全新目录`；未执行的CLI返回NOT_EXECUTED，不能用工具单测替代容量。
- 负载driver会快照worker classes/file jar/driver与scenario，核对开始和结束的工作区签名。不要在冻结采集期间编译或改配置。
- JFR可用Java `jdk.jfr.consumer.RecordingFile` 直接筛选异常。此前把大栈全量导出JSON产生2.4GiB文件，原文件保留，但不要再重复全量导出/一次性json.load。
- 当前没有待确认用户问题或授权请求。暂停仅来自本次用户明确指令；继续指令到来后按本顺序自主推进。
