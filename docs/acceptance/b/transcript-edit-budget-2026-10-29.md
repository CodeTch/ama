# 原型转写编辑、恢复与元数据预算验证（2026-10-29）

当前全量验收仍为 **NOT ACCEPTED**。本记录覆盖这次误停止后的接续成果，不能替代说话人、实际纠错、实时识别、背景材料等全量功能验收。不做像素级比对，原型原件未改。

## 原型对应和修改

依据最终原型 L3039、L3041 与 L3016–3017：

- 段落通过“编辑转写片段”弹窗编辑，字段“转写内容”，取消/保存；空白输入显示“请输入有效转写内容”。取消、Escape 和遮罩关闭不写父草稿和服务端。保存使用现有完整段落、版本与原请求日志，只有成功回执和重新读取都完成才关闭。
- 正文保持完整连续列表，移除擅自加入的每40段分页；搜索框使用原型“搜索转写”。
- “批量替换”的“查找”“替换为”均必填；空值提示“请输入查找与替换内容”，无匹配提示“未找到匹配内容，未执行替换”。替换覆盖会议工作稿全文，不受当前搜索筛选影响；实际保存后显示真实替换数量。
- 沿用原型 `.modal`、`.modal-h/b/f` 样式和原遮罩颜色/模糊；等待未知请求或重新读取期间保留弹窗与恢复归属。中文输入合成期间不保存。

尚未实现的纠错候选、旧建议失效/重新核对、说话人绑定和反馈仍 OPEN；不以这次人工编辑通过代替 F04。

## 实际发现的缺陷及因果证据

| 缺陷 | RED | 修改和 GREEN |
|---|---|---|
| 第41段以后被分到下一页 | `full-prototype-transcript-continuous-recorder-red-v1.log`：40 != 45 | 连续45段，真实文件/Worker/RAW与工作稿回读通过 |
| 设备启动失败后删除了已经落盘的分段身份，后续保存冲突 | 同一 RED：录音保存范围已变化 | 保留无音频的系统中断检查点，重试不删除已有身份；不产生假音频文件；录音单测通过 |
| 弹窗重试被未知状态自身阻塞、替换数量在回执丢失后变成0 | `transcript-modal-causal-red-v2` 两项失败 | 同一原请求可重试，初次真实匹配数量保留；实际组件因果测试通过 |
| 真实转写409未被通用错误码表识别，误当未知请求且无法取消 | `20261029-transcript-modal-conflict-v1/failure.json`、错误契约 RED | 新增受路由/方法/错误结构/代码与状态配对约束的转写拒绝契约；`conflict-v2/report.json` 通过真实并发信息写入→转写409→保存内容未变→重新读取比较→显式重应用/新键保存 |
| 实际保存200后仅GET丢失，第二次点击又编辑了一遍，无法结束恢复 | `20261029-transcript-ack-readback-red-v1/failure.json` | 保存 ticket 的归属持续到重新读取成功；`ack-readback-green-v1/report.json` 证明恰好1次PUT，随后仅GET恢复并清除dirty |
| 点击原型遮罩不关闭弹窗 | `20261029-transcript-backdrop-red-v1/failure.json` | `20261029-transcript-modal-native-v3/report.json` 包含取消/Escape/遮罩0写入及完整校正/替换/丢失ACK恢复 |

错误契约不把任意409当成已拒绝：未知代码、错误状态配对、错误字段/trace等仍保留未知结果；其他路由与未选用该契约的请求不改变分类。完整API生成一致性通过。

## 45段、多次修订暴露的预算问题

真实会议 `5d49b084-14fa-4d9e-beac-64b8698641a6` 有45段、8个转写历史版本。原始文件、人工稿及转写确认读取成功，模板读取成功；纪要 `generation-inputs/current` 与当前纪要读取实际413，未创建生成任务。失败保存于 `20261029-transcript-to-publication-v1`，不作为通过。

只读诊断使用真实本地工厂、原数据及预算，入口仅记录 `BUDGET_EXCEEDED` 的调用位置，不修改生产预算、持久业务或返回值；定位为 `MinutesGraphDiscovery.lockAndRead` 在原始元数据传输前对所有列均按255标量估计峰值。公开诊断只有错误代码/调用位置，不输出配置、凭证或正文。

修正为：在相同 holder、相同完整 selector union、原排序/有限 LIMIT/哨兵及原有锁规则下，先以 COUNT/SUM 获取实际字节、标量与 UTF-16 数字测量。该测量本身也计入**同一原始累计账本**；成员数量不一致立即拒绝，再检查实际缓冲峰值后才传输原始元数据，之后逐单元与已声明图完整比较。原1 MiB边界、取消/时限、锁顺序、原始来源和旧任务档案不变。

