# A-04：双节点 HA 前置检查与故障证据模板

本页是 [部署前置条件](../../superpowers/specs/2026-10-21-ama-deployment-prerequisites.md) 和 [架构 §4](../../superpowers/specs/2026-10-21-ama-architecture-design.md) 的可执行输入说明。原始设计和需求不修改。当前虚拟化平台、VM 标识、宿主、磁盘、网络、订阅和 fencing 能力仍待运维确认；仓库示例不构成通过报告。

固定边界：“两台 RHEL 8.10 x86_64 虚拟机，每台 16 核、32GB 内存”；不新增第三台项目节点。“100 场同时在线录音并实时转写，每场一个采集端，合计 300 名在线用户”；300 包含 100 名录音用户。业务数据仍使用 MySQL。

## 只读运行与输出含义

在仓库根目录使用 Python 3.9+ 标准库，无 pip 依赖：

```sh
python3 tools/probes/ha_preflight.py deploy/inventory.example.json
python3 -m unittest discover -s tools/probes/tests -p test_ha_preflight.py
```

CLI 只读取指定的本地 JSON、计算清单指纹并输出 JSON。不连接 VM、管理接口、SSH 或 S3；不执行输入中的命令；不调用 fence agent、mount、DRBD、Pacemaker 或任何网络接口。不带清单时输出安全失败。故障注入仅由环境责任方在 F-02 安排的测试演练中执行，此工具没有注入开关。

- `canDeploy`：所选部署组合的适用前置条件和清单事实完整且通过离线检查。它描述部署就绪，不决定能否继续独立业务开发或安装开发工具。
- `canClaimAutomaticRecovery`：上述条件通过，且适用 HA 演练都有当前、同一清单范围的结构化通过证据。缺少演练时可出现 `canDeploy=true`、`canClaimAutomaticRecovery=false`。
- `assessment=supplied-evidence-only`：仅检查提交材料的完整性、一致性和时效。工具不验证日志内容、哈希对应的原始文件、签名、身份或真实环境；即使两个布尔值为 true，也不是现场验收、生产认证或工具实际进行了故障测试。
- `blockers`：`id` 对应 PR/PL/HA 编号，`stage=prerequisite` 阻塞部署和恢复声明，`stage=recovery-claim` 仅阻塞恢复声明。`INPUT` 表示格式/模式不合法。
- `checks`：PR-01…15、PL-01…05 全部列出 `applicable` 与 `passed/pending/not-applicable`；不适用不等于实测通过。另输出适用 HA 清单、指纹和时效策略。
- 退出码：`0` 两项判断均通过；`1` 合法读取但存在阻塞；`2` 无清单、无法读取或无效/重复键 JSON。参数用法错误由 argparse 输出用法。示例预期退出 `1`，两项判断均为 false。`--help` 只显示用法。

## 清单填写约定

[deploy/inventory.example.json](../../../deploy/inventory.example.json) 是完整 local＋私有 MySQL 输入骨架。`null`、空映射、空列表和 `pending` 保留未知事实；不能用虚构 VM、地址、审批人或日志填满示例。示例没有真实 VM ID、端点或有效证据。已给定的操作系统/资源和 Protocol C、XFS、Pacemaker 是方案要求，不表示已安装或验证。

该输入是 HA 事实清单，不是运行时应用配置或密钥载体。local 不要求 S3 Endpoint、Bucket 或凭证。公司 S3 和 MySQL 的具体地址、账号、TLS 等由 PL 证据确认，工具不尝试连接；生产秘密不进清单/仓库。`database.mode` 和 `storage.type` 独立选择，不自动替换。

