# D02 查询范围验收

2026-11-01 当前部署 `650986bb024d428829550b3582f015c8802aa0ccac4a2c52e42a9b78b6b6342f`，main，单 agent，固定 harness HTTP 8792 / SMTP 2525。AI 信息建议仍单独进行，本文件不标记阶段 D 完成。

## 实际 CAS 与页面

`.tools/local-app/d02-query/cas-03/report.json` 5 组 PASS，源/运行版本一致，页面 JS 错误 0。通过真实 CAS 登录、创建和保存 API 建立 12 条新会议，无业务 SQL 注入：字面 `%_=` 和大小写检索，两页 10+2 条且总数/阶段统计一致；日期 null 不混入日期范围，UUID 大写检索；专题分配与同条件统计；外人查询为零、外人专题 403；输入防抖、实际翻页及 1440/1024/390 截图。

失败保留：cas-01 目录创建失败（脚本未创建父目录）；cas-02 真正复现未匹配筛选的旧外部来源让查询整体 503。数据库同时暴露材料变更未更新 search projection 的问题。修复公共筛选关系用于外部准备，`JdbcMaterials.advanceSnapshot` 同事务更新投影，V052 仅对内容/名单/日期/任务 fence 全部吻合的旧投影补齐根版本。没有修造外部来源。用户原有旧记录 `54e9ddac-3f7b-4349-bc11-e1229c792482` 的 `doc-kb-compliance-01` 缺失库位置且当前 fixed-harness catalog 不包含该文档；不带筛选、包含它的请求仍按 UNKNOWN 返回 503，有错误提示且可继续筛选。此记录及失败证据保留，未用有效假数据替换。

## 组件与容量

- `continue-d02-full-java1.log` 全生产模块 BUILD SUCCESS；`continue-d02-full-web1.log` 前端 467 PASS，typecheck5 PASS。这是 V052 修复前全套，后续新改动有独立回归，不能把该全套当后续全部代码的验证。
- 最新真实 MySQL `MeetingQueryIT` 2、`MaterialAuthorityGateIT` 5、`TopicListFilterIT` 3 PASS，XML 留在 `.tools/local-app/d02-query-latest-it/`。这些 fixture 的创建根是实际 service，信息/名单为明确标记的 SQL fixture，不代替上面的真实 CAS 保存。
- 修复后 `MaterialSnapshotFenceTest` 1 PASS（先 RED），`MaterialAuthorityGateIT` 5 PASS（不匹配查询不能准备无关外部证明，先 RED）。日志 `continue-material-list-projection-green1.log` / `continue-d02-unrelated-proof-green1.log`。
- 容量 fixture 前缀 a6d5831b：10 万会议、1000 人、100 万转写片段；Java `-Xmx384m` 实际公共关系执行。`.tools/local-app/meeting-query-capacity/a6d5831b/report-reuse5.json` 三类查询分别 50000/1000/100 个匹配，2508/2114/2393 ms，实际 EXPLAIN ANALYZE 一并保存。此为无其他重测并行的单客户端查询基线，非真实模型/完整录音并发容量验收。
- 初始三遍统计扫描超预算，后改一次 stage/topic 联合分组统计；第二次人员查询超行预算，修正为分配固定有界数值缓冲、按实际读取行收费；reuse4 在全前端测试并行时超时，仍如实 FAIL，reuse5 独立复测 PASS。不提高原 5 秒预算掩盖问题。
