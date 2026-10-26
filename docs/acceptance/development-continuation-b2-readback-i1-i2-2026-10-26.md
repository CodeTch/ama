# B2 readback I1/I2：源码与离线修复已采用，尚未发运行许可

更新时间：2026-10-26T11:49:19.374798+08:00。本条为用户明确“继续任务”后的当前接续记录；旧暂停已解除，旧暂停文件/报告/冻结包保留为历史。唯一产品树P=/home/tanren/ama/.worktrees/phase-b-completion，W=P/.superpowers/sdd/2026-10-23-ama-phase-b-remaining。HEAD仍739193dff6dea6c28999260d4d332f99f1a58d12，分支codex/phase-b-completion-20261023，累积未提交工作保留，无commit/merge/push/reset。

EXACT-RETRY-READBACK-I1/I2均已在源码/离线范围解决并独立通过。I1仅精确放行TransactionRunner两条SET，真实required/JdbcTemplate/fakeJDBC验证初始化/holder/commit/close；精确session hash/actor/CSRF与错误/缺失/跨线程许可及SET/DML/DDL/callable/batch拒绝回归通过。I2将readback validator接入真实run_window，早于环境/target/launch；临时公开输入验证有效release进入正确分支，缺失/过期/未来/10类不匹配release/源码变化在副作用前失败，既有3类模式路由保留。

已采用证据：Java相关回归71项0失败/错误/跳过；Python工具回归24项通过；最终离线test-compile退出0。causalRED为guard7/1failure和dispatch8methods/15failed assertions-subtests，首轮GREEN测试源码与causalRED一致；最终M1加强断言另有三个独立mutation失败证据，当前真实guard下71项再次通过。首轮fake契约失败和未配置单元输出目录失败均保留。全量Maven单元回归**未通过**：配置输出目录后的432项中4error，均WorkerShutdownTest反射四参数构造器而Runtime已有六参数；两份源码与旧冻结包相同，单独登记Task13，未混入本批最小修复。

新源码checkpoint：task-11-b2-readback-i1-i2-r1-source-checkpoint-20261026.json，SHA256 0bda13715da92e704c87ce543125144dc5c5c23d27af423f9d686f9bf0a4cb09，1096个完整冻结源码文件；相对旧1094文件仅2修改+2新增。旧2be86f4d源码包与15证据完整保留，首轮173证据及M1增量24证据全部核验。源码报告task-11-b2-readback-i1-i2-r1-source-report-20261026.md。独立审查task-11-b2-readback-i1-i2-r1-independent-review-20261026.md，SHA256 a8ec9206bbb3645fff4b515a4468b79587725269bd0c5b4c7f689c4e5d8396a2，SPEC PASS / QUALITY APPROVED，限定完整readback子功能source/offline。根复核task-11-b2-readback-i1-i2-root-verification-20261026.json。Task11 cap5/5不变。

协作实际记录：主Agent/root唯一实现者与源码修改者；只创建/root/readback_subfunction_reviewer，spawn工具接受model=gpt-6-astra、reasoning_effort=xhigh、fork_turns=none，不再委派。主会话目标gpt-6.1-sol/xhigh，未执行切换，可靠会话配置查询不可用，不声称实际已切换。见task-11-b2-readback-i1-i2-collaboration-20261026.json。

## 下一步与运行门禁

当前无readback release或目标窗口；本批没有实际SQL、会话/上下文、provider/native、Host矩阵或其他运行资源探测。保留资源“仍存活”只有旧窗口历史观察，不能当作当前事实。旧惰性ACK48ebb70d仍命名旧不可覆盖源码checkpoint；新源码必须先经单独配置推广绑定版本化freeze并复审，不能直接使用旧命令或改写旧包。再核验准确资源身份/当前计数，另建新鲜限时一次release。此source修复不等于运行通过。

之后仅允许原有后继task3588aaba-ecfa-4889-8954-46c1454447d4/execution65fa6b21-22da-4a2e-a886-341b16978106/key task11-b2-exact-retry-winner-01的readback：先保存完整17表后像/数字类并通过完整oracle，再一个receipt-only context、4会话生命周期、7既有key请求、最后唯一剩余mode=ro SQLite。业务写0、grant0、native0、providerGET/destinationPOST/publicationPOST增量0；不claim/SEND。旧b2-exact-retry-01始终FAILED/PARTIAL_UNQUALIFIED，36请求/12物理回滚/2并发202/1grant/2SQLite/relay39-17-1保持旧事实，不得重跑或改写PASS。新失败也封存不重耗。

运行后证据复核继续复用同一审查者；原FAILED+新readback联合事实经根采用才进入B3。剩余B3/B4/B5/B6→Task12→Task13与held/UNKNOWN保护、真实legacy来源未证实、T10M1/公平性/完整性/HTTPS/S3/browser/公司能力等义务均继承旧精准暂停记录，不重做已采用Host/B1/B2历史，完成B后止，不进CDEF。详情以旧docs/acceptance/development-pause-b2-exact-retry-readback-2026-10-26.md后半部分为历史事实及未完义务清单。
