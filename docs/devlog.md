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

---

## [2026-11-09] 阶段 4 (Issue #4)：Jetpack Compose 响应式 UI 实现（自适应屏幕大小）与交互集成

1. **遇到了什么问题？**
   - 需要满足“自适应屏幕大小”要求，同时在 Compose BOM `2026.03.01`（Material 3 1.4+）中不再默认传递 `material-icons-core` 依赖；此外需要保证新增/编辑商品弹窗中的可选金额在未填写、填写小数、编辑回填时的体验流畅且可独立执行单元测试。
2. **采取了什么技术方案？**
   - 抽离纯函数与状态类 [`AdaptiveLayoutConfig`](file:///c:/Users/regal/.gemini/antigravity/scratch/mylist/app/src/main/kotlin/com/example/mylist/ui/main/AdaptiveLayoutConfig.kt) 与 [`ItemFormState`](file:///c:/Users/regal/.gemini/antigravity/scratch/mylist/app/src/main/kotlin/com/example/mylist/ui/main/AdaptiveLayoutConfig.kt)，通过 `BoxWithConstraints` 获取实时屏幕宽度，在 `< 600dp`（手机单列）、`600..839dp`（小平板/折叠屏双列）、`>= 840dp`（大平板三列）之间自适应切换 `LazyVerticalGrid` 列数、边距与顶部统计概览卡片布局。
   - 在 [`MainScreen.kt`](file:///c:/Users/regal/.gemini/antigravity/scratch/mylist/app/src/main/kotlin/com/example/mylist/ui/main/MainScreen.kt) 中实现顶部统计概览卡片（[`SummaryDashboardCard`](file:///c:/Users/regal/.gemini/antigravity/scratch/mylist/app/src/main/kotlin/com/example/mylist/ui/main/MainScreen.kt#L291-L391)）、搜索与状态筛选标签栏（[`SearchAndFilterSection`](file:///c:/Users/regal/.gemini/antigravity/scratch/mylist/app/src/main/kotlin/com/example/mylist/ui/main/MainScreen.kt#L430-L497)）、带过期/临期视觉颜色标签与快捷 `+/-` 按钮的商品卡片（[`PurchasedItemCard`](file:///c:/Users/regal/.gemini/antigravity/scratch/mylist/app/src/main/kotlin/com/example/mylist/ui/main/MainScreen.kt#L502-L654)）、内置 Material 3 `DatePickerDialog` 的新增/编辑弹窗（[`AddEditItemDialog`](file:///c:/Users/regal/.gemini/antigravity/scratch/mylist/app/src/main/kotlin/com/example/mylist/ui/main/MainScreen.kt#L708-L855)）以及底部 Snackbar 撤销（Undo）交互。
3. **结果如何？**
   - 新增 7 项针对自适应断点计算与表单状态回填/校验的单元测试，全工程累计 54 项单元测试全部通过，且 Debug APK 编译打包（`assembleDebug`）一次成功。

---

## [2026-11-09] 阶段 5 (Issue #5)：新增商品分类（Category）功能，支持输入/选择分类及按类别分组展示

1. **遇到了什么问题？**
   - 用户提出新需求：商品需要支持分类（Category），既能手动输入新的自定义分类，也能从已有分类中直接选择，并且主列表需要支持**按照类别进行展示**；同时老版本 SQLite 表结构（v1）无 `category` 列，需保证旧数据升级不丢失。
2. **采取了什么技术方案？**
   - 在 [`PurchasedItem`](file:///c:/Users/regal/.gemini/antigravity/scratch/mylist/app/src/main/kotlin/com/example/mylist/domain/model/PurchasedItem.kt)、[`PurchasedItemEntity`](file:///c:/Users/regal/.gemini/antigravity/scratch/mylist/app/src/main/kotlin/com/example/mylist/data/local/PurchasedItemEntity.kt) 与 [`AppDatabase`](file:///c:/Users/regal/.gemini/antigravity/scratch/mylist/app/src/main/kotlin/com/example/mylist/data/local/AppDatabase.kt) 中新增 `category` 字段（默认 `"未分类"`），将数据库升级至 v2 并通过 `ALTER TABLE purchased_items ADD COLUMN category TEXT NOT NULL DEFAULT '未分类'` 实现平滑迁移。
   - 在 [`ItemQueryEngine`](file:///c:/Users/regal/.gemini/antigravity/scratch/mylist/app/src/main/kotlin/com/example/mylist/domain/query/ItemQueryEngine.kt) 中新增 `extractAvailableCategories`（自动合并用户历史自定义分类与常用预设分类并去重）、`groupByCategory`（按类别分组聚合，计算各分类商品项数、总件数、临期数与已过期数，且将 `"未分类"` 置于末尾）、`SortOption.CATEGORY_ASC`（按分类同类聚合排序）以及按分类筛选与分类关键字搜索能力。
   - 在 [`MainScreen.kt`](file:///c:/Users/regal/.gemini/antigravity/scratch/mylist/app/src/main/kotlin/com/example/mylist/ui/main/MainScreen.kt) 中新增 [`CategoryGroupHeader`](file:///c:/Users/regal/.gemini/antigravity/scratch/mylist/app/src/main/kotlin/com/example/mylist/ui/main/MainScreen.kt#L397-L483) 分类分组标题栏（支持点击折叠/展开各分类商品，并展示分类内件数与临期/过期提醒），在顶部栏与筛选栏提供“按类别分组 / 列表视图”一键切换；在 [`AddEditItemDialog`](file:///c:/Users/regal/.gemini/antigravity/scratch/mylist/app/src/main/kotlin/com/example/mylist/ui/main/MainScreen.kt) 中支持手动输入新分类或点选已有分类。
3. **结果如何？**
   - `CategoryFeatureTest` 扩展至 11 项单元测试（涵盖自定义分类、已有分类合并、按类别分组展示 `groupByCategory`、按分类聚合排序、ViewModel 分组模式切换、超长分类拦截、空分类归一化及数据库迁移 SQL），全工程 65 项单元测试全部通过。

---

## [2026-11-09] 阶段 6 (Issue #6)：重构过期日期选择器为 [年 / 月 / 日] 三列联动选择组件

1. **遇到了什么问题？**
   - 用户反馈：在新增/编辑商品选择过期日期时，使用 Material 3 默认 `DatePicker` 点击顶部的年月（例如 `2026-11`）后，系统只弹出纯年份列表（而没有同时展示月、日选择），交互不符合直觉，期望直接出现 **“年”、“月”、“日”三列联动选择**。
2. **采取了什么技术方案？**
   - 设计并实现纯状态模型 [`YearMonthDayPickerModel`](file:///c:/Users/regal/.gemini/antigravity/scratch/mylist/app/src/main/kotlin/com/example/mylist/ui/main/YearMonthDayPickerDialog.kt#L38-L133) 与三列并排滚动选择组件 [`YearMonthDayPickerDialog`](file:///c:/Users/regal/.gemini/antigravity/scratch/mylist/app/src/main/kotlin/com/example/mylist/ui/main/YearMonthDayPickerDialog.kt#L140-L315)：
     - 界面直接并排呈现 **“年”**、**“月”**、**“日”** 三列可滚动选择列表，打开时各列自动滚动并居中高亮当前已选年、月、日；同时顶部提供 `今天`、`+7天`、`+30天`、`+1年` 快捷保质期预设芯片。
     - 通过 `YearMonth.of(year, month).lengthOfMonth()` 实时计算当前选中年月的最大合法天数（28 / 29 / 30 / 31 天），并在切换年份或月份时自动对 `selectedDay` 执行安全钳制（例如从 `2026-01-31` 切换到 `2月` 自动变为 `28日`，从闰年 `2024-02-29` 切换到平年 `2026年` 自动变为 `28日`），杜绝越界异常。
   - 在 [`MainScreen.kt`](file:///c:/Users/regal/.gemini/antigravity/scratch/mylist/app/src/main/kotlin/com/example/mylist/ui/main/MainScreen.kt) 的 `AddEditItemDialog` 中移除原生 `DatePickerDialog`，替换为 [`YearMonthDayPickerDialog`](file:///c:/Users/regal/.gemini/antigravity/scratch/mylist/app/src/main/kotlin/com/example/mylist/ui/main/YearMonthDayPickerDialog.kt#L140-L315)。
3. **结果如何？**
   - 新增 [`YearMonthDayPickerModelTest`](file:///c:/Users/regal/.gemini/antigravity/scratch/mylist/app/src/test/kotlin/com/example/mylist/ui/main/YearMonthDayPickerModelTest.kt)（6 项单元测试，覆盖三列初始化、年月日联动修改、越界输入安全钳制、大小月切换及平年/闰年 2 月 29 日边界条件），全工程 71 项单元测试全部通过，Debug APK 构建通过。

