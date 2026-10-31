# AMA CAS v1 Login Implementation Plan

> **2026-10-30 范围关系：** 本文是 [B 阶段后续总计划](2026-10-30-ama-b-followup-pixel-crud-cas.md) 的 CAS 子任务。登录、退出、会话和所有受保护页面 API 的浏览器验证必须使用固定端口 `ama-integration-harness`；harness 只作可重复模拟器，固定 HTTP `8792`/SMTP `2525` 不改，实际请求证明兼容缺口时同步增加最小兄弟项目回归。

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Make AMA's credential login interoperate with KunLunPlugin's CAS v1 `/v1/tickets/` flow while preserving server-owned HttpOnly sessions.

**Architecture:** Extend the existing server-side `CasClient` with TGT acquisition, service-ticket acquisition, and bounded HTML action parsing. Keep `CasAuthenticationController` as the only credential entry point and feed the resulting ST through the existing CAS XML validation and `SessionIssuer` path.

**Tech Stack:** Java 21, Spring Boot MVC, `java.net.http.HttpClient`/`HttpURLConnection`, JUnit 5, local `HttpServer` test doubles.

**Spec:** `docs/superpowers/specs/2026-10-30-ama-cas-v1-login-design.md`

## Global Constraints

- 修改和验证均在 `/home/tanren/ama` 的 `main` 分支完成。
- TGT、密码和 ST 只在 API 服务端处理，浏览器只收到现有 `__Host-AMA_SESSION`。
- CAS REST 端点采用标准 `POST /v1/tickets/` 获取 TGT、`POST /v1/tickets/{TGT}` 获取 service ticket。
- 不改变会议 API、会话表结构或现有 CAS XML 安全解析规则。

## Review Focus

- CAS 返回 201 但缺少或伪造 `action`：必须归类为 provider failure，不能签发会话（Task 1）。
- 凭据错误和 CAS 暂时不可用：必须保持 `INVALID_CREDENTIALS`/`PROVIDER_UNAVAILABLE` 映射（Task 2）。
- TGT/ST 中包含路径分隔符、控制字符或超长值：必须拒绝并且不发起后续请求（Task 1）。
- 服务端 HTTP 重定向：必须禁止自动跟随（Task 1）。
- 成功登录后的浏览器响应：只能设置现有 HttpOnly 会话 Cookie，不得暴露 TGT/ST（Task 2）。

### Task 1: Implement CAS v1 ticket exchange

**Files:**
- Modify: `backend/api/src/main/java/ama/api/CasClient.java`
- Test: `backend/api/src/test/java/ama/api/CasClientTest.java` (create if absent)

**Interfaces:**
- Consumes: existing `CasClient.login(String,String)` and configured `cas.origin`/`cas.service-url`.
- Produces: `CasClient.login(String,String)` returning the callback URI after obtaining a service ticket; existing `validate(String)` remains the XML validation boundary.

- [ ] **Step 1: Write failing local-server tests** for 201 HTML TGT extraction, service-ticket exchange, invalid action, provider 400/500, and redirect rejection.
- [ ] **Step 2: Run the focused adapter/API test and verify the new cases fail.**
- [ ] **Step 3: Implement bounded v1 exchange**: POST form data to `/v1/tickets/`, extract an HTML `action` ending in `TGT-*`, POST `service` to that TGT resource, accept only a valid `ST-*`, and return the configured service URI with `ticket` query parameter. Use direct connections, finite timeouts, no redirect following, and never include credentials/tickets in exceptions.
- [ ] **Step 4: Run the focused tests and verify they pass.**

### Task 2: Wire controller behavior and regression coverage

**Files:**
- Modify: `backend/api/src/main/java/ama/api/CasAuthenticationController.java`
- Modify: `backend/api/src/main/java/ama/api/CasClient.java` if needed for error normalization
- Test: `backend/api/src/test/java/ama/api/CasControllerTest.java`
- Test: `backend/api/src/test/java/ama/api/CasProtocolTest.java` if parser coverage is needed

**Interfaces:**
- Consumes: Task 1's server-side `CasClient.login(username,password)` result.
- Produces: unchanged `POST /auth/cas/login`, callback session issuance, safe redirect error codes, and existing logout behavior.

- [ ] **Step 1: Add controller tests** proving a successful credential login redirects through the callback path without returning TGT/ST, and that invalid credentials/provider failures retain existing `/login?error=...` codes.
- [ ] **Step 2: Run the focused controller tests and verify the new expectations fail.**
- [ ] **Step 3: Keep the controller as the session boundary**, translating only the normalized client failures and preserving origin checking, HttpOnly cookie issuance, identity mapping, and logout callback clearing.
- [ ] **Step 4: Run all API CAS/security tests.**

### Task 3: Documentation and full verification

**Files:**
- Modify: `backend/README.md`
- Modify: `README.md` only if environment variable or endpoint guidance changes

- [ ] **Step 1: Document that credential login uses server-side CAS v1 TGT/ST exchange and that no browser token storage is supported.**
- [ ] **Step 2: Run the backend module test suite and the relevant web unit tests.**
- [ ] **Step 3: Inspect `git diff`, confirm user changes to `README.md` are preserved, and record fresh test output.**
