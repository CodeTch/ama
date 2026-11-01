# B/C/D Completion Implementation Plan

> 执行方式：executing-plans，由当前单agent在项目根目录main执行。用户已明确要求完成BCD全部任务；不创建分支/worktree/子agent，不自动提交，不重置业务数据和失败证据。

**Goal:** 完成B逐页非语音功能/C完整语音和录音业务/D完整业务规则与原型验收，并按原始要求签收有真实证据的范围。

**Architecture:** 复用现有durable_task、当前会议权限和封闭S/A/F来源链；补齐实际生产接线而非独立演示实现。业务、受控协议、真实模型质量、页面像素和容量分别用原始证据判定。

**Tech Stack:** 当前Java21/MySQL8.4/local mTLS文件服务/Vue/Playwright；固定兄弟harness HTTP8792/SMTP2525。

**Spec:** 2026-10-21-ama-phase-b.md、2026-10-23-ama-phase-b-remaining.md、2026-10-30-ama-b-followup-pixel-crud-cas.md、2026-10-21-ama-phase-c.md、2026-10-21-ama-phase-d.md、功能矩阵和架构设计。

## Global Constraints

- 所有命令、计划、修改和验证从/home/tanren/ama main执行；保留既有未提交修改。
- local/MySQL/单机为当前BCD范围；S3、双MySQL、双活和专属恢复依原裁定保留F，不能据此删除BCD共享可靠性要求。
- 100场录音计入300在线、每2秒分片、持续7200秒；保留每个原始ACK/seq/SHA、失败与延迟，不能放大缓存、放宽fsync或删掉失败样本制造通过。
- 临时字p95≤3秒、稳定字p95≤5秒按实际接口观测；用户已明确所有外部联调统一8792兄弟harness，不再等待真实公司端点。模拟器协议/业务结果如实标注，不能据此声称真实模型语义质量。
- 旧请求结果未知时查原key/body；采用/移除/撤回/删除后的夹具不能重当原状态。原型SHA ab21d0b73f4e48bbf701ee8bca5e958940b61f6e6f680d4ced02de0ce4bab867保持。
- 实际容量采集期间冻结运行二进制/部署/兄弟配置；构建与部署顺序执行，结束后再做源码和运行签名对齐。

## Review Focus

1. Worker真实续租发生在图发现与加锁之间，必须完整重新验证，不能将旧权限快照当授权。
2. 3600分片下持续写入/归档不能随历史行数无界退化；所有已ACK字节必须保存且可验证。
3. 语音阶段重试保留已成功阶段且有实际durable输出；无tokens、匿名说话人不虚构身份或字级坐标。
4. 字幕晚到、重复、撤权或删除不能复活内容；已ACK音频不依赖模型成功。
5. 所有可达控件的像素、真实请求、持久化回读、负例应映射到具体证据；扫描到按钮不等于通过。

## Task 1: 当前义务与C07失败复现

**Files:** tools/load/transport.py、backend/adapters/src/main/java/ama/recording/JdbcRecordingPersistence.java、ama/task/RecordingArchiveRuntime.java、ama/transcript/PublicationWriteGuard.java、ama/storage/JdbcFileUploads.java；测试RecordingUploadRenewalIT、RecordingAppendIT。

- [x] 核对main/根目录、当前签名、B/C/D台账、旧7200报告和真实DB当前任务结果。
- [x] 保存可接续统一ledger；创建新独占C测试schema，不用旧失效连接参数。
- [ ] 用真实MySQL/mTLS/queue续租回归复现归档发现→锁定窗口；分别测3600分片写入SQL和100/300短/600秒实际协议。
- [ ] 只对已证明根因做最小修复，目标RED→GREEN，权限/删除/事务回滚负例保持；不得放松期限/图比较/同步落盘。

## Task 2: C01/C02生产音视频流水线

**Files:** MediaTranscriptParser、TranscriptWorkerRuntimeProvider、TranscriptionHandler、HttpSpeechModelPort、现有media/source/profile迁移与解析器；测试MediaPreparationIT、MediaImportOutputIT、SpeechPipelineIT。

