# B 阶段后续收口报告（2026-10-30）

## 2026-10-31 最新材料、最终确认与发布闭环

材料快照参与的真实纪要生成、同键重放、worker 完成、原生编辑保存/刷新均已验证；名单成员授予/撤权/重新授予与成员只读、审阅阻断/补齐、最终确认 201/同键重放 200、负责人/成员冻结稿一致均已验证。审阅使用明确标记的合成决定，只证明协议与门禁，不证明人工语义评审质量。

最终确认 `855ee54d-9028-4571-b94c-ee2e60f9c63f` 的发布 `7e2edd2f-5dd5-40bd-a7af-e1ef358daafe` 已 SUCCEEDED/APPLIED。负责人和成员正式文档 SHA-256 均为 `c183de334dc111ccb479979ffdc4a8cb31230b2608d2daf7da55ad8b8dbe3d72`。经固定 harness `8792` 的该发布 POST 只有一次，同 AMA 命令重放没有再次发送。证据：`.tools/local-app/material-minutes/roster-review-final/report.json`、`.tools/local-app/material-publication/first/report.json`，各含 1440 与 1024 截图。

Harness 对受控委托知识库协议增加显式可选的本地桥接，保持原签名/响应字节，入口仍为 8792，SMTP 仍 2525。当前进程以 `MOCK_CONTROLLED_KNOWLEDGE_ORIGIN=http://127.0.0.1:46969` 启用桥接；这是隔离协议测试后端，不是生产接口权威。Harness 全量 pytest 32 通过；最新 Web 单元 338、E2E 28 通过。生产 Java 模块 655 个单元通过；旧 StoredSourceGraph 夹具问题已修复，根 Maven 目前剩余历史 integration-tests 环境/证据问题，不能称全量通过。

以上关闭对应业务链路缺口，逐页原型像素比对和未执行行仍按矩阵保持 OPEN，B 阶段尚未整体签收。

## 2026-10-31 continuation evidence

本次接续在 `/home/tanren/ama` 的 `main` 分支完成，未创建分支、worktree 或子 agent。源码当前提交基线为 `2446deb3f2360cf6f4a5ca0f7ecef89d9e66c309`；原型 SHA-256 仍为 `ab21d0b73f4e48bbf701ee8bca5e958940b61f6e6f680d4ced02de0ce4bab867`；固定端口 harness revision 为 `816c04e64f1321b35eb0e159223a31640a2b7f62`，健康检查返回 10 个模块。

新增并已通过的证据：

- `FIXED_PORTS=1` 的 `tools/dev/import_cas_integration.mjs` 真实浏览器流程通过 10/10：匿名会话 401、精确 CAS service callback、ticket 登录、真实会议创建、TXT 字节上传/草稿保存、刷新不重复上传、harness 知识库版本读取、harness 403 错误、导入任务受理和登出撤销。AMA 通过 `AMA_INTEGRATION_KNOWLEDGE_ORIGIN=http://127.0.0.1:8792` 路由材料读取；没有修改 harness 端口或把它作为业务权威。报告保存在 `.tools/local-app/integration-import-cas-harness-final/report.json`。
- Harness 全量测试 `PYTHONPATH=.python_deps:backend .python_deps/bin/pytest -q`：31 passed；CAS 日志检查改为先按 `module=cas` 过滤再使用有界响应，避免大流量导入记录淘汰本次登录日志。
- Web 全量 Playwright `npm run test:e2e --silent`：28/28 通过（1440×900、1024×768）；新增匿名在线录音页断言保持禁用且不调用 `getUserMedia`。Web 单元集 `python3 tools/checks/run.py web`：337/337 通过；`npm run typecheck`、生产构建和 OpenAPI byte check 通过。
- 主页行为检查通过 7/7；信息抽屉真实流程通过，取消/关闭/Escape 不写入、保存回读、丢 ACK 后原请求重试均成立，报告在 `.tools/local-app/prototype-parity/info-drawer-final-3/report.json`。
- C 阶段 focused Java reactor 通过 contracts 5、core 12、adapters 8（含 CAS）、api 23、worker 5、realtime 3；load/realtime-100 注册和 2 个 load tests 通过；全量编译打包通过。
- 固定端口本地 AMA 通过真实 CAS 会话完成录音 HTTP 边界回归：会议创建 201、recording session 201、PCM chunk ACK 200（连续序号无缺口）、finish/archive 200；该项只验证 C 的协议边界，不把录音业务从 B 的 `DEFERRED_VOICE` 范围升级为 B PASS。
- 通过真实 `dev-owner` CAS 会话完成专题和反馈 CRUD：专题创建/列表/归组/移除/删除分别返回 201/200/204/204/200；反馈创建 201、列表 200 且创建记录回读可见。原始摘要在 `.tools/local-app/b-followup/topic-feedback-final/report.json`，UI 入口验证在 `ui-report.json`。

