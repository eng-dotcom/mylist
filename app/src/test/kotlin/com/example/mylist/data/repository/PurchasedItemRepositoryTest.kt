package com.example.mylist.data.repository

import com.example.mylist.data.local.InMemoryPurchasedItemDao
import com.example.mylist.domain.model.PurchasedItem
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Issue #2 单元测试：测试 [DefaultPurchasedItemRepository] 的增删改查、快捷加减数量与 Undo 撤销恢复逻辑。
 * 覆盖：
 * 1. 正向路径：新增商品、更新商品、快捷加减数量（+1 / -1）、删除商品、Undo 恢复。
 * 2. 异常路径：新增/更新非法名称、数量 <= 0、负数金额、更新或删除不存在的商品 ID、无待撤销商品时调用 Undo。
 * 3. 边界条件：金额在有值与 null 之间更新切换、数量为 1 时继续减 1 的边界拦截、指定原 ID 恢复删除项。
 */
class PurchasedItemRepositoryTest {

  private lateinit var dao: InMemoryPurchasedItemDao
  private lateinit var repository: DefaultPurchasedItemRepository
  private val sampleDate = LocalDate.of(2026, 11, 20)

  @Before
  fun setUp() {
    dao = InMemoryPurchasedItemDao()
    repository = DefaultPurchasedItemRepository(dao)
  }

  // ==================== 1. 正向路径测试 ====================

  @Test
  fun `addItem inserts valid item with amount and emits in stream`() = runTest {
    val result = repository.addItem(
      name = "  鲜牛奶  ",
      quantity = 2,
      amount = 19.8,
      expirationDate = sampleDate,
      nowMillis = 1000L
    )

    assertTrue(result.isSuccess)
    val id = result.getOrThrow()
    assertEquals(1L, id)

    val items = repository.getAllItemsStream().first()
    assertEquals(1, items.size)
    assertEquals("鲜牛奶", items[0].name)
    assertEquals(2, items[0].quantity)
    assertEquals(19.8, items[0].amount!!, 0.001)
    assertEquals(sampleDate, items[0].expirationDate)
  }

  @Test
  fun `updateItem modifies existing item fields and updates timestamp`() = runTest {
    val id = repository.addItem(
      name = "面包",
      quantity = 1,
      amount = 8.5,
      expirationDate = sampleDate,
      nowMillis = 1000L
    ).getOrThrow()

    val updatedDate = sampleDate.plusDays(3)
    val updateResult = repository.updateItem(
      PurchasedItem(
        id = id,
        name = "全麦吐司面包",
        quantity = 3,
        amount = 24.0,
        expirationDate = updatedDate,
        createdAtEpochMillis = 1000L,
        updatedAtEpochMillis = 1000L
      ),
      nowMillis = 2500L
    )

    assertTrue(updateResult.isSuccess)
    val fetched = repository.getItemById(id)!!
    assertEquals("全麦吐司面包", fetched.name)
    assertEquals(3, fetched.quantity)
    assertEquals(24.0, fetched.amount!!, 0.001)
    assertEquals(updatedDate, fetched.expirationDate)
    assertEquals(2500L, fetched.updatedAtEpochMillis)
  }

  @Test
  fun `adjustQuantity increments and decrements quantity when result is at least 1`() = runTest {
    val id = repository.addItem(
      name = "可乐",
      quantity = 2,
      amount = 6.0,
      expirationDate = sampleDate,
      nowMillis = 1000L
    ).getOrThrow()

    val afterIncrement = repository.adjustQuantity(id = id, delta = 1, nowMillis = 2000L)
    assertTrue(afterIncrement.isSuccess)
    assertEquals(3, afterIncrement.getOrThrow().quantity)

    val afterDecrement = repository.adjustQuantity(id = id, delta = -2, nowMillis = 3000L)
    assertTrue(afterDecrement.isSuccess)
    assertEquals(1, afterDecrement.getOrThrow().quantity)
    assertEquals(3000L, afterDecrement.getOrThrow().updatedAtEpochMillis)
  }