- [ ] 审计实际配置与durable任务接线，补齐规范化、VAD/SPK/ASR/标点/embedding各阶段的成功输出、错误/重试和全局额度。
- [ ] 先用缺失生产接线的失败用例，再实现；保持RAW/增强/人工/确认稿及原始来源映射，验证音视频、多文件排序、纯文本和无音轨/坏文件边界。
- [ ] 对固定harness做实际业务验证；获准真实端点另验协议/质量，未提供时如实保留资格。

## Task 3: C03/C06纠错、说话人与持久字幕

**Files:** CorrectionInstallation、CorrectionService、SpeakerService、SubtitleReconciler、RealtimeTranscriber、SubtitleEventPublisher、RecordingWebSocketHandler、对应API/迁移/UI。

- [ ] 复用当前手动替换/撤销已通过实现，审计并补自动纠错和说话人持久化/工作台接线，先RED再实现。
- [ ] 补实时有界窗口/全局额度/持久字幕版本和afterEventId回放；真实ACK作为输入，模型失败不阻断音频保存。
- [ ] 测重复词/数字/跨窗重叠、乱序/断线、后台重启、晚到/删除/撤权、显式增强采用与确认门槛；真实时延单列。

## Task 4: B/D完整页面和控件收口

**Files:** docs/acceptance/b/b-followup-page-crud-matrix-2026-10-30.md、docs/acceptance/d/page-matrix.md、web/src各可达组件、tools/dev/prototype_*及只读/业务验证脚本。

- [ ] 从main.ts真实路由与条件分支归并464按钮清单，剔除演示/未来/不可达项并说明，补齐App/名单/导入/材料/转写/纪要/来源/候选/确认/发布/回看/模板的证据。
- [ ] 原型最终渲染对照1440×900、1024×768及D390×844，逐个收口成功/失败/空/加载/禁用/滚动/固定底栏；原图不可改。
- [ ] 每个真实写操作使用明确当前版本和原请求恢复；刷新/重登/第二身份/冲突和失败有证据后才关闭对应行。

## Task 5: 完整容量与最终验收

- [ ] 变化对应生产Java/Web/type/build/OpenAPI/Python及独占MySQL集成全部按适用范围执行、记录所有失败，不用历史隔离夹具结果冒充当前测试。
- [ ] 通过短/600秒资格后执行冻结部署100录音/300真实CAS用户/7200秒、真实丢ACK和进程退出；全部采集、ACK、100归档和原始SHA回读完成才签收协议容量。
- [ ] 真实模型可用时完成全链字幕p95/质量；没有模型配置时继续其他任务并明确外部依赖，不制造通过。
- [ ] 单agent集中自查，更新B/C/D摘要、逐行证据、运行签名和机器状态；只有全部本阶段义务达到原验收条件才标完成。

## Execution ledger

### 2026-11-01 接续：generation600完整结果与C02生产接线

`bcd-append-100-600s-generation-steady-01/report.json`完整结束：100录音/300真实CAS用户/600.0018秒，30000sent/verified、100实际归档、missing0/fatal0；472录音transient和69后台transient保留。ACKp95=132391.337ms、queuep95=130381.756ms；协议PASS，**不满足实时，未运行7200，不是C07最终资格**。冻结部署签名cb497438a26f61663fcf8b8064ccfb32018214103505692e427faa26568e27d8。全部ACK后才恢复开发/构建，归档排空与开发共享主机已单列记录，不伪称独占资源基准。

C02：核心4项与严格codec3项PASS；兄弟harness完整62项、执行fence专题9项及固定8792实际FENCED→late409验证通过，端口8792/2525不变。HttpSpeech执行observer与原pipeline真实HTTP集成5PASS；超时保留UNKNOWN，完整EOF才结算，404不是释放依据。原失败均保留。

当前新源码（尚未部署）追加v3独立归档、严格引擎声明、V056 checkpoint/规范化文件/全局额度资源账本；真实ImportPolicy私有协作者处理五阶段，RAW仍走原封闭completion，标点/向量/匿名聚类分别保存，不自动替换RAW或人工稿。普通上传32MiB不变；每批规范化音频最多2小时、每clip120秒，真实原SHA/PCM SHA绑定，持久化专属键先提交意图再I/O，删除永久retireKey，重试成功stage及PCM复用。旧v1/v2归档和语义保留。

