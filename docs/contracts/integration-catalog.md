# 外部契约与部署证据目录

日期：2026-10-21；基线为已批准架构 v0.2。当前 30 项均 **pending**，没有真实平台联调或部署验收证据。本任务交付的是离线台账工具，未取得公司接口不阻塞工具交付。

来源：[部署前置条件](../superpowers/specs/2026-10-21-ama-deployment-prerequisites.md)、[总体任务及依赖](../superpowers/plans/2026-10-21-ama-delivery-plan.md)、[业务决定](product-decisions.md)。责任方为职能角色，由项目负责人落实人员。`blockedTasks` 仅阻塞所述真实联调、能力定型或部署验收；不把整个任务的独立设计、适配器、单元测试一并阻塞。

## 使用与结果含义

从仓库根目录运行（Python 3 标准库，无依赖安装）：

```sh
python3 tools/probes/check_contracts.py --manifest config/contracts.test.example.json --offline
python3 -m unittest discover -s tools/probes/tests -p test_contracts.py
```

API：`check_contracts(manifest: dict) -> dict`。结果含 `checks[{id,status,owner,blockedTasks,evidence}]`、`decisions`、`continuation` 和固定为 false 的 `integrationVerified`。`status` 词汇限 `verified|pending|failed`；本工具无法离线认证证据来源，因此**不会输出 verified**。通过验证的真实报告须由对应平台及验收责任方在独立验收流程确认，本工具不伪造认证能力。

- `pending`：记录/字段/证据缺失，或有语法有效的受控引用但未验证其真实性；即便提供全部字段与 `passed` 声明仍是 pending。
- `failed`：输入结构、字段或引用非法、重复/未知编号、状态冒充、相互矛盾的证据，或证据声明失败。未知编号仅输出固定错误码，不回显原文。
- 退出码：1 表示有待验证项（示例预期值）；2 表示输入或校验失败；保留 0 表示全部真实验证通过，本离线工具不会返回 0。待验证不是工具运行异常，也不是联调通过。

清单仅允许顶层 `contracts` 数组。每项为 `{ "id": "INT-01", "fields": {}, "evidence": [] }`，`fields` 的允许键就是下表的请求字段；填写已获确认的非敏感说明字符串，缺字段保持 pending，空串或错误类型为 failed。不得设置 `status`、修改 owner/tasks 来自行宣告通过。证据项只允许 `reference` 和 `outcome`，后者为 `passed|failed|pending`。受控引用格式为 `controlled:` 加 1–128 个字母数字/点/下划线/连字符，首字符为字母数字，例如 `controlled:identity-test-report`。这个项目内格式仅为台账引用约定，不是公司接口协议。`credentialReference`、`authenticationReference`、`restrictedAccountReference`、`serviceAccountReference` 同样只填受控引用；`expiredReference` 是引用失效处理策略说明。

不填写口令、token、私钥、完整认证 URL 或真实认证材料；仅通过受控渠道保存。工具**不解析引用、不访问网络、不读取环境变量/凭证文件**。唯一读取的是显式 `--manifest` 指定的清单（最多 1 MiB）；JSON 重复键、NaN 和非法编码不被接受。输出不回显任何输入值、路径、原始异常或引用，仅输出静态字段名、缺项、计数及固定错误码，避免任意层级/URL 中的敏感值泄露。非法命令行参数也不回显。

## 共同约束

- “100 场同时在线录音并实时转写，每场一个采集端，合计 300 名在线用户”；300 包含 100 名录音用户。
- “两台 RHEL 8.10 x86_64 虚拟机，每台 16 核、32GB 内存”；不新增第三台项目节点。
- “业务数据仍使用 MySQL”；`database.mode=private-ha|enterprise-platform` 与 `storage.type=local|s3` 独立。
- “local 模式启动校验不得强制要求 S3 地址、Bucket 或凭证”；新上传/录音固定后端 ID，历史读取按原记录路由。
- “只有一个节点挂载普通 XFS 文件系统并启动数据服务”；隔离成功且副本可用后接管，隔离失败不强制提升。
- 既有模型、知识库、SSO/目录使用适配器；不新建知识库，不以模拟结果冒充集成。不引入 Redis/MQ/Kubernetes/搜索集群为必需依赖。
- 模型输入固定为 16kHz、单声道、16bit PCM WAV；VAD/SPK 为完整 WAV Base64，ASR/embed 为 WAV 二进制。100 场、2 秒窗口约 50 ASR 请求/秒仅为探针负载目标，不能推断已具备吞吐。
- 未确认转写不得生成；待采用候选不得最终确认；发布成功只读；撤回/废止不得恢复或再次发布。负责人草稿/AI 姓名/声纹/行动项负责人不授予访问权限；读取文件重新检查会议权限。
- 原文 P0/P1 均交付；历史不自动清理；不擅加产品名称字数上限；UTC 存储，实际会议日期以 Asia/Shanghai 筛选。

