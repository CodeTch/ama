# 我的会议非语音端到端验证（2026-10-30）

## 已通过的本轮证据

- 会议删除 UI/API smoke：Playwright `meeting-crud.spec.ts` 在 1440×900、1024×768 各 1/1 通过；真实 Vite 应用触发 DELETE、显示确认、刷新列表并隐藏已删除行，API 响应由受控路由固定。
- Web 单测：`npm --prefix web run test:unit` 332/332 通过；新增 topic/feedback facade 各 1/1 通过。
- Web 类型与构建：`npm --prefix web run typecheck`、`npm --prefix web run build`、`npm --prefix web run check:api` 通过。
- OpenAPI：`npm run check:openapi` lint/bundle 通过；bundler 仅报告既有跨文件 schema 重名 warning。
- Java：会议删除 wire 1/1、adapter 2/2、TopicFeedback wire 1/1，以及转写/材料/纪要/发布定向套件均通过；backend API/adapters 编译通过。
- 固定 harness 真实浏览器链：`AMA_LOCAL_URL=http://127.0.0.1:5174 AMA_CAS_ORIGIN=http://127.0.0.1:8792 FIXED_PORTS=1 node tools/dev/import_cas_integration.mjs` 通过 10/10 检查，包含匿名 401、CAS service/ticket、真实创建、TXT 上传/草稿保存、刷新恢复、知识库版本读取、provider 403、导入受理、登出撤销和无页面错误；fixture meeting `67b39f75-f8d3-4b3f-b795-adc73f914625`。
- 重启本地应用加载 V030 与新控制器后，真实 CAS 会话直接调用的核心 CRUD 链通过：会议创建 `201`、专题创建 `201`、会议归组 `204`、反馈创建 `201`、会议删除 `200`、删除后详情读取 `403`；fixture meeting `ea43e876-5b74-47a3-b0bf-93f1a1fd1a75`。

## 当前阻塞与未通过项

- 固定 harness 的真实导入链和核心 CRUD 链已通过；独立 Playwright `shell.spec.ts` 若把 Vite `/api` 代理到默认 8080，仍会把匿名页重定向到 `/login`，导致 8 个壳用例失败。使用现有本地应用 5174/8182 和 harness 8792 的真实链后，CAS/导入/核心 CRUD 路径已通过。
- `python3 tools/checks/run.py all` 当前失败：Python 282 executed、60 failed、4 skipped；尾部失败集中在仓库既有 `.superpowers/sdd/2026-10-23-ama-phase-b-remaining` 缺失历史 ack 文件。它不是本次会议 CRUD 变更引入的回归，但整仓库总门仍未关闭。
- 会议库仍在 provider 不可用时显示真实不可用；专题当前接通创建/列举/归组最小路径，批量归组、审核状态变更和 provider 聚合需固定 fixture 后继续验证。

## 语音延期边界

录音、音视频、ASR、播放和说话人识别仍是 `DEFERRED_VOICE`。新增浏览器用例会在可用的本地应用/harness 环境中断言点击延期入口不会调用 `getUserMedia` 或产生媒体请求；本次由于页面被会话配置重定向，未将该断言写成通过。
