# D03/D04 区域重生成执行补充（实现中，尚未验收）

需求 6.7：区域按钮输入要求，仅产生该区域候选，其他区域不变；明确采用后正文待更新，保留旧正文和人工稿。源是确认转写、锁定材料与排除版本。继续复用现有异步队列、生成输入、候选、采用和历史机制，不能用完整重生成后截取展示冒充实现。

实施选择：新增显式 REGION 能力与受控协议 v3，保留已有 v1/v2 历史读取；不重写既有已归档配置。旧 ELEMENT 保留的未实现能力不能当作已支持。REGION 请求包含区域枚举、当前要素集版本及用户要求；允许七类中的空区域，但须先有一个已保存要素集。

1. 单独不可变 `generation_region_request` 保存 input、meeting、baseSet、category、instructions，避免篡改旧 generation_input 固定行结构和 FULL 历史指纹。prospective facts 和 persisted facts 都以同一行绑定。新 generation-input.v3 规范把该行纳入 scope manifest；旧 v2 仍只 FULL。
2. 新受控 model protocol v3/请求域哈希/归档安装，兄弟 harness 保留端口并透明转发。v3 FULL/REGION 都能使用个人模板；历史 v1/v2 producer、指纹和原键回执继续验证。
3. REGION 输出只允许目标区域 elements 非空，body/title 不产生可覆盖正文的结果；其他区域、敏感目录/背景等不得偷偷改写。源 locator、排除集合、支持级别及正文片段验证依然执行；用户要求不能跳过事实和来源约束。
4. 保存 genuine candidate，禁止任何区域任务自动首次保存。当前稿件始终保留。采用在原根/来源/基集/指纹版本守卫下建立新的 immutable set：目标区域来自候选，非目标区域的文字、行动字段、证据、材料上下文保持；正文ID/文本/人工标题不变，needs_generation=1。旧set及全部旧review历史保留，不伪造新的人工确认。
5. API/任务发现/元数据/原请求持久恢复/OpenAPI/前端弹窗和候选比较同步。失败保留用户要求，后续原键查回；版本变化使候选不可采用且不覆盖稿件。最终确认继续被pending和needs_generation阻断。
6. 同步修复材料来源下现有人工要素编辑的能力缺口，完整历史来源证明先行，不能简单绕过 `SourceVersion` 校验。

验收：实际 CAS + fixed harness 真实生成任务；至少一个带背景材料、一个目标为空、一个人工修改其他区域/正文的样本；不受影响部分逐字段/证据对照；失败/超时/重复原键/同键异体/越权/陈旧采用；源排除负例；完整生成及所有历史原键读取回归。更新 D07 清理顺序和D08区域按钮矩阵。设计未落地前保持 OPEN。

实现细化：保留旧 output v1 非空正文约束；v3 归档绑定 output v2。REGION 必须使用 v2 且标题/正文为空；FULL（v1/v2）仍必须非空正文。实际 ModelOutputReader 校验输出 schema 与归档精确一致，避免静默修改历史解释。


实际执行补记（2026-11-01）：V054已迁移。首次新增release累计依赖预算128不足，独立归档保留回归先RED后通过，本地Admin READ及运行预算改为256，旧归档内容未改。CAS02原区域请求在任务域/事件SQL的FULL-only处503回滚；JFR定位后补入REGION及自身region row/基集同会议约束。原键恢复得到成功task a1d7740a-7602-4bc3-b524-ef4eb24e8ca9、candidate d49dc78a-3b9d-40c4-a550-e539d04309c8。resume-cas02-02真实UI明确采用成功，正文人工标题/文本/id、六类非目标文字与行动负责人/空日期逐项相同，needs_generation=true，最终阻断。

该例整体仍失败：新region字段未生成runtime guards导致比较拒绝，RED→重新生成后修复；后续材料证据编辑在旧TranscriptVersion-only限制503。现新增当前材料快照/解析版本门禁及版本来源读取，生产Java833PASS，实际重新部署验证待进行。历史390候选比较footer横向裁切及换行修改保留记录；用户现已移除全部手机开发与验收，本项不再安排截图复核或作为任何阶段指标。完整新case须覆盖真正已受理丢ACK刷新恢复、当前草稿复制及无日期显示。
