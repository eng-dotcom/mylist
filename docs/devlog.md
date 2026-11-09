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

---

## [2026-11-09] 阶段 1 (Issue #1)：工程脚手架搭建、竖屏锁定与核心领域模型及校验逻辑

1. **遇到了什么问题？**
   - 使用 `android create empty-activity` 生成的工程默认指定 `compileSdk = 37`，但 SDK 仓库中仅存在 `platforms/android-36` 与 `platforms/android-37.0`（不存在 `platforms/android-37`）；同时当前 Windows 环境未预装全局 JDK（`JAVA_HOME` 未配置），导致 Gradle Wrapper 无法直接启动。
2. **采取了什么技术方案？**
   - 下载并解压便携版 Microsoft OpenJDK 21 至 `$env:USERPROFILE\.jdks`，并在 `gradle.properties` 中配置 `org.gradle.java.home`；通过 `android sdk install` 安装 `platforms/android-36` 与 `build-tools/36.0.0`，将 `app\build.gradle.kts` 的 `compileSdk` 和 `targetSdk` 调整为 `36`。
   - 在 `app\src\main\AndroidManifest.xml` 中为 `MainActivity` 增加 `android:screenOrientation="portrait"`，锁定竖屏显示。
   - 按照 TDD 测试先行原则，先编写 `PurchasedItemTest`、`PurchasedItemValidatorTest` 与 `ManifestConfigTest`，再实现 `ExpirationStatus`、`ExpirationInfo`、`PurchasedItem` 及 `PurchasedItemValidator`。
3. **结果如何？**
   - 完整覆盖了正常/临期/已过期状态计算、临界天数（-1/0/7/8天）、可选金额（不填/0/两位小数/超两位小数拦截）及纯正整数数量校验，所有单元测试通过。