本轮发现并处理的产品问题：匿名会话探测增加 2.5 秒有界超时；匿名壳层保留可解释的登录边界；录音页允许渲染静态禁用状态而不访问设备；移动宽度隐藏不适用的顶栏动作，消除 375px 横向溢出；harness 日志诊断按模块过滤。

仍未关闭的证据边界：主页全页/分组/空态像素差异仍有真实数据驱动差异（例如真实会议超过固定原型的 10 条时显示 `10+`）；名单第二身份和完整撤权浏览器证据、Office 文本导入、转写/纪要/审阅/最终冻结/发布/专题/反馈各页面的本轮逐页像素与 CRUD 回读矩阵仍未全部取得；语音业务按范围保持 `DEFERRED_VOICE`。全量 Maven `./mvnw test` 仍有既有 `StoredSourceGraphTest` 的 2 failures + 3 errors，均为 `PROFILE_UNAVAILABLE` 夹具问题，不来自本轮 C focused 代码。

当前结论：`NOT ACCEPTED`。

本次继续工作均在 `/home/tanren/ama` 的 `main` 分支完成，原型 HTML 未修改，CAS/材料联调使用固定端口 harness `127.0.0.1:8792`/SMTP `2525`。

已取得的新鲜证据：

- Web 单元测试 `npm run test:unit`：335/335 通过。
- Web 类型检查、构建和 OpenAPI 字节校验通过；构建仅保留既有 chunk size warning。
- 会议 CRUD Playwright：1440×900 与 1024×768 的删除确认/真实 DELETE/列表刷新均通过。
- 固定 harness CAS 首页行为：7/7 通过，真实 CAS 登录身份、搜索、日期筛选、状态筛选、专题视图和成员范围请求均有日志。
- 信息抽屉真实浏览器流程：取消、关闭、Escape 均不写入；保存后读取回显；丢 ACK 后重试同一幂等键和请求体。
- API/适配器会议、名单、删除和修订令牌聚焦测试：6/6 通过。
- 固定 harness 导入流程修正了上传输入等待竞态；新鲜运行通过匿名 401、CAS callback、会议创建、TXT 上传、草稿保存及刷新不重复上传共 6 项，随后在知识库无材料行处停止。

本轮新增实现：

- 原型 parity 脚本改用真实 `/login` CAS 表单，不再调用退役的 `/api/dev/login`；新增脚本回归测试。
- 会议信息抽屉显示“确认已保存名单”，实际触发已有 `CONFIRM_ROSTER` API 命令；未保存草稿不会出现该确认控件。
- 导入联调脚本在设置真实文件字节前等待“选择会议文件”可用，避免在恢复记录/授权尚未完成时静默丢失文件选择。

仍保持开放的原因：

- 首页全页/分组/空态像素差异仍存在。真实 API 返回有继续游标时只能诚实显示 `10+`、`1/…`，原型使用完整数量；当前差异未用遮罩掩盖。
- 名单 owner/member 的完整确认、撤回和第二身份浏览器证据被前一次中断尝试污染了持久 fixture，当前只保留 API/命令测试和 owner 信息抽屉证据。
- 知识库材料行在本次固定 harness 运行中不可用，材料选择、403/缺版本和全链导入仍未关闭。
- 纪要、审阅、最终冻结、发布、专题、知识库、反馈和语音延期页面尚未绑定本次报告的新鲜逐页像素与真实 CRUD 证据；语音行仍按 `DEFERRED_VOICE` 处理。

因此矩阵中的 `OPEN`、`UNTESTED` 和未解释像素差异继续阻止整体 `ACCEPTED`；不得把已有历史报告或静态 demo 结果升级为本轮通过。

2026-10-31 首页正式精确比对新增：完整有限真实夹具，32/32 视口/全页 zero pixel；没有修改原型，没有掩码/容差，Chromium 双方统一禁用 partial raster 后通道1渲染差异消失。报告 `.tools/local-app/prototype-parity/home-finite-final-20261031/report.json`。该结果不替代其他页面或 >10 条不透明分页的独立业务验证，整体 B 仍按完整矩阵收口。
