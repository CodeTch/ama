# 本地 B Worker 共存与有界池饱和

沿已批准 R2 B-R10，采用当前单 MySQL、真实 RuntimeProvider 与 controlled providers，不重开旧窗口或伪造 SQL 业务行。主线程唯一源码写者，sole reviewer 独立复核。

1. 两个全新会议通过现有 UI 上传与业务 API 取得已完成 RAW；停止普通 Worker 后分别建立真实已确认稿/生成输入、受理一个 MINUTES_GENERATE。第三个会议排入真实 TXT。独立已生成会议通过真实审阅、冻结、发布受理，在 genuine native mapping INSERT 后未提交时 halt86，保留原 MARKED execution / RECONCILING task。先做 publication cut、后排新任务，确保 native cut helper 的 sole-pending 条件不被绕过。
2. 单个真实 TranscriptWorkerRuntimeProvider，minutes capacity=1、TXT独立池、配置的 publication/reconciliation capacity；透明 DataSource 仅在第一个真实 minutes S commit 返回后有界暂停。记录此时另一 MINUTES READY/attempt0、真实续租推进、物理身份/root NOWAIT；在暂停期间 TXT 和原 UNKNOWN publication 对账必须完成。观察实际知识库 client traffic GET>0/POST=0，不从存储相同推导无重复发送。
3. 及时释放（最多22秒，原30秒预算不改），两个 minutes 原任务最终各一个 execution/candidate，TXT唯一 RAW及成功事件，publication原task/execution与单一mapping/document不变；数据库只读快照、provider store/grants/identity摘要核对。无手写业务行/lease/task/marker。
4. parent finally 精确终止自己的资格子进程并恢复普通 Worker/全部六服务；最终原任务实际 API settle。失败记录保留，不将一次观察当作任意容量/所有维护来源证明。legacy repair 的真实 origin 若不可获得，保留来源限定并由 final obligation audit 评估本地B依赖。

验证产物写入全新 .tools/local-app/native-coexistence/<uuid> 及 native-crash/<uuid>；独立审查后仅按实际断言采纳。后续锁序/剩余原子性、B00和全分支审查仍须收口。
