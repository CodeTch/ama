# S3 物理键资格调查（未关闭）

2026-10-22；真实Java SDK测试中的发现。参考服务仍是RustFS1.0.3受控本地开发fixture，不是已选定/已验收的生产S3服务。[此前原语探针](s3-rustfs-capability-preflight.md)的成功/失败记录保留，本记录追加实际适配器阶段发现，不撤销或改写旧证据。

## 已观察事实

- Java SDK原始PUT：单段255byte成功200，256/512/1024byte均400 InvalidArgument；相同总长的分段样例当时成功200。`S3KeyBudgetDiagnosticIT`期望512普通生成键成功，实际1failure/0error，原断言及日志保留；不能改成接受400以宣称通过。
- 原fixture第一次退出139：2026-10-21T17:42:36.827183472Z，OOMKilled=false。一次原实例重启于17:44:22.855772711Z后约10.42秒再次退出139，OOMKilled=false。日志记录rustfs-worker stack overflow。共两次退出、一次重启。
- 原始深路径探针发生在退出之前；尚未证明精确因果代码路径，也未证明浅层映射能修复该服务故障。随后ConnectionRefused属于fixture失败，不计业务行为RED或成功。
- 原container/data/故障日志保留；新浅层测试使用独立owned目录、container、固定loopback endpoint和后端/config/bucket身份，不将旧token/历史数据重指向新实例。深层raw诊断退出可发现JUnit源集，以保留证据形式存档。

协调者只读核对了原始key诊断汇总、两份退出State及stack日志；没有自行请求provider、重启或修改数据。源v2适配器22文件快照已保全，SHA `68674b9dfa709302ac2cb1b5c6dd31ed1cccd73a767b04181eb2c8ccfd1952be`。上述原始证据和source-only快照已复制到 `artifacts/phase-b/s3-key-envelope/` 并带SHA清单，无凭证文件。

## 实施门

原data/v2映射可让512普通逻辑键留在单个物理段，因此不能凭旧总长预算宣称符合应用信封。保持StorageKey和产品文件名语义，物理层修订采用新布局/身份；不要求业务自行分段，不增加255产品字数限制，不把objectKey全hash后扫描整个namespace。

独立设计作者已提出v3保序转义、scope全绑定及有界分段候选；根已读完整提案，fresh独立设计审查进行中，尚未采用或实施。不修改原设计SHA，不重新解释已生成v2对象。后续必须真实证明单段/总长/深度、prefix/order/pagination、重启、共同合同和协议恢复，分别标注旧/新布局执行证据。数学方案不代替实际provider资格。

生产服务选择、支持写入者策略、durable ACK、容量/HA、旧fixture故障根因及实际平台资格仍为开放门；这项调查不批准生产部署或修改任何MySQL迁移。
