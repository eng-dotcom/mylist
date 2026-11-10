package com.example.mylist.ui.main

import com.example.mylist.domain.model.ExpirationStatus
import com.example.mylist.domain.model.PurchasedItem
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 已购商品列表的二级折叠/展开状态模型：
 * 1. 管理每一大类（Category）的折叠/展开状态（[collapsedCategories]）；
 * 2. 管理每个商品栏（PurchasedItem）的详情展开/收起状态（[expandedItemIds]），默认全部收起。
 */
data class ItemExpansionState(
  val expandedItemIds: Set<Long> = emptySet(),
  val collapsedCategories: Set<String> = emptySet()
) {
  /** 判断指定商品栏是否处于详情展开态（默认 false，仅展示名称和剩余/已过期天数） */
  fun isItemExpanded(itemId: Long): Boolean = expandedItemIds.contains(itemId)

  /** 判断指定大类是否处于折叠收起态（默认 false，即大类默认展开） */
  fun isCategoryCollapsed(category: String): Boolean = collapsedCategories.contains(category.trim())

  /**
   * 点击商品栏切换该商品详情的展开/收起状态。
   * 对非法 ID（<= 0）安全忽略。
   */
  fun toggleItem(itemId: Long): ItemExpansionState {
    if (itemId <= 0L) return this
    val updated = if (expandedItemIds.contains(itemId)) {
      expandedItemIds - itemId
    } else {
      expandedItemIds + itemId
    }
    return copy(expandedItemIds = updated)
  }

  /**
   * 移除指定商品的展开状态（例如商品被删除时清理）。
   */
  fun removeItem(itemId: Long): ItemExpansionState {
    if (!expandedItemIds.contains(itemId)) return this
    return copy(expandedItemIds = expandedItemIds - itemId)
  }

  /**
   * 点击大类标题切换该分类的折叠/展开状态。
   * 当收起某个大类时，自动将该大类下已展开的商品收起，保证再次展开大类时商品恢复紧凑展示。
   */
  fun toggleCategory(
    category: String,
    itemsInCategory: Collection<Long> = emptyList()
  ): ItemExpansionState {
    val trimmed = category.trim()
    if (trimmed.isEmpty()) return this
    return if (collapsedCategories.contains(trimmed)) {
      copy(collapsedCategories = collapsedCategories - trimmed)
    } else {
      copy(
        collapsedCategories = collapsedCategories + trimmed,
        expandedItemIds = expandedItemIds - itemsInCategory.toSet()
      )
    }
  }
}

/**
 * 商品卡片视图展示模型（纯数据映射，用于严格约束收起态与展开态展示的字段）。
 *
 * - 当 [isExpanded] 为 `false`（默认收起态）时：仅暴露 [name] 与 [expirationBadgeText]（剩余过期天数或已过期天数），
 *   [expirationDateText]、[amountText]、[quantityText] 均为 `null`，[showActions] 为 `false`。
 * - 当 [isExpanded] 为 `true`（点击商品栏展开后）时：完整暴露过期日期、金额、数量与操作按钮。
 */
data class ItemCardDisplayModel(
  val id: Long,
  val name: String,
  val category: String,
  val expirationBadgeText: String,
  val expirationStatus: ExpirationStatus,
  val isExpanded: Boolean,
  val expirationDateText: String?,
  val amountText: String?,
  val quantityText: String?,
  val showActions: Boolean
) {
  companion object {
    fun fromItem(
      item: PurchasedItem,
      isExpanded: Boolean,
      today: LocalDate = LocalDate.now()
    ): ItemCardDisplayModel {
      val expInfo = item.expirationInfo(today)
      return if (!isExpanded) {
        ItemCardDisplayModel(
          id = item.id,
          name = item.name,
          category = item.category,
          expirationBadgeText = expInfo.label,
          expirationStatus = expInfo.status,
          isExpanded = false,
          expirationDateText = null,
          amountText = null,
          quantityText = null,
          showActions = false
        )
      } else {
        ItemCardDisplayModel(
          id = item.id,
          name = item.name,
          category = item.category,
          expirationBadgeText = expInfo.label,
          expirationStatus = expInfo.status,
          isExpanded = true,
          expirationDateText = "过期日期：${item.expirationDate.format(DateTimeFormatter.ISO_LOCAL_DATE)}",
          amountText = item.formattedAmount()?.let { "金额：$it" } ?: "未填金额",
          quantityText = "${item.quantity}",
          showActions = true
        )
      }
    }
  }
}

