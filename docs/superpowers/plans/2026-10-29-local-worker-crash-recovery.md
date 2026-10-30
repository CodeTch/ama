# B-QUAL 日常单机 genuine Worker 崩溃/恢复批次

沿 R2 既有 B-R04/05/07/10/11，不恢复封存资源；同一个日常 MySQL、本地文件和既有本地受控模型/知识库。只终止新建的有界资格子进程，普通 Worker 由已核验 stop-worker/start-worker 暂停/恢复，其他现有服务保留。所有输入/最终确认/发布请求由真实页面/API建立，不插入SQL业务/结果行、不重置租约、不伪造 marker或成功回执。

## 组合与阶段

使用当前 TranscriptWorkerRuntimeProvider.create()，与普通 TranscriptWorkerMain 相同的 genuine contextual TXT WorkerLoop、external minutes/publication WorkerLoop、ExternalExecutionSession、reconciliation 和 publication maintenance。仅 DataSource 透明观测/故障切点不同；不替换 handler、不包装 preparation 重试、不手工分发SQL结果。

1. TXT：正常双文件上传→受理，普通 Worker 已停；确认唯一待办就是本次 task。新资格进程在真实 RAW transcript_revision INSERT 返回且实际事务未提交时记录 cut 元数据并 Runtime.halt。进程退出后核对 RAW/segments/root及关联注册没有部分提交，原 accepted 输入和源文件保留。恢复普通Worker，由 claim 自身处理过期 RUNNING 租约并完成原任务，核对唯一 RAW、顺序和事件/回执，不用SQL续租/重置状态。
2. Minutes：正常 generation task，在真实 candidate/保存图写入后、完成提交前相同 native crash。核对无部分候选/保存图；若模型已产生外部结果，使用原模型业务键/持久 store 的真正查回恢复，不能额外生成。保留 permits/UNKNOWN与任务归属关系，记录实际结果，失败不假报通过。
3. Publication：正常必需审阅/冻结→受理，在真实 external_document_mapping INSERT 后、结果事务COMMIT前 native crash。远端已有的受控文档不得重发/删除来重试；核对DB mapping/root未部分提交、已持久 marker/manifest保留，普通reconciler按原key查回并应用，最终只有一个文档/提交/执行/映射。
4. 独立组件：在上述在途状态允许的阶段，使用原有 owned PID/env/start-time 检查与相同私有身份/store/端口，对相应模型/KB进程单独恢复，记录新PID和同一身份/成果。不得重新安装/恢复已撤销授权；不能以全栈普通重启替代。
5. 共存/饱和：在实际root-free准备位置有界阻塞一个 minutes handler，容量1时第二同池任务保持未领取，TXT与知识库/UNKNOWN对账仍推进，心跳有效；释放后原任务和排队任务均能完成。全程以生产池/循环执行，记录每类副作用数量和上限，未完成不写通过。旧56.85秒领取延迟单独记录本轮观测，不因新成功改写历史原因。

## 编排守则

- 启动前源码/编译/既有回归及现有重要缺陷处理；新发现立即前置。源码变动后先普通 start 更新源指纹，再停Worker；整个暂停窗口不改运行源码。每个切点先唯一任务/目标表/meeting/task绑定，真正SQL返回1且事务active/autocommit=false才允许 halt；cut记录只含IDs/阶段/计数，不写敏感配置或正文。
- 标记“队列已受理”与“业务已成功”分开。队列fixture需从原浏览器脚本最小扩展，单独输出，保留默认全流程语义；Worker不健康只影响执行观察，不放松业务canSubmit/权限检查。
- 资格进程有有限等待，父编排器只处理该精确Popen/PID；finally恢复普通Worker和必要已停的owned提供方。任何未知外部效果只查原请求，保留未决现场和失败日志，禁止新key重发。
- 验证后适用回归及同一 reviewer 集中审查，再更新 B 唯一义务表。此批通过仍不能省略信息/读取/锁集合/全部适用核心义务和最终全分支审查；不自动进入C/D/E/F或Git发布。