| 字段 | 合同与安全判定 |
| --- | --- |
| `schemaVersion` / `inventoryId` | 整数 `1`；真实清单修订标识。布尔 `true` 不是版本号。任何事实变更必须更新修订并重新取证。 |
| `database.mode` | `private-ha` 或 `enterprise-platform`。 |
| `storage.type` / `backendId` | `local` 或 `s3`；稳定后端 ID。`s3` 额外指定 `provider=private|enterprise-platform`。未知模式不推断。 |
| `storage.root` | local 必填绝对目录，禁止 `/` 和 `..`；必须等于受保护 `local-files` 的路径。 |
| `storage.metadataDatabaseMode` / `metadataSchema` | 私有 S3 必填：前者与所选 MySQL 来源一致，后者为 Filer 专用 schema；权限、容量及备份由 PR-10/适用 PL-01 复核。 |
| `nodes` | 恰好两条，不允许第三个项目节点。每条 `id`、`vmId`、`hostId` 必须已知；节点、VM 和宿主均分别不同。每条记录 `storageDomain`、`powerDomain`，操作系统 `os=RHEL, version=8.10, arch=x86_64, vcpus=16, memoryGiB=32`。资源不能用布尔值代替。 |
| `faultDomains` | `antiAffinity=true`，`sharedRiskAcknowledged=true`，并有 PR-03 依赖图和风险边界。共用 SAN、电源等必须如实列出；仅填写不同名字不能证明隔离。允许已知共享风险有明确边界，绝不承诺共同故障可自动恢复。宿主相同一律阻塞。 |
| `virtualization` | 项目管理 HA 适用：真实 `product`、`version`；PR-01 包含平台语义和组件支持证明。 |
| `ingress.mode` | `enterprise-lb` 或 `cluster-vip`。入口始终需 PL-05。若项目管理 VIP，即使 DB/S3 都在公司平台，仍需集群、隔离与网络证据。 |
| `fencing` | 项目管理 HA 必填：`enabled=true`、已知 `provider/agent`、`managementPathIndependent=true`、`powerOffVerified=true`；`targets` 恰好映射两节点 ID 到清单里的 VM ID，无重复或第三台目标。PR-02 另须受限账号、业务网断开/guest 卡死时可达、旧写入方确已停止访问写资源的实测。guest SSH 停服务不是 fencing。 |
| `replication.network` | 项目管理 HA 必填 `id`、`independent=true`、`rttMs>=0`、`throughputMbps>0`、`0<=packetLossPercent<=100`。数值必须有限且不是布尔值；格式合法不表示性能合格。PR-05 审批测量范围、复制/Corosync/管理路径及防相互隔离策略；PR-11 验证实际单节点/重同步负载目标。 |
| `replication.protocol` | 私有数据必须为同步 `C`；不能把副本同步等同于应用持久化。 |
| `replication.volumes` | 私有数据必须有非空受保护卷清单。每卷唯一 `id`，`filesystem=xfs`，绝对 `mountPoint`；`devices` 与 `capacityGiB` 的键必须恰好是两节点 ID，设备是 `/dev/` 下绝对路径，两侧容量相同且为正数。每侧各有完整容量，具体 IOPS/容量余量由 PR-11 批准。 |
| 每卷的挂载/副本 | `filesystemUuid` 为 XFS UUID，`observedMountUuid` 必须匹配；`mountedOn` 和 `primaryOn` 必须是同一个仅含一个真实节点 ID 的列表。所有私有卷共置同一活动端。`replicas` 键恰好对应两节点，值均为 `UpToDate`。备用端不挂载。未知、失步、双 Primary、双挂载、错误挂载身份一律不通过。 |
| 每卷的恢复范围 | `protectedComponents` 列出下表组件；`componentPaths` 必须恰好同名映射到本卷挂载点内的绝对路径，同一组件不得出现在多个卷。这里只做路径词法校验，不解析宿主符号链接；实际挂载身份、SELinux、路径逃逸与 flush/barrier 必须由 PR-04/PR-15 证明。 |
| `cluster` | 项目管理 HA 必填 `manager=pacemaker`，下列字段均需严格 `true`：`singleLifecycleManager/fencingBeforePromotion/twoNodePolicyReviewed/startupFencing/singlePrimary/mountExclusive/orderedColocated/rejoinRequiresResync/refusePromotionOnUnknown`；`automaticFailback=false`。PR-08 审阅真实集群配置和顺序；禁止 systemd/Podman 在备用端独立启动数据服务，禁止关闭 fencing 或隔离失败后强制提升。 |

