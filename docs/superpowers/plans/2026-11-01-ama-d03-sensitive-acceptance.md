# D03 敏感排除实际联调补充

执行中，主项目 main，单 agent，不另建工作树。用户已授权自主选择方案并连续推进至 D 完成。外部 CAS/模型/知识库仍统一经兄弟项目固定8792；模拟器和受控模型不代表公司真实质量。

当前普通本地生成归档使用 controlled:rich-no-sensitive，无法从真实业务生产者得到敏感摘录。选择增加显式本地测试场景 standard/sensitive，默认standard；sensitive安装独立版本的新v3 prompt/model/renderer/budget归档，指令controlled:rich-output，保留全部历史索引和原字节。服务状态缓存包含场景，切换场景必须正常启动，不直接改业务数据库或冒充模型检测结果。

1. local_app 读取严格的 AMA_LOCAL_MINUTES_SCENARIO=standard|sensitive，prepare安装器只对sensitive附加明确开发参数。新场景身份进入runtime状态，避免相同源码时错误复用旧进程。
2. 控制模型的rich输出从未排除的sourceId中选择真实来源；不允许排除的来源继续从sourceRefs/evidence/sensitive/background进入输出。无合格来源时返回空要素及明确无可用来源的非事实性正文。该行为只用于确定性契约联调，不能当作零漏检、事实准确率或延迟验收。
3. 真实CAS新TXT含至少三段，经RAW恢复、确认、FULL v3生成得到两类摘录。UI预览必须读真实确认原文；单项排除保存后旧人工正文保留、needs_generation=true。原键重放、版本冲突与非当前摘录来源422保留。
4. 重新生成只给候选；通过原输入manifest、rendered source集合和候选sourceRefs/evidence核对排除的物理来源未使用。再核对全部排除及共享同物理来源的类别合并，不用单纯搜索合成输出文本替代来源闭包验证。
5. 默认standard及历史v1/v2/v3归档继续读取。完成测试后正常恢复standard，固定端口和既有撤回/失效数据不改变。
6. 并行工作流中发现连续生成的PublicationGraph固定页元数据预留可提前超过总预算；新增实际reader回归RED→GREEN，按同一剩余字节/标量/UTF16/副本预算缩页，仍执行原admitCapture和完整S/F比较，不提高上限。
7. 完成后继续D01材料变更旧稿保留、D07含KB/local/建议/区域表的实际删除与外部原件保持、D08全页面及按钮矩阵。该补充不关闭B/C原失败或遗留义务。

安装器实现约束补充：每次都先安装/读取普通v3归档，再追加/读取敏感场景归档；两种模式共用包含二者的最终index pin，只切换被选择的prompt/model。这样从sensitive切回standard也不会丢失敏感场景历史任务所需的归档。新归档仍是追加发布，未修改旧release。
