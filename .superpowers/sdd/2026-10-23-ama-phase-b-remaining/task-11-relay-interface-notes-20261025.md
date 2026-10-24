# Task11 relay 已批准接口的只读接线笔记

状态：replacement 独立审冻结期；仅只读核对，未修改产品、未启动业务或发送 provider 请求。本笔记落实 R1 §7.1/HTTP 表，不新增设计。

| 实际位置 | 已有能力 / 待接点 |
|---|---|
| `tools/providers/controlled_knowledge.py` → `Provider.route` 的 `/v1/knowledge/documents/{id}` 分支 | 校验服务 client；以委托的实际 viewer 调用 `grant(actor,library,folder,True)`；返回 immutable payload、payloadSha256、publicationId、documentId、resultId、provider/store。应用 READ 不能替代此 grant。 |
| `backend/adapters/src/main/java/ama/transcript/ControlledKnowledgeClient.java` → `authorized` / `exchange` | 固定 pinned loopback、私有 bearer+Ed25519 delegation、purpose/method/resource/body digest、一跳有界 transport。新 relay 必须使用 DOCUMENT_READ、实际 viewer、固定 provider document ID，不能把 DB document_url 当任意 fetch URL。 |
| `JdbcPublicationService.Authorization` / `authorize` | 当前 authorize 固定 EDIT。relay 必须接实际 READ S/F gate；不能沿用 EDIT 排除合法 member，也不能以 receipt lookup 权限读文档。 |
| `backend/adapters/src/main/java/ama/meeting/JdbcMeetingAccess.java` → `lock` / `require` | READ 已校验 actual ACTIVE identity、meeting lifecycle/governance、owner 或当前 confirmed roster + actual grant + ENTERPRISE member 链；roles 不构成 meeting access。 |
| `PublicationController` / `PublicationErrors` | 待接 GET `/publications/{publicationId}/document`、owner/member status 与同源 officialDocumentUrl；错误保持真实认证/ACL/供应方拒绝分类。 |

既定流程：S 下真实 meeting READ + 精确 immutable mapping/provider/document/final + lifecycle，保存有界元数据；根锁外 DOCUMENT_READ，验证实际 response IDs 与 canonical payload digest；F 重新锁真实 READ/mapping/lifecycle 后才发有界逃逸静态 HTML。private,no-store；CSP default-src none/frame-ancestors self；nosniff。无重定向、外部资源、原始 HTML/Markdown 执行、URL/cookie/DOM 中的 provider credential。owner/member sanitized status 不返回内部 document_url、manifest、proof 或 service identity secret。

合法 fixture 顺序：
1. 继续使用 Task11 fresh schema/原 provider store/keys，任何新首次 publication POST 按既有 prestart identity/source custody。
2. 创建独立 owner/member/outsider/admin 身份和 session。新 meeting 在 final 前通过现有 save-info/roster-confirmation 注册 member；base `Task11FinalPublicationIT.afterCandidate` 当前写空 roster，未来仅增合法 roster hook。
3. actual provider operator 为 owner/member 配置 READ（发布 actor 另需 PUBLISH），真实 final→首次 publish 成功后测试同源 official view。
4. 分别撤销 meeting READ 与 provider READ，验证双 ACL；outsider/role-only admin/direct provider URL 各否认；检查无凭据泄漏/重定向/可执行内容。恢复与清理也使用实际协议。
5. 现有 main03/lost-response01 成功 publication roster 为空；不得 SQL 添加 published 成员或假 grant 链。accept04 replacement no2 继续禁 claim/send，不能用于绕独立门禁的 relay setup。

未验证：relay Java/API 尚未实现；member HTML/浏览器、全部撤权矩阵、fresh-process 与 S3 对照仍 PENDING。provider destination 现只有实际 controlled-library/controlled-root；不同目标语义的后续 fixture 必须通过受控 operator 合法准备，不能伪写应用 publication 绑定。