受保护恢复组件：

| 选择 | 本项目受保护卷中的必需组件 | 其他证据 |
| --- | --- | --- |
| 私有 MySQL | `mysql-data` | PR-09 提交刷盘、binlog、崩溃恢复与所选版本 |
| local | `local-files`、`upload-staging`、`recovery-manifests` | PR-15 文件及目录同步、不可覆盖原子发布、暂存恢复、挂载丢失拒写；无需任何 S3 配置 |
| 私有 S3 | `s3-objects`、`s3-indexes`、`s3-master-state`、`s3-config` | PR-10 固定产品/版本/许可证、契约及落盘资格；不能只保护对象内容而遗漏索引/Master 状态 |
| 私有 S3＋私有 MySQL | 另含 `s3-filer-metadata` | Filer schema 独立列出恢复路径，不能仅凭 `mysql-data` 标签推断覆盖 |
| 私有 S3＋公司 MySQL | Filer 元数据由公司 MySQL 保护，不添加虚构本地元数据卷 | PL-01＋PR-10 验证专用 schema、权限/配额/备份与对象协调恢复 |

清单表示实际仍在服务的部署范围。更换默认后端不会消除历史引用或在途上传所需的 local/私有 S3 HA；多后端并存/迁移必须逐个仍服务的后端取证并在联合验收中复核，不能用仅含新默认后端的报告声称整个系统已合格。该 v1 清单每次描述一个文件后端组合，不能独立认证多后端迁移。新上传/录音固定原后端 ID，历史读/删按记录路由，禁止遇到平台错误就静默切成本地成功。

## PR/PL 适用性与所需材料

表中“所有”包括纯公司数据服务的双应用节点。输入事实检查与材料证据两者都要通过；业务开发不被 pending 运维材料阻塞。`私有数据` 指私有 MySQL、local、私有 S3 中任一；`项目管理 HA` 指私有数据或 `cluster-vip` 入口。

