> 当前范围已按用户最新要求调整：仅桌面端1440×900、1024×768；所有手机端开发与验收项已移出，不属于F待办。历史多视口报告保留原文，不再作为手机端门槛。详见 [桌面端范围裁定](../../development/2026-11-02-desktop-only-scope.md)。

> 2026-11-01 最新长文件验证裁定：当前C阶段仅要求30分钟长录音文件（28800000样本、57600044字节、900分片、15个120秒窗口/75 checkpoint）完整归档→独立受管转写来源→RAW；两小时长文件测试标准后移F。生产既有有界上限和旧失败证据保留，不靠扩大普通32MiB上传绕过来源授权。

> 最新阶段与容量裁定（用户2026-11-01最终更正）：**C阶段**以10场会议并发、50人同时在线、持续不间断录音30分钟（1800秒）验证；10名录音用户计入50人，后台用户40。场景tools/load/scenarios/realtime-10-30m.json，CLI load realtime-10-30m。**50场并发/200人在线验证移到F阶段**（后台150、沿用7200秒场景realtime-50），不阻塞本轮BCD完成。每2秒分片、ACK/原始SHA完整性和字幕temporary p95≤3秒/stable p95≤5秒保持；D沿用其业务/页面验收义务，不另设该容量门槛。下文100/300及旧50/200计划/报告保留历史，不自动折算为C或F已验收。

# 录音归档独立转写来源接线计划（C验证30分钟，两小时标准归F）

父计划Task3/5；在C06生产接线验证之后执行。用户授权main/单agent自行选择，不发起确认；外部仍固定兄弟8792/2525。此文是设计与执行约束，不是实现或验收结果。

Ruling: 以真实READY录音归档为唯一输入，生成独立、受管、同SHA的TRANSCRIPT_INPUT副本，再复用现有封闭intake/Worker/RAW/来源读取链。普通导入沿用32MiB解析profile；独立录音profile只接受服务器核对过的归档副本，最大230400044字节/115200000样本/7200秒。理由是现有INPUT_BATCH_FILE引用策略要求真实TRANSCRIPT_INPUT upload来源；不通过改purpose、假造upload_session或放开任意archive文件来绕过它。代价是显式请求转写时增加一次有界流式复制和存储；仅保存录音不触发复制/模型。

1. 先真实测试RED：已finish归档READY→仅复制真实归档，原输入不可变/SHA相等/完整EOF；重复请求/进程重建恢复同upload与分片；未READY/非当前archive/第二身份/禁用/删除/变epoch拒绝；当前验证30分钟57600044字节实际流经受管mTLS；两小时230400044字节标准后移F，普通32MiB解析限制保持。
2. 新迁移保存recording_transcript_source技术job：原archive/current input/session/meeting/owner/epoch、原file/hash/bytes/samples；固定新upload id/稳定key、copy file ID、lease attempt/owner/expiry、state/error和timestamps。每archive唯一，所有内容随meeting清理，不能提前结算未知I/O。迁移首次成功应用后固定SHA。
3. 显式POST准备来源，GET只读进度；每录音唯一可恢复job、有界线程调度，无任意存储键/任意文件ID/任意purpose选择。每次开始/每part/F都重新确认当前owner/meeting/lifecycle/epoch、同一READY archive及完整来源。真实FileUploads.beginPinned/part/complete；副本分片≤8MiB，复制SHA/EOF/长度必须逐项相同；未知ACK重用稳定原upload/key/part，不另造文件掩盖失败。
4. 注册独立不可变录音processing/parser profile：原v3不改。实际来源copy表是放大上限的必要能力，profile与输入fingerprint绑定；ordinary intake仍32MiB，录音仅单文件、实际样本≤两小时。Worker总有界流可读230400044，旧profile自身32MiB约束不变。FFmpeg和speech checkpoints保持120秒窗与64/2全局额度，成功阶段不重跑。
5. 只有技术来源READY后才使用现有PROCESSING_IMPORT原键命令受理输入；真实新input_batch/transcription_input/源FileRegistry引用/queue与根版本一次事务。准备进度不伪造业务成功；页面原命令未知时恢复原请求，不另发新键。最终RAW严格来自完整归档ASR，不将实时字幕直接转成会后RAW。
6. OpenAPI/SDK/运行schema/真实页面：归档状态READY后提供开始转写/读取进度/重试，显示真实受理/任务失败；保存并处理流程自动显式准备后受理，保存只归档。权限改变清空私有来源，刷新/第二CAS/三视口验证。
7. 生产Java/Web/type/build/API、实际MySQL/mTLS/固定harness重跑并正常部署；完整原生录音→封存→归档→独立ASR→RAW→确认来源图浏览器证据。30分钟来源流验证与C阶段10/50/1800实际持续采集资格分别记录，F接收两小时文件与50/200容量标准，不混为一项。

