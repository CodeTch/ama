# 企业智能语音会议助手

按已批准的[架构 v0.2](docs/superpowers/specs/2026-10-21-ama-architecture-design.md)与[分阶段计划](docs/superpowers/plans/2026-10-21-ama-delivery-plan.md)开发。当前交付阶段 A：可启动后端、原型页面外壳和关键验证工具。业务闭环在后续阶段实现，公司接口及运维证据继续作为相应联调/部署前置条件。

完整结果、测试数量、审查修复、待验证项和 B 阶段入口见[阶段 A 验收报告](docs/acceptance/a/summary.md)。

## 本地开发

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

后端启动见 [backend/README.md](backend/README.md)；前端/视觉工具见 [web/README.md](web/README.md)。未配置数据库时存活检查可用、就绪检查返回 503；前端显示企业登录与会议服务未接入。当前不创建会议、不访问麦克风、不调用公司模型或发布知识库内容。

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