`speech-config-red.log`因缺v3资源真实RED；首green因归档误加换行违反原CanonicalJson字节门禁而失败，已使用原规范序列化，无放宽校验；`speech-v3-components-green2.log`相关19PASS。`speech-v3-production-it-green1.log`3个完整任务+8输出策略共11项含4失败：真实输出策略漏v3时间戳（已修），取消探针每字符查询造成超时（改为每事务提交检查一次），删除后原队列正确抛AccessDenied（测试改为精确预期），原共享schema旧挂起任务导致跨fixture后端不可用（新独占schema隔离，不清旧数据）。V056由拥有者root迁移、业务仍ama_local，未授SUPER/未改binlog信任；新独占schemaama_c_recording_a10ebab938c7，私有连接参数仅在忽略配置中。

`speech-v3-production-it-green2.log`3项真实任务/恢复/删除PASS。新增worker生命周期维护器只结算资源事实，无法写业务内容或承认任意模型结果；需继续真实UNKNOWN/全局额度/删除负例与新浏览器CAS，再推进C03/C06和完整D。

### 下一实现选择：录音历史校验与语音阶段接线

600秒诊断采集期间仅写源码/测试与进行轻量只读调查，不执行Maven、构建、部署或改变运行配置。现有部署03a715的二进制与原始日志保持冻结。新测试尚未运行，不能记作RED或PASS。

录音：当前一遍历史聚合仍随分片增长。计划增加V055数据库触发器：每次chunk INSERT/UPDATE/DELETE都在同一事务推进所属session的manifest_generation；生产正常顺序仍先锁session后写chunk。每个Persistence实例只保存有界、单part且完整连续前缀的已验证摘要，命中必须在锁内读取当前generation并逐项匹配part与会话；重启/淘汰/任意旧chunk变动/多part/缺口均回到完整校验。所有缓存发布延迟到afterCommit，回滚不能留下可复用证明，防止回滚后generation数值复用。必要邻居/重试字节SHA、总样本/总分片/尾部上限和文件fsync均保留。完整扫描、parts与邻居读取必须使用当前锁读，不能把RR旧快照绑定到新generation。新增真实MySQL测试先验证3601历史后的下一ACK索引读≤24、实例重建重新扫描、缓存热后旧损坏仍拒绝、回滚后同generation不同样本不得复用。先运行RED，再修改产品。

语音：现有v2本地离线ASR保留历史可回读；采用独立归档的v3媒体引擎配置描述固定harness五阶段、模型版本、窗口/时限及输出界限，不能悄悄改变v2语义。生产处理仍从真实import lease、完整授权文件、原SHA开始，在有界规范化资源上运行VAD/DIARIZATION/ASR/ENHANCEMENT/EMBEDDING。技术checkpoint按input/file/PCM SHA/窗口/引擎配置哈希绑定，每个成功阶段不可变；重试复用成功阶段，失败阶段没有成功输出。写checkpoint前重新检查同一队列租约、完整当前域与删除栅栏；技术表不能成为RAW写入旁路，RAW仍由原封闭completion原子提交。原始识别与增强候选分存，增强须显式采用；缺tokens、置信度和真实身份不补造。长音频不能通过扩大普通文件32MiB上限实现，录音归档单独以受管内部流分窗处理。

全局模型额度必须在数据库跨进程协调，并给实时池保留份额；发送前持久准入，超时/断线仍算未知在途，不能随客户端超时释放。harness仅提供受控测试语义，若需要查询/取消证据则同步扩展其可验证协议与测试，端口8792/2525保持。公司能力和真实模型质量不由harness结果推断。

C06拟复用同一受管模型准入和stage存储，采用每part三片（最多6秒）的同一字幕segment：第1/2片分别输出覆盖2/4秒的temporary修订，第3片以同一segment的更高revision输出完整6秒的本地stable结果；结束/换part时不足三片的尾部需补稳定任务。这里stable是本应用已封口窗口，不是虚构平台提供稳定标志，也不声称跨窗口真实说话人聚类。所有文本替换同一segment，按事件/版本去重，不按字符串删重复词。每2秒仍最多一次ASR，但重叠音频秒数必须如实计入模型负载。不可达成实时预算时明确积压并保留会后补处理，不把迟到字当实时。持久window/cursor属于一个录音任务下的技术子工作，不为3600片各建durable_task，避免突破当前完整图256任务上限。输入只能从已提交且已验证的连续ACK范围取PCM；模型出错不回滚音频ACK。事件提交和发送均需当前删除/撤权栅栏，重启/断线使用持久eventId与revision恢复。生产页面应挂载真实LiveTranscript，现有独立helper不是交付证据。