新增 `MinutesMetadataAdmissionTest`：5项中3项在旧实现真实失败，修正后5项通过，覆盖8×45记录、已消耗预算不重置、增减成员在原始传输前拒绝、负值/超限测量拒绝、实际单元变更仍拒绝。该项是JDBC边界替身测试；真实MySQL语句由下述诊断和native路径另证。

`20261029-generation-budget-diagnostic-v2.json`：相同真实会议、相同原始预算的 current 与 templates 都完成。完整生成/发布链路已补证，范围和重启恢复区别见下一节；全量原型仍不签收。

另发现直接进入纪要页的原始恢复缺口：当真实转写已确认、模板及排除设置均未初始化时，旧后端只返回 `CAPABILITY_UNAVAILABLE`，而前端按真实原因禁用生成。现在在原有版本/只读/材料/冻结门槛后，仅对实际缺少转写确认或完整初始设置分别返回 `TRANSCRIPT_CONFIRMATION_REQUIRED` / `SETTINGS_REQUIRED`；单边设置缺失继续拒绝，不允许覆盖。`MinutesGenerationPrerequisiteTest` 旧实现5项中2项RED，修正后5项GREEN。真实v4已通过生成、审阅和冻结；发布曾停在回执处理，后续保留同一原执行完成恢复，见下节。

## 发布回执缓存过期及原执行恢复

v4继续证明：同一45段会议恰好1个成功生成任务、实际来源回读、5项人工审阅、1次信息保存/1次空名单确认/1次最终冻结；发布POST恰好1次且真实202，随后主动丢失浏览器ACK。旧Worker却停留在RECONCILING，故v4本身继续保留FAIL。

对**精确本工作树Worker**做15秒有界JFR异常观察，没有改代码、任务或事实。`20261029-worker-exception-diagnostic-v1/report-stack64.json` 记录实际 `BUDGET_EXCEEDED`：原30秒账本已到期，但 `ExternalExecutionSession.retry` 继续持有同一准备结果调用 `prepareObservation`，无法退出本地缓存。

修正只改变本地准备结果的寿命：默认/普通事务失败继续保留同一回执；实际纪要及发布准备结果遇到预算或取消失败时退出缓存，停止维持该本地会话，交由原有持久化协调流程对**原执行**重新GET和验证。既不重置旧账本，也不确认未知结果、结清未知许可或重发外部操作。寿命判断异常时仍保留唯一回执。新增4项会话因果测试旧实现2项RED，修正后4项GREEN；2项域策略、5项物理取消/未知许可测试通过。

正常重启本工作树后，`20261029-original-publication-recovery-v1/report.json` 真实通过：原publication/task/execution编号完全相同、executionNo仍为1，恢复期间0次发布POST，原任务SUCCEEDED，正式文档200；已发布owner页面无编辑入口，使用当前版本号实际PUT仍409且版本未变。总链路只有原来的1次发布POST。该项证明**新owner会话在重启后查询和恢复原执行**；不冒充“未重启时内存过期路径”的native测试，后者由真实JFR根因与会话因果测试覆盖。

汇总证据 `20261029-core-protocol-combined-v1.json` 只认可这一受控Provider的B核心协议链路，不抹掉v4 FAIL，也不替代全功能或语义质量验收。

## 当前源码回归

- Java：选定112类、590项，失败/错误/跳过均0，`full-prototype-java-selected-observation-v1.log`，BUILD SUCCESS。新建本轮unit导出目录，没有重启旧封存资格窗口；不声称所有历史环境探针通过。
- Web：307项、0失败/跳过；typecheck、build、API生成一致性通过，`full-prototype-transcript-modal-full-*-v16.log`。
- 当前工作树六服务正常使用5174/8182/8183，主目录另一组服务未停止。产品修复已通过正常启动器打包并重启。
- 纪要仍使用受控合成Provider；真实请求、来源、审阅/冻结和发布协议验证不证明模型语义质量。完整全量功能及新媒体资源/并发资格仍待完成。

本批源代码与测试的实际副本及SHA绑定保存在 `.tools/local-app/prototype-parity/20261029-transcript-budget-final-v1/manifest.json`（32文件，只覆盖本批，不冒充全仓冻结）。原型SHA保持 `ab21d0b73f4e48bbf701ee8bca5e958940b61f6e6f680d4ced02de0ce4bab867`。