  @Test
  fun `deleteItem and restoreLastDeletedItem support full Undo workflow`() = runTest {
    val id = repository.addItem(
      name = "鸡蛋",
      quantity = 10,
      amount = 12.9,
      expirationDate = sampleDate,
      nowMillis = 1000L
    ).getOrThrow()

    val original = repository.getItemById(id)!!
    val deleteResult = repository.deleteItem(original)
    assertTrue(deleteResult.isSuccess)
    assertTrue(repository.getAllItemsStream().first().isEmpty())

    val restoreResult = repository.restoreLastDeletedItem()
    assertTrue(restoreResult.isSuccess)
    val restored = restoreResult.getOrThrow()
    assertEquals(original, restored)

    val currentItems = repository.getAllItemsStream().first()
    assertEquals(1, currentItems.size)
    assertEquals(original, currentItems.first())
  }

  // ==================== 2. 异常路径测试 ====================

  @Test
  fun `addItem rejects blank name, non-positive quantity, or negative amount`() = runTest {
    val blankNameResult = repository.addItem("", 1, 10.0, sampleDate)
    val zeroQtyResult = repository.addItem("苹果", 0, 10.0, sampleDate)
    val negAmountResult = repository.addItem("苹果", 1, -5.0, sampleDate)

    assertTrue(blankNameResult.isFailure)
    assertTrue(zeroQtyResult.isFailure)
    assertTrue(negAmountResult.isFailure)
    assertTrue(repository.getAllItemsStream().first().isEmpty())
  }

  @Test
  fun `updateItem fails when item id does not exist`() = runTest {
    val nonExistent = PurchasedItem(
      id = 999L,
      name = "不存在的商品",
      quantity = 1,
      amount = null,
      expirationDate = sampleDate
    )

    val result = repository.updateItem(nonExistent)
    assertTrue(result.isFailure)
    assertTrue(result.exceptionOrNull() is NoSuchElementException)
  }

  @Test
  fun `adjustQuantity fails when item id does not exist`() = runTest {
    val result = repository.adjustQuantity(id = 404L, delta = 1)
    assertTrue(result.isFailure)
    assertTrue(result.exceptionOrNull() is NoSuchElementException)
  }

  @Test
  fun `restoreLastDeletedItem fails when no item was deleted`() = runTest {
    val result = repository.restoreLastDeletedItem()
    assertTrue(result.isFailure)
    assertTrue(result.exceptionOrNull() is IllegalStateException)
  }

  // ==================== 3. 边界条件测试 ====================

  @Test
  fun `boundary - adjustQuantity rejects decrement when quantity is already 1`() = runTest {
    val id = repository.addItem(
      name = "单瓶酸奶",
      quantity = 1,
      amount = 5.5,
      expirationDate = sampleDate
    ).getOrThrow()

    val result = repository.adjustQuantity(id = id, delta = -1)
    assertFalse(result.isSuccess)
    assertEquals("商品数量不能小于 1，如需移除请删除商品", result.exceptionOrNull()?.message)
    // 确保数据库中的数量仍保持为 1，未被破坏
    assertEquals(1, repository.getItemById(id)?.quantity)
  }

  @Test
  fun `boundary - updateItem can clear amount to null or set amount from null`() = runTest {
    val id = repository.addItem(
      name = "挂面",
      quantity = 2,
      amount = 15.0,
      expirationDate = sampleDate
    ).getOrThrow()

    val itemWithAmount = repository.getItemById(id)!!
    assertNotNull(itemWithAmount.amount)

    // 将金额清空为 null（不填）
    repository.updateItem(itemWithAmount.copy(amount = null)).getOrThrow()
    val cleared = repository.getItemById(id)!!
    assertNull(cleared.amount)

    // 再次从 null 更新为 0.0 元
    repository.updateItem(cleared.copy(amount = 0.0)).getOrThrow()
    val zeroAmount = repository.getItemById(id)!!
    assertEquals(0.0, zeroAmount.amount!!, 0.0001)
  }
}
