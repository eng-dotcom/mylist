package com.example.mylist.data.local

import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 内存版 [PurchasedItemDao] 实现，用于 JVM 单元测试与 Compose Preview 预览。
 */
class InMemoryPurchasedItemDao(
  initialItems: List<PurchasedItemEntity> = emptyList()
) : PurchasedItemDao {

  private val mutex = Mutex()
  private val idGenerator = AtomicLong(
    (initialItems.maxOfOrNull { it.id } ?: 0L)
  )
  private val itemsState = MutableStateFlow(initialItems)

  override fun observeAllItems(): Flow<List<PurchasedItemEntity>> = itemsState.asStateFlow()

  override suspend fun getItemById(id: Long): PurchasedItemEntity? = mutex.withLock {
    itemsState.value.firstOrNull { it.id == id }
  }

  override suspend fun insertItem(item: PurchasedItemEntity): Long = mutex.withLock {
    val assignedId = if (item.id > 0L) {
      if (item.id > idGenerator.get()) {
        idGenerator.set(item.id)
      }
      item.id
    } else {
      idGenerator.incrementAndGet()
    }
    val entityToSave = item.copy(id = assignedId)
    val current = itemsState.value.toMutableList()
    val existingIndex = current.indexOfFirst { it.id == assignedId }
    if (existingIndex >= 0) {
      current[existingIndex] = entityToSave
    } else {
      current.add(entityToSave)
    }
    itemsState.value = current.toList()
    assignedId
  }

  override suspend fun updateItem(item: PurchasedItemEntity): Int = mutex.withLock {
    val current = itemsState.value.toMutableList()
    val index = current.indexOfFirst { it.id == item.id }
    if (index < 0) {
      return@withLock 0
    }
    current[index] = item
    itemsState.value = current.toList()
    1
  }

  override suspend fun deleteItem(item: PurchasedItemEntity): Int {
    return deleteItemById(item.id)
  }

  override suspend fun deleteItemById(id: Long): Int = mutex.withLock {
    val current = itemsState.value.toMutableList()
    val removed = current.removeAll { it.id == id }
    if (removed) {
      itemsState.value = current.toList()
      1
    } else {
      0
    }
  }
}
