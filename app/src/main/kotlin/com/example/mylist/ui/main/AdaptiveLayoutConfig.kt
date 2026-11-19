package com.example.mylist.ui.main

import com.example.mylist.domain.model.ExpirationCalculator
import com.example.mylist.domain.model.ExpirationInputMode
import com.example.mylist.domain.model.PurchasedItem
import com.example.mylist.domain.model.ShelfLifeUnit
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
 * 支持两种过期日期录入方式：
 * 1. [ExpirationInputMode.DIRECT_EXPIRATION]：直接选择过期日期（保留原有方式）；
 * 2. [ExpirationInputMode.PRODUCTION_AND_SHELF_LIFE]：输入生产日期 + 保质期（天/月/年）自动计算过期日期。
 */
data class ItemFormState(
  val editingItemId: Long? = null,
  val name: String = "",
  val category: String = PurchasedItem.DEFAULT_CATEGORY,
  val quantity: String = "1",
  val amount: String = "",
  val expirationDate: LocalDate = LocalDate.now().plusDays(7),
  val expirationInputMode: ExpirationInputMode = ExpirationInputMode.DIRECT_EXPIRATION,
  val productionDate: LocalDate = LocalDate.now(),
  val shelfLifeValue: String = "7",
  val shelfLifeUnit: ShelfLifeUnit = ShelfLifeUnit.DAYS,
  val createdAtEpochMillis: Long? = null,
  val errorMessage: String? = null,
  val isDefaultCategory: Boolean = true,
  val isDefaultQuantity: Boolean = true,
  val isDefaultShelfLife: Boolean = true
) {
  val isEditMode: Boolean
    get() = editingItemId != null && editingItemId > 0L

  /**
   * 当用户点击（聚焦）“商品分类”输入框时：
   * 若当前值为缺省值“未分类”，自动清空为空白字符串 `""`，方便直接输入新分类（如同商品名称输入框）。
   */
  fun onCategoryFocus(): ItemFormState {
    return if (isDefaultCategory && category.trim() == PurchasedItem.DEFAULT_CATEGORY) {
      copy(category = "", isDefaultCategory = false, errorMessage = null)
    } else {
      this
    }
  }

  /**
   * 当用户点击（聚焦）“商品数量”输入框时：
   * 在新增商品模式下，若当前值为初始缺省值 `"1"`，自动清空为空白字符串 `""`，方便直接输入目标数量。
   */
  fun onQuantityFocus(): ItemFormState {
    return if (!isEditMode && isDefaultQuantity && quantity.trim() == "1") {
      copy(quantity = "", isDefaultQuantity = false, errorMessage = null)
    } else {
      this
    }
  }

  /**
   * 当用户点击（聚焦）“保质期”输入框时：
   * 若当前值为初始缺省值 `"7"`，自动清空为空白字符串 `""`，方便直接输入保质期数值。
   */
  fun onShelfLifeFocus(): ItemFormState {
    return if (isDefaultShelfLife && shelfLifeValue.trim() == "7") {
      copy(shelfLifeValue = "", isDefaultShelfLife = false, errorMessage = null)
    } else {
      this
    }
  }

  /**
   * 用户手动输入或选择分类时更新状态。
   */
  fun updateCategory(newCategory: String): ItemFormState {
    return copy(
      category = newCategory,
      isDefaultCategory = newCategory.trim() == PurchasedItem.DEFAULT_CATEGORY,
      errorMessage = null
    )
  }

  /**
   * 用户手动输入数量时更新状态，并标记为非缺省值（避免再次点击时被清空）。
   */
  fun updateQuantity(newQuantity: String): ItemFormState {
    return copy(
      quantity = newQuantity,
      isDefaultQuantity = false,
      errorMessage = null
    )
  }

  /**
   * 切换过期日期录入方式（直接输入过期日 / 生产日期 + 保质期自动计算）。
   */
  fun switchExpirationInputMode(mode: ExpirationInputMode): ItemFormState {
    if (mode == expirationInputMode) return this
    return if (mode == ExpirationInputMode.PRODUCTION_AND_SHELF_LIFE) {
      val calc = ExpirationCalculator.validateAndCalculate(
        productionDate = productionDate,
        rawShelfLife = shelfLifeValue,
        unit = shelfLifeUnit
      )
      copy(
        expirationInputMode = mode,
        expirationDate = calc.getOrNull() ?: expirationDate,
        errorMessage = null
      )
    } else {
      copy(
        expirationInputMode = mode,
        errorMessage = null
      )
    }
  }

  /**
   * 直接修改过期日期（保留原有直接选择过期日期方式）。
   */
  fun updateDirectExpirationDate(newExpirationDate: LocalDate): ItemFormState {
    return copy(
      expirationDate = newExpirationDate,
      errorMessage = null
    )
  }

  /**
   * 更新生产日期，并在保质期合法时自动重新计算过期日期。
   */
  fun updateProductionDate(newProductionDate: LocalDate): ItemFormState {
    val calc = ExpirationCalculator.validateAndCalculate(
      productionDate = newProductionDate,
      rawShelfLife = shelfLifeValue,
      unit = shelfLifeUnit
    )
    return copy(
      productionDate = newProductionDate,
      expirationDate = calc.getOrNull() ?: expirationDate,
      errorMessage = null
    )
  }

  /**
   * 更新保质期时长字符串，并在合法时自动重新计算过期日期。
   */
  fun updateShelfLifeValue(newValue: String): ItemFormState {
    val calc = ExpirationCalculator.validateAndCalculate(
      productionDate = productionDate,
      rawShelfLife = newValue,
      unit = shelfLifeUnit
    )
    return copy(
      shelfLifeValue = newValue,
      isDefaultShelfLife = false,
      expirationDate = calc.getOrNull() ?: expirationDate,
      errorMessage = null
    )
  }

  /**
   * 更新保质期单位（天 / 个月 / 年），并在保质期数值合法时自动重新计算过期日期。
   */
  fun updateShelfLifeUnit(newUnit: ShelfLifeUnit): ItemFormState {
    val calc = ExpirationCalculator.validateAndCalculate(
      productionDate = productionDate,
      rawShelfLife = shelfLifeValue,
      unit = newUnit
    )
    return copy(
      shelfLifeUnit = newUnit,
      expirationDate = calc.getOrNull() ?: expirationDate,
      errorMessage = null
    )
  }

  /**
   * 一键应用常用保质期快捷预设（如 7天、30天、6个月、12个月 等）并自动计算过期日期。
   */
  fun applyShelfLifePreset(value: Int, unit: ShelfLifeUnit): ItemFormState {
    val newExpiration = ExpirationCalculator.calculateExpirationDate(
      productionDate = productionDate,
      shelfLifeValue = value,
      unit = unit
    )
    return copy(
      shelfLifeValue = value.toString(),
      shelfLifeUnit = unit,
      isDefaultShelfLife = false,
      expirationDate = newExpiration,
      errorMessage = null
    )
  }

  /**
   * 校验当前表单字段，若不合法则在返回的 [ItemFormState.errorMessage] 中写入错误原因。
   */
  fun validate(): ItemFormState {
    val resolvedExpirationDate = if (expirationInputMode == ExpirationInputMode.PRODUCTION_AND_SHELF_LIFE) {
      val calcResult = ExpirationCalculator.validateAndCalculate(
        productionDate = productionDate,
        rawShelfLife = shelfLifeValue,
        unit = shelfLifeUnit
      )
      if (!calcResult.isSuccess) {
        return copy(errorMessage = calcResult.errorMessage)
      }
      calcResult.getOrNull()!!
    } else {
      expirationDate
    }

    val result = PurchasedItemValidator.validateAndCreate(
      id = editingItemId ?: 0L,
      rawName = name,
      rawCategory = category,
      rawQuantity = quantity,
      rawAmount = amount,
      expirationDate = resolvedExpirationDate,
      createdAtEpochMillis = createdAtEpochMillis
    )
    return if (result.isSuccess) {
      copy(expirationDate = resolvedExpirationDate, errorMessage = null)
    } else {
      copy(errorMessage = result.errorMessage)
    }
  }

  companion object {
    fun forNewItem(
      defaultDate: LocalDate = LocalDate.now().plusDays(7),
      defaultProductionDate: LocalDate = LocalDate.now()
    ): ItemFormState {
      return ItemFormState(
        editingItemId = null,
        name = "",
        category = PurchasedItem.DEFAULT_CATEGORY,
        quantity = "1",
        amount = "",
        expirationDate = defaultDate,
        expirationInputMode = ExpirationInputMode.DIRECT_EXPIRATION,
        productionDate = defaultProductionDate,
        shelfLifeValue = "7",
        shelfLifeUnit = ShelfLifeUnit.DAYS,
        createdAtEpochMillis = null,
        errorMessage = null,
        isDefaultCategory = true,
        isDefaultQuantity = true,
        isDefaultShelfLife = true
      )
    }

    fun fromExistingItem(
      item: PurchasedItem,
      defaultProductionDate: LocalDate = LocalDate.now()
    ): ItemFormState {
      val formattedAmount = item.amount?.let { String.format(Locale.US, "%.2f", it) }.orEmpty()
      return ItemFormState(
        editingItemId = item.id,
        name = item.name,
        category = item.category,
        quantity = item.quantity.toString(),
        amount = formattedAmount,
        expirationDate = item.expirationDate,
        expirationInputMode = ExpirationInputMode.DIRECT_EXPIRATION,
        productionDate = defaultProductionDate,
        shelfLifeValue = "7",
        shelfLifeUnit = ShelfLifeUnit.DAYS,
        createdAtEpochMillis = item.createdAtEpochMillis,
        errorMessage = null,
        isDefaultCategory = item.category.trim() == PurchasedItem.DEFAULT_CATEGORY,
        isDefaultQuantity = false,
        isDefaultShelfLife = true
      )
    }
  }
}
