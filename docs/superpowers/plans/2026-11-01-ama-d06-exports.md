# D-06 已保存版本授权导出实施计划

> 使用 executing-plans 单 agent 执行；用户要求所有工作在 `/home/tanren/ama` 的 main，禁止 worktree、子 agent，不增加确认停点。

**Goal:** 从真实已保存/已发布固定版本提供 Markdown、PDF、Word 导出，生成、下载和长流均检查当前授权与治理状态。

**Architecture:** 复用现有 `JdbcMinutesSavedQueries` 的真实来源与权限证明及统一 `FileRegistry`。转换器只接受已经读取的标题/正文，不读取来源 URL；任务、版本、当前可访问性和文件引用由调用方的持久化协议证明。PDF/Word 在受限 Java 21 子进程转换，先生成私有产物，再在最终授权检查后注册为受管文件。

**Tech Stack:** 当前 Java 21、MySQL、PDFBox 3.0.8、JDK ZIP/XML、Vue；不添加外部转换服务。使用已有文泉驿/DejaVu 字体并绑定显式安装路径。

**Spec:** `2026-10-21-ama-phase-d.md` D-06；`../specs/2026-10-21-ama-requirements-matrix.md` MIN-15/PUB/LIST 授权规则；既有架构第9—14节。

## 约束与裁决

- 所有实际外部调用经兄弟 harness 固定8792/2525；此转换器不需要外部接口，不抓取正文中的链接/图片/本地路径。
- 未保存草稿、待采用候选不能成为导出源。发布后仅固定已发布版本。来源已变更/撤权/治理未知或失效时拒绝迟到结果与下载。
- 不用下载成功推断模型事实正确；受控模型案例仅验证保存内容与版本。
- 三格式都从相同已保存文本构造。标题、Markdown章节和表格有明确排版；其他未经解释的文本保留，不执行HTML或外部资源。缺字拒绝该转换，不能静默删字。
- 技术预算：输入UTF-8不超过1MiB，输出不超过32MiB，最多1000页；受限子进程256MiB/30秒，取消时终止进程树；这些是转换资源限制，不是新的产品表单字数限制。
- 录音长测运行期间仅修改源码、执行有记录的限定验证；不部署、不改运行角色和harness。D04带材料模板修复及导出功能的实际部署/浏览器验收待长测结束。

## Review Focus

1. 图片/HTML中的 file/http 路径不能被转换进程读取，XML/ZIP不能注入外部关系。
2. emoji、中文、长段、表格跨页与空行不得静默丢失；字体不支持时明确失败。
3. 排队期间正文修改、成员撤权、治理失效必须让原任务/下载失效；不能用旧授权重新开放文件。
4. 相同key/body查回唯一任务；不同正文/格式冲突；不确定提交不能重复生成或注册第二文件。
5. 长流每5秒重新授权；取消/删除时中断，物理产物与未注册临时文件可恢复清理。

## Task 1：确定性、无外部读取的三格式转换

Files: `backend/adapters/src/main/java/ama/export/MinutesExportDocument.java`、`MinutesExportRenderer.java`、`backend/adapters/src/test/java/ama/export/MinutesExportRendererTest.java`。

接口：`MinutesExportDocument(String title,String body)`；`MinutesExportRenderer.render(MinutesExportDocument,Format,Fonts,BooleanSupplier): byte[]`，Format为MD/PDF/DOCX，Fonts仅显式受信任字体路径。转换器不授予业务或文件权限。

- [x] 先写失败测试：MD原文一致；DOCX ZIP/XML实际解析中文/章节/表格且没有外部关系；PDF实际提取中文/emoji并跨页；外部链接为文字、无请求；坏Unicode/取消/超限拒绝。
- [x] 运行定向 Maven，记录RED；再实现共有文档块与输出，运行同一批得到GREEN。另加PDF重复转换字节回归，修复默认时钟ID后7PASS。

## Task 2：受限子进程

Files: `MinutesExportProcess.java`、`MinutesExportMain.java`、`MinutesExportProcessTest.java`。

