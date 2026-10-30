# 企业智能语音会议助手

当前按[Demo 优先调整计划 R2](docs/superpowers/plans/2026-10-28-ama-demo-first-adjustment.md)和[总计划](docs/superpowers/plans/2026-10-21-ama-delivery-plan.md)推进：前期本地 MySQL、本地文件、单机，按“会议/信息→上传/转写→生成/审阅→发布/文档”逐段同步原型复刻与联调，先交付 **M1-Demo 可演示闭环**，再以 **B-QUAL 完成 M1-B 正式本地验收**。所有尚未完成的 S3、双 MySQL、双活适配及专属依赖最后部署阶段集中处理。

截至 2026-10-30，A 本地工程已交付，B-L1～4 的会议信息/名单、上传导入、转写/生成、纪要/审阅/发布已有源码采纳，local/S3 已有完整 API 链的限定证据；DEMO-01 的 CAS 入口已使用 `ama-integration-harness` 的真实 `/cas/login` 与 `/cas/p3/serviceValidate` 完成浏览器联调。**Demo 尚待验收，B 仍 INCOMPLETE**：文件/Worker/模型/发布装配、原型对照和深度事务/预算/锁序/恢复证明仍待后续片段与 M1-B。真实 AI/企业联调单独验证，受控合成结果不代表真实能力。已有功能、完成状态和 S3 实现全部保留；详细接续见 [DEVELOPMENT_HANDOFF.md](DEVELOPMENT_HANDOFF.md)。

完整结果、测试数量、审查修复、待验证项和 B 阶段入口见[阶段 A 验收报告](docs/acceptance/a/summary.md)。

## 本地开发

需要可登录、可读写会议数据的本地环境时，在本工作树根目录执行 `python3 tools/dev/local_app.py start`，然后打开 `http://127.0.0.1:5173/meetings`。启动器使用独立持久化 MySQL，并通过联调辅助项目的 CAS 登录页完成开发身份认证；账号、CAS 地址、代理配置及停止/重启方法见[本地开发说明](docs/development/local-development.md)。

在 Linux/WSL x86_64 的仓库根目录执行。工具链安装在忽略的 `.tools/`，不会替换系统 Java/Node。

```sh
python3 tools/bootstrap.py
. .tools/env.sh
npm ci --ignore-scripts
npm --prefix web ci --ignore-scripts
npm --prefix web exec playwright install chromium
python3 tools/checks/run.py all
```

启动前端，打开终端显示地址的 `/meetings`：

```sh
. .tools/env.sh
npm --prefix web run dev
```

后端启动见 [backend/README.md](backend/README.md)；前端/视觉工具见 [web/README.md](web/README.md)。未配置数据库时存活检查可用、就绪检查返回 503。现有本地启动器可登录、创建会议并保存信息，但尚未安装文件服务、Worker、模型或发布提供方；已有相应业务源码及受控 API 证据不能替代该启动入口的完整 Demo 装配和浏览器验收。实时录音、公司模型/知识库真实集成和生产资格仍以各阶段记录为准。

## 验证边界

```sh
python3 tools/checks/run.py web client.test.ts
python3 tools/checks/run.py e2e shell.spec.ts
python3 tools/checks/run.py visual shell.spec.ts
npm --prefix web run typecheck
npm --prefix web run build
npm --prefix web run check:api
```

已有 local/S3 文件实现和私有/公司 MySQL 来源配置保留；前期只验证本地 MySQL/local/单机，S3/双 MySQL/双活未完适配和专属验证归最后 F。配置存在不代表对应部署完成。上述测试不认证真实平台、100 路实时吞吐、300 人在线或自动恢复。详情见[接口目录](docs/contracts/integration-catalog.md)、[模型验证报告](docs/acceptance/a/model-capacity.md)、[HA 前置报告](docs/acceptance/a/ha-prerequisites.md)。真实凭证、生产录音和受控测试样本不得提交到仓库。