C02具体接线选择（尚待实现）：

- 新归档媒体v3描述固定8792的同步harness引擎、Host、256维fixture embedding、HTTP3秒、120秒/1920000样本有界离线clip。保留v1/v2原始语义与可回读；v3文档/SRT/VTT可复用原已安装解析器但须在新profile中明确固定其模型/解析器manifest。FFmpeg实际6.1.1-3ubuntu5，二进制SHA ed16af623947494a72e284b6eb8ff225f2da22b38b5d5069c2fd4b4ba3384e41；引擎声明和实际二进制应匹配。多文件仍按受理ordinal、来源原SHA和每文件真实时间，不造tokens/企业身份。
- V056计划持久化checkpoint及跨进程模型准入。checkpoint键绑定input/fingerprint、file/source SHA、normalized PCM SHA、clip样本范围和归档引擎SHA；stage类型和payload严格JSON/摘要/配额，成功不可覆盖，候选文字与RAW分开。checkpoint生产writer必须是原ImportPolicy私有协作者，先q.validatedLease完整当前域/任务/输入/文件锁再技术写入；不能公开一个可随意写任意模型结果并被RAW消费的Store。core的公开Store只是端口，不是权限。
- 全局capacity使用稳定的harness服务额度标识，不能用每次profile哈希新建独立池而翻倍额度；本地显式测试配置offline2/realtime保留64/total66，绝不宣称公司配额。准入先持久化，再实际HTTP发送；同步完整响应EOF可证完成，timeout/disconnect保持未知，通过已实际验证的harness执行查询恢复。404不能证明从未执行，不能释放在途；只有端口确证未发送或provider完成/原执行进程确证死亡才结算。quota查询/外部HTTP必须在数据库事务外，入库按统一capacity→call顺序；业务checkpoint另须当前真实queue域，晚到不能恢复删除内容。
- HttpSpeechModelPort新增可选内部调用observer：在有界body准备后、request.call前签发唯一executionId并加header，记录body SHA；完整响应EOF/未发送/未知分别回调，保留既有无observer兼容和并发/超时测试。全局debt表仅保存nonce、摘要、路径与状态，不能保留识别原文或阻止删除源内容；敏感checkpoint随meeting/input清理，未知外部配额事实不能被删除操作当成已完成。
- normalizer产生真实可重读有界WAV/PCM SHA；v3处理音频时先校验下载元数据/原SHA并持有SourceReadLifetime。普通文件32MiB限制保持；两小时录音归档需独立ASR输入/RECORDING_ARCHIVE来源权限和内部有界流，不能冒充普通上传放宽所有用途。此项仍未实现，C02/C06最终必须处理。
- 本地安装沿LocalDocumentSetup/LocalMediaSetup归档发布链追加v3，保留旧archive并仅切换明确默认配置。media provenance/ConfigBindings/manifest、输出策略和保存/确认图都需认识v3，不能仅改能力列表。所有新增写入仍通过原封闭RAW completion，不直接修改当前WORKING/已确认稿。

2026-11-01：用户明确扩大为完成BCD全部任务。当前无长测运行。旧长测88错误=15 QueueFull+71归档ValueError+2归档HTTP403；原报告保留。当前DB回读85个原归档任务：68SUCCEEDED、17FAILED（14准备失败、3attempt limit），不能回写原长测PASS。现有配置仅本地8792模拟器，真实模型位置问题待用户回复；其余工作继续。

独占夹具初始化两次失败均非产品RED：首次环境变量USER应为USERNAME；第二次新schema默认bin与冻结V010要求的原始ai_ci继承不符。保留原空/部分初始化schema和日志，不改迁移或清理旧证据，重新建立符合标准的独占ai_ci schema再运行。

