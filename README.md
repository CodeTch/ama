> **本轮边界（最新）：** 用户要求先完成“我的会议”首页像素复刻并停下。首页所列静态场景32项/真实交互13项已通过；详见 [docs/acceptance/b/homepage-pixel-parity-2026-10-29.md](docs/acceptance/b/homepage-pixel-parity-2026-10-29.md)。其余页面不自动继续，整套原型/B阶段仍为 NOT ACCEPTED。

> **最新用户指令：逐页1:1、像素级复刻。** 旧“不做像素级比对”规则已撤销；既有报告仅证明其原范围，新像素矩阵尚未通过。当前计划：`docs/superpowers/plans/2026-10-29-pixel-perfect-prototype.md`。

> **当前原型全量复刻仍未验收通过。** 用户要求全部页面和可达功能复刻原型，撤销旧“未来功能仅做页面”豁免；不做像素级比对。

# 企业智能语音会议助手

当前按[全量原型补齐计划](docs/superpowers/plans/2026-10-29-full-prototype-completion.md)和[验收标准](docs/acceptance/b/prototype-parity-2026-10-29.md)继续返工。真实五种文档与七种媒体导入、回听、分段录音和后续处理已取得局部验证；转写编辑、全文替换、两类ACK/并发恢复及45段核心生成/发布恢复的新证据见[本批记录](docs/acceptance/b/transcript-edit-budget-2026-10-29.md)。实时识别、材料、说话人/纠错、专题、模板/局部生成、反馈等仍有 OPEN。当前源代码与证据状态见[持续验证记录](docs/acceptance/b/prototype-parity-validation-2026-10-29.md)和[接续说明](DEVELOPMENT_HANDOFF.md)。

旧 R2/M1-B 的 ACCEPTED/独立 APPROVE 只对应此前限定的配置、后端与受控流程，其报告继续保留；不能替代这次全页面、全功能验收，也不表示当前没有实现待办。旧来源见[正式本地B记录](docs/acceptance/b/formal-local-b-2026-10-29.md)。

完整结果、测试数量、审查修复、待验证项和 B 阶段入口见[阶段 A 验收报告](docs/acceptance/a/summary.md)。

## 本地开发

需要可登录、可读写会议数据的本地环境时，先启动兄弟联调项目 `ama-integration-harness` 的 CAS 服务，再在本工作树执行 `python3 tools/dev/local_app.py start --web-port 5174 --api-port 8182 --management-port 8183`，然后打开 `http://127.0.0.1:5174/meetings`。开发和生产都经过服务端 CAS 票据校验后建立 AMA 会话；启动器使用独立持久化 MySQL 和示例会议。CAS 账号、代理配置及停止/重启方法见[本地开发说明](docs/development/local-development.md)。

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

后端启动见 [backend/README.md](backend/README.md)；前端/视觉工具见 [web/README.md](web/README.md)。未配置数据库时存活检查可用、就绪检查返回 503。现有本地启动器可登录、创建会议、保存信息及确认名单，并已接通本地 mTLS 文件服务与独立 TXT Worker，可完成有序 TXT 上传导入、转写编辑保存及确认。已安装本地受控模型、候选/编辑/审阅、最终冻结及签名知识库发布，负责人/成员正式文档双层 ACL 与全部组件普通重启已有实际证据。固定合成结果不代表真实 AI 质量；Demo 主要结构与代表性冲突/IME 已验收，深层稳定性资格继续 B-QUAL。实时录音、公司模型/知识库真实集成和生产资格仍以各阶段记录为准。

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
