package com.example.mylist.data.local

import com.example.mylist.domain.model.PurchasedItem
import java.time.LocalDate

/**
 * 数据库表 `purchased_items` 持久化实体对象。
 *
 * @property id 主键 ID（新建时为 0L，插入数据库后自动递增）
 * @property name 商品名称（非空）
 * @property quantity 商品数量（正整数 >= 1）
 * @property amount 商品金额（可空，null 表示未填写）
 * @property expirationDateEpochDay 过期日期（使用 [LocalDate.toEpochDay] 存储）
 * @property createdAtEpochMillis 创建时间戳（毫秒）
 * @property updatedAtEpochMillis 更新时间戳（毫秒）
 */
data class PurchasedItemEntity(
  val id: Long = 0L,
  val name: String,
  val quantity: Int,
  val amount: Double? = null,
  val expirationDateEpochDay: Long,
  val createdAtEpochMillis: Long,
  val updatedAtEpochMillis: Long
)

/**
 * 将数据库实体转换为领域模型 [PurchasedItem]。
 */
fun PurchasedItemEntity.toDomainModel(): PurchasedItem {
  return PurchasedItem(
    id = id,
    name = name,
    quantity = quantity,
    amount = amount,
    expirationDate = LocalDate.ofEpochDay(expirationDateEpochDay),
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis
  )
}

/**
 * 将领域模型 [PurchasedItem] 转换为数据库实体 [PurchasedItemEntity]。
 */
fun PurchasedItem.toEntity(): PurchasedItemEntity {
  return PurchasedItemEntity(
    id = id,
    name = name,
    quantity = quantity,
    amount = amount,
    expirationDateEpochDay = expirationDate.toEpochDay(),
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis
  )
}
