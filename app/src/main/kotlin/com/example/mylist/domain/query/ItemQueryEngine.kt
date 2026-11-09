package com.example.mylist.domain.query

import com.example.mylist.domain.model.ExpirationStatus
import com.example.mylist.domain.model.PurchasedItem
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Locale

/**
 * 商品列表状态过滤枚举。
 */
enum class StatusFilter(val displayName: String) {
  ALL("全部"),
  EXPIRING_SOON("临期"),
  EXPIRED("已过期"),
  NORMAL("正常")
}

/**
 * 商品列表排序方式枚举。
 */
enum class SortOption(val displayName: String) {
  EXPIRATION_ASC("过期日期：由近到远"),
  EXPIRATION_DESC("过期日期：由远到近"),
  CREATED_DESC("添加时间：最新优先"),
  CREATED_ASC("添加时间：最早优先"),
  AMOUNT_DESC("金额：从高到低"),
  AMOUNT_ASC("金额：从低到高"),
  NAME_ASC("商品名称：A-Z")
}

/**
 * 顶部统计概览卡片数据模型。
 *
 * @property totalItemCount 商品总项数（条目数）
 * @property totalQuantityCount 商品总件数（所有商品数量之和）
 * @property expiringSoonCount 临期商品项数（0..7天）
 * @property expiredCount 已过期商品项数（<0天）
 * @property normalCount 正常商品项数（>7天）
 * @property totalSpentAmount 已填写金额的商品总花费
 */
data class SummaryStats(
  val totalItemCount: Int = 0,
  val totalQuantityCount: Int = 0,
  val expiringSoonCount: Int = 0,
  val expiredCount: Int = 0,
  val normalCount: Int = 0,
  val totalSpentAmount: Double = 0.0
) {
  fun formattedTotalSpent(): String {
    return String.format(Locale.US, "¥%.2f", totalSpentAmount)
  }
}

/**
 * 商品查询、状态筛选、多维排序与统计汇总纯函数引擎。
 */
object ItemQueryEngine {

  /**
   * 对商品列表执行关键字搜索、状态筛选与多维排序。
   */
  fun filterAndSort(
    items: List<PurchasedItem>,
    searchQuery: String,
    statusFilter: StatusFilter,
    sortOption: SortOption,
    today: LocalDate = LocalDate.now()
  ): List<PurchasedItem> {
    val trimmedQuery = searchQuery.trim()

    val filtered = items.asSequence()
      .filter { item ->
        if (trimmedQuery.isEmpty()) {
          true
        } else {
          item.name.contains(trimmedQuery, ignoreCase = true)
        }
      }
      .filter { item ->
        when (statusFilter) {
          StatusFilter.ALL -> true
          StatusFilter.EXPIRING_SOON -> item.expirationStatus(today) == ExpirationStatus.EXPIRING_SOON
          StatusFilter.EXPIRED -> item.expirationStatus(today) == ExpirationStatus.EXPIRED
          StatusFilter.NORMAL -> item.expirationStatus(today) == ExpirationStatus.NORMAL
        }
      }
      .toList()

    val comparator: Comparator<PurchasedItem> = when (sortOption) {
      SortOption.EXPIRATION_ASC ->
        compareBy<PurchasedItem> { it.expirationDate }.thenBy { it.id }

      SortOption.EXPIRATION_DESC ->
        compareByDescending<PurchasedItem> { it.expirationDate }.thenBy { it.id }

      SortOption.CREATED_DESC ->
        compareByDescending<PurchasedItem> { it.createdAtEpochMillis }.thenBy { it.id }

      SortOption.CREATED_ASC ->
        compareBy<PurchasedItem> { it.createdAtEpochMillis }.thenBy { it.id }

      SortOption.AMOUNT_ASC ->
        Comparator { a, b ->
          when {
            a.amount == null && b.amount == null -> a.id.compareTo(b.id)
            a.amount == null -> 1 // 未填金额排在最后
            b.amount == null -> -1
            else -> {
              val cmp = a.amount.compareTo(b.amount)
              if (cmp != 0) cmp else a.id.compareTo(b.id)
            }
          }
        }

      SortOption.AMOUNT_DESC ->
        Comparator { a, b ->
          when {
            a.amount == null && b.amount == null -> a.id.compareTo(b.id)
            a.amount == null -> 1 // 未填金额排在最后
            b.amount == null -> -1
            else -> {
              val cmp = b.amount.compareTo(a.amount)
              if (cmp != 0) cmp else a.id.compareTo(b.id)
            }
          }
        }

      SortOption.NAME_ASC ->
        compareBy<PurchasedItem, String>(String.CASE_INSENSITIVE_ORDER) { it.name }.thenBy { it.id }
    }

    return filtered.sortedWith(comparator)
  }

  /**
   * 计算顶部统计概览卡片数据。
   */
  fun computeSummaryStats(
    items: List<PurchasedItem>,
    today: LocalDate = LocalDate.now()
  ): SummaryStats {
    if (items.isEmpty()) {
      return SummaryStats()
    }

    var totalQuantity = 0
    var expiringSoon = 0
    var expired = 0
    var normal = 0
    var totalSpent = BigDecimal.ZERO

    for (item in items) {
      totalQuantity += item.quantity
      when (item.expirationStatus(today)) {
        ExpirationStatus.EXPIRING_SOON -> expiringSoon++
        ExpirationStatus.EXPIRED -> expired++
        ExpirationStatus.NORMAL -> normal++
      }
      if (item.amount != null) {
        totalSpent = totalSpent.add(BigDecimal.valueOf(item.amount))
      }
    }

    return SummaryStats(
      totalItemCount = items.size,
      totalQuantityCount = totalQuantity,
      expiringSoonCount = expiringSoon,
      expiredCount = expired,
      normalCount = normal,
      totalSpentAmount = totalSpent.toDouble()
    )
  }
}
