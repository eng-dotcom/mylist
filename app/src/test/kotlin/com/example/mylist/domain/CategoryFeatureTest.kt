package com.example.mylist.domain

import com.example.mylist.data.local.AppDatabase
import com.example.mylist.data.local.InMemoryPurchasedItemDao
import com.example.mylist.data.local.toDomainModel
import com.example.mylist.data.local.toEntity
import com.example.mylist.data.repository.DefaultPurchasedItemRepository
import com.example.mylist.domain.model.PurchasedItem
import com.example.mylist.domain.query.ItemQueryEngine
import com.example.mylist.domain.query.SortOption
import com.example.mylist.domain.query.StatusFilter
import com.example.mylist.domain.validation.PurchasedItemValidator
import com.example.mylist.ui.main.ItemFormState
import com.example.mylist.ui.main.PurchasedItemViewModel
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Issue #5 单元测试：测试商品分类（Category）的新增输入、已有分类提取选择、按分类筛选、持久化映射与校验。
 * 覆盖：
 * 1. 正向路径：输入自定义新分类、从已有商品提取分类列表、按分类筛选、Repository/ViewModel 增改分类。
 * 2. 异常路径：超长分类名称（> 20 字符）校验拦截。
 * 3. 边界条件：分类留空/null/空白字符串时自动归为默认分类 "未分类"、重复分类去重、数据库 v1->v2 升级 SQL。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CategoryFeatureTest {

  private val testDispatcher = UnconfinedTestDispatcher()
  private val today = LocalDate.of(2026, 11, 9)

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  // ==================== 1. 正向路径测试 ====================

  @Test
  fun `validateCategory accepts custom category and trims whitespace`() {
    val result = PurchasedItemValidator.validateCategory("  进口冷链  ")
    assertTrue(result.isSuccess)
    assertEquals("进口冷链", result.getOrNull())
  }

  @Test
  fun `extractAvailableCategories merges custom user categories and preset categories without duplicates`() {
    val items = listOf(
      PurchasedItem(
        id = 1L,
        name = "猫粮",
        category = "宠物用品",
        quantity = 1,
        amount = 199.0,
        expirationDate = today.plusDays(90)
      ),
      PurchasedItem(
        id = 2L,
        name = "鲜牛奶",
        category = "饮品乳品",
        quantity = 2,
        amount = 19.9,
        expirationDate = today.plusDays(5)
      ),
      PurchasedItem(
        id = 3L,
        name = "猫罐头",
        category = "宠物用品",
        quantity = 6,
        amount = 45.0,
        expirationDate = today.plusDays(180)
      )
    )

    val categories = ItemQueryEngine.extractAvailableCategories(items, includePresets = true)
    // 用户自定义分类 "宠物用品" 应当出现在可选列表中，且不重复
    assertTrue(categories.contains("宠物用品"))
    assertTrue(categories.contains("饮品乳品"))
    assertEquals(1, categories.count { it == "宠物用品" })
    assertEquals(1, categories.count { it == "饮品乳品" })
    // "未分类" 置于列表末尾兜底
    assertEquals(PurchasedItem.DEFAULT_CATEGORY, categories.last())
  }

  @Test
  fun `filterAndSort filters by selected category and supports searching by category name`() {
    val milk = PurchasedItem(
      id = 1L,
      name = "纯牛奶",
      category = "饮品乳品",
      quantity = 2,
      expirationDate = today.plusDays(5)
    )
    val shampoo = PurchasedItem(
      id = 2L,
      name = "洗发水",
      category = "个护清洁",
      quantity = 1,
      expirationDate = today.plusDays(300)
    )
    val items = listOf(milk, shampoo)

    val filteredByCategory = ItemQueryEngine.filterAndSort(
      items = items,
      searchQuery = "",
      statusFilter = StatusFilter.ALL,
      sortOption = SortOption.EXPIRATION_ASC,
      today = today,
      categoryFilter = "个护清洁"
    )
    assertEquals(listOf(shampoo.id), filteredByCategory.map { it.id })

    val searchedByCategoryKeyword = ItemQueryEngine.filterAndSort(
      items = items,
      searchQuery = "乳品",
      statusFilter = StatusFilter.ALL,
      sortOption = SortOption.EXPIRATION_ASC,
      today = today
    )
    assertEquals(listOf(milk.id), searchedByCategoryKeyword.map { it.id })
  }

  @Test
  fun `ViewModel adds custom category, updates availableCategories, and updates item category`() = runTest {
    val repository = DefaultPurchasedItemRepository(InMemoryPurchasedItemDao())
    val viewModel = PurchasedItemViewModel(repository = repository, todayProvider = { today })

    val added = viewModel.addItem(
      rawName = "冻干咖啡",
      rawCategory = "咖啡茶饮",
      rawQuantity = "2",
      rawAmount = "59.00",
      expirationDate = today.plusDays(60)
    )
    assertTrue(added)

    val stateAfterAdd = viewModel.uiState.value
    assertEquals("咖啡茶饮", stateAfterAdd.items.first().category)
    assertTrue(stateAfterAdd.availableCategories.contains("咖啡茶饮"))

    // 编辑该商品，切换为已有分类 "饮品乳品"
    val item = stateAfterAdd.items.first()
    val updated = viewModel.updateItem(
      id = item.id,
      rawName = item.name,
      rawCategory = "饮品乳品",
      rawQuantity = "3",
      rawAmount = "59.00",
      expirationDate = item.expirationDate,
      createdAtEpochMillis = item.createdAtEpochMillis
    )
    assertTrue(updated)
    assertEquals("饮品乳品", viewModel.uiState.value.items.first().category)
  }

  @Test
  fun `groupByCategory groups items by category, computes per-category stats, and places 未分类 last`() {
    val items = listOf(
      PurchasedItem(
        id = 1L,
        name = "杂物收纳盒",
        category = "未分类",
        quantity = 1,
        expirationDate = today.plusDays(365)
      ),
      PurchasedItem(
        id = 2L,
        name = "鲜牛奶",
        category = "饮品乳品",
        quantity = 2,
        expirationDate = today.plusDays(3) // 临期
      ),
      PurchasedItem(
        id = 3L,
        name = "酸奶",
        category = "饮品乳品",
        quantity = 4,
        expirationDate = today.minusDays(1) // 已过期
      ),
      PurchasedItem(
        id = 4L,
        name = "吐司面包",
        category = "食品生鲜",
        quantity = 1,
        expirationDate = today.plusDays(2) // 临期
      )
    )

    val groups = ItemQueryEngine.groupByCategory(items, today)
    assertEquals(3, groups.size)
    // 具名分类排在前面，"未分类" 置于末尾
    assertEquals(listOf("饮品乳品", "食品生鲜", "未分类"), groups.map { it.category })

    val dairyGroup = groups.first { it.category == "饮品乳品" }
    assertEquals(2, dairyGroup.itemCount)
    assertEquals(6, dairyGroup.totalQuantity)
    assertEquals(1, dairyGroup.expiringSoonCount)
    assertEquals(1, dairyGroup.expiredCount)
    assertEquals(listOf(2L, 3L), dairyGroup.items.map { it.id })
  }

  @Test
  fun `filterAndSort with SortOption CATEGORY_ASC sorts items by category and then expirationDate`() {
    val items = listOf(
      PurchasedItem(id = 1L, name = "项目A", category = "未分类", quantity = 1, expirationDate = today.plusDays(1)),
      PurchasedItem(id = 2L, name = "项目B", category = "个护清洁", quantity = 1, expirationDate = today.plusDays(10)),
      PurchasedItem(id = 3L, name = "项目C", category = "个护清洁", quantity = 1, expirationDate = today.plusDays(2))
    )

    val sorted = ItemQueryEngine.filterAndSort(
      items = items,
      searchQuery = "",
      statusFilter = StatusFilter.ALL,
      sortOption = SortOption.CATEGORY_ASC,
      today = today
    )
    // 同分类聚合，分类内按过期日期由近到远，且 "未分类" 排在最后
    assertEquals(listOf(3L, 2L, 1L), sorted.map { it.id })
  }

  @Test
  fun `ViewModel toggleGroupByCategory switches display mode and updates categoryGroups`() = runTest {
    val repository = DefaultPurchasedItemRepository(InMemoryPurchasedItemDao())
    val viewModel = PurchasedItemViewModel(repository = repository, todayProvider = { today })

    viewModel.addItem(
      rawName = "苹果",
      rawCategory = "食品生鲜",
      rawQuantity = "5",
      rawAmount = "15.0",
      expirationDate = today.plusDays(7)
    )
    viewModel.addItem(
      rawName = "牙膏",
      rawCategory = "个护清洁",
      rawQuantity = "2",
      rawAmount = "18.0",
      expirationDate = today.plusDays(180)
    )

    val initialState = viewModel.uiState.value
    assertEquals(2, initialState.categoryGroups.size)

    val initialGrouped = initialState.isGroupedByCategory
    viewModel.toggleGroupByCategory()
    assertEquals(!initialGrouped, viewModel.uiState.value.isGroupedByCategory)

    viewModel.setGroupByCategory(true)
    assertTrue(viewModel.uiState.value.isGroupedByCategory)
  }

  // ==================== 2. 异常路径测试 ====================

  @Test
  fun `validateCategory rejects category name exceeding 20 characters`() {
    val tooLong = "这是一个非常非常非常非常非常漫长的自定义分类名称超过二十字"
    val result = PurchasedItemValidator.validateCategory(tooLong)
    assertFalse(result.isSuccess)
    assertEquals("分类名称不能超过 20 个字符", result.errorMessage)
  }

  // ==================== 3. 边界条件测试 ====================

  @Test
  fun `boundary - null, empty, or blank category normalizes to default 未分类`() {
    assertEquals("未分类", PurchasedItemValidator.validateCategory(null).getOrNull())
    assertEquals("未分类", PurchasedItemValidator.validateCategory("").getOrNull())
    assertEquals("未分类", PurchasedItemValidator.validateCategory("   ").getOrNull())
  }

  @Test
  fun `boundary - entity mapping and SQLite schema include category column and migration SQL`() {
    val domain = PurchasedItem(
      id = 9L,
      name = "维生素C",
      category = "医药保健",
      quantity = 1,
      amount = 29.9,
      expirationDate = today.plusDays(120)
    )
    val entity = domain.toEntity()
    assertEquals("医药保健", entity.category)
    assertEquals(domain, entity.toDomainModel())

    assertTrue(AppDatabase.CREATE_TABLE_SQL.contains("category TEXT NOT NULL DEFAULT '未分类'"))
    assertTrue(AppDatabase.MIGRATE_1_TO_2_SQL.contains("ALTER TABLE purchased_items ADD COLUMN category"))

    val editForm = ItemFormState.fromExistingItem(domain)
    assertEquals("医药保健", editForm.category)
  }

  @Test
  fun `boundary - groupByCategory returns empty list when items is empty and normalizes blank category`() {
    assertTrue(ItemQueryEngine.groupByCategory(emptyList(), today).isEmpty())

    val blankCatItem = PurchasedItem(
      id = 10L,
      name = "临时物品",
      category = "   ",
      quantity = 1,
      expirationDate = today.plusDays(10)
    )
    val groups = ItemQueryEngine.groupByCategory(listOf(blankCatItem), today)
    assertEquals(1, groups.size)
    assertEquals(PurchasedItem.DEFAULT_CATEGORY, groups.first().category)
  }
}