保留现有全部失败和旧请求，禁止切分支/worktree/子agent/自动提交。继续BD完整矩阵和冻结容量，不在本局部结束后停下。

当前进展：真实RecordingTranscriptSourceIT先缺JdbcRecordingTranscriptSources编译RED（recording-source-production-red1.log），后实现真实owner/epoch/current READY archive/queue SUCCEEDED证明、独立复制job、稳定upload begin/part/complete、每part重新授权/租约、完整源SHA/EOF与最终copy receipt核验、只读进度与显式调度；API/页面/长profile尚未接线。V060独占新库ama_c_recording_3b64241f569b已成功50迁移，SHA `54fd3408912dbe5bd3c9d73fce68277696be4e0d58ace17ce09ae652cb9743cf`，不能再改。主库仍059，当前测试配置指向3b64241f569b。首实际两项1PASS/1FAIL，失败仅断言Long48和BigInteger48类型不同，复制本身READY与SHA已通过，已修数值比较；越权/删除/清理完整PASS。新recording-parser-v1 / recording-processing-v1独立配置已写，StrictConfigArchiveReader现不支持该身份，真实配置RED正在执行。普通v3字节未改，不能将当前源码当部署。


recording-source-production-green2.log 原两项真实MySQL/mTLS/queue归档/副本/完整回执/重启/越权/删除全部PASS。录音专用配置strict archive身份缺支持真实PROFILE_UNAVAILABLE RED→实现后录音+旧v1/v2共5项PASS（recording-profile-config-green1.log）；进一步完整binding和错误TXT step测试已写待跑。源码新增core RecordingTranscriptProfile(PARSER ama.recording-wav.v1/PROCESSING ama.import-recording-wav.v1)，普通旧profile bytes未改。新增源资格proof“同字节普通COMPLETED上传不能冒充录音副本”和真实part提交后ACK丢失复用原upload负例，正在origin RED编译（readyCopy helper缺失预期）。helper草案/tmp/recording_source_helper.txt，尚未并入产品。

后续worker设计收紧：保留Settings.aggregate普通上限，引入可选独立recordingSourceBytes（四参数旧构造保留），仅录音profile启用230400044预算；normalizer也仅在真实准备租约/current ROOT下验证READY origin proof后扩大。不能直接放大公共worker aggregate使普通批次绕过总限额。API默认v3配置保持，新录音配置通过独立recording-config-version/sha选择。


source-origin-green1四项PASS，含真实part后回复丢失重用原upload/part与普通同字节上传拒绝。完整归档ASR新用例先actual PROFILE_UNAVAILABLE RED，随后接线Media支持独立recording parser、authority prepare/reserve及ImportTaskDomainPolicy S/F真实READY origin proof、Settings独立录音上限（旧四参数保留0禁用）、pipeline/checkpoint/audio/review支持原+录音profile。首次green1真实任务FAILED/MEDIA_INVALID_MEDIA，定位FfmpegNormalizer.Limits仍硬性32MiB而新profile传230400044预算；普通Limits构造保持原32MiB，新增显式recording容量工厂并由已有真实origin准备门禁调用。新actual解码单测缺recording工厂RED正在执行，旧默认不放大。

下一接线：ConfiguredProcessing读独立recording-source-bytes而不扩大ordinary aggregate；LocalSpeechSetup append独立parser/processing保持v3默认，同时返回独立recording-config-version/sha和新index pin；ProcessingConfiguration仅对真实ready copy选择录音processing且放大该单文件的预算。完成正式API/runtime与页面的显式准备/进度/原import key恢复，不能把复制READY当RAW。拟提供session路径及meeting当前录音来源发现，避免其它设备/processing页依赖本机IndexedDB。最后实际两小时230400044流/完整harness流水线与RAW/历史确认图验证，再BD/冻结100/300/7200。

FFmpeg显式recording容量+旧32MiB上限+strict recording/旧v1/v2及full binding新10项PASS（recording-normalizer-profile-green1.log）。完整ASR green2已经实际5个checkpoint成功，RAW completion发现ImportTaskDomainPolicy的timed membership仍只有旧3种profile；新增精确第4配置，不放开未知配置。green3转为真实claim前FileContracts.Unavailable：旧失败输入的私有backend已不在新夹具catalog，尚未到新业务任务；原库和原任务不清理。新双catalog回归recording-import-claim-starvation-red.log再次真实RED。Ruling: 在现有skip=true候选领取窗口，对FileUnavailable与既有NotFound一致返回false，允许后续候选；非skip准备/完成仍原异常，禁止切后端/伪造内容/修改旧失败任务。正在recording-archive-asr-and-origin-green4.log六项实际回归。

