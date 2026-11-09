package com.example.mylist.data.local

import com.example.mylist.domain.model.PurchasedItem
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Issue #2 单元测试：测试数据库实体 [PurchasedItemEntity] 与领域模型 [PurchasedItem] 双向映射及建表 SQL 规范。
 */
class PurchasedItemEntityMapperTest {

  @Test
  fun `domain to entity and back preserves all fields when amount is present`() {
    val expiration = LocalDate.of(2026, 12, 25)
    val domain = PurchasedItem(
      id = 42L,
      name = "进口车厘子",
      quantity = 2,
      amount = 88.50,
      expirationDate = expiration,
      createdAtEpochMillis = 1000L,
      updatedAtEpochMillis = 2000L
    )

    val entity = domain.toEntity()
    assertEquals(42L, entity.id)
    assertEquals("进口车厘子", entity.name)
    assertEquals(2, entity.quantity)
    assertEquals(88.50, entity.amount!!, 0.001)
    assertEquals(expiration.toEpochDay(), entity.expirationDateEpochDay)
    assertEquals(1000L, entity.createdAtEpochMillis)
    assertEquals(2000L, entity.updatedAtEpochMillis)

    val mappedBack = entity.toDomainModel()
    assertEquals(domain, mappedBack)
  }

  @Test
  fun `boundary - domain to entity and back preserves null optional amount`() {
    val expiration = LocalDate.of(2026, 11, 9)
    val domain = PurchasedItem(
      id = 7L,
      name = "苏打饼干",
      quantity = 1,
      amount = null,
      expirationDate = expiration,
      createdAtEpochMillis = 5000L,
      updatedAtEpochMillis = 5000L
    )

    val entity = domain.toEntity()
    assertNull(entity.amount)

    val mappedBack = entity.toDomainModel()
    assertNull(mappedBack.amount)
    assertEquals(domain, mappedBack)
  }

  @Test
  fun `schema - CREATE_TABLE_SQL defines purchased_items table and all required columns`() {
    val sql = AppDatabase.CREATE_TABLE_SQL
    assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS purchased_items"))
    assertTrue(sql.contains("id INTEGER PRIMARY KEY AUTOINCREMENT"))
    assertTrue(sql.contains("name TEXT NOT NULL"))
    assertTrue(sql.contains("quantity INTEGER NOT NULL"))
    assertTrue(sql.contains("amount REAL"))
    assertTrue(sql.contains("expirationDateEpochDay INTEGER NOT NULL"))
    assertTrue(sql.contains("createdAtEpochMillis INTEGER NOT NULL"))
    assertTrue(sql.contains("updatedAtEpochMillis INTEGER NOT NULL"))
  }
}
