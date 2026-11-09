package com.example.mylist.domain.model

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Issue #1 单元测试：测试核心领域模型 [PurchasedItem] 与过期状态计算 [ExpirationStatus]。
 * 覆盖：正向路径、异常/过期路径、以及临界天数（-1天、0天、7天、8天）与可选金额边界条件。
 */
class PurchasedItemTest {

  private val baseToday = LocalDate.of(2026, 11, 9)

  // ==================== 1. 正向路径测试 ====================

  @Test
  fun `normal status when expiration date is more than 7 days away`() {
    val item = PurchasedItem(
      id = 1L,
      name = "纯牛奶",
      quantity = 2,
      amount = 39.9,
      expirationDate = baseToday.plusDays(15),
      createdAtEpochMillis = 1000L,
      updatedAtEpochMillis = 1000L
    )

    val info = item.expirationInfo(baseToday)
    assertEquals(ExpirationStatus.NORMAL, info.status)
    assertEquals(15L, info.daysDelta)
    assertEquals("剩余 15 天", info.label)
    assertEquals("¥39.90", item.formattedAmount())
  }

  @Test
  fun `expiring soon status when expiration date is within 7 days`() {
    val item = PurchasedItem(
      id = 2L,
      name = "全麦面包",
      quantity = 1,
      amount = 12.5,
      expirationDate = baseToday.plusDays(3),
      createdAtEpochMillis = 1000L,
      updatedAtEpochMillis = 1000L
    )

    val info = item.expirationInfo(baseToday)
    assertEquals(ExpirationStatus.EXPIRING_SOON, info.status)
    assertEquals(3L, info.daysDelta)
    assertEquals("剩余 3 天", info.label)
  }

  // ==================== 2. 异常/过期状态路径测试 ====================

  @Test
  fun `expired status when expiration date is in the past`() {
    val item = PurchasedItem(
      id = 3L,
      name = "酸奶",
      quantity = 4,
      amount = null,
      expirationDate = baseToday.minusDays(5),
      createdAtEpochMillis = 1000L,
      updatedAtEpochMillis = 1000L
    )

    val info = item.expirationInfo(baseToday)
    assertEquals(ExpirationStatus.EXPIRED, info.status)
    assertEquals(-5L, info.daysDelta)
    assertEquals("已过期 5 天", info.label)
    assertNull(item.formattedAmount())
  }

  // ==================== 3. 边界条件测试 ====================

  @Test
  fun `boundary - expires today (0 days left) is EXPIRING_SOON with today label`() {
    val item = PurchasedItem(
      id = 4L,
      name = "鲜虾",
      quantity = 1,
      amount = 45.0,
      expirationDate = baseToday,
      createdAtEpochMillis = 1000L,
      updatedAtEpochMillis = 1000L
    )

    val info = item.expirationInfo(baseToday)
    assertEquals(ExpirationStatus.EXPIRING_SOON, info.status)
    assertEquals(0L, info.daysDelta)
    assertEquals("今天到期", info.label)
  }

  @Test
  fun `boundary - expires yesterday (-1 day) is EXPIRED`() {
    val item = PurchasedItem(
      id = 5L,
      name = "豆腐",
      quantity = 1,
      amount = 3.5,
      expirationDate = baseToday.minusDays(1),
      createdAtEpochMillis = 1000L,
      updatedAtEpochMillis = 1000L
    )

    val info = item.expirationInfo(baseToday)
    assertEquals(ExpirationStatus.EXPIRED, info.status)
    assertEquals(-1L, info.daysDelta)
    assertEquals("已过期 1 天", info.label)
  }

  @Test
  fun `boundary - expires in exactly 7 days is EXPIRING_SOON`() {
    val item = PurchasedItem(
      id = 6L,
      name = "果汁",
      quantity = 3,
      amount = 18.0,
      expirationDate = baseToday.plusDays(7),
      createdAtEpochMillis = 1000L,
      updatedAtEpochMillis = 1000L
    )

    val info = item.expirationInfo(baseToday)
    assertEquals(ExpirationStatus.EXPIRING_SOON, info.status)
    assertEquals(7L, info.daysDelta)
    assertEquals("剩余 7 天", info.label)
  }

  @Test
  fun `boundary - expires in exactly 8 days is NORMAL`() {
    val item = PurchasedItem(
      id = 7L,
      name = "燕麦片",
      quantity = 1,
      amount = 29.99,
      expirationDate = baseToday.plusDays(8),
      createdAtEpochMillis = 1000L,
      updatedAtEpochMillis = 1000L
    )

    val info = item.expirationInfo(baseToday)
    assertEquals(ExpirationStatus.NORMAL, info.status)
    assertEquals(8L, info.daysDelta)
    assertEquals("剩余 8 天", info.label)
  }

  @Test
  fun `boundary - amount is zero formats as 0_00`() {
    val freeItem = PurchasedItem(
      id = 8L,
      name = "赠品纸巾",
      quantity = 1,
      amount = 0.0,
      expirationDate = baseToday.plusDays(30),
      createdAtEpochMillis = 1000L,
      updatedAtEpochMillis = 1000L
    )

    assertEquals("¥0.00", freeItem.formattedAmount())
  }
}

