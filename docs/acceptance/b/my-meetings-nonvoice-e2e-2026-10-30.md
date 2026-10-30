# 我的会议非语音端到端验证（2026-10-30）

## 已通过的本轮证据

- 会议删除 UI/API smoke：Playwright `meeting-crud.spec.ts` 在 1440×900、1024×768 各 1/1 通过；真实 Vite 应用触发 DELETE、显示确认、刷新列表并隐藏已删除行，API 响应由受控路由固定。
- Web 单测：`npm --prefix web run test:unit` 332/332 通过；新增 topic/feedback facade 各 1/1 通过。
- Web 类型与构建：`npm --prefix web run typecheck`、`npm --prefix web run build`、`npm --prefix web run check:api` 通过。
- OpenAPI：`npm run check:openapi` lint/bundle 通过；bundler 仅报告既有跨文件 schema 重名 warning。
- Java：会议删除 wire 1/1、adapter 2/2、TopicFeedback wire 1/1，以及转写/材料/纪要/发布定向套件均通过；backend API/adapters 编译通过。

## 当前阻塞与未通过项

- 固定 CAS harness（HTTP `8792`、SMTP `2525`）和完整数据库 fixture 未在本次会话同时启动，`tools/dev/import_cas_integration.mjs` 的真实浏览器链未执行；报告不能写成 CAS/数据库端到端通过。
- 直接运行现有 `shell.spec.ts` 时，本地 API 会将无会话请求重定向到 `/login`，与该测试期望的匿名壳 `/meetings` 不一致，因此 8 个矩阵用例失败；这属于当前本地运行配置/会话状态边界，需要在固定 harness + 本地应用启动链中重跑。
- `python3 tools/checks/run.py all` 当前失败：Python 282 executed、60 failed、4 skipped；尾部失败集中在仓库既有 `.superpowers/sdd/2026-10-23-ama-phase-b-remaining` 缺失历史 ack 文件。它不是本次会议 CRUD 变更引入的回归，但整仓库总门仍未关闭。
- 会议库仍在 provider 不可用时显示真实不可用；专题当前接通创建/列举/归组最小路径，批量归组、审核状态变更和 provider 聚合需固定 fixture 后继续验证。

## 语音延期边界

录音、音视频、ASR、播放和说话人识别仍是 `DEFERRED_VOICE`。新增浏览器用例会在可用的本地应用/harness 环境中断言点击延期入口不会调用 `getUserMedia` 或产生媒体请求；本次由于页面被会话配置重定向，未将该断言写成通过。
