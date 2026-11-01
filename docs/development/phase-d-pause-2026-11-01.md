# 2026-11-01 当前批次暂停与精准接续

用户最新要求：**完成当前批次调度后停止，记录状态；后续收到“继续任务”才接续。** 该要求覆盖此前“阶段 D 完成前不停下”。本批没有启动后续专题/全页面验收批次，也没有把 D 标为完成。

## 工作位置与运行环境

- 全程 `/home/tanren/ama`、`main`、单 agent；未创建分支/worktree，未提交、重置或清理既有变更。
- 主树 HEAD：`8479f5e433e2909e109788a0af477cf2433eb415`。兄弟项目 HEAD：`09ca6421bcd4412f294ddaee6b2247374c0d53ad`，仍为其原 `master` 分支，保留并行变更。
- 当前运行：standard 场景、V054，本地 Web 5174、API 8182、management 8183、MySQL 46443。服务保留运行。
- 当前源码/部署签名：`531ac0128c910789e1bb598a4e4117c208ffbbc9457627024e7ec8cfcb2210fc`。实际 v4 API 六组验收时签名是 `377765521a0de8e7e2acdca2abac3ee02c44b7779b1bc2b03cecd7e523910161`；后续 build 生成的 `web/dist/index.html` 也被现有 source_signature 纳入，因而重新部署对齐到 531ac。未在该过程中另改产品逻辑。
- standard恢复后回读 **2组通过**：`.tools/local-app/sensitive-exclusion/standard-readback-02/report.json`，签名531ac一致，采用/排除空集/旧SET拒读/原件SHA/旧v3原键均保持。首个readback-01因build产物导致签名未对齐而FAIL，保留；未改成通过。
- 所有外部联调仍通过兄弟固定 HTTP **8792** / SMTP **2525**。受控模型/知识库只证明接口与业务边界，不代表公司真实服务权威或语义质量。
- `.tools/local-app/state.json` 含凭据，只读取所需字段，不整体输出。准确机器状态见 `.tools/local-app/task-current-status.json`。

## 本批完成的修改与验证

1. 材料敏感排除不再只接受转写：完整 StoredSourceGraph 证明后，检查当前确认、材料快照、文档、解析器版本；当前 SET 只允许真实证据/敏感/背景目录成员，SET 不授予编辑或审阅能力。前端增加独立背景目录，材料来源使用“锁定来源”文字。
2. 保存旧排除项需要准确当前已保存的来源/类别组合，以及曾应用该项的已验证来源。采用新稿后检测目录不再出现旧来源，仍可连续保存；移除后不能以旧历史重新加入未检测项。原始文件/转写/旧稿不因排除而删除。
3. 实际验证发现整份知识库排除仍经段落别名进入模型的缺陷。新增物理版本重叠规则：整份排除覆盖对应页段；局部排除也移除包含它的整份副本，保留其他段落，不做传递扩大。原文、派生要素/背景摘要与所有新输出 sourceRefs 同步受约束。
4. 新行为通过 protocol/request-hash v4 和独立新 release 装配。旧 v1—v3 的配置、请求、回执保留；输入仍 v3、输出仍 v2。standard/sensitive 均使用新 v4，安装器保留两种旧 v3 和两种新 v4 的共同最终索引。
5. 补齐无稿读取失败的错误/重读入口和授权等待期 loading。初始按钮竞态回归 15 项通过，但材料原文完整浏览器预览仍有失败，未宣称修复完毕。
6. 发现并补齐 D/LIST-05 的专题“仅本人负责且已发布会议可新增归集”门禁：全部锁定对象检查后统一 422，原子批量无部分更新；旧回执重放不重新应用，历史未发布关联仍可移出/随合法删除清理。准备外部证明在事务外进行。MySQL 22 项通过；**真实新发布正向与专题 UI 尚未执行**。

验证证据：

| 范围 | 结果 | 日志/报告（均相对 .tools/local-app） |
| --- | --- | --- |
| 生产 Java 模块 | 854 通过，0失败/错误/跳过 | continue-source-exclusions-production-java2.log |
| 完整前端 | 481 通过 | continue-source-exclusions-web-full2.log |
| 专题 MySQL IT | 22 通过，独立 C 测试 schema | continue-topic-published-green2.log |
| 本地启动/场景工具 | 30 通过 | continue-source-exclusions-runtime1.log |
| 兄弟透明桥接 | 5 通过，包含 v4 | continue-source-exclusions-harness2.log |
| 前端类型/build/API生成一致 | 通过 | continue-minutes-loading-typecheck1.log、continue-source-exclusions-final-web-build.log、continue-source-exclusions-final-api-check.log |
| v4 实际 CAS/API/Worker | **6组通过** | sensitive-exclusion/material-v4-api-01/report.json |
| v4 完整浏览器 | **失败保留，未关闭** | sensitive-exclusion/material-v4-01/report.json、material-v4-02/report.json |

Java“生产模块”不包含需要历史冻结证据或专用环境的整个 integration-tests 单元夹具集合，不得改写为全 Java/全 IT 都通过。构建存在现有大 chunk 提示，本批未做打包拆分。

## 当前真实业务状态，禁止误当待处理原夹具

会议 **d3b0423d-1a61-4995-adf7-6bddee8ca7f7** 仍 ACTIVE：

