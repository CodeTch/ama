# 账号 1 多场景测试数据重建实施计划

> **2026-10-30 范围关系：** 本文是 [B 阶段后续总计划](2026-10-30-ama-b-followup-pixel-crud-cas.md) 的测试 fixture 子任务。数据只用于真实页面 CRUD、权限和像素对齐；不得用脚本直接写入业务结果来替代浏览器/API 操作证据，也不改变 CAS harness 的固定端口或业务语义。

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 清空当前项目专属 MySQL 的业务数据，并为账号 1 重建覆盖类型、阶段、生命周期、治理状态、日期和参会关系的可重复测试数据。

**Architecture:** 使用开发工具脚本校验项目自有 MySQL 后按外键依赖清理业务表；通过现有会议 HTTP 接口建立会议、信息、名单、授权、审计和 outbox 基础事实，再用受控 fixture 更新补齐展示所需的阶段/生命周期/治理状态。脚本输出摘要并可重复运行。

**Tech Stack:** Python 3、Docker MySQL 8.4、项目本地 HTTP API、Flyway schema。

**Spec:** `docs/superpowers/specs/2026-10-30-account1-test-data-reset-design.md`

## Global Constraints

- 只操作项目专属 `ama_local_dev`，不连接其他数据库。
- 只在 `/home/tanren/ama` 的 `main` 分支工作，不创建 worktree。
- 保留表结构和 `flyway_schema_history`，清理后恢复 `FOREIGN_KEY_CHECKS=1`。
- 脚本必须在执行前校验容器名、数据库名和回环连接。

## Review Focus

- 清理漏表或外键顺序错误：验证所有业务表计数为零后再重建。
- 重跑累积重复数据：使用固定命名空间并验证重跑计数稳定。
- 阶段/状态展示样本缺少根记录：逐项验证八种 stage、三种 lifecycle、五种 governance_state。
- 名单与授权投影不一致：验证 confirmed roster、grant 和 person projection 数量。
- 临时会话泄漏：执行结束删除脚本创建的账号 1 session。

### Task 1: Add reset and scenario seed tool

**Files:**
- Create: `tools/dev/seed_account1_scenarios.py`
- Test: `tools/dev/tests/test_seed_account1_scenarios.py`

- [ ] **Step 1: Add dry-run validation tests** for owned container/database checks, cleanup table ordering, scenario counts, and summary shape.
- [ ] **Step 2: Implement connection discovery, cleanup, identity bootstrap, HTTP API creation, fixture state matrix, and summary output.**
- [ ] **Step 3: Run the focused Python tests.**

### Task 2: Execute the reset and seed against the current project MySQL

- [x] **Step 1: Run the reset and seed against the current owned database.**
- [x] **Step 2: Verify all required stage, lifecycle, governance, category, date, roster, grant and foreign-key counts.**
- [ ] **Step 3: Re-run after the local application is available for a full repeatability check.**

### Task 3: Final verification

- [ ] **Step 1: Confirm `FOREIGN_KEY_CHECKS=1`, no orphan rows, and account 1 remains ACTIVE.**
- [ ] **Step 2: Run repository checks relevant to the new tool and report the resulting summary.**
