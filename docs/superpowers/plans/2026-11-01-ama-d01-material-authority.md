> 当前范围已按用户最新要求调整：仅桌面端1440×900、1024×768；所有手机端开发与验收项已移出，不属于F待办。历史多视口报告保留原文，不再作为手机端门槛。详见 [桌面端范围裁定](../../development/2026-11-02-desktop-only-scope.md)。

# D01 材料批量引用与持续外部权限 Implementation Plan

> 执行：沿用 executing-plans，main 单 agent。用户已要求自主选择方案持续推进到 D 完成；不创建工作树、子代理，不增加审批或自行提交。

**Goal:** 知识库材料批量全检、精确版本与当前账号权限，既可正常使用，又能在撤权/撤回后停止缓存正文、派生结果和长流读取。

**Architecture:** 保留现有受管文件与材料快照链。扩展已固定身份/签名的 controlled knowledge 协议提供批量只读权限证明，所有实际调用仍经兄弟 harness 8792。事务外取证明，事务内核对当前依赖集合及原始截止时间；权限缓存不进入不可变来源正文图。harness 只透明转发签名路径，模拟库不作为生产权威。

**Tech Stack:** 现有 Java21/MySQL/local storage/Vue，Ed25519，Python controlled provider；不添加基础设施。

**Spec:** `2026-10-21-ama-phase-d.md` D01/D05/D06；当前设计为本轮实现判断，并非用户另行签署的外部真实公司协议。

## Global Constraints

- 外部路径统一固定 harness HTTP8792；SMTP2525 不改。
- 1—64 项批量；每项精确 provider/library/document/version，重复项拒绝；1 项失效不新增任何材料引用或应用快照。
- 当前登录身份与本地会议 ACL 独立重验；远端权限证明绝不能授予本地会议权限。
- 证明绝对截止时间最多5秒，失败不续期；所有保留材料历史参与依赖校验。
- 已撤回/废止来源本地立即拒读；不以新证明复活终态。无法映射的真实外部文档允许独立签名证明，不默认相信缓存。
- 解析失败与不支持明确展示；在线受管中间文件由既有清理账本管理，不能伪称没有物理副作用。

## Review Focus

1. 一项403/版本变化：整个批次不新增材料引用、不切有效快照。
2. 原请求重放与不同顺序/内容：回执稳定，改变请求409。
3. 权限撤销、签名过期/错账号/错store：缓存、派生正文及流均拒绝。
4. 引用历史、间接引用、本地pending：不能因删掉当前选择而重新开放旧正文。
5. 证明刷新不修改不可变来源图；网络I/O不持有业务事务锁。

## Task 1: 外部协议与桥接

**Files:** `tools/providers/controlled_knowledge.py`, `tools/providers/test_knowledge_material_authority.py`, sibling `backend/app/main.py`, `backend/tests/test_controlled_bridge.py`。

**Interface:** POST `/v1/knowledge/material-authorizations`, purpose MATERIAL_READ；exact body `{protocol,actorId,items:[{libraryId,documentId,version}]}`；验 bearer+签名委托，校验全批当前read grant、ACTIVE、payload hash；响应 `{providerId,storeInstanceId,assertion}`，claims固定五pins+schema+actorId+itemsSha256+issuedAt+expiresAt。不返回正文。

- [x] 写原权限/撤权/终态/错版本/部分有效/重复与边界用例，确认 RED。
- [x] 实现签名全检和材料路由透明转发；保留普通非controlled模拟页面接口。
- [x] 运行完整 provider51 / harness41 PASS，保留 RED/GREEN证据（continue-material-authority-provider-green1.log、continue-material-bridge-green1.log）；尚未部署。

## Task 2: 应用侧按需权限刷新与过期门禁

**Files:** 新 `KnowledgeMaterialClaims`, `JdbcMaterialAuthority`，修改 `ControlledKnowledgeClient`, `MaterialKnowledge`, `JdbcMeetingAccess`, `PublishedDependencyValidity`；新迁移保存library与按actor/meeting证明缓存；API请求准备与列表读取、registry下载、worker来源准备接入事务外刷新。

**Interface:** `prepareExternalRead(actor,meeting)` 只准备短期外部证明，本地ACL另查；`PublishedDependencyValidity.deadline(actor,meeting)` 核对保留依赖指纹和签名原截止；流沿用 deadline封顶。证明仅由真实client验签产生。

