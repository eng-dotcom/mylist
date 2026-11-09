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
  CATEGORY_ASC("商品分类：同类聚合"),
  CREATED_DESC("添加时间：最新优先"),
  CREATED_ASC("添加时间：最早优先"),
  AMOUNT_DESC("金额：从高到低"),
  AMOUNT_ASC("金额：从低到高"),
  NAME_ASC("商品名称：A-Z")
}

/**
 * 按商品类别分组展示的数据模型。
 */
data class CategoryGroup(
  val category: String,
  val items: List<PurchasedItem>,
  val itemCount: Int = items.size,
  val totalQuantity: Int = items.sumOf { it.quantity },
  val expiringSoonCount: Int = 0,
  val expiredCount: Int = 0
)

/**
 * 顶部统计概览卡片数据模型。
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
 * 商品查询、状态与分类筛选、多维排序与统计汇总纯函数引擎。
 */
object ItemQueryEngine {

  /**
   * 提取当前所有可选分类列表（合并用户已录入的自定义分类与预设分类，去重并将“未分类”置于末尾）。
   */
  fun extractAvailableCategories(
    items: List<PurchasedItem>,
    includePresets: Boolean = true
  ): List<String> {
    val result = linkedSetOf<String>()
    // 优先展示用户已经在商品中使用过的自定义/已有分类
    items.map { it.category.trim() }
      .filter { it.isNotEmpty() && it != PurchasedItem.DEFAULT_CATEGORY }
      .forEach { result.add(it) }

    if (includePresets) {
      PurchasedItem.PRESET_CATEGORIES
        .filter { it != PurchasedItem.DEFAULT_CATEGORY }
        .forEach { result.add(it) }
    }

    result.add(PurchasedItem.DEFAULT_CATEGORY)
    return result.toList()
  }

  /**
   * 对商品列表执行关键字搜索（匹配名称或分类）、分类过滤、状态筛选与多维排序。
   */
  fun filterAndSort(
    items: List<PurchasedItem>,
    searchQuery: String,
    statusFilter: StatusFilter,
    sortOption: SortOption,
    today: LocalDate = LocalDate.now(),
    categoryFilter: String? = null
  ): List<PurchasedItem> {
    val trimmedQuery = searchQuery.trim()
    val trimmedCategory = categoryFilter?.trim().orEmpty()

    val filtered = items.asSequence()
      .filter { item ->
        if (trimmedQuery.isEmpty()) {
          true
        } else {
          item.name.contains(trimmedQuery, ignoreCase = true) ||
            item.category.contains(trimmedQuery, ignoreCase = true)
        }
      }
      .filter { item ->
        if (trimmedCategory.isEmpty()) {
          true
        } else {
          item.category.equals(trimmedCategory, ignoreCase = true)
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

      SortOption.CATEGORY_ASC ->
        compareBy<PurchasedItem> {
          val cat = it.category.trim().ifEmpty { PurchasedItem.DEFAULT_CATEGORY }
          if (cat == PurchasedItem.DEFAULT_CATEGORY) 1 else 0
        }
          .thenBy(String.CASE_INSENSITIVE_ORDER) {
            it.category.trim().ifEmpty { PurchasedItem.DEFAULT_CATEGORY }
          }
          .thenBy { it.expirationDate }
          .thenBy { it.id }

      SortOption.CREATED_DESC ->
        compareByDescending<PurchasedItem> { it.createdAtEpochMillis }.thenBy { it.id }

      SortOption.CREATED_ASC ->
        compareBy<PurchasedItem> { it.createdAtEpochMillis }.thenBy { it.id }

      SortOption.AMOUNT_ASC ->
        Comparator { a, b ->
          when {
            a.amount == null && b.amount == null -> a.id.compareTo(b.id)
            a.amount == null -> 1
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
            a.amount == null -> 1
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
   * 将商品列表按照类别（Category）进行分组聚合，计算每个类别下的商品数、总件数、临期与过期统计，
   * 并将默认分类“未分类”固定排在末尾。
   */
  fun groupByCategory(
    items: List<PurchasedItem>,
    today: LocalDate = LocalDate.now()
  ): List<CategoryGroup> {
    if (items.isEmpty()) {
      return emptyList()
    }

    val groupedMap = linkedMapOf<String, MutableList<PurchasedItem>>()
    for (item in items) {
      val normalizedCategory = item.category.trim().ifEmpty { PurchasedItem.DEFAULT_CATEGORY }
      groupedMap.getOrPut(normalizedCategory) { mutableListOf() }.add(item)
    }

    val orderedKeys = groupedMap.keys.sortedWith(
      Comparator { a, b ->
        when {
          a == b -> 0
          a == PurchasedItem.DEFAULT_CATEGORY -> 1
          b == PurchasedItem.DEFAULT_CATEGORY -> -1
          else -> 0 // 保持首次出现的顺序
        }
      }
    )

    return orderedKeys.map { category ->
      val groupItems = groupedMap[category].orEmpty()
      var expiringSoon = 0
      var expired = 0
      var totalQty = 0
      for (item in groupItems) {
        totalQty += item.quantity
        when (item.expirationStatus(today)) {
          ExpirationStatus.EXPIRING_SOON -> expiringSoon++
          ExpirationStatus.EXPIRED -> expired++
          ExpirationStatus.NORMAL -> Unit
        }
      }
      CategoryGroup(
        category = category,
        items = groupItems,
        itemCount = groupItems.size,
        totalQuantity = totalQty,
        expiringSoonCount = expiringSoon,
        expiredCount = expired
      )
    }
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
