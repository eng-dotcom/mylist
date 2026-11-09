package com.example.mylist.data.repository

import com.example.mylist.data.local.PurchasedItemDao
import com.example.mylist.data.local.toDomainModel
import com.example.mylist.data.local.toEntity
import com.example.mylist.domain.model.PurchasedItem
import com.example.mylist.domain.validation.PurchasedItemValidator
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 已购商品仓库层接口，提供增删改查、分类持久化、快捷数量加减与误删恢复（Undo）能力。
 */
interface PurchasedItemRepository {
  /**
   * 响应式获取全部已购商品领域模型列表。
   */
  fun getAllItemsStream(): Flow<List<PurchasedItem>>

  /**
   * 根据 ID 查询单个商品。
   */
  suspend fun getItemById(id: Long): PurchasedItem?

  /**
   * 新增已购商品。
   */
  suspend fun addItem(
    name: String,
    quantity: Int,
    amount: Double?,
    expirationDate: LocalDate,
    nowMillis: Long = System.currentTimeMillis(),
    category: String = PurchasedItem.DEFAULT_CATEGORY
  ): Result<Long>

  /**
   * 更新已有商品信息。
   */
  suspend fun updateItem(
    item: PurchasedItem,
    nowMillis: Long = System.currentTimeMillis()
  ): Result<Unit>

  /**
   * 列表项快捷加减商品数量（如 +1 / -1）。
   */
  suspend fun adjustQuantity(
    id: Long,
    delta: Int,
    nowMillis: Long = System.currentTimeMillis()
  ): Result<PurchasedItem>

  /**
   * 删除指定商品，并暂存以支持撤销（Undo）。
   */
  suspend fun deleteItem(item: PurchasedItem): Result<Unit>

  /**
   * 撤销恢复最近一次删除的商品。
   */
  suspend fun restoreLastDeletedItem(): Result<PurchasedItem>

  /**
   * 将指定商品重新恢复写入数据库。
   */
  suspend fun restoreItem(item: PurchasedItem): Result<Unit>
}

/**
 * [PurchasedItemRepository] 的默认实现，封装字段合法性校验、DAO 持久化与 Undo 缓存。
 */
class DefaultPurchasedItemRepository(
  private val dao: PurchasedItemDao
) : PurchasedItemRepository {

  private data class ValidatedFields(
    val name: String,
    val category: String,
    val quantity: Int,
    val amount: Double?
  )

  @Volatile
  private var lastDeletedItem: PurchasedItem? = null

  override fun getAllItemsStream(): Flow<List<PurchasedItem>> {
    return dao.observeAllItems().map { entities ->
      entities.map { it.toDomainModel() }
    }
  }

  override suspend fun getItemById(id: Long): PurchasedItem? {
    return dao.getItemById(id)?.toDomainModel()
  }

  override suspend fun addItem(
    name: String,
    quantity: Int,
    amount: Double?,
    expirationDate: LocalDate,
    nowMillis: Long,
    category: String
  ): Result<Long> {
    val validated = validateDomainFields(name, category, quantity, amount)
      .getOrElse { return Result.failure(it) }

    val item = PurchasedItem(
      id = 0L,
      name = validated.name,
      category = validated.category,
      quantity = validated.quantity,
      amount = validated.amount,
      expirationDate = expirationDate,
      createdAtEpochMillis = nowMillis,
      updatedAtEpochMillis = nowMillis
    )
    val id = dao.insertItem(item.toEntity())
    return Result.success(id)
  }

  override suspend fun updateItem(
    item: PurchasedItem,
    nowMillis: Long
  ): Result<Unit> {
    if (item.id <= 0L) {
      return Result.failure(IllegalArgumentException("商品 ID 无效"))
    }
    val validated = validateDomainFields(item.name, item.category, item.quantity, item.amount)
      .getOrElse { return Result.failure(it) }

    val updatedItem = item.copy(
      name = validated.name,
      category = validated.category,
      quantity = validated.quantity,
      amount = validated.amount,
      updatedAtEpochMillis = nowMillis
    )
    val affectedRows = dao.updateItem(updatedItem.toEntity())
    if (affectedRows == 0) {
      return Result.failure(NoSuchElementException("未找到 ID 为 ${item.id} 的商品"))
    }
    return Result.success(Unit)
  }

  override suspend fun adjustQuantity(
    id: Long,
    delta: Int,
    nowMillis: Long
  ): Result<PurchasedItem> {
    val existingEntity = dao.getItemById(id)
      ?: return Result.failure(NoSuchElementException("未找到 ID 为 $id 的商品"))

    val newQuantity = existingEntity.quantity + delta
    if (newQuantity < 1) {
      return Result.failure(IllegalStateException("商品数量不能小于 1，如需移除请删除商品"))
    }

    val updatedEntity = existingEntity.copy(
      quantity = newQuantity,
      updatedAtEpochMillis = nowMillis
    )
    val rows = dao.updateItem(updatedEntity)
    if (rows == 0) {
      return Result.failure(NoSuchElementException("未找到 ID 为 $id 的商品"))
    }
    return Result.success(updatedEntity.toDomainModel())
  }

  override suspend fun deleteItem(item: PurchasedItem): Result<Unit> {
    val rows = dao.deleteItemById(item.id)
    if (rows == 0) {
      return Result.failure(NoSuchElementException("未找到 ID 为 ${item.id} 的商品"))
    }
    lastDeletedItem = item
    return Result.success(Unit)
  }

  override suspend fun restoreLastDeletedItem(): Result<PurchasedItem> {
    val itemToRestore = lastDeletedItem
      ?: return Result.failure(IllegalStateException("没有可撤销恢复的已删除商品"))

    dao.insertItem(itemToRestore.toEntity())
    lastDeletedItem = null
    return Result.success(itemToRestore)
  }

  override suspend fun restoreItem(item: PurchasedItem): Result<Unit> {
    dao.insertItem(item.toEntity())
    if (lastDeletedItem?.id == item.id) {
      lastDeletedItem = null
    }
    return Result.success(Unit)
  }

  private fun validateDomainFields(
    rawName: String,
    rawCategory: String?,
    quantity: Int,
    amount: Double?
  ): Result<ValidatedFields> {
    val nameResult = PurchasedItemValidator.validateName(rawName)
    if (!nameResult.isSuccess) {
      return Result.failure(IllegalArgumentException(nameResult.errorMessage))
    }

    val categoryResult = PurchasedItemValidator.validateCategory(rawCategory)
    if (!categoryResult.isSuccess) {
      return Result.failure(IllegalArgumentException(categoryResult.errorMessage))
    }

    val quantityResult = PurchasedItemValidator.validateQuantityValue(quantity)
    if (!quantityResult.isSuccess) {
      return Result.failure(IllegalArgumentException(quantityResult.errorMessage))
    }

    val amountResult = PurchasedItemValidator.validateAmountValue(amount)
    if (!amountResult.isSuccess) {
      return Result.failure(IllegalArgumentException(amountResult.errorMessage))
    }

    return Result.success(
      ValidatedFields(
        name = nameResult.getOrNull()!!,
        category = categoryResult.getOrNull()!!,
        quantity = quantityResult.getOrNull()!!,
        amount = amountResult.getOrNull()
      )
    )
  }
}
