# 企业智能语音会议助手

按已批准的[架构 v0.2](docs/superpowers/specs/2026-10-21-ama-architecture-design.md)与[分阶段计划](docs/superpowers/plans/2026-10-21-ama-delivery-plan.md)开发。截至 2026-10-27，阶段 A 本地工程已交付，阶段 B 正在完成首个业务闭环，尚未整体验收。已实现会议列表、创建、部分详情与任务界面及多项后端能力；完整编辑、上传与纪要发布前端仍待接续。公司真实联调与生产资格单独记录。后续按[已批准的简化开发流程](docs/superpowers/plans/2026-10-27-ama-development-workflow.md)执行；当前状态与下次“继续”入口见 [DEVELOPMENT_HANDOFF.md](DEVELOPMENT_HANDOFF.md)。

完整结果、测试数量、审查修复、待验证项和 B 阶段入口见[阶段 A 验收报告](docs/acceptance/a/summary.md)。

## 本地开发

需要可登录、可读写会议数据的本地环境时，在本工作树根目录执行 `python3 tools/dev/local_app.py start`，然后打开 `http://127.0.0.1:5173/meetings`。启动器使用独立持久化 MySQL、开发账号和示例会议；账号、密码文件、代理配置及停止/重启方法见[本地开发说明](docs/development/local-development.md)。

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

后端启动见 [backend/README.md](backend/README.md)；前端/视觉工具见 [web/README.md](web/README.md)。未配置数据库时存活检查可用、就绪检查返回 503；业务可用性取决于已配置的身份、数据库和适配器；已接入页面可创建空会议并查看部分保存信息和任务，不能据此认定完整 B 闭环已交付。实时录音、公司模型/知识库真实集成和生产资格仍以各阶段记录为准。

## 验证边界

```sh
python3 tools/checks/run.py web client.test.ts
python3 tools/checks/run.py e2e shell.spec.ts
python3 tools/checks/run.py visual shell.spec.ts
npm --prefix web run typecheck
npm --prefix web run build
npm --prefix web run check:api
```

local/S3 是独立文件后端选择，MySQL 支持私有与公司平台两类来源。上述测试不认证真实平台、100 路实时吞吐、300 人在线或自动恢复。详情见[接口目录](docs/contracts/integration-catalog.md)、[模型验证报告](docs/acceptance/a/model-capacity.md)、[HA 前置报告](docs/acceptance/a/ha-prerequisites.md)。真实凭证、生产录音和受控测试样本不得提交到仓库。
