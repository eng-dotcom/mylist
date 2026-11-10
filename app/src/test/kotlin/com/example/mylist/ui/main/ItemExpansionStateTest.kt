package com.example.mylist.ui.main

import com.example.mylist.data.local.InMemoryPurchasedItemDao
import com.example.mylist.data.repository.DefaultPurchasedItemRepository
import com.example.mylist.domain.model.ExpirationStatus
import com.example.mylist.domain.model.PurchasedItem
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Issue #7 单元测试：测试商品列表二级折叠与商品栏点击展开详情逻辑（[ItemExpansionState] 与 [ItemCardDisplayModel]）。
 * 覆盖：
 * 1. 正向路径：
 *    - 默认收起态仅包含商品名称和剩余/已过期天数，不展示过期日期、金额、数量及操作按钮；
 *    - 点击商品栏切换为展开态后，完整展示过期日期、金额、数量及编辑/删除按钮；
 *    - 再次点击商品栏收起详情；大类标题支持独立折叠/展开。
 * 2. 异常路径：
 *    - 对非法商品 ID（<= 0）或空白分类名执行展开/折叠切换时安全忽略，不污染状态。
 * 3. 边界条件：
 *    - 当某个已展开的商品被删除时，自动从 expandedItemIds 中清理；
 *    - 当某个大类被收起时，该大类下已展开的商品自动收起，避免再次展开大类时残留旧展开态。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ItemExpansionStateTest {

  private val testDispatcher = UnconfinedTestDispatcher()
  private val today = LocalDate.of(2026, 11, 10)

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
  fun `collapsed item card display model only exposes name and remaining or expired days`() {
    val expiringItem = PurchasedItem(
      id = 1L,
      name = "鲜牛奶",
      category = "饮品乳品",
      quantity = 3,
      amount = 19.90,
      expirationDate = today.plusDays(4)
    )

    val collapsedModel = ItemCardDisplayModel.fromItem(
      item = expiringItem,
      isExpanded = false,
      today = today
    )

    // 默认收起态：只展示名称和剩余过期天数
    assertEquals("鲜牛奶", collapsedModel.name)
    assertEquals("剩余 4 天", collapsedModel.expirationBadgeText)
    assertEquals(ExpirationStatus.EXPIRING_SOON, collapsedModel.expirationStatus)
    assertFalse(collapsedModel.isExpanded)

    // 其它信息（过期日期、金额、数量、操作按钮）在默认收起态下均不展示
    assertNull(collapsedModel.expirationDateText)
    assertNull(collapsedModel.amountText)
    assertNull(collapsedModel.quantityText)
    assertFalse(collapsedModel.showActions)
  }

  @Test
  fun `expanded item card display model exposes expiration date, amount, quantity, and actions`() {
    val expiredItem = PurchasedItem(
      id = 2L,
      name = "全麦吐司",
      category = "食品生鲜",
      quantity = 2,
      amount = 12.50,
      expirationDate = today.minusDays(2)
    )

    val expandedModel = ItemCardDisplayModel.fromItem(
      item = expiredItem,
      isExpanded = true,
      today = today
    )

    // 点击展开态：展示名称、已过期天数，同时展示过期日期、金额、数量与操作按钮
    assertEquals("全麦吐司", expandedModel.name)
    assertEquals("已过期 2 天", expandedModel.expirationBadgeText)
    assertEquals(ExpirationStatus.EXPIRED, expandedModel.expirationStatus)
    assertTrue(expandedModel.isExpanded)

    assertEquals("过期日期：2026-11-08", expandedModel.expirationDateText)
    assertEquals("金额：¥12.50", expandedModel.amountText)
    assertEquals("2", expandedModel.quantityText)
    assertTrue(expandedModel.showActions)
  }

  @Test
  fun `toggling item row switches between collapsed and expanded states independently`() {
    var state = ItemExpansionState()
    assertFalse(state.isItemExpanded(1L))
    assertFalse(state.isItemExpanded(2L))

    // 点击商品 1 展开
    state = state.toggleItem(1L)
    assertTrue(state.isItemExpanded(1L))
    assertFalse(state.isItemExpanded(2L))

    // 点击商品 2 展开（支持同时查看或独立切换）
    state = state.toggleItem(2L)
    assertTrue(state.isItemExpanded(1L))
    assertTrue(state.isItemExpanded(2L))

    // 再次点击商品 1 收起
    state = state.toggleItem(1L)
    assertFalse(state.isItemExpanded(1L))
    assertTrue(state.isItemExpanded(2L))
  }

  // ==================== 2. 异常路径测试 ====================

  @Test
  fun `toggling invalid item id or blank category name is safely ignored`() {
    val initial = ItemExpansionState()

    val afterInvalidId = initial.toggleItem(0L).toggleItem(-10L)
    assertTrue(afterInvalidId.expandedItemIds.isEmpty())

    val afterBlankCategory = initial.toggleCategory("").toggleCategory("   ")
    assertTrue(afterBlankCategory.collapsedCategories.isEmpty())
  }

  // ==================== 3. 边界条件测试 ====================

  @Test
  fun `boundary - collapsing a category automatically collapses expanded items in that category`() {
    val itemInDairy = PurchasedItem(
      id = 10L,
      name = "酸奶",
      category = "饮品乳品",
      quantity = 2,
      expirationDate = today.plusDays(3)
    )
    val itemInFood = PurchasedItem(
      id = 20L,
      name = "苹果",
      category = "食品生鲜",
      quantity = 5,
      expirationDate = today.plusDays(6)
    )

    var state = ItemExpansionState()
      .toggleItem(itemInDairy.id)
      .toggleItem(itemInFood.id)
    assertTrue(state.isItemExpanded(10L))
    assertTrue(state.isItemExpanded(20L))

    // 收起“饮品乳品”大类时，该大类下的商品 10L 自动收起，而“食品生鲜”下的商品 20L 保持原状
    state = state.toggleCategory(
      category = "饮品乳品",
      itemsInCategory = listOf(itemInDairy.id)
    )
    assertTrue(state.isCategoryCollapsed("饮品乳品"))
    assertFalse(state.isItemExpanded(10L))
    assertTrue(state.isItemExpanded(20L))

    // 再次点击“饮品乳品”大类展开时，商品 10L 处于默认只显示名称和过期天数的收起态
    state = state.toggleCategory(
      category = "饮品乳品",
      itemsInCategory = listOf(itemInDairy.id)
    )
    assertFalse(state.isCategoryCollapsed("饮品乳品"))
    assertFalse(state.isItemExpanded(10L))
  }

  @Test
  fun `boundary - ViewModel cleans up expandedItemIds when item is deleted and handles null amount in expanded model`() = runTest {
    val repository = DefaultPurchasedItemRepository(InMemoryPurchasedItemDao())
    val viewModel = PurchasedItemViewModel(repository = repository, todayProvider = { today })

    viewModel.addItem(
      rawName = "矿泉水",
      rawCategory = "饮品乳品",
      rawQuantity = "6",
      rawAmount = null,
      expirationDate = today.plusDays(30)
    )

    val addedItem = viewModel.uiState.value.items.first()
    // 默认不展开
    assertFalse(viewModel.uiState.value.expandedItemIds.contains(addedItem.id))

    // 点击展开该商品
    viewModel.toggleItemExpanded(addedItem.id)
    assertTrue(viewModel.uiState.value.expandedItemIds.contains(addedItem.id))

    // 验证未填金额商品在展开态下显示“未填金额”
    val expandedNoAmountModel = ItemCardDisplayModel.fromItem(
      item = addedItem,
      isExpanded = true,
      today = today
    )
    assertNotNull(expandedNoAmountModel.amountText)
    assertEquals("未填金额", expandedNoAmountModel.amountText)

    // 删除该商品后，其 ID 从 expandedItemIds 中自动移除
    viewModel.deleteItem(addedItem)
    assertFalse(viewModel.uiState.value.expandedItemIds.contains(addedItem.id))
  }
}