| 编号 | 适用条件 | 原始证据应覆盖 |
| --- | --- | --- |
| PR-01 | 项目管理 HA | 虚拟化产品/版本、稳定 VM ID、agent/API、重启与断电语义 |
| PR-02 | 项目管理 HA | 最小权限目标范围、独立管理链路、真实断电确认先于提升 |
| PR-03 | 所有 | 两宿主反亲和、存储/电源依赖图、共同故障的明确不承诺范围 |
| PR-04 | 私有数据 | 块设备/卷身份、完整容量、持久化与 flush/barrier、唯一 XFS 挂载、完整路径清单 |
| PR-05 | 项目管理 HA | 同步/Corosync/管理网络独立故障路径、测量、防相互隔离和优先级 |
| PR-06 | 所有 | 实际入口/数据路径、VIP（若有）、DNS/TLS、路由/ARP/反欺骗，切换后客户端可达，无多套 VIP 抢占器 |
| PR-07 | 项目管理 HA | RHEL 内核补丁、HA Add-On 订阅、Pacemaker/Corosync/fence 来源、DRBD 9 模块（私有数据适用）、Secure Boot 与升级回退 |
| PR-08 | 项目管理 HA | 真实集群配置审阅：隔离先于提升、两节点/启动隔离、唯一管理者、单 Primary/互斥挂载、共置/顺序、重同步/无自动切回；入口专用集群的数据卷项注明不适用 |
| PR-09 | 私有 MySQL | 固定版本、事务提交/binlog/虚拟盘落盘链一致、崩溃恢复、备用端不自行启动 |
| PR-10 | 私有 S3 | 产品版本/许可证、S3 契约、对象/索引/Filer 元数据/Master/配置完整恢复、durable ACK 和断电恢复 |
| PR-11 | 所有 | 两 VM 规格、磁盘/网络/超分与内存保证、单节点 100 场实时＋总计 300 在线、重同步/备份/导出成本 |
| PR-12 | 所有 | RHEL、镜像/软件源与摘要、NTP/DNS/CA、扫描、Podman/systemd/Python 3.9+、SELinux 与证书续期 |
| PR-13 | 所有 | 保留/备份/binlog、加密、协调恢复点、恢复演练和目的地；双机内备份无法覆盖共同故障 |
| PR-14 | 所有 | 告警/接收/值守，复制失步/磁盘满/隔离失败/模型积压/发布异常，敏感日志规则 |
| PR-15 | local | 根目录/卷身份/账号/SELinux/内部服务身份与地址/空间/inode；文件和目录持久化、原子发布、受保护暂存、路径逃逸、Range、挂载丢失拒写、切换完整性 |
| PL-01 | 公司 MySQL（包括私有 S3 的 Filer 选用公司 MySQL） | 版本/入口/TLS/schema/权限/额度/事务/SKIP LOCKED/字符集、备份/维护/切换 |
| PL-02 | 公司 S3 | Endpoint/Region/寻址/Bucket/签名/TLS/受控凭证、限额、multipart/Range/校验/分页/删除、一致性/持久化/SLA |
| PL-03 | 使用任一公司数据服务 | 两应用节点链路/端口/配额；S3 约 50 PUT/s 与视频、20 路回听等实际负载。此负载是探针目标，不表示已具备容量 |
| PL-04 | 使用任一公司数据服务 | 真实 RTO/RPO、维护/限流、故障通知、支持渠道/trace、入口切换/长连接恢复 |
| PL-05 | 所有 | 企业 LB 两后端健康检查、WSS/SSE/Range；无企业 LB 时两 VM 内受控入口 HA 的网络/隔离证据 |

组合推导：公司 MySQL＋local 适用 PR-01…08、PR-11…15、PL-01/03/04/05，豁免 PR-09/10 和 PL-02；公司 MySQL＋公司 S3＋企业 LB 豁免私有组件 PR-01/02/04/05/07/08/09/10/15，但仍需 PR-03/06/11/12/13/14 和全部 PL。若后者改为项目管理 VIP，重新适用 PR-01/02/05/07/08。私有 MySQL 与公司 S3 则仍有私有 DB HA，不因文件外置而豁免。

## 结构化证据与时效

`evidence` 以 PR/PL 编号为键，`drills` 以 HA 编号为键，两者使用同一记录结构。示例为待填写模板，不要直接改一个布尔值作为“通过”：

```json
{
  "status": "pending",
  "observedAt": null,
  "recordedBy": null,
  "reviewedBy": null,
  "inventorySha256": null,
  "summary": null,
  "artifact": {"reference": null, "sha256": null}
}
```

仅接受 `status=passed`，非空非占位的操作人、独立复核人和实质摘要，操作人/复核人去首尾空白后不同，原始证据引用和非占位 64 位小写十六进制 SHA-256。`observedAt` 是带 UTC 时区的 ISO-8601 时间（例如使用真实采集时刻的 `...Z` 或 `...+00:00`），不得未来时间、无时区或超过 30 天。30 天是本预检保守材料有效期，不替代运维的持续监控策略。失败、pending、布尔值、缺字段、错误挂载/副本或矛盾事实都不能被一张 passed 报告覆盖。

每条记录的 `inventorySha256` 必须等于当前事实指纹。算法：从整个 JSON 对象移除顶层 `evidence`、`drills`，以 Python `json.dumps(facts, sort_keys=True, separators=(',', ':'), ensure_ascii=False, allow_nan=False)` 序列化，UTF-8 编码后 SHA-256。CLI 即使失败也输出此指纹，便于真实取证绑定；不要从示例复制一个指纹。事实改动会使所有旧证据失效。JSON 不允许重复键；NaN/Infinity 等非有限事实不会获得有效指纹。

