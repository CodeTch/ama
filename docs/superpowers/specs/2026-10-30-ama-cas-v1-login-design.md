# AMA CAS v1 登录调整设计

## 意图

参考 `KunLunPlugin` 的企业 CAS 登录协议，将 AMA 的登录请求改为服务端调用 CAS REST API：先向 `/v1/tickets/` 提交用户名和密码取得 TGT，再用 TGT 为 AMA 的 service 申请一次性 ST，最后沿用现有 CAS 校验、身份同步和本地会话签发流程。

## 目标

- 支持 CAS v1 REST 登录返回的 `201` HTML 表单，并严格提取 `TGT-*`。
- TGT、密码和 ST 只在 API 服务端处理，浏览器只收到现有 `__Host-AMA_SESSION`。
- 保留现有身份属性映射、数据库会话、HttpOnly/Secure Cookie、CSRF、登出和错误码语义。
- 覆盖成功、凭据错误、非法响应、服务不可用和票据校验失败。

## 非目标

- 不把 TGT 存入前端存储或暴露给浏览器。
- 不改变会议 API、会话表结构或现有 CAS XML 安全解析规则。
- 不依赖真实企业 CAS 才能运行单元测试。

## 约束

- 修改和验证均在 `/home/tanren/ama` 的 `main` 分支完成。
- CAS origin、service URL、logout service URL 继续使用现有环境变量。
- CAS REST 端点采用标准 `POST /v1/tickets/` 获取 TGT、`POST /v1/tickets/{TGT}` 获取 service ticket；请求禁止跟随重定向并使用直连网络。

## 方案

- 方案 A：服务端完整接管 TGT 和 ST（采用）。安全边界与现有数据库会话一致，只需扩展 CAS client 和 controller。
- 方案 B：前端直接保存 TGT。实现较短，但会绕过 HttpOnly 会话、服务端撤销和 CSRF 保护，因此不采用。

## 实现要点

`POST /auth/cas/login` 保持现有表单接口。CAS client 将凭据提交到 `/v1/tickets/`，只接受成功状态并从 `action` 属性提取 TGT；随后向 TGT 资源提交 `service`，校验返回的 ST 形态，并将其用于现有 `/cas/p3/serviceValidate`。错误仍映射为 `INVALID_CREDENTIALS` 或 `PROVIDER_UNAVAILABLE`。新增协议测试使用本地 HTTP server，不记录凭据或票据。