2026-11-01 归档真实竞态：archive-discovery-red3最终11项中4个新增用例均PROVENANCE_UNAVAILABLE，另一个继承Range夹具403。四个新增用例在真实完整图发现后执行queue.renew，覆盖begin前和begin/part/complete物理返回后的发现窗口。修复为存储专用完整任务并集变化信号→事务回滚→同一预算/期限内完整重新准备，最多3次；未接受旧图或重复物理RPC。archive-discovery-green1目标4项全部PASS。新加持续变化3次封顶/无nativeIO和删除负例；Range403已定位旧测试仅伪造session属性，未经过新鲜流读取要求的真实SessionSecurityFilter，改为真实JDBC会话+真实filter，不降级产品检查，正在组合回归。

原来缺失的锁定离线ASR已通过仓库install_local_asr恢复：faster-whisper-small固定revision536b0662742c02347bc0e980a01041f333bce120，manifest97b01ed2c124f97d7f30969d563b66fcba2a42e2511c50eb91dcf4409b11b303；仅安装，不是识别/质量/实时通过。当前产品修改未部署，无容量测试运行。

2026-11-01 接续：用户已明确所有外部接口均通过/home/tanren/ama-integration-harness调测和按需同步修复，固定8792/2525不改；不再询问或等待真实模型配置，持续推进直到D全部完成。归档续租组合26、相关录音/媒体/语音MySQL集成67、生产Java854全部PASS。归档修复尚未部署。

C07写入实际RED：新3600历史单part测试使用MySQL本会话Handler_read_next测量server索引扫描而非源码断言，当前每ACK7202行超过一遍完整校验+有界邻居4000预算。最小修复复用同一完整校验SQL的min/max/count证明单part完整前缀；有缺口/多part保留原完整frontier，未省略历史损坏验证。完整RecordingAppendIT回归进行中。当前容器2CPU/768MiB、buffer pool128MiB、fsync事务=1和sync_binlog=1，尚未改资源。

C07接续：append-server-work-green全16PASS，3600历史MySQL实际索引读3602（原7202）；生产Java854再次全绿。正常部署eea3de526fb92966b82d87f2c81624973d208c9921eb042852ac0ed03101b1f4，当前DB映射端口46431（旧46443记录仅历史），pool32/file32不变。native bcd-archive-append-hard-crash-01实际10PASS，含SIGKILL、原PCM恢复、逐帧SHA归档和回听。100/300/60s bcd-append-100-60s-01在130秒全部3000ACK，之后归档仍排空中，暂不签收。

Ruling：100/300短测采集与全部ACK结束后允许记录共享主机的测试/媒体功能验证；不把其归档期间资源计为独占容量资格。未变更该运行冻结的API/worker/files。线程采样显示26个文件线程卡在native fsync，包含对相同scope/key的重复临时发布；不是仅由MySQL扫描造成。LocalImmutableReplayIT实际RED（精确重试仍3次LINK）；修复先验证完整新输入和实际旧文件摘要/大小，再FILE_SYNC+DIR_SYNC/root、路径inode复核和卷guard。只消除无效临时副本，必要fsync保留。LocalDurabilityIT逐操作失败/实际进程退出及32并发组合回归进行中。

D08：Published TranscriptForm新实际渲染RED（回看仍显示批量替换等工作台控件）→独立已发布只读展示，保留真实时间定位、无时间戳不造时钟；转写视图4项PASS。父页面完整布局/原型对照仍需继续，尚未视觉签收。

C07短测最终结果：bcd-append-100-60s-01完整100/300，3000sent/verified，100归档逐帧SHA，missing0/errors0，2885transient全部保留；ACKp95=73291.436ms、queuep95=71291.549ms。该60秒协议报告PASS，仅为短预检，长测/实时资格没有关闭。LocalImmutableReplayIT+LocalDurabilityIT+32并发全42PASS，新重放路径未部署。

C01真实媒体503根因：JFR实际API异常栈定位JdbcTranscriptAuthority.prepare→ConfigSchema.manifest第95行，v2生成清单时仍只识别v1而写IMPORT_TXT。MediaImportOutputIT两项真实受理新回归：v2 PROFILE_UNAVAILABLE真实RED，v1完整受理/同键回放PASS。修正使用MediaTranscriptParser.supportsProfile后全6项进行中。原浏览器media-cas-01的503、media-cas-02的创建前会话加载竞态、media-cas-03的响应体采集失败都保留；fixture514f5f89-6335-48f5-8889-f4e0aae3e7a1原请求key/body已保存report，不新键重提它。新脚本先等前端真实退出链接（会话已加载），只在非202时采集响应体；后续用新输出/新会议验证。

