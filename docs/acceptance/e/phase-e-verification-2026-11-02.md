# 阶段 E 最终验证与证据（2026-11-02）

正式结论见 [summary.md](summary.md)。以下路径均相对于项目根目录，不包含登录 Cookie、CSRF、数据库口令或声纹主密钥。原请求文件是私有证据，保持 0600，不应上传为公共附件。

## 固定版本与运行

- AMA：/home/tanren/ama，main，正常部署与工作源均为 c2e2de9006ed4d7a98f61735178539a2d1257cbd4fc3afc6e3be4dcb25ec5f72。最终 CAS 的报告也记录相同双签名。
- 兄弟：/home/tanren/ama-integration-harness，保留 master 与既有改动；所有 CAS/人员目录/模型/知识库/邮件业务往返统一 HTTP 8792，SMTP 2525。
- Web 5174、API 8182、管理 8183；启动器的既有六角色保持运行，就绪返回 200/UP。
- V062、V063 仅追加；当前 Flyway 已应用 53 个迁移，最大版本 63，失败 0。原有版本缺号不是执行失败。
- 数据集列表读 V063 存储元数据，EXPLAIN 使用 operations_dataset_history 倒序索引；sort_buffer_size 保持 262144，未通过扩大服务器缓冲掩盖问题。
- 当前在线声纹测试登记均已撤销、密文清空；邮件撤销场景仍为 BLOCKED，查询/回放原回执不会重新排队。

## 回归记录

| 范围 | 根目录下日志 | 判据 |
| --- | --- | --- |
| 生产 Java | .tools/local-app/phase-e/final-java.log | BUILD SUCCESS；contracts/core/adapters/realtime/api/worker 200 份新鲜报告总 913，失败/错误/跳过 0；不统计 integration-tests 的旧 Surefire 文件 |
| 实际 MySQL | .tools/local-app/phase-e/final-it.log | GovernanceLifecycleIT 15、CorpusFeedbackIT 15、VoiceprintMatchingIT 16、MeetingDeletionIT 8，共 54，含继承执行；BUILD SUCCESS |
| Web | .tools/local-app/phase-e/final-web.log | TAP 总 606、pass 606、fail/cancelled/skipped 0 |
| 类型 | .tools/local-app/phase-e/final-type.log | vue-tsc --noEmit 退出 0 |
| 合同 | .tools/local-app/phase-e/final-openapi.log | 类型及运行时响应 schema 与 OpenAPI 字节一致，101 schemas |
| 构建 | .tools/local-app/phase-e/final-build.log | vite build 成功；保留原 bundle 大小提示 |
| 兄弟全回归 | .tools/local-app/phase-e/final-harness.log、final-harness.xml | 新鲜 JUnit 132、failures/errors/skipped 0 |
| 启动器 | .tools/local-app/phase-e/final-launcher.log | 20 tests，OK |
| 正常部署 | .tools/local-app/phase-e/final-deploy.log | 无临时绕过启动；迁移后六角色 true |
| 数据只读核验 | .tools/local-app/phase-e/final-runtime-database.json | 迁移、列表索引、邮件审计/状态、密文清理、就绪及主密钥文件权限 |

最终 JUnit 报告另复制至 .tools/local-app/phase-e/final-junit/，汇总与哈希记录在 final-verification.json、final-evidence-index.json。日志与复制件来自本轮实际执行，不依赖目录内历史失败文件的笼统计数。

可重复的无业务副作用回归命令先在项目根目录加载 .tools/env.sh；Java 生产模块使用 ./mvnw -B -ntp -pl backend/contracts,backend/core,backend/adapters,backend/realtime,backend/api,backend/file-service,backend/worker -am test；Web 使用 npm --prefix web run test:unit、run typecheck、run check:api、run build。实际 IT 必须向已批准的独立 MySQL 测试库注入 AMA_TEST_DATABASE_*，使用当前私有 test-database-speech-current.json，不能把失效的旧 resume-c-it-env.json 或运行库当测试库。

## 正常部署后的 CAS

完整最终报告：.tools/local-app/phase-e/cas-08-final/report.json，日志 cas-08-final.log。复用工具为 tools/dev/governance_cas_integration.mjs。每次新业务场景必须选择从未使用的输出目录；工具在发出变更前保存原键原体，不能为掩盖旧 FAIL 重跑已接受的一次性命令。