- [x] Java21安全策略只允许读取显式classpath/输入/字体、写独占私有输出；拒绝Socket、任意文件、子进程；父进程读取有界输出并校验结果。
- [x] 实际子进程验证中文PDF/DOCX、失败无部分可用文件、超时/取消停止、外部资源不可访问；保留失败日志且不含正文。4组件测试+confinedUnicodeIT通过，样本实际PDF/LibreOffice视觉及150段文本核验；新增独立进程字节重复验证已通过（confined-unicode-02）。

## Task 3：持久任务、版本与受管文件

Files: 后续增量迁移（分配前核实最新版本），`JdbcMeetingExports`、导出任务策略、文件引用策略、`ExportController`、`contracts/openapi/exports.yaml` 及生成SDK。

- [ ] 实际MySQL RED：只有当前可读已保存版本可受理；原键回放；排队修改/撤权/治理失效；相同源不同格式；迟到完成原子拒绝。
- [ ] 复用队列/身份与会议根锁序、当前来源图及FileRegistry事务能力；严格绑定任务、actor、sourceRevision、sourceHash、格式、fileId；保留历史任务但不泄露正文。
- [ ] 下载通过统一授权流和Range，5秒重验；所有失败保持不可用，不因已有物理字节放行。

### 当前接线裁决（2026-11-01，长测冻结期间）

- V046 新增 `generated_file_origin`，复用 `file_object`/`file_reference`。上传与生成来源冲突时拒读；生成产物不能经过普通 `bind` 获得新引用。实际 C 测试库 3 项通过，主库暂不迁移。
- `SavedMinutesExportSource` 先用真实 saved graph 读取准备，承载对象私有且绑定 issuer/actor/meeting；已发布必须匹配 final 的完整版本集合。任务接线仍须实际数据验证。
- `MinutesGraphCapture.capturePublicationCurrent` 已有 controls/tasks 插入点：导出输入与产物控制行应在 sources/files/tasks 之前预锁。使用该既有分段顺序，不能先取得 source task 锁再发现新的导出控制行。
- 队列继续使用 `JdbcTaskQueue.installConfined` 和真实 carrier。准备阶段允许无授权事实用于取消/失败收敛；只有当前 READ capability 能读取/转换/登记内容。成员 READ 导出不要求 owner EDIT，也不放开发布后业务写入。
- 每个转换尝试先记录独占产物意图和唯一受管 key，再执行存储写入。迟到/失败产物保持不可下载；相同请求键返回原任务。D07 必须枚举这些未登记意图，并永久封闭 key 后才确认清理，不能只删除当前 data 后允许迟到 PUT 重新落盘。
- 当前 `LocalFileStore.delete` 只删 data，不能独自证明上述永久清理；D07 应复用已有 retire/discard 的封闭语义。PDF 字节确定性也要增加实际回归，不默认两次渲染相同。
- `RevocableInputStream` 与 API 的 `AuthorizedDownloads` 已接文件/转写来源/录音回听。真实 session filter 签发私有重验器；复用原会话，每 2 秒重验，5 秒到期即关闭，阻塞/晚到成功不续命。当前是组件验证，真实 CAS 撤权与治理端到端仍待接线后验证。

## Task 4：原生页面与端到端验收

- [ ] 编辑页只提供Markdown；回看页三格式、任务进度/失败重试/原请求查回；不把实现细节加入产品文案。
- [ ] 固定harness CAS实测owner/member/foreign、发布前后版本、撤权与治理期间流终止、重复/丢ACK。
- [ ] 实际打开PDF/Word样本核对中文、表格、章节、换页及已保存内容，完成原型对照后更新D06/D08证据，组件通过不能关闭整项。

### 01:12 组件接线进度

- 生成来源完整读取6PASS，真实队列3PASS；V047仅迁移到隔离C测试库。队列输入无正文，转换尝试独占存储key，真实完成回调才登记三张文件/引用表。失败或过期尝试保留清理意图。
- 完整下载需要同时复核保存图和导出产物，加入注册策略的身份预锁与真实 registry 来源集合凭据复用；仍由 registry 校验凭据issuer/事务/完整集合，不能用调用方DTO替代文件证明。当前接线验证中。
- Task3不打勾：真实保存图端到端、API与worker安装尚未完成。主库V045与固定8792服务保持长测冻结。
