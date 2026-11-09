# 开发日志 (DevLog)

本文件记录项目开发过程中的重大修改、架构决策及问题排查记录。

---

## [2026-11-09] 阶段 0：需求梳理与项目仓库初始化

1. **遇到了什么问题？**
   - 初始需求文档 `requirement.md` 仅包含基础功能点，缺乏对字段边界条件、临期状态判定、防误删交互及持久化方案的具体定义；同时本地工作区尚未初始化 Git 仓库及关联 GitHub Issues。
2. **采取了什么技术方案？**
   - 完善 `requirement.md`，补充了临期/过期视觉标签（已过期、7天内临期、正常）与状态筛选、列表快捷加减数量（不含单位，保持纯正整数）、删除后 Snackbar 撤销（Undo）、顶部统计概览卡片以及多维度排序功能。
   - 确定采用 **Kotlin + Jetpack Compose (Material 3) + Room + MVVM + Coroutines/Flow** 技术栈，严格执行测试先行（TDD）开发流程。
   - 初始化 Git 仓库（`main` 分支），通过 `gh` CLI 创建远程 GitHub 仓库并将各阶段开发任务拆解为 GitHub Issues。
3. **结果如何？**
   - 需求边界清晰，文档结构（`requirement.md`、`README.md`、`docs\devlog.md`）齐备，所有开发阶段已录入 GitHub Issues 管理。
