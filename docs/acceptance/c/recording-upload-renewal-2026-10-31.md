# 录音归档跨文件 I/O 续租修复验证（2026-10-31）

真实100路600秒测试中Worker在文件I/O后记录PROVENANCE_UNAVAILABLE；60秒受限JFR的域异常调用栈定位PublicationGraph.lock/PublicationWriteGuard.tasks。该图包含durable_task.expires_at，原上传开始、分片和完成均跨耗时I/O复用原快照，Worker正常续租后快照自然失效。

JdbcFileUploads保留每次I/O前的完整授权预检；真实native RPC返回且receipt/token/blob尺寸与SHA验证后，重新采集完整PublicationWriteGuard准备图，提交事务使用该新图。原cancelEpoch、sameSession、part集合、大小/hash、当前EDIT与目的限制、所有原图比较、审计/outbox、完成注册边界保留。没有跳过expiry、放宽图比较或重新发送已消费输入流。

真实RecordingUploadRenewalIT在实际mTLS FileStoragePort返回后调度真正queue.renew当前lease：修复前10项/3失败（begin/part/complete均失败）；修复后加3个当前删除负例，共13项通过。三个续租用例核实原WAV全部48字节及原始4字节PCM，且每种native上传RPC恰好一次。删除负例核实同三个位置在物理I/O后会议删除，元数据不确认新session/part/file/reference。

证据：`.tools/local-app/recording-upload-real-renewal-red.log`、`renewal-closed-authority-0.log`。真实fixture为批准Cschema中的新会议、原生文件服务、真正封闭任务注册与PublicationWriteGuard，没有伪造完成回调或业务RAW。

生产Java688、Web410、类型/构建/OpenAPI字节一致通过，正常部署签名与验证汇总见`.tools/local-app/renewal-release-verification.json`。独立浏览器/负载证据另外记录，本文不替代实际7200秒或模型字幕资格。

扩展旧FileRegistryUploadIT曾被Bfixture schema守卫拒绝；尝试在Cschema继承运行时，旧processing-disabled夹具缺少产品强制PublicationWriteGuard导致37/39失败。没有把此失败计入通过，没有降级产品守卫；本轮新建继承类及其父夹具接缝已撤回，失败日志保留。最终13项使用已安装真实完整权限链的RecordingArchive fixture。
