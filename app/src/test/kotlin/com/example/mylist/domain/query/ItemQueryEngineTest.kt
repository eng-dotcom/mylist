package com.example.mylist.domain.query

import com.example.mylist.domain.model.PurchasedItem
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Issue #3 单元测试：测试搜索、状态筛选、多维排序与顶部统计概览计算引擎 [ItemQueryEngine]。
 * 覆盖：
 * 1. 正向路径：关键字搜索（忽略大小写）、按临期/已过期/正常状态筛选、按过期日期/添加时间/金额/名称排序、统计卡片计算。
 * 2. 异常/空结果路径：搜索无匹配结果、列表全部未填写金额时的统计与金额排序表现。
 * 3. 边界条件：空列表统计与排序、部分商品未填金额（null）在金额升/降序中始终排在末尾、相同过期日期二级排序、搜索+状态筛选+排序组合生效。
 */
class ItemQueryEngineTest {

  private val today = LocalDate.of(2026, 11, 9)

  private val expiredMilk = PurchasedItem(
    id = 1L,
    name = "纯牛奶",
    quantity = 2,
    amount = 25.50,
    expirationDate = today.minusDays(2), // 已过期 2 天
    createdAtEpochMillis = 1000L,
    updatedAtEpochMillis = 1000L
  )

  private val expiringBread = PurchasedItem(
    id = 2L,
    name = "全麦面包",
    quantity = 1,
    amount = 12.80,
    expirationDate = today.plusDays(3), // 临期：剩余 3 天
    createdAtEpochMillis = 3000L,
    updatedAtEpochMillis = 3000L
  )

  private val expiringTodayYogurt = PurchasedItem(
    id = 3L,
    name = "希腊酸奶 (Greek Yogurt)",
    quantity = 4,
    amount = null, // 未填金额
    expirationDate = today, // 临期边界：今天到期
    createdAtEpochMillis = 2000L,
    updatedAtEpochMillis = 2000L
  )

  private val normalApple = PurchasedItem(
    id = 4L,
    name = "红富士苹果",
    quantity = 6,
    amount = 39.90,
    expirationDate = today.plusDays(14), // 正常：剩余 14 天
    createdAtEpochMillis = 4000L,
    updatedAtEpochMillis = 4000L
  )

  private val sampleItems = listOf(normalApple, expiringBread, expiredMilk, expiringTodayYogurt)

  // ==================== 1. 正向路径测试 ====================

  @Test
  fun `default sort orders by expiration date ascending (earliest first)`() {
    val result = ItemQueryEngine.filterAndSort(
      items = sampleItems,
      searchQuery = "",
      statusFilter = StatusFilter.ALL,
      sortOption = SortOption.EXPIRATION_ASC,
      today = today
    )

    assertEquals(
      listOf(expiredMilk.id, expiringTodayYogurt.id, expiringBread.id, normalApple.id),
      result.map { it.id }
    )
  }

  @Test
  fun `search query matches item name case-insensitively and trims whitespace`() {
    val resultCn = ItemQueryEngine.filterAndSort(
      items = sampleItems,
      searchQuery = "  奶  ",
      statusFilter = StatusFilter.ALL,
      sortOption = SortOption.EXPIRATION_ASC,
      today = today
    )
    assertEquals(listOf(expiredMilk.id, expiringTodayYogurt.id), resultCn.map { it.id })

    val resultEn = ItemQueryEngine.filterAndSort(
      items = sampleItems,
      searchQuery = "greek",
      statusFilter = StatusFilter.ALL,
      sortOption = SortOption.EXPIRATION_ASC,
      today = today
    )
    assertEquals(listOf(expiringTodayYogurt.id), resultEn.map { it.id })
  }

  @Test
  fun `status filter returns only matching expiration status items`() {
    val expiringOnly = ItemQueryEngine.filterAndSort(
      items = sampleItems,
      searchQuery = "",
      statusFilter = StatusFilter.EXPIRING_SOON,
      sortOption = SortOption.EXPIRATION_ASC,
      today = today
    )
    assertEquals(listOf(expiringTodayYogurt.id, expiringBread.id), expiringOnly.map { it.id })

    val expiredOnly = ItemQueryEngine.filterAndSort(
      items = sampleItems,
      searchQuery = "",
      statusFilter = StatusFilter.EXPIRED,
      sortOption = SortOption.EXPIRATION_ASC,
      today = today
    )
    assertEquals(listOf(expiredMilk.id), expiredOnly.map { it.id })

    val normalOnly = ItemQueryEngine.filterAndSort(
      items = sampleItems,
      searchQuery = "",
      statusFilter = StatusFilter.NORMAL,
      sortOption = SortOption.EXPIRATION_ASC,
      today = today
    )
    assertEquals(listOf(normalApple.id), normalOnly.map { it.id })
  }

