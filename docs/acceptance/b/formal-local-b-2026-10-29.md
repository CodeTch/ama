# R2 正式本地 B 验收记录

2026-10-29。**M1-B ACCEPTED：R2 正式本地受控范围的 B 阶段验收已完成。** 维护者已完整阅读并采用 sole reviewer 的原BASE→V2最终全分支 APPROVE，当前没有开放的 actionable P2及以上阻塞项；L1–L6及B-R01–14本地启用部分全部关闭。

工作树 `/home/tanren/ama/.worktrees/phase-b-completion`，分支 `codex/phase-b-completion-20261023`，HEAD `4b68213dc6b59f9cc850b6acce1a70ed4102235a`。原独立审查下界 `34cb226aefe99c67d3681af68d748b0aeea4e073`；最终V2源码快照1077实现文件，manifest SHA256 `052198c146e4803a5174f549e19d698a2500a89a9bcf8884390028bee3492d98`。没有创建Git提交、合并或推送。

## 验收范围

依据 [Demo-first R2](../../superpowers/plans/2026-10-28-ama-demo-first-adjustment.md)、[B唯一开放义务表](summary.md)及 sole reviewer 的最终义务审计。目标是 fresh/current合法迁移前缀的单MySQL、本地mTLS文件服务、单主机、受控模型及isolated-loopback签名知识库流程。M1-Demo已按原型/真实browser闭环先行采用；本记录补核心事务、authority、预算、竞态、恢复与维护资格。

公司SSO/目录、真实AI语义质量/事实/来源/行动项、真实知识库/企业ACL及quota/cutoff继续PLATFORM-B；接口具备即接，实际能力交付前验收。S3未完适配、双MySQL/双活、后端迁移、主机/存储灾难及生产矩阵按R2 DEP归属最后F。C/D/E尚未启用的能力维持原阶段。此范围不能被读作企业/生产全部通过。

## 已完成修正

- 初始生成准备遇到自身真实heartbeat推进expiry时，只在同task/owner/attempt和其余完整四表字段/集合严格相同的条件下，发issuer-bound私有signal，丢弃整轮并完整重捕获；最多三轮、同ledger/原30秒。普通命令/读取/nulllease/recorded仍严格。D完成后立即收费，覆盖失败轮D费用保留。真实RED、限定GREEN、次数/累计/截止/中断均有证据，原失败日志保留。
- 信息/名单命令的旧LONGTEXT直接读取改为同genuine holder/gate下parent+完整child metadata→共享budget reserve→exactPK transfer；identity与名单同ledger，覆盖复核仅取有界固定IDs。known CREATE/SAVE/CONFIRM重放继续status-only当前授权与原结果，不被后来宽目录姓名阻断。审查新增Unicode ID上界P2修正为每ID1020UTF8 bytes/255scalar/510UTF16 units，保留四份owned copy计量；对应补充平面/中文及低一单位边界真实RED→GREEN。
- Worker组件重启健康端口预检设置SO_REUSEADDR，与真实server一致；real listener仍拒绝，TIME_WAIT真实RED/10tests GREEN和立即重启同session/body/其他五进程不变有证据。

具体生产/资格差异和原因见 [续租记录](minutes-preparation-renewal-2026-10-29.md)、[核心资格](local-core-qualification-2026-10-29.md)与[authority/完整集合/锁覆盖](local-authority-lock-coverage-2026-10-29.md)。

## 当前实际验证

