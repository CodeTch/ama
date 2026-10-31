# 项目开发约束

- 所有开发计划、代码修改、测试和验证都在项目根目录 `/home/tanren/ama` 的 `main` 分支进行。
- 不创建或使用 `.worktrees`，也不通过 Git worktree 建立或切换工作树。
- 开始工作前确认当前目录是项目根目录且当前分支是 `main`。
- 除非用户明确要求，否则不自行切换到其他分支。
- 开发需要对接外部接口时，通过@\wsl.localhost\Ubuntu-24.04\home\tanren\ama-integration-harness cas项目的接口进行调测试验证与修复，ama-integration-harness为当前项目同时并行开发的兄弟项目，不要把它当作真实权威的接口实现，假如遇到问题也同步修改ama-integration-harness代码（ama-integration-harness项目以固定端口启动，这个设计不修改）