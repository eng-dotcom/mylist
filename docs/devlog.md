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

---

## [2026-11-09] 阶段 2 (Issue #2)：数据库持久化、Repository 仓库层与增删改及快捷加减数量逻辑

1. **遇到了什么问题？**
   - 项目使用 Kotlin `2.3.20` 与 AGP `9.0.1`，若引入需要 KSP/KAPT 注解处理器的重型 ORM 编译插件，容易出现 Kotlin 2.3.20 编译器插件版本不匹配且增加不必要的构建耗时；同时需要保证 JVM 单元测试能脱离 Android 设备高速执行。
2. **采取了什么技术方案？**
   - 采用清晰的 `PurchasedItemDao` 接口抽象，提供两套实现：
     1. 生产环境使用基于 Android 原生 `SQLiteOpenHelper` + `MutableStateFlow` 响应式流封装的 [`AppDatabase`](file:///c:/Users/regal/.gemini/antigravity/scratch/mylist/app/src/main/kotlin/com/example/mylist/data/local/AppDatabase.kt)（管理 `purchased_items` 表，支持 `CONFLICT_REPLACE` 原 ID 恢复与响应式监听）。
     2. 单元测试与 Compose Preview 使用协程安全的 [`InMemoryPurchasedItemDao`](file:///c:/Users/regal/.gemini/antigravity/scratch/mylist/app/src/main/kotlin/com/example/mylist/data/local/InMemoryPurchasedItemDao.kt)。
   - 实现 [`DefaultPurchasedItemRepository`](file:///c:/Users/regal/.gemini/antigravity/scratch/mylist/app/src/main/kotlin/com/example/mylist/data/repository/PurchasedItemRepository.kt)，内置字段二次校验、`adjustQuantity` 快捷数量加减（数量为 `1` 时拦截 `-1` 防止出现 `<= 0` 的非法数量）以及 `lastDeletedItem` 缓存以支持误删 Undo 恢复。
3. **结果如何？**
   - 零外部注解处理器冲突，新增 13 项针对实体映射、表结构 DDL 与仓库层正/异/边界路径的单元测试全部秒级通过。

---

## [2026-11-09] 阶段 3 (Issue #3)：搜索、状态筛选、多维排序与顶部统计概览计算逻辑（ViewModel 层）

1. **遇到了什么问题？**
   - 需要支持搜索关键字、4 种状态筛选（全部/临期/已过期/正常）与 7 种排序方式（过期日期升/降序、添加时间升/降序、金额升/降序、名称排序）任意组合，且金额为可选字段（`null`），在按金额升序或降序时若直接比较会导致 `null` 排在最前干扰有效金额排序；同时统计卡片中的总金额使用 `Double` 直接累加容易产生二进制浮点数精度误差（如 `78.20000000000002`）。
2. **采取了什么技术方案？**
   - 实现纯函数查询与统计引擎 [`ItemQueryEngine`](file:///c:/Users/regal/.gemini/antigravity/scratch/mylist/app/src/main/kotlin/com/example/mylist/domain/query/ItemQueryEngine.kt)：
     - 为金额升序和降序编写自定义 `Comparator`，确保 `amount == null`（未填写金额）的商品在升序和降序中始终排在已填写金额的商品之后，并以 `id` 作为二级稳定排序键。
     - 在 `computeSummaryStats` 中使用 `BigDecimal.valueOf(amount)` 累加已填金额，消除浮点精度误差。
   - 实现 [`PurchasedItemViewModel`](file:///c:/Users/regal/.gemini/antigravity/scratch/mylist/app/src/main/kotlin/com/example/mylist/ui/main/PurchasedItemViewModel.kt)，通过 `combine` 响应式整合数据流、搜索词、状态筛选、排序与待撤销商品状态；当列表项数量为 `1` 且用户点击 `-` 时，自动转为可撤销删除（`pendingUndoItem`），兼顾快捷操作与防误删安全。
3. **结果如何？**
   - 新增 12 项针对 `ItemQueryEngine` 与 `PurchasedItemViewModel` 的单元测试全部通过，全工程累计 46 项单元测试 100% 通过。

