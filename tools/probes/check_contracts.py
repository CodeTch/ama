"""Offline contract inventory. Never contacts services or resolves evidence/credentials.

Only catalog-owned metadata is emitted. Manifest strings (including opaque references)
are deliberately omitted: secrets can occur under arbitrary keys, URLs or error text.
"""
import argparse
import json
from pathlib import Path
import re


# id, title, accountable role, affected tasks, requested fields (not company protocols).
CATALOG = [
    ("INT-01", "统一登录", "身份平台", "B-02 F-05", "protocol callback stableUserId tokenSessionLifetime logoutRevocation disablePropagation clockSkew testTenant errorFormat credentialReference"),
    ("INT-02", "人员与部门目录", "人员目录平台", "B-02 D-02 E-02 F-05", "search pagination incrementalSync duplicateNames disabledStaff departmentChanges externalIdentity knowledgeUserMapping quotas credentialReference"),
    ("INT-03", "模型容量与格式", "模型平台＋开发", "A-03 B-05 B-07 C-02 C-06 F-04", "authenticationReference host formats wavDuration responseFields timeout cancellation rateLimits asrRequestsPerSecond audioSecondsThroughput offlineConcurrency llmQuota"),
    ("INT-04", "模型质量", "模型平台＋产品", "A-03 B-07 C-02 C-03 C-06 D-03 F-04", "terminology numbers overlap silence longAudio timestamps tokens speakers subtitleStability embeddingDimension modelVersion sanitizedDataset acceptanceCriteria"),
    ("INT-05", "知识库材料引用", "知识库平台", "D-01 D-06 F-05", "userMapping search versionPinning download fileAcl directoryAcl expiredReference rateLimits permissionChanges credentialReference"),
    ("INT-06", "知识库发布与状态", "知识库平台", "B-08 D-05 F-02 F-05", "destinations idempotencyOrLookupKey status documentUrl withdraw abolish signedEvents replay authoritativeQuery consistencyWindow credentialReference"),
    ("INT-07", "公司邮件", "邮件平台＋产品", "E-04 F-05", "protocol authorizedSender quotas queryableMessageId deduplication recipients contentMode unknownResultPolicy credentialReference"),
    ("INT-08", "文件解析和导出", "产品＋开发＋运维", "B-06 C-01 D-01 D-06 F-05", "formats sizes durations chineseFonts fontLicense ocrService exportConsistency processResourceLimits"),
    ("INT-09", "安全、审计和声纹", "企业安全＋产品", "B-02 D-07 E-01 E-02 E-03 F-03 F-05 F-06", "dataClassification accessScope voiceConsent voiceRevocation voiceRetention modelDataBoundary encryptionKeyOwnership auditRetention immutableArchive"),
    ("INT-10", "浏览器与访问域", "桌面运维＋安全", "C-05 C-07 F-05", "browserVersions https microphone audioWorklet indexedDb backgroundPolicy deviceQuota offlineCachePolicy"),
    ("PR-01", "虚拟化平台与 VM 管理能力", "虚拟化运维", "A-04 F-01 F-02", "platform version vmIdentifiers fenceAgentOrApi rebootPowerOffSemantics"),
    ("PR-02", "可信 fencing", "虚拟化＋系统运维", "A-04 F-02", "restrictedAccountReference vmScope independentManagementPath guestHangTest stoppedWriterProof"),
    ("PR-03", "宿主和存储故障域", "虚拟化＋存储运维", "A-04 F-01 F-02", "physicalHosts antiAffinity storageDependencies commonFailureRisks"),
    ("PR-04", "独立持久化块设备", "存储＋系统运维", "A-04 B-04 F-01 F-02", "perNodeCapacity protectedPaths flushBarrier powerLossDurability activeOnlyXfs"),
    ("PR-05", "同步与集群网络", "网络＋系统运维", "A-04 F-01 F-02 F-04", "rtt throughput packetLoss failurePaths trafficSeparation fencingPriority mutualFencingPrevention"),
    ("PR-06", "VIP 与访问路径", "网络运维", "A-04 F-01 F-02", "applicationVip dataVip dnsTls subnet arpRouting antiSpoofing takeoverReachability singleVipController"),
    ("PR-07", "RHEL 与内核组件", "系统运维＋采购支持", "A-04 F-01 F-02", "rhelPatch kernel haSubscription supportScope clusterPackages drbd9Compatibility secureBoot upgradeRollback"),
    ("PR-08", "集群资源约束", "系统运维＋开发", "A-04 F-01 F-02", "fenceBeforePromote twoNodePolicy startupFencing drbdProtection singlePrimary exclusiveMount ordering colocation noAutoFailback rejoinResync"),
    ("PR-09", "MySQL 持久化与恢复", "数据库＋系统运维", "A-04 B-01 F-02 F-03", "version commitFlush binlog filesystemVirtualDiskSemantics crashRecovery standbyStartProtection"),
    ("PR-10", "私有 S3 产品资格", "存储运维＋开发", "A-04 B-04 F-01 F-02", "candidateVersion license s3Contract objectRecovery indexRecovery filerMysqlRecovery masterRecovery durableAck powerLossRecovery"),
    ("PR-11", "资源与容量", "运维＋开发", "A-04 C-07 F-04", "diskCapacity iops latency cpuOvercommit guaranteedMemory networkBandwidth singleNode100Meetings300Users replicationCost resyncCost exportCost backupCost"),
    ("PR-12", "安装、镜像与时间", "系统运维＋安全", "A-04 F-01 F-06", "internalRepositories imageDigests vulnerabilityScan ntp dns ca certificateRenewal podman systemd selinux firewall"),
    ("PR-13", "备份和恢复目的地", "数据库＋存储＋安全", "F-03 F-06", "retention backupInterval binlogRetention recoveryPoint recoveryTime encryption restoreExercise sharedFailureBoundary externalBackupApproval"),
    ("PR-14", "告警与值守", "运维＋开发", "F-02 F-06", "channels severity duty diskFull replicationLag fencingFailure modelBacklog publicationReconciliation logCapacity minimalSensitiveData"),
    ("PR-15", "本地文件后端", "系统运维＋安全＋开发", "A-04 B-04 F-01 F-02 F-03", "protectedRoot volumeIdentity serviceAccountReference selinux internalServiceIdentity internalAddress spaceQuota inodeQuota fileDirectorySync atomicPublish protectedStaging pathEscapeProtection range mountLossRejectWrite takeoverIntegrity noS3Required"),
    ("PL-01", "公司 MySQL", "数据库平台", "B-01 F-01 F-02 F-04", "version writeEndpoint tlsCa credentialReference minimumPrivileges connectionQuota charset collation transactions skipLocked maintenance backup failover"),
    ("PL-02", "公司 S3", "对象存储平台", "B-04 F-01 F-02 F-04", "endpoint region addressing bucket signature tls credentialReference objectLimits multipart range checksum pagination deletion durability consistency sla"),
    ("PL-03", "网络与限额", "平台＋网络", "C-07 F-04", "selectedServiceLinks allowedPorts connectionQuota requestQuota smallObject50PutsPerSecond largeVideo twentyPlaybacks concurrentProcessing"),
    ("PL-04", "故障责任与观测", "平台运维", "F-02 F-06", "rto rpo incidentNotification rateLimitErrors maintenanceErrors support trace endpointFailover connectionLoss"),
    ("PL-05", "入口 HA", "网络运维", "A-04 F-01 F-02", "enterpriseLoadBalancerAvailability backendHealth wss sse range fallbackTwoVmIngress networkAndFencing"),
]

