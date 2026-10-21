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

独立设计作者已提出v3保序转义、scope全绑定及有界分段修订；根完整读提案和fresh独立审查，SPEC/design-quality均PASS、无发现项，现采用为实施依据，尚未实现或真实资格验证。不修改原设计SHA，不重新解释已生成v2对象。后续必须真实证明单段/总长/深度、prefix/order/pagination、重启、共同合同和协议恢复，分别标注旧/新布局执行证据。数学方案不代替实际provider资格。

生产服务选择、支持写入者策略、durable ACK、容量/HA、旧fixture故障根因及实际平台资格仍为开放门；这项调查不批准生产部署或修改任何MySQL迁移。

## v3 设计采用（2026-10-22）

[精确设计](../../superpowers/specs/2026-10-22-ama-s3-key-layout-v3.md)原始SHA `065fd8b7f4852c8d395352d83083a67ad800523bd9ca5fcf1c1fb5db8f8c702b`，[独立审查](s3-key-layout-independent-review.md)原始SHA `8e280d7d84d6968fba53f7e40c2ad9f4393f5c550fbee4a420697303bf517b43`。原始报告和SHA另保存在 `artifacts/phase-b/s3-key-layout-v3-design/`；Git文档可能规范化换行，原始审查字节由该归档保全。[原整体设计](../../superpowers/specs/2026-10-22-ama-s3-file-infrastructure-design.md)协议及原SHA保持，只有此修订显式替代v2物理映射。

设计选择：scope摘要有完整静态身份绑定，F保序转义，199编码字符自动分段且每段加x；普通生成字母/数字/下划线/连字符512字符为590物理byte/6段，最大段200、总预算1024及深度8。不是所有512字符ASCII标点串的可表示保证，不改变LONGTEXT产品文件名规则。实际服务端生成语法由后续B04注册器明确并验证，不靠VARCHAR512推断。凭证轮换/超时/current revision不进入物理路由，物理namespace重指用新backendID。

仅批准实现该内部版本化映射；控制路径也必须实测满足预算，旧v2测试对象与失败保全并独立路由/身份，不重解释。接下来真实Java/property/HTTP/prefix/order/cursor/range/MPU/restart及完整独立代码审查仍须通过；不清除原provider故障或生产前置条件。
