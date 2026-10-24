# Task4 独立复审中途暂停线索（非最终报告）

用户于 2026-10-23 18:19–18:20 Asia/Shanghai 要求停止开发，root 已 interrupt /root/b_task04_intake_source_review。task-04-review.md 尚不存在。以下仅复制审查子代理已发消息的技术线索，不是最终 finding 编号、严重度裁决、完整 SPEC/quality verdict，也不能当作采用许可。

审查者已读全部 32 个 source diff，primary/audit SHA 匹配。初步认为 accepted replay 在 JdbcProcessingIntake.restore:165–175 中只检查 task/input/profile 连接和 batch ordinals 连续性，未将原 ImportRequest 贯穿 lookup/restore，从而不能确认原有 ordered file IDs/revision/request binding/full immutable shape，或防止 result_id 指向同 meeting 的另一份合法 acceptance。修复必须保留 replay 独立性，只核对 DB 中原不可变 metadata，不重新要求当前 source eligibility。

另一个初步问题：contracts/openapi/processing.yaml:356–367 中 nullable eligibility reason 的 enum 不含 null，但正常 ELIGIBLE 响应发出 null。还有 capability/limit 边界小差异尚在检查。

下次首先 resume 同一审查者，从既有阅读位置完成报告，不重复广泛审阅；拿到完整报告后 root 逐项核对，再按 fix-loop round1 恢复原作者 /root/b_task04_intake_implementation，测试覆盖改动，限定复审。若原 handle 不可用，新审查者明确接续身份、原范围/现有线索，独立完成报告，不把本便笺冒充最终审查。
