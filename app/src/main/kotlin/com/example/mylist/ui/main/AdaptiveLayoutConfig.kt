package com.example.mylist.ui.main

import com.example.mylist.domain.model.PurchasedItem
import com.example.mylist.domain.validation.PurchasedItemValidator
import java.time.LocalDate
import java.util.Locale

/**
 * 屏幕宽度尺寸分类：
 * - [COMPACT]：手机竖屏（< 600dp）
 * - [MEDIUM]：折叠屏展开 / 小平板（600dp .. 839dp）
 * - [EXPANDED]：大尺寸平板 / 桌面窗口（>= 840dp）
 */
enum class ScreenSizeCategory {
  COMPACT,
  MEDIUM,
  EXPANDED
}

/**
 * 自适应屏幕大小布局参数配置。
 */
data class AdaptiveLayoutConfig(
  val category: ScreenSizeCategory,
  val gridColumns: Int,
  val horizontalPaddingDp: Int,
  val cardSpacingDp: Int,
  val summaryColumns: Int
) {
  companion object {
    const val MEDIUM_BREAKPOINT_DP = 600
    const val EXPANDED_BREAKPOINT_DP = 840

    fun fromScreenWidthDp(widthDp: Int): AdaptiveLayoutConfig {
      val safeWidth = widthDp.coerceAtLeast(0)
      return when {
        safeWidth < MEDIUM_BREAKPOINT_DP -> AdaptiveLayoutConfig(
          category = ScreenSizeCategory.COMPACT,
          gridColumns = 1,
          horizontalPaddingDp = 16,
          cardSpacingDp = 12,
          summaryColumns = 2
        )
        safeWidth < EXPANDED_BREAKPOINT_DP -> AdaptiveLayoutConfig(
          category = ScreenSizeCategory.MEDIUM,
          gridColumns = 2,
          horizontalPaddingDp = 24,
          cardSpacingDp = 16,
          summaryColumns = 4
        )
        else -> AdaptiveLayoutConfig(
          category = ScreenSizeCategory.EXPANDED,
          gridColumns = 3,
          horizontalPaddingDp = 32,
          cardSpacingDp = 16,
          summaryColumns = 4
        )
      }
    }
  }
}

/**
 * 新增 / 编辑商品弹窗表单状态模型。
 */
data class ItemFormState(
  val editingItemId: Long? = null,
  val name: String = "",
  val quantity: String = "1",
  val amount: String = "",
  val expirationDate: LocalDate = LocalDate.now().plusDays(7),
  val createdAtEpochMillis: Long? = null,
  val errorMessage: String? = null
) {
  val isEditMode: Boolean
    get() = editingItemId != null && editingItemId > 0L

  /**
   * 校验当前表单字段，若不合法则在返回的 [ItemFormState.errorMessage] 中写入错误原因。
   */
  fun validate(): ItemFormState {
    val result = PurchasedItemValidator.validateAndCreate(
      id = editingItemId ?: 0L,
      rawName = name,
      rawQuantity = quantity,
      rawAmount = amount,
      expirationDate = expirationDate,
      createdAtEpochMillis = createdAtEpochMillis
    )
    return if (result.isSuccess) {
      copy(errorMessage = null)
    } else {
      copy(errorMessage = result.errorMessage)
    }
  }

  companion object {
    fun forNewItem(defaultDate: LocalDate = LocalDate.now().plusDays(7)): ItemFormState {
      return ItemFormState(
        editingItemId = null,
        name = "",
        quantity = "1",
        amount = "",
        expirationDate = defaultDate,
        createdAtEpochMillis = null,
        errorMessage = null
      )
    }

    fun fromExistingItem(item: PurchasedItem): ItemFormState {
      val formattedAmount = item.amount?.let { String.format(Locale.US, "%.2f", it) }.orEmpty()
      return ItemFormState(
        editingItemId = item.id,
        name = item.name,
        quantity = item.quantity.toString(),
        amount = formattedAmount,
        expirationDate = item.expirationDate,
        createdAtEpochMillis = item.createdAtEpochMillis,
        errorMessage = null
      )
    }
  }
}

