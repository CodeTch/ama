# 材料变更保留稿件与当前版本清理实际验收

状态：本地受管链路通过。报告 `.tools/local-app/material-change-deletion/cas-01/report.json`，实际CAS，经兄弟固定8792接口，源码/部署签名一致；报告中包含准确signature、目标/witness会议、任务、请求及五组检查。主项目main，单agent。

- 从真实TXT/RAW/确认、KB、FULL及REGION任务建立的未发布会议出发，上传并解析新的本地TXT材料为READY。有效材料快照改变、needs_generation从false变true，人工标题/正文/id与要素集逐字段保持。添加材料本身未产生生成任务。
- 明确FULL生成使用新的KB+local快照，只产生待核对候选，旧稿继续保留，最终确认阻断。生成输入经来源校验回读，未使用业务SQL插入夹具。
- 原键删除202/重放/异体409及立即403；持久清理任务COMPLETE。删除前独立读取每个file_object对应key文件、字节长度与SHA；删除后逐项确认data不存在且永久discarded标记存在。证据physical-before.json/physical-after.json。不是只看任务状态。
- 新增meeting_info_suggestion、generation_region_request连同候选/输入/材料/正文/要素/转写/上传/检索等在线行逐表只读COUNT为0。既有D07故障恢复/中断/迟到写/录音栅栏IT证据继续保留。
- 另一真实会议在删除后重新通过应用经harness获取同一知识库原件，目录版本/哈希不变，新受管副本下载200且逐字节SHA与原件一致。没有把上游原件计入本地清理范围。

清理界限：本地受管文件、上传分片与在线SQL正文/派生数据；全局归档、最少删除审计/永久栅栏、独立测试证据保留。外部原件、上游受控模型服务的独立记录及备份不在本报告的擦除声明内；S3/跨后端与备份恢复抑制沿阶段F义务，不作虚报。