这些要求阻止“已验证=true”冒充演练，但不能阻止人编造结构完整的报告。原始日志、哈希、复核人权限、签名/工单与环境对应关系仍由验收责任方人工核验。摘要应引用所列通过标准、未覆盖边界和失败记录，不能只写“测试成功”。不得提交凭证、用户音频或含敏感正文的日志到仓库。

## HA-01…14 故障演练输入与逐项证据模板

以下均为 pending。每次演练先复制公共记录，再填写场景专属材料；本页不提供自动注入命令。运维/平台责任方先确认测试环境、授权窗口、注入/停止条件、告警和值守、备份及回滚途径。生产不得因运行本预检而收到任何动作。

公共记录（每一个 HA 编号分别填写，不能跨场景共用一句结论）：

```text
HA 编号 / run ID / 状态：pending
部署组合、backend ID、清单 SHA-256、软件/镜像/内核/配置版本：pending
环境责任方、注入执行人、独立复核人、窗口/终止条件/回滚计划：pending
负载：录音场数、总在线（含录音用户）、ASR 请求率、上传/回听/重同步/备份：pending
注入前：活动节点、mount UUID、Primary/UpToDate、ACK/提交水位、空间和告警：pending
UTC 时间线：检测、隔离请求、管理端确认停止写、提升、挂载、服务恢复、连接重建：pending
唯一写入证据：两节点资源/进程/挂载/复制状态、管理平面日志和写入审计：pending
数据核验：逐一对照 durable ACK/DB 提交、大小/hash、缺失/重复/孤儿、失败响应：pending
结果：实测 RTO/RPO、保存缺口/积压/追平、实际通过标准、未覆盖边界及失败项：pending
收尾：旧节点重同步/无自动切回、配置恢复、告警恢复、日志脱敏与归档：pending
证据：原始文件引用及 SHA-256；observedAt/recordedBy/reviewedBy/summary：pending
```

RTO 初始目标引用架构 NFR-07 的 ≤120 秒（私有数据故障到重新可写）；RPO=0 仅针对正常同步且落盘资格通过时已确认数据的设计目标。未上传、未 ACK、共同故障和复制断开后的降级写入不能套用同等承诺。演练应同时保留失败样本；不以“页面可打开”代替数据核验。