## 适用性

所有记录均保留；不选用的部署分支也不标 verified。本工具不选择部署组合，不将 pending 未选分支误作当前组合必须满足的条件。验收责任方按以下范围筛选，不能以字段齐全替代实测：

- INT-01…10 为共有集成条件；PL-05 的入口能力始终需落实，企业负载均衡无可用证据时只能另行验证两 VM 内受控入口 HA。
- PR-01…08 适用于实际运行的私有 MySQL、本地文件卷或私有 S3 的 HA；PR-09 仅私有 MySQL；PR-10 仅私有 S3；PR-15 仅 local。PR-11…14 的资源、安装、备份和值守仍需各实际部署组合落实。
- PL-01 仅公司 MySQL；PL-02 仅公司 S3；PL-03/04 仅实际选用的公司数据服务。公司 MySQL＋local 不免除 PR-15 与本地 HA 条件。local 不以 PL-02/PR-10 为启动前提。

## 逐项台账

请求字段为需要收集的内容分类，**不预先规定公司原始协议/响应格式**。证据及通过标准按原前置清单保留。全表初始状态 pending。

| 编号 | 条件 | 责任方 | 阻塞真实验收的任务 | 所需证据/通过标准 | 待请求字段 |
| --- | --- | --- | --- | --- | --- |
| INT-01 | 统一登录 | 身份平台 | B-02 F-05 | OIDC/SAML/其他具体协议，回调、用户稳定 ID、令牌/会话寿命、登出/撤销、停用传播、时间偏差、测试租户与错误格式 | `protocol`, `callback`, `stableUserId`, `tokenSessionLifetime`, `logoutRevocation`, `disablePropagation`, `clockSkew`, `testTenant`, `errorFormat`, `credentialReference` |
| INT-02 | 人员与部门目录 | 人员目录平台 | B-02 D-02 E-02 F-05 | 搜索/分页/增量同步、同名区分、离职停用、部门变动、外部人员身份、知识库用户 ID 映射、接口额度 | `search`, `pagination`, `incrementalSync`, `duplicateNames`, `disabledStaff`, `departmentChanges`, `externalIdentity`, `knowledgeUserMapping`, `quotas`, `credentialReference` |
| INT-03 | 模型容量与格式 | 模型平台＋开发 | A-03 B-05 B-07 C-02 C-06 F-04 | 文档端点的认证/Host/格式、真实支持的 WAV 时长、响应字段、超时/取消/限流；100 场短窗口所需约 50 ASR 请求/s 与音频秒吞吐、并发离线任务和 LLM 配额 | `authenticationReference`, `host`, `formats`, `wavDuration`, `responseFields`, `timeout`, `cancellation`, `rateLimits`, `asrRequestsPerSecond`, `audioSecondsThroughput`, `offlineConcurrency`, `llmQuota` |
| INT-04 | 模型质量 | 模型平台＋产品 | A-03 B-07 C-02 C-03 C-06 D-03 F-04 | 企业术语、数字、多人交叠、静音、长语音；时间戳/tokens/说话人能力；临时与稳定字幕语义；声纹维度/模型版本；双方认可的脱敏测试集和质量标准 | `terminology`, `numbers`, `overlap`, `silence`, `longAudio`, `timestamps`, `tokens`, `speakers`, `subtitleStability`, `embeddingDimension`, `modelVersion`, `sanitizedDataset`, `acceptanceCriteria` |
| INT-05 | 知识库材料引用 | 知识库平台 | D-01 D-06 F-05 | 用户身份映射、可访问文件搜索、版本固定、内容下载、文件及目录 ACL、引用失效、限流；引用权限变更的处理方式 | `userMapping`, `search`, `versionPinning`, `download`, `fileAcl`, `directoryAcl`, `expiredReference`, `rateLimits`, `permissionChanges`, `credentialReference` |
| INT-06 | 知识库发布与状态 | 知识库平台 | B-08 D-05 F-02 F-05 | 可发布库/目录；幂等创建或可可靠查回的唯一客户端业务键；发布状态、文档 URL、撤回/废止、签名回调/事件重放、权威查询、最终一致窗口 | `destinations`, `idempotencyOrLookupKey`, `status`, `documentUrl`, `withdraw`, `abolish`, `signedEvents`, `replay`, `authoritativeQuery`, `consistencyWindow`, `credentialReference` |
| INT-07 | 公司邮件 | 邮件平台＋产品 | E-04 F-05 | SMTP/HTTP、授权发件人、限额、可查询业务消息标识/去重能力、收件范围、链接/正文/附件、结果未知后的策略；本轮不发送任何邮件 | `protocol`, `authorizedSender`, `quotas`, `queryableMessageId`, `deduplication`, `recipients`, `contentMode`, `unknownResultPolicy`, `credentialReference` |
| INT-08 | 文件解析和导出 | 产品＋开发＋运维 | B-06 C-01 D-01 D-06 F-05 | 明确文件格式/大小/时长能力表、中文字体及授权、扫描件 OCR 是否已有服务、PDF/Word 导出一致性、隔离进程资源限制 | `formats`, `sizes`, `durations`, `chineseFonts`, `fontLicense`, `ocrService`, `exportConsistency`, `processResourceLimits` |
| INT-09 | 安全、审计和声纹 | 企业安全＋产品 | B-02 D-07 E-01 E-02 E-03 F-03 F-05 F-06 | 数据分级、访问范围、声纹登记授权/撤销/保留、模型数据使用边界、加密密钥归属、审计保留与是否需不可篡改归档 | `dataClassification`, `accessScope`, `voiceConsent`, `voiceRevocation`, `voiceRetention`, `modelDataBoundary`, `encryptionKeyOwnership`, `auditRetention`, `immutableArchive` |
| INT-10 | 浏览器与访问域 | 桌面运维＋安全 | C-05 C-07 F-05 | 企业支持浏览器版本、HTTPS、麦克风权限、AudioWorklet/IndexedDB 可用性、后台策略、终端配额、离线本地缓存处理 | `browserVersions`, `https`, `microphone`, `audioWorklet`, `indexedDb`, `backgroundPolicy`, `deviceQuota`, `offlineCachePolicy` |
| PR-01 | 虚拟化平台与 VM 管理能力 | 虚拟化运维 | A-04 F-01 F-02 | 平台产品、版本、两台 VM 稳定标识；支持的 fence agent/API；明确重启与断电接口语义，不以 guest SSH 停服务冒充隔离 | `platform`, `version`, `vmIdentifiers`, `fenceAgentOrApi`, `rebootPowerOffSemantics` |
| PR-02 | 可信 fencing | 虚拟化＋系统运维 | A-04 F-02 | 受限服务账号仅可操作指定 VM；管理路径在业务网断开、guest 卡死时仍可用；实测确认旧节点已经停止访问写入资源后才允许提升 | `restrictedAccountReference`, `vmScope`, `independentManagementPath`, `guestHangTest`, `stoppedWriterProof` |
| PR-03 | 宿主和存储故障域 | 虚拟化＋存储运维 | A-04 F-01 F-02 | 两台 VM 的物理宿主、反亲和配置、底层存储依赖图；共同 SAN/存储池/电源风险明确；避免一个宿主故障同时损失两台 | `physicalHosts`, `antiAffinity`, `storageDependencies`, `commonFailureRisks` |
| PR-04 | 独立持久化块设备 | 存储＋系统运维 | A-04 B-04 F-01 F-02 | 两台各有完整容量，受保护的 DB/本地文件或私有 S3/元数据路径清单；flush/barrier、掉电持久性证据；XFS 只能在活动端挂载 | `perNodeCapacity`, `protectedPaths`, `flushBarrier`, `powerLossDurability`, `activeOnlyXfs` |
| PR-05 | 同步与集群网络 | 网络＋系统运维 | A-04 F-01 F-02 F-04 | RTT、吞吐、丢包、独立故障路径；DRBD、Corosync、管理流量互不形成无法识别的共同故障；故障隔离策略有明确优先级与防相互隔离设计 | `rtt`, `throughput`, `packetLoss`, `failurePaths`, `trafficSeparation`, `fencingPriority`, `mutualFencingPrevention` |
| PR-06 | VIP 与访问路径 | 网络运维 | A-04 F-01 F-02 | 应用 VIP、数据 VIP、DNS/TLS、网段、ARP/路由/反欺骗策略；VM 迁移/接管后客户端实际可达；不同时运行多个独立 VIP 抢占器 | `applicationVip`, `dataVip`, `dnsTls`, `subnet`, `arpRouting`, `antiSpoofing`, `takeoverReachability`, `singleVipController` |
| PR-07 | RHEL 与内核组件 | 系统运维＋采购支持 | A-04 F-01 F-02 | RHEL 8.10 补丁/内核、HA Add-On 订阅及支持范围、Pacemaker/Corosync/fence agent 来源、DRBD 9 兼容模块、Secure Boot 签名、升级回退方式 | `rhelPatch`, `kernel`, `haSubscription`, `supportScope`, `clusterPackages`, `drbd9Compatibility`, `secureBoot`, `upgradeRollback` |
| PR-08 | 集群资源约束 | 系统运维＋开发 | A-04 F-01 F-02 | 配置审阅：隔离先于提升；两节点策略、启动隔离、DRBD 资源保护、单 Primary、挂载互斥、资源顺序/共置、无自动切回、恢复节点先重同步；不得关闭 fencing 换取演示成功 | `fenceBeforePromote`, `twoNodePolicy`, `startupFencing`, `drbdProtection`, `singlePrimary`, `exclusiveMount`, `ordering`, `colocation`, `noAutoFailback`, `rejoinResync` |
| PR-09 | MySQL 持久化与恢复 | 数据库＋系统运维 | A-04 B-01 F-02 F-03 | 锁定版本；提交刷盘、binlog、文件系统和虚拟盘语义一致；数据库崩溃恢复实测；数据库服务不能由备用节点自行启动 | `version`, `commitFlush`, `binlog`, `filesystemVirtualDiskSemantics`, `crashRecovery`, `standbyStartProtection` |
| PR-10 | 私有 S3 产品资格 | 存储运维＋开发 | A-04 B-04 F-01 F-02 | SeaweedFS 候选固定版本/许可证；S3 契约测试；对象数据、索引、Filer MySQL 元数据、Master 状态都在恢复清单；响应成功前落盘语义及断电恢复有实测证据 | `candidateVersion`, `license`, `s3Contract`, `objectRecovery`, `indexRecovery`, `filerMysqlRecovery`, `masterRecovery`, `durableAck`, `powerLossRecovery` |
| PR-11 | 资源与容量 | 运维＋开发 | A-04 C-07 F-04 | 磁盘容量/IOPS/延迟、vCPU 是否超分、内存保证、网络带宽；按单节点故障后承载 100 场实时＋300 在线测试；同步复制、重同步、导出和备份成本计入 | `diskCapacity`, `iops`, `latency`, `cpuOvercommit`, `guaranteedMemory`, `networkBandwidth`, `singleNode100Meetings300Users`, `replicationCost`, `resyncCost`, `exportCost`, `backupCost` |
| PR-12 | 安装、镜像与时间 | 系统运维＋安全 | A-04 F-01 F-06 | 内网镜像/软件源、镜像摘要、漏洞扫描、NTP、DNS、CA、证书续期、Podman/systemd 版本及 SELinux 策略；不以关闭 SELinux/防火墙作为默认方案 | `internalRepositories`, `imageDigests`, `vulnerabilityScan`, `ntp`, `dns`, `ca`, `certificateRenewal`, `podman`, `systemd`, `selinux`, `firewall` |
| PR-13 | 备份和恢复目的地 | 数据库＋存储＋安全 | F-03 F-06 | 数据保留、备份周期、binlog 保留、恢复点与恢复时限、加密及恢复演练；仅双机内备份须承认共同故障边界；外部企业备份若获准则配置接入 | `retention`, `backupInterval`, `binlogRetention`, `recoveryPoint`, `recoveryTime`, `encryption`, `restoreExercise`, `sharedFailureBoundary`, `externalBackupApproval` |
| PR-14 | 告警与值守 | 运维＋开发 | F-02 F-06 | 告警接收渠道、等级、值班归属；磁盘满、复制失步、隔离失败、模型积压、发布对账异常均可观测；日志容量及最小敏感信息规则 | `channels`, `severity`, `duty`, `diskFull`, `replicationLag`, `fencingFailure`, `modelBacklog`, `publicationReconciliation`, `logCapacity`, `minimalSensitiveData` |
| PR-15 | 本地文件后端 | 系统运维＋安全＋开发 | A-04 B-04 F-01 F-02 F-03 | 受保护根目录和卷身份、专用账号、SELinux 标签、内部服务身份/地址、空间及 inode 配额；文件/目录同步、原子发布、受保护上传暂存、路径逃逸防护、Range 续读、挂载丢失拒写及切换后完整性实测；local 无 S3 配置即可运行 | `protectedRoot`, `volumeIdentity`, `serviceAccountReference`, `selinux`, `internalServiceIdentity`, `internalAddress`, `spaceQuota`, `inodeQuota`, `fileDirectorySync`, `atomicPublish`, `protectedStaging`, `pathEscapeProtection`, `range`, `mountLossRejectWrite`, `takeoverIntegrity`, `noS3Required` |
| PL-01 | 公司 MySQL | 数据库平台 | B-01 F-01 F-02 F-04 | 版本、写入口、TLS/CA、最小权限 schema/账号、连接额度、字符集/排序规则、事务和 SKIP LOCKED 能力、维护窗口、备份及故障切换行为 | `version`, `writeEndpoint`, `tlsCa`, `credentialReference`, `minimumPrivileges`, `connectionQuota`, `charset`, `collation`, `transactions`, `skipLocked`, `maintenance`, `backup`, `failover` |
| PL-02 | 公司 S3 | 对象存储平台 | B-04 F-01 F-02 F-04 | Endpoint/Region/寻址、Bucket、签名算法、TLS、凭证、对象限额、multipart/Range/校验和/分页/删除语义、持久化及一致性保证、服务 SLA | `endpoint`, `region`, `addressing`, `bucket`, `signature`, `tls`, `credentialReference`, `objectLimits`, `multipart`, `range`, `checksum`, `pagination`, `deletion`, `durability`, `consistency`, `sla` |
| PL-03 | 网络与限额 | 平台＋网络 | C-07 F-04 | 两台应用到实际选用 MySQL/S3 的链路、允许端口、连接和请求速率配额；S3 以约 50 PUT/s 小对象负载测试，并纳入视频大文件、20 路回听和并发处理 | `selectedServiceLinks`, `allowedPorts`, `connectionQuota`, `requestQuota`, `smallObject50PutsPerSecond`, `largeVideo`, `twentyPlaybacks`, `concurrentProcessing` |
| PL-04 | 故障责任与观测 | 平台运维 | F-02 F-06 | 外部服务 RTO/RPO、故障通知、限流/维护错误、支持渠道及 trace 对接；应用恢复测试须覆盖平台入口切换和长连接断开 | `rto`, `rpo`, `incidentNotification`, `rateLimitErrors`, `maintenanceErrors`, `support`, `trace`, `endpointFailover`, `connectionLoss` |
| PL-05 | 入口 HA | 网络运维 | A-04 F-01 F-02 | 可用企业负载均衡的两后端健康检查与 WSS/SSE/Range 配置；若无，则两 VM 内建立受控入口 HA，其网络/隔离要求仍需验证 | `enterpriseLoadBalancerAvailability`, `backendHealth`, `wss`, `sse`, `range`, `fallbackTwoVmIngress`, `networkAndFencing` |