D08当前Web总回归486PASS/1FAIL，失败为旧meeting-detail-progress测试仍要求已发布页5步骤，与原型已发布详情冲突；已将断言改为无步骤且冻结结果保持挂载，定向与转写共5PASS。完整Web需在余下样式收口后复验，不能将这次全量写全绿。

新部署5bcf13b1d8c062cf5b4fc01351f7c565a5f213a5b1039fd9498d22ae0083f3b8（V054、main、DB46431/pool32/file32/8792）。MediaImportOutputIT全6PASS，生产Java854/Web487/type/build全绿。media-original-recovery-04：用media-cas-03原meeting514f5f89、原key及原body实际重试返回202，真正Worker离线ASR SUCCEEDED；读取唯一RAW、源SHA与真实时间范围、中文UI全部通过，识别合成参考英文完整四句。不是公司模型质量或完整C02。正在全12格式+坏文件/无音轨验证，另采当前已发布页面原型无mask差异基线。

C01 media-cas-04报告15/15业务PASS（12格式WAV/MP3/M4A/MP4/MOV/MKV/WEBM/AAC/FLAC/OGG/M4V/AVI+损坏/无音轨/第二文件损坏整批无RAW），含全部SHA来源回读；包装命令最终143，业务报告与结束问题分别记录，不能声称该命令exit0。后续长脚本改脱离PTY运行并记录teardown，当前没有容量运行。

D08发布页：加入从真实materials READ端点读取的只读抽屉，晚到/切换/503/403清空回归2PASS；知识库链接使用真实publication→library-context链，晚到lookup不能请求旧scope、撤READ清空回归2PASS。完整Web491/type/buildPASS（尚未最后CSS收口）。首次published-pixel-01六图差异保留；pixel-02-hmr报告显著缩小但仍非零，且明确sourceAligned=false/qualifying=false。已发现旧编辑容器类触发原型CSS隐藏topbar-right、am-header-group带编辑工具条边框/行高，正在移除已发布态的编辑容器/工具并复核。纯文本原型会假造00:00，产品采用禁用--:--并保留无音频说明，不能为像素写假时钟。新组件写入结束标记错误在导入之前纠正，测试实际结果才记入记录。

D08真实音频发布夹具：media-cas-04的AAC会议60868165-95f2-409e-9d84-ab4d8b78b339已完成真实RAW→WORKING/确认、harness生成、显式审阅、最终确认和发布；aac-published-01报告PASS、browser-closed.json确认关闭，后续只读，不再当待生成会议。AAC浏览器实际解码readyState4/duration7.664646/errorNull。源码前端492/type/build全绿。

发布页原型对照：aac-pixel-01-hmr 478像素，02-hmr 21像素，03-hmr在1440与1024的core/body×viewport/fullPage八项全0，无mask/容差/图像替换/参考DOM或CSS改动。原因为旧编辑布局的多余包裹产生独立渲染盒；只读包装div使用display:contents后消除实际渲染差异，未换浏览器参数。参考通过原型自己的acBlob接受真实经权限和SHA核验的AAC输入，正文映射其原生body-v2/fullText字段，未改renderer。报告明确sourceAligned=false/qualifying=false，正在构建/正常部署后正式复验。390原型header横向溢出导致fullPage宽464，产品保持390内可用；此差异单列，不能称390零像素。纯文本--:--与原型假造00:00差异保留，不能为零像素编造时间。

D08正式复验03a715be52682e536acf2a71c2f43e6d3576aa7c76ec1db769c46ff7c8824faf：完整Web492/type/buildPASS。owner-01来源超时原证据保留；进一步实际probe确认首屏之后权限复核期间loading短暂为true而非永久卡死，入口缺少禁用语义。增加aria-disabled与相同现有guard一致，验收等待真实可用状态后owner-02三视口全部PASS，来源HTTP200、正文/材料/导出格式/无写入均通过。member-01三视口PASS。aac-pixel-04-aligned与部署源码签名一致，1440/1024 core/body×viewport/fullPage八项全部0像素；390保留原型横向溢出与产品适配差异，报告总pass=false，不伪称所有视觉门禁通过。当前开始同签名原生SIGKILL录音恢复，随后600秒实际协议诊断。