# Differences D-* are decisions, not implementation task D-*.
# Approved policy: docs/superpowers/specs/2026-10-21-ama-product-supplement.md.
# Remaining fields are unresolved details; adopted policy never qualifies services.
DECISIONS = [
    ("D-01", "adopted", "产品", "C-03", "使用原型最终行内纠错入口，保留完整纠错动作和来源抽屉；不删除需求要求的能力", ""),
    ("D-02", "adopted", "产品", "B-09 C-01 D-02", "新导入入口使用统一信息抽屉，不恢复内嵌信息区", ""),
    ("D-03", "adopted", "产品", "D-02", "负责人在统一信息抽屉编辑可选实际会议日期，未知数据保持空；各视图依原需求/原型显示空态或“--”，不以创建日期替代", ""),
    ("D-04", "product-specification", "产品＋模型", "B-06 C-01 C-02", "单批可按顺序合并多个音视频；单批可处理多个纯转写文档；不在同一处理批次混合音视频和转写文档。未证明的格式仍按能力矩阵说明", "formats sizes durations"),
    ("D-05", "product-specification", "产品＋安全", "B-03 B-04 B-06 C-01 D-02", "不新增产品名称字数上限；公开技术资源保护", "requestBudget parsingBudget uploadBudget capabilityDisplay errorBehavior"),
    ("D-06", "product-specification", "产品", "B-06 C-03 D-04 D-05 D-06", "转写保留显式保存；纪要保留显式保存及自动保存，失败/冲突保留草稿，确认/导出只用已保存版本；保留离开提醒；仅纪要自动保存触发时机、保存指示及离开提醒交互细节待补充", "perEditorSaveTiming saveIndicator leaveReminder"),
    ("D-07", "product-specification", "产品", "B-06 C-03", "默认纠错零待处理才确认", "degradedConfirmationAllowed explicitApproval auditReason eligibility"),
    ("D-08", "adopted", "产品", "B-08 D-05", "发布成功只读；保留历史；撤回/废止不得恢复或再次发布", ""),
    ("D-09", "adopted", "产品", "B-07 D-03", "七类主体为概述、讨论、结论、行动、风险、待确认、分歧；其他为辅助目录", ""),
    ("D-10", "product-specification", "产品＋邮件平台", "E-04", "负责人只能发给仍有查看权限的已确认企业参会人，不向外部邮箱发送；只发送绑定已保存版本的受控链接，不附正文或文件；发送前重验权限和发布/撤回状态；结果 UNKNOWN 先查回，不能盲目重发", "senderAuthorization template messageKey"),
    ("D-11", "product-specification", "产品＋安全", "E-01 E-02 E-03", "反馈、声纹与配置均有候选/审核/生效记录；审核权不隐含原会议正文读取权；声纹需明确用途授权，可撤销；配置发布支持独立审批和回滚，只允许已定义配置键，不能填写任意命令或任意 URL；开发验证使用受控测试角色与合成数据，不等于企业安全审批；企业角色、允许用途及保留/删除策略未落实前，不启用全局数据复用、真实声纹登记或自动清理", "roles dataPurpose evaluationDataset retention"),
    ("D-12", "platform-evidence", "模型平台", "A-03 C-02 C-06 F-04", "短窗口 HTTP 先验证；定位与身份能力不足显式降级", "streamingProtocol wordTimestamps throughput onlineIdentity degradedDisplay"),
    ("D-13", "platform-evidence", "产品＋桌面运维", "C-05 C-07 F-05", "站内跳转保持；休眠关闭不承诺录音", "browserBaseline backgroundRecording sleepCloseBehavior deviceLimits"),
    ("D-14", "platform-evidence", "知识库平台", "B-08 D-05 F-02 F-05", "稳定业务键查回、签名事件与对账；缺能力不得伪造成功", "idempotentLookup signedEvents withdrawal permissionSync reconciliationWindow"),
    ("D-15", "product-specification", "产品＋安全", "B-02 D-02 E-02", "外部身份与企业账号分开；候选实名人工确认不产生授权；声纹需明确用途授权，可撤销；身份候选不授权会议访问；企业角色、允许用途及保留/删除策略未落实前，不启用全局数据复用或真实声纹登记", "externalIdentity responsiblePersonMapping"),
    ("D-16", "product-specification", "产品＋运维", "B-06 D-01 D-06", "支持矩阵；无 OCR 的扫描件明确不可解析，保留来源定位", "formats scannedDocuments ocrAvailability sourceLocator failureDisplay"),
    ("D-17", "product-specification", "产品＋安全＋运维", "D-07 F-03 F-05", "历史不自动清理；仅校验无引用中间产物可清理", "temporaryRetention referenceValidation backupDeletion auditRetention restoreDeletionProtection"),
    ("D-18", "adopted", "产品", "D-04", "人工标题不被 AI 或会议改名自动覆盖；重新联动只能由用户显式操作；标题与会议名分开版本化，使用已保存标题发布", ""),
    ("D-19", "adopted", "用户已确认；开发＋运维落实", "B-04 F-01 F-02 F-03", "local / s3 与 MySQL 来源独立；local 不要求 S3；会话固定后端、历史按原记录路由", ""),
]


