# RustFS 1.0.3 受控 S3 参考实现预检

日期：2026-10-22。范围：**本地测试参考实现的真实 HTTP 能力探测**；允许据此继续 S3 适配器开发，不代表 B-04、共同 Java 契约、私有产品定型或生产资格通过。既有 SeaweedFS 4.02 失败结论及全部证据保留，见 [前次记录](s3-capability-preflight.md)。

官方 release/source 为 `1.0.3 / 331fb896285a7686f927baf8d9d4dc2d3d3a10a9`，实际启动版本、源码 revision、Apache-2.0 许可和 linux/amd64 均已核对；固定平台镜像摘要 `sha256:128220dc9382bac81940a1a9616ee8c274a3f3b8f647eedbeb50df5bb8b49922`。来源：[官方发行](https://github.com/rustfs/rustfs/releases/tag/1.0.3)、[固定源码许可](https://github.com/rustfs/rustfs/blob/331fb896285a7686f927baf8d9d4dc2d3d3a10a9/LICENSE)。仅运行任务自己的 loopback、单进程/单盘容器，2 CPU、2 GiB 限额，没有数据库、公司平台或产品代码修改。

|实际检查|结果与边界|
|---|---|
|never-versioned/enabled/suspended 三种模式，每种 PUT/PUT、complete/complete、PUT/complete 各20组|180组全部只有一个200和一个412；完整GET的实际字节/哈希始终对应成功方。是有界正确性探测，不是负载证明|
|版本、重放、正常重启|串行同/异内容条件重试均412，原内容保留；enabled模式60个成功版本的VersionId与当前GET一致。相同内容的逻辑成功仍需适配器查验|
|确定性响应丢失|三种模式共9次：代理看到PUT/complete200或abort204后在响应头前断连，客户端确实无HTTP响应；后续查回与冲突写保留原内容|
|实际校验与multipart|错误PUT/part SHA256被400拒绝；5MiB非末part通过，少1字节被拒绝；倒序/错误ETag拒绝，重复manifest却被接受，需要适配器独立拒绝重复与伪造receipt|
|Range诊断|三个`bytes=16-15`错误请求返回400，探针预期416，失败原样保留；合法内部EOF零长度读必须适配器本地处理。超长Range会被服务截短，适配器须先验证精确区间|

原始统计为1512条操作结果、267项诊断检查（264真、3假）、9个代理上游观察。一次 CreateMultipartUpload 超时没有HTTP响应，效果未知，不能推断未创建；后续当前列表未见残留会话。镜像元数据解析、目录权限及重启后临时端口变化等harness问题另存，没有归为提供者功能结果，也没有重放已经完成的竞争测试。

源码、固定客户端来源/哈希、所有脱敏请求与结果、失败和续跑记录、资源测量、完整报告和清单共46份文件保存在 `artifacts/phase-b/s3-rustfs-1.0.3/`，归档 `manifest.json`逐项登记SHA256。规范 `evidence/probe.jsonl` SHA256为 `f457cc93cf8cce8408e1c3d1b76633f7e359669a60fd3e3959e958ab16d254f9`；完整报告SHA256为 `aceb017af73faa3e8a30425ef734eb22490992b185f2bcdb4e7117efb2caa41e`。凭证、server.env、数据、未脱敏服务日志和依赖缓存均不在归档中。原容器已按ID/标签/挂载/loopback所有权检查停止并移除，数据保留；没有操作其他容器。

后续 S3FileStorage 仍须证明：真实流和组装后的大小/完整SHA、通用任意逻辑分片到合法S3传输part的持久化合并、冻结清单、相同内容及完成/中止幂等、跨进程恢复、精确范围和后端路由，以及数据库登记/权限/不可读孤儿。用户metadata及ETag不作可信摘要。原子条件写不能阻止另一个获授权主体无条件覆盖，生产还需明确项目写入边界和桶策略资格。RHEL/XFS、durable ACK、断电、DRBD、隔离、自动接管及容量均未由此通过。