|类别|结果及作用域|
|---|---|
|Java最终V2|545 tests / 101 classes，0 failure、0 error、0 skip，BUILD SUCCESS；W/bqual-final-java02.log|
|Web最终|249 tests全绿，无失败/跳过；typecheck/build/check-api/OpenAPI全部成功。OpenAPI原有bundler warnings保留，不声称zero warnings|
|本地装配工具|lifecycle10、provider2全部通过；最后normalstart安装V2，frontend/API/Worker/files/model/knowledge六服务均live|
|真实browser受影响回归|最终browser02：两tab200/409、草稿保留/reapply、IME不保存半成品、换tab保留、一次最终保存、旧生成409且原task/human body不变；发布tuple被拒绝、saved graph与正式文档SHA不变|
|读前预算|物理UTF8/scalar/UTF16/copy/response精确边界与取消/累计；完整当前S/A/F单ledger；六tiny配置在目标body前拒绝；D失败轮累计、原deadline和真实interrupt；名单84KB Unicode在配置262144下SAVE/CONFIRM具体Unavailable/0raw|
|原请求/当前权限|当前V2 genuine replay02三原ACK/0rawname，改payloadConflict、新wide请求budget拒绝、disabledactor拒绝、目录还原；不以原ACK授予新权限|
|事务与真实竞态|info及generation input实际INSERT后rollback；intake四个实际INSERT后SQLException/ambient拒绝及指定root/refs/input/task/receipt/audit/outbox/sequence回滚；generation真实S/A后、serialized F首写前owner命令root+1，具体PROVENANCE拒绝/无额外受理图|
|读取撤权|成员S commit→正常owner API撤名单确认→F拒绝；正式文档实际HTTP后身份停用F拒绝、S后发送前provider撤READ真实403，恢复后同SHA|
|native crash / restart|RAW/candidate/mapping三个actual INSERT precommit halt86；原任务正常恢复且各唯一domain结果；在途model/KB各独立same-store重启，普通六服务/MySQL与组件重启持久性按各case范围|
|生成池饱和|coexistence04：minutes capacity1真实S停12.029s，另任务READY0；TXT/原MARKED发布GET-only同时推进；释放后原attempt1各一exec/candidate，pub GET1/POST0/原execution和唯一mapping|
|对账池饱和|saturation02：真实reconciliation capacity1/Semaphore0，另一原operation未领取；有界占槽期间普通minutes/TXT与额外2次实际maintenance调用栈内查询推进（计数不证明独立扫描轮数）（原报告17.219s含释放后恢复，不是精确占槽时间）；释放后原pub GET2/POST0、各原execution/一mapping/doc，provider grants/version/identity不变|
|B00/current schema|真实Flyway validate通过，16个正式SQL/16条成功history；只读65业务表501列/133secondary indexes/128FK/83CHECK，B00-1.7 review model99表；legacy保留及D01两未启用列差异明确，列类型/索引/约束名称分类/FK列metadata另存；CHECK文本与FK更新/删除规则沿用逐号已采纳schema资格；不新增DDL/repair|
|原型保护|原HTML SHA256 ab21d0b7…，两共享shell golden与HEAD逐字节一致；没有改golden掩盖回归|

W为 `.superpowers/sdd/2026-10-23-ama-phase-b-remaining`。最终批次索引 W/bqual-final-verification-20261029.json；当前源证据pins W/bqual-current-source-evidence-pins-20261029.json；V1及所有RED/setup/观察器失败记录保留，最终采用V2。

## 维护来源与历史限定

sole reviewer已逐路径裁定：当前eligible unmarked publication终止经同事务seal，不会正常留下可供legacy repair的terminal+UNDECIDED；已marked的terminal+UNDECIDED可合法存在，但不eligible。fresh local B可复用现代READY0/prepared recovery与真实maintenance扫描/饱和推进证据，无需禁用hook或伪造SQL origin。pre-hook legacy positive origin仍UNQUALIFIED，不认证接管此类旧pending数据的目标升级。

B01-IMM-01历史迁移字节偏差永久PARTIAL，B04-Q401与旧O5/O6/原UNKNOWN/56.85s领取延迟等按原source/环境保留；当前成功不追认旧根因、NO_EFFECT或旧资源恢复资格。当前owned fresh/current库使用冻结前缀，不repair、不修改历史SQL，不恢复已消费旧B-L5资源。

## 独立最终结论

**APPROVE，已采用。** [原BASE→V2最终独立审查](../../../.superpowers/sdd/2026-10-23-ama-phase-b-remaining/bqual-final-branch-source-review-20261029.md)（SHA256 `609979da8c6400402d4621a794fbf612d404fb8e22141f925a170ce61e350cce`）确认：V2全部1077现场/快照文件SHA与manifest相符，tracked diff与原BASE现场diff逐字节一致；全部最终回归和当前genuine证据相符，没有仍开放的actionable P2+。维护者已完整阅读报告并采用其全部范围限定。

Unicode P2由独立closure关闭；L2/L3/L4/B00由合并独立裁定关闭；B-R01–14当前本地部分通过，B-R15 PLATFORM-B、B-R16最后F、B-R17历史限定保留。本结论达到用户授权的正式本地B目标，**在B阶段边界停止，不自动开始C/D/E/F**。工作树与六服务保持可用，未执行Git提交、合并或推送。