| 对象 | 当前值/状态 |
| --- | --- |
| meeting revisionToken | **13** |
| 当前正文 | `e72d3b8f-bb18-4c31-a157-ff9e4ff9e568`，保留人工标题，titleOrigin=HUMAN |
| 当前要素集 | `2d436d9b-2a7d-48d8-86ee-1fb538f5cfe0` |
| 当前排除修订 | `940501bf-2315-467b-92f0-1dc8e1cc0281`，**空集合**（已明确移除） |
| needsGeneration | true；移除设置后尚未重新生成，不应最终确认 |
| 新 v4 task/input | `b41376dc-f502-48e6-9964-1fe8a42ea67b` / `210697ac-80c4-4107-917f-bae8c393092b`，SUCCEEDED |
| 新 v4 candidate | `12398057-88c3-49f5-85ee-aaa621902c18`，**ADOPTED** |
| 旧不安全 v3 task | `7d5307e4-f4d6-4d39-83b2-62655bfb2cf2`，SUCCEEDED；不能改写为失败 |
| 旧不安全 v3 candidate | `c80eacad-4ffe-4e41-b836-a2339bfe898c`，**REJECTED**，不是 PENDING |
| 最初 v3 task | `234a478d-ecf3-4be5-a95b-3a6306e90fad`，原请求重放继续返回同任务 |
| 当前 KB material/file | `f39e90dd-48b5-4171-be09-97baa39ad6e7` / `239582d8-c9cb-489d-8fc0-5b5c36be99af`；原受管字节保留 |
| 旧 SET | `5e09f693-c643-4a4e-a5d2-cf228e855f23`，不能作为 current SET；该路径返回404是正确行为 |

六组 API 证明包括：真实请求 backgroundMaterials 为空；新候选有效来源只剩转写；显式采用与人工标题保护；已保存旧项连续两次保存；明确移除与再加入422；旧 v3 原键回放和原文件可读。报告明确不覆盖完整 UI 或模型语义质量。

所有操作原 key/body 保存在各案例的 `private-originals.json`；已发送但结果未知时必须查回原请求，不能换 key 新建任务。失败案例、原始红测和 provider-store 请求/结果都保留。

## 仍未完成与接续顺序

1. **先处理材料 v4 浏览器预览失败。** `material-v4-02` 中 API 来源与权限检查通过，但政治敏感材料的预览等待超时，行后来消失，没有新生成。根因尚未定位。应采集来源请求的 targetKind/targetId、requestfailed/abort、组件 epoch、当前 body/set/revision、minutesScopeReady 与 session/owner 状态；已有 network 仅记录响应的 pathname/status，部分404是脚本主动负例，不能直接推断是 UI 失败，也不要延长现有权限有效期限。
2. 为避免把已采用/已移除的 d3 夹具当原状态，已准备全新 UI 案例脚本 **`.tools/local-app/continue-sensitive-material-v4-ui-cas.mjs`**，只做过语法检查、未运行。先在根目录 `source .tools/env.sh`；若目标输出目录已存在，先查看原任务/请求记录，不得覆盖重跑。再以启动器切换到 sensitive（不是手改 state）；运行新输出目录，例如 `.tools/local-app/sensitive-exclusion/fresh-material-v4-ui-01`。脚本会创建真实 TXT+KB 会议、生成含 KB 检测的稿件，再验证预览、排除、v4 过滤、采用、旧项保留与 UI 移除。若已受理后失败，先看该目录的原请求、owned-fixture/latest-task，而不是重跑创建新 key。
3. 完成材料 UI 后恢复 standard，运行已准备、**尚未启动**的 **`.tools/local-app/continue-d08-published-topic-cas.mjs .tools/local-app/d08-page-matrix/topic-cas-01`**。它创建新的真实发布会议，再验证专题正向、未发布/混批422、外人403、非空删除409、原键和三视口。当前主库没有可直接复用的已证明有效发布正向夹具；`93716717...` 虽本地 ACTIVE/PUBLISHED，但上游依赖已撤回，必须拒读。
4. 完成 D08 全页面/按钮/弹窗/三视口与原始原型对照。原型 SHA `ab21d0b73f4e48bbf701ee8bca5e958940b61f6e6f680d4ced02de0ce4bab867` 不改。reference-01 是8状态×3视口的原件截图，不是应用通过；静态437按钮清单不是437业务成功。已有页面证据复用，未绑定的状态保持开放。
5. 运行变化对应的最终回归和自查，补齐 `docs/acceptance/d/summary.md`、page-matrix 与正式阶段验收状态。不要因当前暂停或测试通过就关闭未验项。

执行约束：继续时先确认根目录/main；不建 worktree/分支/子 agent。Maven 与实际 CAS 资格运行顺序执行，实际案例期间冻结源和 Maven。`source_signature()` 会计入 web/dist/index.html，**构建后再部署对齐指纹，再跑资格**；不能伪造 state 绑定。兄弟项目可做有证据的兼容修复，固定端口不改。

D01材料变更与 D07实际删除的 `ef8a6056-aa01-4dad-8a37-9a74de189ee8` 已删除并物理清理，禁止当可读夹具。primary05/e882已撤回、db0已废止，禁止复活旧治理夹具。

## 阶段资格与本批自查

B、C、D 均仍 **NOT_ACCEPTED**。B/C 的100路7200秒长测 FAILED（347298发送、342798 ACK、43200归档chunks、12归档、88错误；ACK p95 503341ms）及全部原证据保留。独立物理 ACK 字节核验通过不能替代容量验收。S3/双MySQL/双活与备份恢复义务仍按既有 F 范围，语音依赖不计为 D 完成功能。

本批按用户单 agent 约束做定向自查，未声称独立审查：核对当前来源门、SET成员与无审阅授权、非传递的物理版本排除、全部输出引用、v4旧归档隔离、已保存旧项保留/移除，以及专题全批锁定后拒绝。完整浏览器预览和 D08 尚未通过，作为明确开放项接续。