ConfiguredProcessing已读取独立recording-source-bytes（默认0不扩大普通aggregate）；LocalSpeechSetup追加独立录音配置并返回新index pin/独立recording-config-version/sha，v3默认保持；ProcessingConfiguration只有真实readyCopy才选录音profile及单文件230400044预算。以上新增生产安装代码尚未正常部署/实际API验证。


六项归档转写完整IT已PASS（recording-archive-asr-and-origin-green4.log），包含真实五阶段→RAW、origin拒绝、lost part恢复、backend不可用候选公平领取。新的两小时来源stream测试在新独占schema327536fe2506进行（recording-two-hour-source-stream-it1.log），实际3600片/230400044字节/60窗口/300checkpoint，fixture只64KiB逐块生成、真实录音ACK/归档队列，archive与ASR都有真实20秒租约续租；该测试不是持续采集容量。不能并发启动另一个Maven。当前session36522。

新增严格source OpenAPI/SDK及83运行schema、TS client缺模块RED→2PASS（recording-source-client-green1.log）。HTTP probe/test已写，期待RecordingTranscriptController/Runtime缺类RED；**等待当前长来源IT结束后才运行该RED**，避免并行Maven。HTTP测试同时要求current meeting只读发现/未归档不建job、显式空body准备、不接受任意fileId、READ403和无RAW；另ordinary公开uploads超过32MiB应413且无upload记录（当前产品尚未该额外metadata门禁，预期实际RED，内部beginPinned不改变）。没有实现controller/runtime/readCurrent/前端composable/UI。


两小时首次stream真实失败保留：recording-two-hour-source-stream-it1.log，耗时约16分，3600片/230400044真实归档与副本/60PCM窗/300checkpoint完成，RAW F被PROVENANCE_UNAVAILABLE拒绝；不能签收。旧默认@TempDir文件已按测试框架清理，数据库/日志/失败报告保存；后续该测试改CleanupMode.NEVER保留私有文件证据。新2窗JFR诊断（print须--stack-depth256，CLI默认5帧不是完整栈）定位真实PublicationGraph.lock→RecordingArchiveRuntime PREPARED_FACTS与心跳续租撞窗口；初始F也曾撞窗口。实际生产WorkerLoop.dispatch本身会DRAINING_RENEWAL再DISPATCH，夹具已正确模拟该阶段，不延长20秒lease。

生产归档技术准备现在独立TASK_PREPARATION（actual lease/owner/root/complete source不变，publicationStorageChanged完整回滚重捕获≤3），最终queue completion仍MUTATION并要求真实WRITING scope；没有把准备cap当完成cap。新归档candidate在真实owner/root下核对固定backend，skip=true遇未配置历史route跳过而不切后端；非skip仍失败。原失败库旧task保留，新增pending archive route公平性回归。

recording-source-http-and-two-window-diagnostic5.log 共11项全部PASS（ArchiveTaskIT7+source4）：真实2窗/10checkpoint→RAW、current meeting只读source API/显式空body/unknown file字段拒绝/第二身份403/no hidden RAW、公开TRANSCRIPT_INPUT metadata>32MiB真实201 RED→413且无upload，及pending archive不可用路由不饿死新任务。实际HTTP runtime/controller已接线未部署；readCurrent无归档使用只读存在性而非在嵌套事务catch Conflict造成UnexpectedRollback503。

前端source client2/controller5/panel1+RecordingPage2实际10PASS（recording-source-mounted-web-green1.log），typecheck2PASS；新controller原fixture语法错误日志保留，修fixture后缺模块真实RED2→GREEN，不将最初语法错当产品RED。SourceAPI83 schema，关联task只是导航观察而非receipt，原import未知仍重放原key/body。RecordingTranscription已挂原生finished页和processing页，开始转写后真正复用intake/RAW；保存并开始转写显式携带transcribe=1意图。离开未知请求有guard，scope/actor晚到清空；source技术准备自然幂等，intake独立private journal。下一：完整Web/生产Java/配置与隔离负例、两小时源新重跑（要JFR且分阶段日志）、正常部署/API/CAS真全链，然后完整BD/50-200-7200。


用户最新将当前长文件验收缩为30分钟、两小时归F。it2真实3600分片和230400044字节归档已经完成，其后收到范围调整，已SIGTERM仅停止自有测试进程组2468559；原日志/JFR/数据完整保留，状态CANCELLED_BY_USER_REVISED_ACCEPTANCE，不签收两小时。新C测试thirtyMinuteArchiveStreamsToIndependentRawWithoutAnUploadLimitBypass，900分片/57600044字节/15窗/75checkpoint；F两小时原测试保留，显式AMA_RUN_PHASE_F_TWO_HOUR_SOURCE=1启用，默认SKIP且不冒充通过。

