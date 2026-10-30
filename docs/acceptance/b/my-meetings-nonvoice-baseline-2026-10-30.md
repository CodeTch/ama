# 我的会议非语音基线（2026-10-30）

本记录只覆盖本计划 Task 1 的 CAS/fixture 基线，不把后续页面 CRUD 记为通过。

## 已验证

- 工作目录：`/home/tanren/ama`；分支：`main`。
- `python3 -m unittest tools.dev.tests.test_seed_account1_scenarios -v`：2/2 通过。
- `source .tools/env.sh && ./mvnw -pl backend/api -Dtest=CasControllerTest,CasClientTest test`：18/18 通过。
- `node --check tools/dev/import_cas_integration.mjs`：通过。
- Fixture 声明 `1`、`dev-owner`、`dev-member`、`dev-viewer`、`dev-admin` 和权限负例 `dev-revoked`；平台管理员仍由 `platform_admin` 与 `role_assignment` 双重记录。
- `FIXED_PORTS=1` 会拒绝非 `http://127.0.0.1:8792` 的 CAS origin；错误信息不包含票据、密码或会话值。

## 尚未执行/阻塞

- 当前会话没有启动 `ama-integration-harness`（固定 HTTP `8792`、SMTP `2525`）和完整本地应用，因此未运行浏览器 CAS→会议→导入链；该项属于后续 Task 8 的真实联调证据。
- 负例命令 `FIXED_PORTS=1 AMA_CAS_ORIGIN=http://127.0.0.1:8793 node tools/dev/import_cas_integration.mjs` 按预期以退出码 1 拒绝错误端口。

## 证据边界

CAS 单测证明 callback、ticket 校验、来源限制、session cookie 和 logout revoke 的代码契约；它不替代固定 harness 的浏览器请求日志。后续报告必须附 harness revision、AMA source hash、fixture 标识和非敏感请求状态，不记录密码、TGT、ST、Bearer、Cookie 或私钥。
