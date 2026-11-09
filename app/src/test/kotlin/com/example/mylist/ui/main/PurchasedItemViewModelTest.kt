package com.example.mylist.ui.main

import com.example.mylist.data.local.InMemoryPurchasedItemDao
import com.example.mylist.data.repository.DefaultPurchasedItemRepository
import com.example.mylist.domain.query.SortOption
import com.example.mylist.domain.query.StatusFilter
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
 * Issue #3 单元测试：测试 [PurchasedItemViewModel] 的状态流、增删改、搜索筛选排序、快捷加减数量与删除撤销（Undo）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PurchasedItemViewModelTest {

  private val testDispatcher = UnconfinedTestDispatcher()
  private val today = LocalDate.of(2026, 11, 9)
  private lateinit var repository: DefaultPurchasedItemRepository
  private lateinit var viewModel: PurchasedItemViewModel

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    repository = DefaultPurchasedItemRepository(InMemoryPurchasedItemDao())
    viewModel = PurchasedItemViewModel(
      repository = repository,
      todayProvider = { today }
    )
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  // ==================== 1. 正向路径测试 ====================

  @Test
  fun `adding items updates uiState items and summaryStats sorted by expiration asc`() = runTest {
    val added1 = viewModel.addItem(
      rawName = "苹果",
      rawQuantity = "5",
      rawAmount = "20.00",
      expirationDate = today.plusDays(10)
    )
    val added2 = viewModel.addItem(
      rawName = "鲜牛奶",
      rawQuantity = "2",
      rawAmount = "15.50",
      expirationDate = today.plusDays(2)
    )

    assertTrue(added1)
    assertTrue(added2)

    val state = viewModel.uiState.value
    assertEquals(2, state.items.size)
    // 默认按过期日期升序：鲜牛奶 (+2天) 排在 苹果 (+10天) 前面
    assertEquals("鲜牛奶", state.items[0].name)
    assertEquals("苹果", state.items[1].name)

    // 验证顶部统计概览卡片数据
    assertEquals(2, state.summaryStats.totalItemCount)
    assertEquals(7, state.summaryStats.totalQuantityCount)
    assertEquals(1, state.summaryStats.expiringSoonCount)
    assertEquals(35.50, state.summaryStats.totalSpentAmount, 0.001)
  }

  @Test
  fun `updating searchQuery, statusFilter, and sortOption dynamically filters and sorts items`() = runTest {
    viewModel.addItem("纯牛奶", "1", "10.0", today.minusDays(1)) // 已过期
    viewModel.addItem("草莓牛奶", "2", "18.0", today.plusDays(3)) // 临期
    viewModel.addItem("全麦面包", "1", null, today.plusDays(20)) // 正常

    // 1. 搜索 "牛奶"
    viewModel.updateSearchQuery("牛奶")
    assertEquals(2, viewModel.uiState.value.items.size)

    // 2. 在搜索基础上筛选 "临期"
    viewModel.updateStatusFilter(StatusFilter.EXPIRING_SOON)
    assertEquals(1, viewModel.uiState.value.items.size)
    assertEquals("草莓牛奶", viewModel.uiState.value.items[0].name)

    // 3. 重置搜索与筛选，切换为按金额降序排序
    viewModel.updateSearchQuery("")
    viewModel.updateStatusFilter(StatusFilter.ALL)
    viewModel.updateSortOption(SortOption.AMOUNT_DESC)
    assertEquals(
      listOf("草莓牛奶", "纯牛奶", "全麦面包"),
      viewModel.uiState.value.items.map { it.name }
    )
  }

  @Test
  fun `deleteItem sets pendingUndoItem and undoDelete restores item to list`() = runTest {
    viewModel.addItem("巧克力", "3", "29.90", today.plusDays(30))
    val item = viewModel.uiState.value.items.first()

    viewModel.deleteItem(item)
    assertTrue(viewModel.uiState.value.items.isEmpty())
    assertNotNull(viewModel.uiState.value.pendingUndoItem)
    assertEquals("巧克力", viewModel.uiState.value.pendingUndoItem?.name)

    viewModel.undoDelete()
    assertNull(viewModel.uiState.value.pendingUndoItem)
    assertEquals(1, viewModel.uiState.value.items.size)
    assertEquals("巧克力", viewModel.uiState.value.items.first().name)
  }

  // ==================== 2. 异常路径测试 ====================

  @Test
  fun `addItem with invalid fields sets errorMessage and returns false`() = runTest {
    val success = viewModel.addItem(
      rawName = "   ",
      rawQuantity = "2",
      rawAmount = "10",
      expirationDate = today
    )

    assertFalse(success)
    assertEquals("商品名称不能为空", viewModel.uiState.value.errorMessage)
    assertTrue(viewModel.uiState.value.items.isEmpty())

    viewModel.clearError()
    assertNull(viewModel.uiState.value.errorMessage)
  }

  // ==================== 3. 边界条件测试 ====================

  @Test
  fun `boundary - increment and decrement quantity on list item, and decrementing from 1 deletes with Undo`() = runTest {
    viewModel.addItem("盒装酸奶", "1", "6.50", today.plusDays(4))
    val initial = viewModel.uiState.value.items.first()
    assertEquals(1, initial.quantity)

    // 点击 +1 -> 数量变为 2
    viewModel.incrementQuantity(initial)
    val afterInc = viewModel.uiState.value.items.first()
    assertEquals(2, afterInc.quantity)

    // 点击 -1 -> 数量变为 1
    viewModel.decrementQuantity(afterInc)
    val afterDecTo1 = viewModel.uiState.value.items.first()
    assertEquals(1, afterDecTo1.quantity)

    // 当数量为 1 时再次点击 -1 -> 自动移除该商品并放入 pendingUndoItem 支持一键撤销
    viewModel.decrementQuantity(afterDecTo1)
    assertTrue(viewModel.uiState.value.items.isEmpty())
    assertEquals("盒装酸奶", viewModel.uiState.value.pendingUndoItem?.name)

    // 点击撤销 -> 恢复数量为 1 的盒装酸奶
    viewModel.undoDelete()
    assertEquals(1, viewModel.uiState.value.items.size)
    assertEquals(1, viewModel.uiState.value.items.first().quantity)
  }
}