当前组合it1出现ArchiveTaskIT retry测试第3次单次claim为空（真实RED，旧日志保留），其余已观察Archive6/7及SpeechReview14PASS。JFR例外13:59:09.917060874Z，数据库该任务READY/attempt2/available_at13:59:09.379528；仅此不足判定时钟根因。发现夹具用客户端Instant算数据库available_at且将非阻塞SKIP LOCKED返回Optional的claim当一次必成；改为数据库TIMESTAMPDIFF读剩余期限，并有界3秒真实poll，同时保存任何空claim的DB时刻/剩余期限，不修改生产queue/available_at/尝试次数或权限。后续需定向实际复验，不能先声称修复通过。


recording-thirty-minute-source-current-it2 完整detached实际Maven BUILD SUCCESS/exit0：69总、68PASS/1SKIP/0FAIL/0ERROR；Append23、Archive7、SpeechReview14、source12(11PASS/两小时F用例1SKIP)、Resource2、DurableSubtitle11。30分钟来源900真实分片/57600044字节/15PCM窗/75checkpoint/RAW1800000ms，elapsed243s、renewals86，普通60s/recording600s独立配置实际工作；完整来源处理超过60秒，证明普通期限不足，未放宽普通输入。Settings预算2项RED缺API→GREEN，source由真实队列保留facts选择deny-only期限，原manifest/profile/READY origin验证与封闭S/F不变。原Archive单次claim夹具在it2观察eligible任务仍可暂时为空（DB remaining_micros=-1388192），有界真实poll后正常第3attempt失败和显式重试成功；未改生产queue或backoff。前台it1 SIGTERM143与JFR诊断保留，不合并为该成功报告。下一步生产Java/正常部署V060/新native丢ACK同键恢复→并发短预检→C10/50连续1800秒及一份完整归档经实际HTTP生产worker到RAW，继续BD收口。


生产Java878/0失败全PASS。首次正常部署V060失败保留normal-deploy-v060-01，JFR精确归因ArchiveReadState.dependencies→ControlledArchiveReleaseWriter.publish→LocalRegionMinutesSetup；新录音配置使完整58档案依赖258，原256边预算不足（非档案数量不足）。仅本地安装辅助类统一LocalArchiveBudget.READ依赖384，其余64档案/1MiB/深度等逐项沿用原值，通用ControlledMinutesAdmin.READ仍256不变；local_minutes运行配置同384。Python实际RED256≠384→完整53项49PASS/4SKIP。正常deploy02已成功，主V060、AMA签名0c2e4f5bfc205a3a3c57d82ed613efe9cc3faeef02820b17518c4b1166103a9b、全部6roles就绪，最终catalog f28a63e481c989b7a649477cd9ac0ded6da4dd5e01fc48da1bb8cda30d4b4ff0，58档案/258边，固定8792/2525不变。

native-cas01首次登录白页超时（0业务步骤/无pageerror），后续冷探针完整登录页正常，原报告保留，不称冷启动资格通过。native-cas02新版本真实PCM/字幕/实际SIGKILL缓存与原字节恢复/刷新新part/暂停续录/完整finish和archive→copy→intake提交后响应丢失均实际通过；在同页刷新后原请求按钮消失而失败。根因application.ts新增recording journal身份watch在session hydration时把尚未解析的meetingScope=null当显式离开清理，组件级恢复测试没有覆盖应用watch。真实RED application hydration EMPTY≠RESTORED；修为像现有minutes/transcript一样先仅同步身份、路由提交后才同步meeting作用域，显式logout/跨actor/离开仍清理。完整Web521/type/build/OpenAPI83一致PASS。旧cas02不改为PASS、不重造其sessionStorage，原API请求/receipt保留；需要新部署后cas03完整浏览器原键恢复。


暂停终态：用户要求当前批次结束后停止，native-cas-04已结束。真实11检查点（含刷新重放原请求且实际202回执相同）通过，后续验证脚本错误访问RAW-only状态nullable saved.segments而FAIL。当前业务DB只读确认归档和copy READY、import task e519a93d-1d12-46cb-91ce-5392656a8222 SUCCEEDED、RAW3586e924-c532-4a43-8039-7a9ddc501215。未修该新验证脚本问题、未启动短预检或并发长测。load transcribe_archive也须改用history RAW revision.selected；下次先只读接续原04，旧pass=false保持，具体见docs/development/2026-11-01-bcd-pause-and-resume.md。源/部署63f0089a45c840a748c302d33da42196033ed6975553433f31fc755a30580fee一致，六服务运行，无开发检查后台任务。