def _failure(code):
    return {"id": "MANIFEST", "status": "failed", "owner": "项目负责人",
            "blockedTasks": [], "evidence": {"issues": [code], "values": "[REDACTED]"}}


def check_contracts(manifest: dict) -> dict:
    """Inventory supplied metadata; pending is never integration acceptance.

    ``verified`` is intentionally never emitted by this offline external checker.
    Missing input is pending; malformed/duplicate/contradictory input or a reported
    failed evidence outcome fails. Even complete passed evidence remains pending.
    """
    checks = []
    indexed = {}
    known = {row[0] for row in CATALOG}
    if (not isinstance(manifest, dict) or set(manifest) - {"contracts"}
            or not isinstance(manifest.get("contracts"), list)):
        checks.append(_failure("invalid-manifest"))
    else:
        for record in manifest["contracts"]:
            if not isinstance(record, dict) or not isinstance(record.get("id"), str) or record["id"] not in known:
                checks.append(_failure("invalid-or-unknown-record"))
            elif record["id"] in indexed:
                checks.append(_failure("duplicate-record"))
                indexed[record["id"]] = None
            else:
                indexed[record["id"]] = record

    for ident, title, owner, tasks, requested in CATALOG:
        record = indexed.get(ident)
        issues = []
        status = "pending"
        received = 0
        required = requested.split()
        missing = required[:]
        if ident in indexed and record is None:
            status = "failed"
            issues.append("duplicate-record")
        elif record is None:
            issues.append("missing-contract")
        else:
            fields = record.get("fields", {})
            evidence = record.get("evidence", [])
            if set(record) - {"id", "fields", "evidence"}:
                issues.append("unsupported-record-fields-or-status-claim")
            if not isinstance(fields, dict) or set(fields) - set(required):
                issues.append("invalid-fields")
            else:
                missing = [name for name in required if not isinstance(fields.get(name), str) or not fields[name].strip()]
                for name in required:
                    if name in fields and (not isinstance(fields[name], str) or not fields[name].strip()):
                        issues.append("invalid-field-value")
                    if (name in {"credentialReference", "authenticationReference",
                                 "restrictedAccountReference", "serviceAccountReference"}
                            and name in fields and not _controlled_reference(fields[name])):
                        issues.append("invalid-controlled-reference")
            if not isinstance(evidence, list):
                issues.append("invalid-evidence")
            else:
                received = len(evidence)
                outcomes_by_reference = {}
                for entry in evidence:
                    if (not isinstance(entry, dict) or set(entry) != {"reference", "outcome"}
                            or not _controlled_reference(entry.get("reference"))
                            or not isinstance(entry.get("outcome"), str)
                            or entry["outcome"] not in {"passed", "failed", "pending"}):
                        issues.append("invalid-evidence-entry")
                    else:
                        outcomes_by_reference.setdefault(entry["reference"], set()).add(entry["outcome"])
                if any("failed" in outcomes for outcomes in outcomes_by_reference.values()):
                    issues.append("reported-evidence-failure")
                if any(len(outcomes) > 1 for outcomes in outcomes_by_reference.values()):
                    issues.append("conflicting-evidence-outcomes")
            if issues:
                status = "failed"
        checks.append({"id": ident, "status": status, "owner": owner,
                       "blockedTasks": tasks.split(), "evidence": {
                           "assessment": "offline-unverified", "received": received,
                           "missingFields": missing, "issues": sorted(set(issues)),
                           "values": "[REDACTED]"}})

    decisions = [{"id": ident, "category": category, "owner": owner,
                  "blockedTasks": [] if category == "adopted" else tasks.split(),
                  "affectedTasks": tasks.split(), "baseline": baseline,
                  "pendingFields": fields.split(),
                  "status": "adopted" if category == "adopted" else "pending"}
                 for ident, category, owner, tasks, baseline, fields in DECISIONS]
    continuation = {}
    for phase in "BCDEF":
        continuation[phase] = {
            "implementation": "may-continue",
            "qualification": "pending",
            "externalGates": [c["id"] for c in checks if any(t.startswith(phase + "-") for t in c["blockedTasks"])],
            "decisionGates": [d["id"] for d in decisions if any(t.startswith(phase + "-") for t in d["blockedTasks"])],
        }
    return {"mode": "offline", "checks": checks, "decisions": decisions,
            "continuation": continuation, "integrationVerified": False}