## B/C/D/E/F 可以继续的工作

| 阶段 | 可继续 | 尚不可宣称完成 |
| --- | --- | --- |
| B | 领域、版本、权限端口、文件/任务/模型/发布适配器与受控测试 | B-02 登录/目录真联调，B-07 模型真调用，B-08 知识库真发布；数据库/文件所选组合需真实证据 |
| C | 录音协议、缓存、重连、转写 UI；先完成输入组合与纠错补充规格 | A-03/C-06/F-04 模型实时能力，C-07 真实模型容量、浏览器录音限制与真实处理质量 |
| D | 已明确的材料/权限/来源/列表/模板/回看框架和 UI；具体缺口先完成专项规格 | 知识库材料/发布/失效真联调，解析/OCR/导出与安全完整验收 |
| E | 收敛邮件、反馈审核、声纹、评测与配置规格；适配器设计 | D-10/11/15、INT-07/09 相关规则批准前不能定型，真实邮件未知结果不得盲目重发 |
| F | 部署模板、用例、恢复/备份/安全/运维手册准备 | 实际平台、配额、存储、隔离、故障演练与生产资格；不把离线结果视为上线批准 |

机器结果 `continuation` 列出每阶段外部与业务决定编号，`qualification` 始终为 pending。`may-continue` 是允许推进不依赖缺口的实现，不跳过阶段内依赖或产品规格。