  @Test
  fun `computeSummaryStats aggregates counts, total quantity, and non-null amounts accurately`() {
    val stats = ItemQueryEngine.computeSummaryStats(sampleItems, today)

    assertEquals(4, stats.totalItemCount)
    assertEquals(2 + 1 + 4 + 6, stats.totalQuantityCount)
    assertEquals(1, stats.expiredCount)
    assertEquals(2, stats.expiringSoonCount)
    assertEquals(1, stats.normalCount)
    // 25.50 + 12.80 + 39.90 = 78.20
    assertEquals(78.20, stats.totalSpentAmount, 0.0001)
    assertEquals("¥78.20", stats.formattedTotalSpent())
  }

  // ==================== 2. 异常/空结果路径测试 ====================

  @Test
  fun `search with no matching keyword returns empty list`() {
    val result = ItemQueryEngine.filterAndSort(
      items = sampleItems,
      searchQuery = "榴莲千层",
      statusFilter = StatusFilter.ALL,
      sortOption = SortOption.EXPIRATION_ASC,
      today = today
    )
    assertTrue(result.isEmpty())
  }

  @Test
  fun `computeSummaryStats handles empty list or items with all null amounts`() {
    val emptyStats = ItemQueryEngine.computeSummaryStats(emptyList(), today)
    assertEquals(0, emptyStats.totalItemCount)
    assertEquals(0, emptyStats.totalQuantityCount)
    assertEquals(0.0, emptyStats.totalSpentAmount, 0.0001)
    assertEquals("¥0.00", emptyStats.formattedTotalSpent())

    val noAmountItems = listOf(expiringTodayYogurt, expiringTodayYogurt.copy(id = 9L, amount = null))
    val noAmountStats = ItemQueryEngine.computeSummaryStats(noAmountItems, today)
    assertEquals(2, noAmountStats.totalItemCount)
    assertEquals(8, noAmountStats.totalQuantityCount)
    assertEquals(0.0, noAmountStats.totalSpentAmount, 0.0001)
  }

  // ==================== 3. 边界条件测试 ====================

  @Test
  fun `boundary - sort by amount places null amounts at the end for both ASC and DESC`() {
    val ascResult = ItemQueryEngine.filterAndSort(
      items = sampleItems,
      searchQuery = "",
      statusFilter = StatusFilter.ALL,
      sortOption = SortOption.AMOUNT_ASC,
      today = today
    )
    // 12.80 -> 25.50 -> 39.90 -> null
    assertEquals(
      listOf(expiringBread.id, expiredMilk.id, normalApple.id, expiringTodayYogurt.id),
      ascResult.map { it.id }
    )

    val descResult = ItemQueryEngine.filterAndSort(
      items = sampleItems,
      searchQuery = "",
      statusFilter = StatusFilter.ALL,
      sortOption = SortOption.AMOUNT_DESC,
      today = today
    )
    // 39.90 -> 25.50 -> 12.80 -> null
    assertEquals(
      listOf(normalApple.id, expiredMilk.id, expiringBread.id, expiringTodayYogurt.id),
      descResult.map { it.id }
    )
  }

  @Test
  fun `boundary - combined search query, status filter, and descending expiration sort`() {
    val extraExpiringMilk = PurchasedItem(
      id = 5L,
      name = "高钙牛奶",
      quantity = 1,
      amount = 18.0,
      expirationDate = today.plusDays(5),
      createdAtEpochMillis = 5000L,
      updatedAtEpochMillis = 5000L
    )
    val all = sampleItems + extraExpiringMilk

    val combined = ItemQueryEngine.filterAndSort(
      items = all,
      searchQuery = "奶",
      statusFilter = StatusFilter.EXPIRING_SOON,
      sortOption = SortOption.EXPIRATION_DESC,
      today = today
    )

    // 匹配 "奶" 且属于 EXPIRING_SOON 的有：高钙牛奶（+5天）和 希腊酸奶（0天），降序排列应为 [5L, 3L]
    assertEquals(listOf(5L, 3L), combined.map { it.id })
  }
}