- [ ] 验签与依赖集合 RED；实现精确claims、library持久化、全历史依赖指纹/有界递归。
- [ ] 按需刷新和当前活跃请求有界续验；不能通过重读缓存延长原证明。
- [ ] 真实MySQL/固定harness材料读取、撤权/撤回、长流与派生输入拒绝验证。

## Task 3: 原子批量材料引用

**Files:** `JdbcMaterials`, material API/OpenAPI/SDK, picker command与UI。

- [ ] 批量有一项失效时引用数/有效快照不变，过期revision/原键改变409，原键重放同结果。
- [ ] 分离批量预检/解析暂存与一次事务提交；先校验全部外部版本和权限，再登记引用并推进一次快照；最后事务重新校验本地根和证明。
- [ ] 固定harness实际CRUD、取消不变、模板包含材料、候选保留旧人工稿验收。

## Task 4: 格式与页面证据

- [ ] 核对已冻结格式矩阵和现有parser测试，补齐原始样本页/段定位、畸形/压缩炸弹/扫描件无OCR明确失败。
- [ ] 真实浏览器材料与知识库弹窗1440/1024两个桌面视口，对照原型、权限失效和失败恢复；记录D01/D04/D05/D08证据。

### Task 2 实施细化（03:45，以下尚未实现）

已完成 `KnowledgeMaterialClaims`（3新测试+5治理回归）与 `MaterialAuthorityDependencies`（3元数据策略测试）。后者读取全部保留材料修订，按 provider/library/document/version 去重排序，遍历本地发布来源最多64节点、256边、深度16，最多64不同精确版本；library未知/超预算为暂不可核实，本地pending/terminal来源明确拒绝。未映射到本地发布的外部文档由真实外部签名证明处理，不推定有效。

后续实现采用以下接口，避免在业务锁内进行网络调用：

1. `MeetingAccess.prepareExternalRead(Actor,String)` 默认为no-op；`JdbcMeetingAccess`安装回调。回调前的独立事务只验证真实本地ACTIVE身份、根生命周期和owner/有效成员ACL，不转移正文、不产生可复用READ gate；随后事务外刷新。普通require仍完整验证权限和外部截止时间。本地ACL须先于外部状态检查，不能向无权用户泄露外部可用性。
2. V050新增 `material_knowledge_location(reference_id,library_id)` 与 `material_authority_cache(meeting_id,actor_id,dependency_hash,pins_hash,proof_hash,state,verified_until,demand_until,next_check_at,lease_owner,lease_until,last_error)`；metadata回填仅从唯一匹配已成功本地发布获取library，不猜旧模拟引用。缓存只保存已验签证明摘要与原绝对截止，不能把更新时间当权威。新表加入D07所有权清理顺序；迁移将未完成PURGE任务cursor重置0，防止新增清理步骤改变旧下标导致跳过数据。
3. `JdbcMaterialAuthority`使用本地ACL检查后的真实metadata构造package-confined请求能力，`ControlledKnowledgeClient.materialAuthority`仅接受该能力；proof校验精确actor/itemsSha256/5pins/原始<=5秒期限。事务内再次核对当前依赖指纹后写cache；网络失败不续期，当前同指纹收到明确拒绝时立即撤销旧正向cache；不同指纹的旧请求不能覆盖新cache。配置pinsHash也纳入检查。
4. 请求按需刷新；API启用的缓存服务维护有限活跃需求（请求后120秒，或仍有该会议READY/RUNNING/RECONCILING任务），后台批次续验仍重新调用外部。DB lease只用于工作调度，不延长proof。worker安装同一校验与事务外刷新回调，复用API后台维护的共享缓存；无需每个进程都启后台轮询。
5. 发布自己的短期权威仍使用原治理protocol。增加 `JdbcPublicationGovernance.ensureFresh(meeting)` 供已本地授权的需求刷新：仅在截止剩余不足2秒时重新取真实签名，不能放宽5秒；周期poll继续处理丢callback。读取和转换的事务外间隙刷新，任何事务内过期仍503/可重试，已明确撤回仍403。
6. API `HandlerInterceptor`在session和路由匹配后，仅从固定资源映射发现meeting并调用prepare；治理命令/status和删除MANAGE跳过，保留不可读时操作与查回能力。列表候选在各自事务之前prepare；registry下载/Range续验、JdbcMinutesFactsReader读准备前后、worker转换/存储间隙同样接入。严禁在已开事务里调用prepare。
7. 原子批量引用先全量源版本/权限预检，再解析/暂存，再对“原依赖+新增项”取新proof，最后一次根事务登记全部引用/location/回执和一次snapshot。新cache要在登记新引用后的文件绑定权限检查前安装，绑定当前完整依赖集合；任何失效或revision变化整体回滚引用/快照。已写入的受管中间文件仍记录并由清理管理，不能声称物理无副作用。

