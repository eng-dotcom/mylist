package com.example.mylist.data.local

import kotlinx.coroutines.flow.Flow

/**
 * 已购商品数据访问对象 (DAO) 接口。
 */
interface PurchasedItemDao {
  /**
   * 响应式监听所有已购商品列表。
   */
  fun observeAllItems(): Flow<List<PurchasedItemEntity>>

  /**
   * 根据主键 ID 查询单个商品记录。
   */
  suspend fun getItemById(id: Long): PurchasedItemEntity?

  /**
   * 插入新增商品或恢复（Undo）已删除商品，返回记录主键 ID。
   */
  suspend fun insertItem(item: PurchasedItemEntity): Long

  /**
   * 更新已有商品记录，返回受影响的行数。
   */
  suspend fun updateItem(item: PurchasedItemEntity): Int

  /**
   * 删除指定商品实体，返回受影响的行数。
   */
  suspend fun deleteItem(item: PurchasedItemEntity): Int

  /**
   * 根据主键 ID 删除指定商品记录，返回受影响的行数。
   */
  suspend fun deleteItemById(id: Long): Int
}