1. 负责人、平台管理员、普通成员分别走固定 harness 的真实 CAS，再验证服务端当前身份/角色。
2. 实际 UI 声纹登记：屏蔽已受理响应，刷新后按原键原体恢复；返回内容没有特征向量。
3. 管理员审核、本人撤销；其他身份调用被拒；撤销不能复活。
4. 绑定当前源修订提交反馈，管理员无源会议访问权仍能审核最小披露建议；撤回立即退出消费。
5. UI 创建三样本不可变数据集；真实治理 worker 得到逐样本结果，人工复核后发布经正负声纹样本评测的阈值。
6. 失败样本保留总数与分母；对应配置发布被拒。
7. 回滚追加修订并恢复原完整配置，不修改在途评测快照。
8. 邮件负责人、当前收件授权、过期修订、其他身份边界；AMA 受理丢 ACK 后刷新原请求；远端返回不可确认成功时先 UNKNOWN、后按业务键查为 SENT；harness 请求日志证明每个业务键仅一次发送 POST。
9. 普通/管理员声纹、语料、评测、配置、邮件六种状态在 1440×900 与 1024×768 共 12 图；document.scrollWidth 不超过视口。
10. 真实另一标签页退出后刷新，私人治理内容清空。

最终对象只用于读取和技术恢复，不应再次登记、审核、发布或发送：

| 对象 | ID |
| --- | --- |
| 已撤销声纹 | 7bda234f-192f-4351-850d-16daed89020e |
| 已撤回语料会议 / 反馈 | c9753454-b786-4f3f-9bc9-a5bea9c6b876 / cc4c4031-8349-4e80-92b4-881e1e0cee4f |
| 不可变数据集 / 完成评测 | b58c990f-36fd-4233-8f45-1a5536bfc25f / ab2a8c84-44df-4a03-b0f8-401e65f93af5 |
| SENT 邮件 / harness 消息 | 491f45bf-c599-44e8-9797-7f2aed8a451d / msg-bc55f8eaac45ca3a455db513 |

## 来源撤销的真实邮件边界

独立新源先经实际 AAC→worker→RAW→工作稿/确认→纪要生成与审阅，见 mail-source-ready-01/report.json。mail-withdrawal-01/failure.json 前两组已证明实际冻结发布及“已受理邮件在执行前撤回来源”：任务 BLOCKED，harness 没有该业务键发送请求。随后历史回执恢复返回 403，原 FAIL 原样保留。

修复服务元数据授权及 ExternalReadPreparation 的提前门禁后，使用同一已发布/撤回对象与同一私有原请求接续，未重做冻结/发布、未创建新邮件：

- mail-withdrawal-final/report.json：原受理元数据仍可回放；同一 BLOCKED 任务且无第二行；新键发送与其他人恢复均拒；远端业务键仍查无发送，4 项通过。
- mail-closed-ui-final/report.json：原负责人实际弹窗能看 BLOCKED 结果、发送按钮不可用；其他人看不到私人任务；两个桌面截图，新增邮件 POST 为 0，2 项通过。
- 会议 932bf3fe-8ae2-46d9-9471-2b202ea3eb38，发布 d95335ba-b210-4ffa-bfc8-fb8a67bc6f96，BLOCKED 邮件 86029388-7807-4132-9a61-c3460c20ffd6。
- tools/dev/mail_withdrawal_cas_integration.mjs 仅供未来全新“已完成审阅但尚未冻结”的独立夹具。当前原件应使用私有 mail_withdrawal_resume.mjs 只读恢复，不再运行一次性创建脚本。

## 失败保留与解释

本轮先保留核心策略、加密、harness 重复发送和 Web 原请求的 RED，再实现通过。真实缺陷包括：源提交者停用后仍可消费、有限 PII 模式未遮蔽、嵌套配置漏字段误收、大数据集 JSON 排序触发 MySQL 1038、JDK h2c 造成 harness 收到空 JSON、来源撤销后历史回执被提前门禁拦截。均有相应修复与最终证据；不通过改原报告、放松授权或换键重提解释成成功。

cas-01～06 的原 FAIL、旧失效测试库连接失败、修改依赖期间的瞬态 Web 失败、mail-withdrawal-01 与早期恢复失败全部留存。早期已接受写入使用原日志查询确认；声纹已撤销、已确认语料按原件处理。cas-07 是前一次完整通过，cas-08-final 是冻结部署后的最终通过。旧失败不被覆盖，新验证也不声称旧批次曾通过。

本证据限定本地受控桌面功能、契约和副作用边界；真实语义质量、企业规则审批、企业邮箱送达及 F 的容量/部署资格未在此认证。