| 场景 | 适用条件与注入前提 | 必须附加的原始证据与判定 |
| --- | --- | --- |
| HA-01 API/实时进程终止 | 所有；双应用节点、入口健康检查、录音重连和补传条件就绪；在责任方窗口终止所选测试进程 | 摘流/重连时间、未 ACK 序号补传、旧 writerEpoch 拒写、无重复提交；真实客户端和服务端日志 |
| HA-02 Worker 退出、外部请求仍运行 | 所有；明确任务租约、attempt 和可对账外部请求；使用授权测试模型/平台 | 租约到期/领取时间、旧结果拒写、外部副作用业务键/对账、超时未释放真实模型额度的证据 |
| HA-03 活动 VM 断电/卡死 | 项目管理 HA；PR-01/02/03/05/07/08 先通过，私有数据另有 PR-04/组件资格/已知健康副本；平台数据＋项目 VIP 时记录入口 VM 故障，不声称本地数据提升 | 管理平面停止写确认必须早于提升/VIP 接管；两节点时间线、唯一写入、RTO 和所有 ACK/提交核验；外部 DB/S3 只观察连接，不由本集群启停 |
| HA-04 业务/复制网分区 | 项目管理 HA；网络依赖图、隔离优先级、防相互隔离和恢复手段经审阅 | 分区范围、两侧集群/管理日志、最多一个 Primary/写入方（无私有卷时记录唯一 VIP）、无相互隔离失控；不得将网络不可达等同于旧节点已停止 |
| HA-05 fencing 失败/不可达 | 项目管理 HA；已知旧写入方状态不明的可恢复测试条件、预先准备中止和告警路径 | 隔离失败时不提升、不开放第二写入口；告警和客户端明确未保存/待重试，没有虚假 ACK；恢复后再确认安全顺序 |
| HA-06 副本落后/卷不健康/未同步 | 私有数据；可识别 UpToDate 与旧副本、保留已确认数据清单；不得向生产注入 | 不提升旧/未知副本的集群决定和日志；已确认数据核验、降级标志、无静默回滚；local/私有 S3 的索引及元数据也查验 |
| HA-07 旧节点重新加入 | 项目管理 HA；故障节点恢复可控，私有数据先核验当前活动端与版本水位 | 先备用重同步、无双挂载/双写/自动切回；重同步限速、满载队列和完成水位；入口专用集群记录资源重入与无自动抢回 |
| HA-08 磁盘满/故障、DB/文件服务短错 | 所有；按实际后端选择安全注入点，平台服务由平台安排；保留历史和容量水位 | 写失败返回/重试、缓存边界、持久化步骤失败不 ACK、不清历史；local 查空间/inode/挂载，S3 查服务错误和对象/元数据一致性 |
| HA-09 知识库成功但响应丢失 | 所有；真实平台契约、稳定客户端业务键/权威查询、授权测试目录就绪 | 查回原文档、唯一发布记录、未知态不能编辑后再次发布；请求/查询/最终文档 ID 与状态证据；没有平台协议时保持 pending |
| HA-10 撤权/撤回/废止中断回听/下载 | 所有；身份/目录/知识库联动与真实流已建立，窗口内变更由责任方安排 | 新访问拒绝、已有流按约定窗口终止（架构建议每 5 秒检查权限版本），时间线和已下载字节边界；所有读取再次会议授权，已下载内容无法远程收回 |
| HA-11 协调 DB 与文件恢复 | 所有；一致性备份、binlog/对象或文件清单、选定协调恢复点、隔离恢复目标就绪 | DB 版本/材料/音频/纪要/导出引用逐项对应；local 与 S3 分别记录孤儿/缺失处理、hash；公司 DB＋local 也验证联合一致性，复制不代替备份 |
| HA-12 单台接管持续满载再恢复第二台 | 所有；所选组合与平台额度、前序故障资格、负载和观察窗口经批准 | 单节点 100 场实时＋总计 300 在线，保存缺口/识别积压/追平分列；重同步/导出/备份额外开销；平台服务组合验证入口/应用失效和重连，不假设平台吞吐 |
| HA-13 local 写中断/目录同步失败/挂载丢失 | 仅 local；PR-15、卷身份和受保护暂存就绪，已知所有 ACK 文件及上传会话 | 无半文件登记成功、没有系统盘同名目录写入；文件/目录同步失败拒绝 ACK，切换后每个 ACK 文件可读且 hash 一致；未确认上传由原受保护暂存续传 |
| HA-14 默认后端更换/迁移中断 | 所有；有后端变更或迁移时实际执行，暂不迁移时用锁定路由契约与回滚测试证明而不能凭空豁免；需要两后端同时可用的范围单独联合验收 | 历史读/删按记录、进行中上传/录音固定原 ID；复制/大小/hash 核验完成前不改位置；重试/回滚不误删引用文件；无后端故障静默改写到另一端 |

公司 MySQL＋公司 S3＋企业 LB 不要求本项目实施 HA-03…07 或 HA-13 的私有故障注入；仍需 HA-01/02/08/09/10/11/12/14，以及 PL 的平台故障责任和真实测试证据。私有 MySQL＋公司 S3 仍需 HA-03…07。local 不需要私有 S3 资格，却始终需要 HA-13，不能拿 S3 报告代替。所有未取得的运维/平台/联合验证材料维持 pending，不修改成模拟成功。