依赖源码尚未接到生产读取；下一步从真实本地ACL准备门禁测试和V050实际MySQL集成开始。当前部署仍V049。

### 04:05 实际应用链路检查点

`material-authority/batch-cas-04/report.json` 六组PASS，源码=部署 `8f0e7372fe0ff6e6bb3505ab9810e5dd5343dfa164b0d8236f5216087c2c7457`，V051。新consumer meeting `97919ef9-7613-4e4a-8f42-6a1c8c3e0a7c` 引用primary05与e882两份仍ACTIVE发布，可用于随后撤回失效传播。固定harness实际无效批次409且零refs/快照变化、有效2份一次revision/snapshot、原键重放/改顺序409、过5秒续签、文件下载、外人403全部通过。

前3次实际case的有效批次503保留：最终诊断INVALID_SCHEMA，根因客户端把数组items传给只支持对象根节点的canonical方法。新增独立数组规范字节摘要+签名匹配测试，4PASS，case04才是修复后证据。case04截图只是导入前工作台占位，不作为材料弹窗视觉验收。

后续：隔离解析与原始格式样本、实际UI批量/取消/失ACK、模板+材料生成、跨来源撤权/长流、D02/D03/D05/D08全部继续。不得提前撤回primary05，先完成D01/D04仍依赖它的正向场景。

## 2026-11-01 04:34 本地导入实际故障与修复

- `ui-cas-02` 暴露未绑定 MATERIAL_SOURCE 错误走公开下载导致404；`MaterialIntakeReadIT` RED同栈复现，GREEN 2/2。新增 package-owned 完成来源读取，短事务内重新证明原上传/用途/会议/当前权限，有界名称读取；存储 I/O 在事务外，续读不延长原证明截止。公开无引用下载仍404。
- 已部署隔离解析器、transitive新引用证明及全会议显示期限；`ui-cas-03/04` 已实际得到 DOCX 3段、PPTX 2页、PDF 2页，但完整恢复用例未通过，不作整体验收。
- 本地上传回执503后：保留内存原文件可直接重试，刷新丢失File对象后重新选同名同SHA文件接续旧upload，原key/body保持；单元6PASS。
- 浏览器 `input-cancel-red-03/report.json` 记录原生input[type=file] cancel(bubbles=true)误关闭父dialog；修复父dialog `cancel.self`，待真实GREEN。所有失败case保留。
- 全会议缓存视图续验覆盖未发布知识库consumer；组件5PASS，失败立即隐藏、重新读取恢复，不授予新权限。


### 04:49 材料权限续验竞争的实际根因

ui-cas-07 在实际 CAS 浏览器多文件导入中再次失败，诊断日志均落在 JdbcMaterialAuthority 的续验租约争抢分支（原47行），不是外部 DENIED。原逻辑把其他请求正在获取真实短期证明视为立即503，导致全工作区读失败和 fail-closed 展示清空。并发纯读探针两轮各120请求未复现，均保留，不冒充 RED。实际浏览器失败和数据库组件新增回归确认相同触发点。

MaterialIntakeReadIT 扩展到4项，continue-material-refresh-contention-red1.log 结果4项1失败：真实MySQL现存续验租约，另一线程在200ms后提交同actor/pins/dependencies的新证明，当前读取仍提前失败。修复只在事务外最多等待2秒，随后重新取得本地ACL、精确依赖和新证明；不延长5秒签名截止、不以等待代表授权，过期且续验停滞仍503。GREEN验证正在运行。组件缓存为明确测试边界，实际网络验收仍通过8792兄弟harness。
