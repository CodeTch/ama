# 知识库材料导入修复验收（2026-11-01）

本次范围是“导入会议→从知识库选择背景材料→添加/恢复/读取”。AMA 与兄弟 harness 同步修改，单 agent，主项目 main，无分支/worktree/提交/数据重置。harness 保持固定 HTTP8792/SMTP2525；本地签名和权限只用于共同开发的模拟协议。

## 修复

- 统一 Python/Java 对所有控制字符的 canonical JSON 编码；字面量反斜杠不被误替换。列表、下载、版本、大小和授权采用同一摘要。
- harness 显式载入本地保留的开发身份，验证 bearer、Ed25519 委托、actor、请求方法/完整资源/正文摘要/期限；按同一目录和实际版本生成五秒批量权限证明。种子、已解析上传草稿与既有受控发布文档均可在明确权限下读取；撤权持久保存。
- AMA 保留完整性校验和原请求；版本预校验失败通过原回执表持久拒绝，与成功提交共用根锁。只有确定的拒绝可移除并重新选择；未知响应不得丢弃。
- 前端保留安全错误码/追踪号，明确提示版本/内容/权限失败；重新选择时清除旧私有选择，关闭时清除旧列表，防止再次提交旧版本。
- 持久化 AMA 的 allowlist 启动设置、harness 的材料身份/读者/桥接/CAS5174白名单；正常启动不依赖旧 shell 的临时环境变量。

## 最终验证

| 范围 | 结果 | 证据（项目根目录下） |
|---|---|---|
| harness 后端全套 | 53 PASS | `.tools/local-app/material-repair-harness-all2.log` |
| Java 生产模块 | 854 PASS，0失败/错误/跳过 | `.tools/local-app/material-repair-java-units.log` |
| 材料回执/权限 MySQL | 11 PASS | `.tools/local-app/material-repair-receipt-green.log` |
| 前端完整单元测试 | 484 PASS | `.tools/local-app/material-repair-web-final2.log` |
| 本地启动配置工具 | 19 PASS | `.tools/local-app/material-repair-runtime-green.log` |
| 类型检查、构建、API一致 | PASS | `material-repair-types-final2.log`、`material-repair-build-final2.log`、`material-repair-web-build.log`（均在 `.tools/local-app/`） |
| 三份截图材料、真实 CAS/主文件上传/丢ACK恢复/摘要/保存重开 | PASS | `.tools/local-app/material-repair-e2e/actual-04/report-prepare.json` |
| 整套服务和数据库重启、原键重放、三份文件下载/页面恢复 | PASS | `.tools/local-app/material-repair-e2e/actual-04/report-recover.json` |
| 最终源码再部署后的三份材料回读 | PASS | `.tools/local-app/material-repair-e2e/final-readback/report-recover.json` |
| 真正版本变化后的拒绝、清选择、刷新版本、重新添加及内容读取 | 4组PASS，无页面错误 | `.tools/local-app/material-repair-e2e/reselection-5f37e806-6d13-4f0e-9cca-3a90a0b9c610/report.json` |

三份材料为“存储每月价格走势”、场外期权估值报告、“合规制度材料 01”。保留会议 `6a6c4cc9-3866-4fa7-a393-b4e3ab1f0f57`。两个原始上传文件的物理 SHA-256 在整套重启后保持不变。最终服务六角色全部存活，API ready UP、harness materials configured，源码/部署一致；机器证据 `.tools/local-app/material-repair-final-verification.json`。

失败证据全部保留：初次直接启动遗漏CAS5174白名单；丢响应测试驱动的默认5秒断言及关闭上下文失败；CDP读取响应正文失败；重新选择时旧行短暂显示的真实界面竞态。驱动失败未改写为通过；actual-02 已提交的三份材料通过原请求摘要核对恢复，没有以新键冒充原请求。最初 IT 连接参数失效和文件服务夹具未就绪也保留；最终11项并非整个历史 integration-tests 套件。

按单 agent 约束做了定向代码自查，没有独立子 agent 审查。本验收不扩展到历史 B/C/D 全阶段、真实公司接口认证或语音模型质量。