def _controlled_reference(value):
    # Opaque identifier only; never open a file, env var, URL or credential manager.
    return isinstance(value, str) and re.fullmatch(r"controlled:[A-Za-z0-9][A-Za-z0-9._-]{0,127}", value) is not None


class _SafeParser(argparse.ArgumentParser):
    def error(self, message):
        raise ValueError("invalid-cli")


def main(argv=None):
    parser = _SafeParser(description=__doc__)
    parser.add_argument("--manifest", required=True)
    parser.add_argument("--offline", action="store_true", required=True)
    try:
        args = parser.parse_args(argv)
        # Only this explicit manifest is read; referenced evidence is never resolved.
        with Path(args.manifest).open("rb") as source:
            raw = source.read(1_048_577)
        if len(raw) > 1_048_576:
            raise ValueError("manifest-too-large")
        manifest = json.loads(raw, object_pairs_hook=_unique_keys,
                              parse_constant=_reject_constant)
        report = check_contracts(manifest)
    except (OSError, ValueError, TypeError, RecursionError):
        report = {"mode": "offline", "checks": [_failure("invalid-input-or-cli")],
                  "integrationVerified": False}
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return 2 if any(c["status"] == "failed" for c in report["checks"]) else 1


def _unique_keys(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError("duplicate-json-key")
        result[key] = value
    return result


def _reject_constant(value):
    raise ValueError("non-json-number")


if __name__ == "__main__":
    raise SystemExit(main())
