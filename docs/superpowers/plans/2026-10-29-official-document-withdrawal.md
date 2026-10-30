# R2 正式文档读取中的撤权

承接唯一 B-R08/06/12。复用当前日常单机中真实已发布的资格会议/文档，生产工厂创建的完整 Processing/Transcript/Minutes 装配及实际隔离 loopback HTTP 与签名授权知识库客户端（ConfiguredPublication 的 ISOLATED_LOOPBACK_TEST 模式；本批不宣称 TLS/mTLS）。只改变本地测试身份状态或通过正常提供方管理命令撤回读取授权；不写 SQL 业务行，不改冻结/发布事实，不恢复封存资源。

- 基线：dev-member 双方有效授权，genuine officialDocument 返回非空 HTML，记录摘要和真实客户端 GET/POST 计数。
- 会议侧：利用 PreparationCancellation 的真实注册关闭回调，暂停在实际网络响应已完整解析、socket已关闭、HTTP exchange 尚未向调用者返回的位置，确认没有事务。短暂停用精确 dev-member 目录身份；释放后 F 当前身份/会议授权拒绝，HTML 不返回，原业务快照不变。finally恢复原状态，genuine再次读取与基线字节一致。
- 提供方侧：利用真实网络注册回调暂停在签名调用已构建、实际连接/发送尚未开始的位置（S/manifest已完成且没有事务），用正常 owned provider CLI 撤回 dev-member READ，再释放。实际 GET 返回403，genuine service抛具体 OFFICIAL_DOCUMENT_FORBIDDEN，不返回HTML，POST计数不增加。finally按已核验原授权恢复，读取回到同一摘要。该位置证明请求期间当前提供方授权，不声称撤权能追回已经交付给调用者的字节。
- 两种模式必须真实生产工厂和同客户端，不能以伪JSON、替代provider或直接调用私有F来证明。资格工具有限等待且原身份/授权有私有备份，父编排finally恢复；读业务零修改与显式目录/授权变化分开记录。

运行源码/工具变化后普通start刷新指纹，再验证；不在native暂停窗口变更源码。完成同一独立reviewer审查后更新B摘要，不将代表两条竞态写成所有读取端点或整个B通过。

独立审查 bqual-official-document-withdrawal-review-20261029.md 指出原计划将本客户端误写成mTLS。该描述现按实际配置修正；两条 genuine 撤权断言不变，不通过改运行协议来追认原表述。文件服务的本地mTLS及历史独立TLS结果保持各自原证据范围。