C07 replay-steady-01最终协议PASS：100/300/600.001秒，30000sent/verified、100归档SHA、missingACK0、fatal0，318录音transient及52后台transient保留；ACKp95=64673.299ms、queuep95=62674.602ms，不是实时/7200资格。655.237秒所有ACK后才启动隔离开发/测试；归档排空与开发共享主机记录单列。

录音generation缓存：append-verified-generation-red真实下一ACK扫描3603行超24预算；V055 INSERT/UPDATE/DELETE触发session generation，进程内1024摘要仅事务提交后发布，跨实例/淘汰/损坏/缺口/多part回完整校验，必要fsync不变。green1迁移被运行账号SUPER限制拒绝，保留原2d4dcccfa312部分迁移及日志；未开trust_function_creators/未加运行账号权限。新独占schema7dfcba1e0817由本地管理员完整45迁移成功，runtime仍ama_local。local_app迁移单独root、服务仍原角色，本地工具19项PASS。green2的2个新增测试夹具错误（应Gap异常、nextPart前必须pause）已修正。专门移除当前锁读后两项实际RR RED：旧writer epoch返回1而非2、聚合旧行与新邻居不一致；随后恢复当前锁读，green3完整22/22 PASS，下一ACK实际Handler_read_next=2，实例重建重新完整扫描、旧行修改/删除、回滚代数复用和两项RR均保持。生产Java858/Web492/type/build/API75全部PASS，相关13组集成完整PASS（计数见generation-related-it.log）。修复尚未部署；当前600旧测试已全部结束，可以正常部署后做native和新600诊断。

D08来源弹窗：诊断脚本在03a实际复现已发布仍显示提交全文审阅（aac-source-readonly-red-01），产品现在隐藏写控件并保留只读审阅历史；首次HMR绿色尝试因归档负载下权限复核503清空了页面，报告保留，不称通过。正式脚本已要求来源200、写控件0、原审阅历史200，待正常部署后正式复验。

C02核心SpeechPipeline新4项RED缺类→GREEN，成功阶段不重跑/保存失败不进入下一阶段/错stage与越界拒绝。此时仍未生产接线，不据此关闭C02。兄弟harnessmaster未切换分支；新增受控X-Ama-Speech-Execution跟踪+GET/v1/harness/speech-executions/{id}，仅保存请求/响应摘要和真实完整响应结束状态，客户端超时不结算；Linux同boot/pidnamespace真实owner进程结束可证TERMINATED，否则保留未知。原4项真实RED后当前实现及新增流式/取消/实际进程终止回归进行中，固定端口8792/2525未动，运行实例尚未重启此新增接口。需继续全局额度、durable技术checkpoint、v3完整生产流水线，再C03/C06及完整D矩阵，不能停。

2026-11-01 C02持续推进：实际资源回归中的404→真实FENCED→迟到409已PASS；全局额度首轮遇到真实续租期间PublicationGraph锁定图不一致，早返回IMPORT_PREPARATION_FAILED而非进入模型准入。diagnostic3/4/5保留；JFR实际栈定位PublicationGraph.lock→ImportTaskDomainPolicy.capacity→JdbcSpeechCheckpoints.current→JdbcSpeechAudio.open（非推测）。新TASK_PREPARATION用途仍完整锁定当前图/全部来源/真实租约，发行者、实际事务、完整spec和purpose全部绑定；负向SnapshotChanged只触发最多3次完整回滚重捕获，不接受旧图，不改变RAW完成使用的TASK_MUTATION。checkpoint/规范化文件/准入只消费准备用途，RAW completion保持原封闭通道。

`speech-v3-production-it-green6.log`完整5PASS：三项任务/新attempt阶段与原PCM复用/删除；两项实际资源协议，包含两条30秒延迟的真实HTTP超时STARTED在跨队列及删除时持续占用offline2、不借用reserved64、实际COMPLETED摘要相符才结算。JFR启动输出导致诊断fork native-channel warning，Maven exit0与5项报告均明确，不能将warning记为产品故障或抹除。`speech-v3-preparation-capability-it.log`新增完整负例1PASS，准备capability不能冒充TASK_MUTATION或RAW完成，但合法生产任务仍成功。`speech-v3-output-policy-it-green.log`8PASS，v3实际受理与时间来源、旧v1/v2兼容、纯文本不得造时间和外源拒绝保持。生产Java全回归正在执行，正常部署/浏览器尚未做；C03/C06/两小时ASR及D矩阵仍开放，继续。

2026-11-01 C02正常部署d3cf2d7e8cf07ba1ea9e2a13b69a2ff1cd4f9516da4873d38b8c3f4563f22c4b/V056/六角色，完整生产Java863PASS（首轮4个WorkerShutdown反射旧构造错误保留，更新并新增维护器关闭失败仍关闭两backend负例后全绿）。真实CAS WAV1PASS，排序/字幕5PASS。十二格式连续case10PASS后M4V任务/RAW/源均成功但UI导航空白，原失败不改；原M4V新的只读4PASS、AVI独立1PASS完成12格式覆盖，不称原连续报告PASS。C03契约RED缺类→GREEN2四项，candidateId/decision摘要绑定、无任意文字输入、旧SAVE hash golden保持；业务尚未接线，新命令显式503且无HTTP入口，不当作交付。C03详细接线记录见c03-speech-review-next.md；C03/C06/两小时独立ASR来源/D完整矩阵仍开放，继续执行。


## 2026-11-01 当前批次结束：按用户要求暂停（最新状态）

用户最新要求完成当前批次后停止并保存接续信息，此要求覆盖前文“D完成前不停”。**本批已收尾，无下一批正在运行。** B/C/D仍NOT_ACCEPTED，恢复入口为 [bcd-pause-2026-11-01-speech-review.md](../../development/bcd-pause-2026-11-01-speech-review.md)、根DEVELOPMENT_HANDOFF首段与机器状态 `.tools/local-app/task-current-status.json`；机器状态PAUSED_BY_USER。收到“继续任务”直接按新文档顺序接续。

C03后端本批已接线：V057候选/原键批次（仅新测试库已应用）、真实S/F快照/预算/模型摘要核验、显式proposal/read、CORRECTION接受/拒绝、人工修改STALE、立即UNDO/UNDONE、S和F待处理确认门禁、原batch/决定receipt恢复、不变RAW与meeting物理删除。生产配置仍DISABLED_NOT_INSTALLED，无新增HTTP/OpenAPI/SDK/UI/实际安装接线，本批代码未部署。

最终证据在 `.tools/local-app/bcd-completion/`：`c03-backend-batch-final-it1.log` **5PASS**；`c03-backend-batch-production-java.log` **865PASS**（0失败/错误/跳过，仅生产模块）；契约green2 **4PASS**；兄弟 `c03-harness-binary-full-green.log` **64PASS**；固定8792/2525真实重启证明COMPLETED摘要/FENCED/迟到409 PASS。当前批次未改前端，未把旧Web492/type/build冒充新重跑。所有首次RED/预算失败/迁移前置失败及原连续十二格式FAIL保留，新证据不改写旧失败。

运行仍d3cf2d7e8cf07ba1ea9e2a13b69a2ff1cd4f9516da4873d38b8c3f4563f22c4b、主库V056/46431、pool32/file32/standard六角色；当前源码825dc9b3d18781b1935894d1502bb6bb3a3894854088ad0d72140c551fd79037。独占测试库1737f5dfd77a已完整47迁移至V057；057 SHA `01cac8bcebfe72faf9a08b898ac88323bc82fdf8d24c9eb60e3745ca1866ca47`，后续不能改已应用脚本。兄弟master/PID1893722/固定8792与2525，不是企业权威。无测试/浏览器/负载进程，本地常驻服务保留。

下一批先补必要多候选/并发/治理/损坏/预算/原键负例，再C03 HTTP/严格契约/SDK/运行schema/UI journal与正常部署CAS；然后版本化说话人、C06持久字幕、独立两小时归档ASR、B/D页面按钮原型矩阵和冻结容量。100/300/600协议PASS但ACKp95=132.391秒，仍不等于实时或7200。S3/HA等按R2留F。不切分支、不建worktree/子agent、不提交/重置、不清理旧证据。
