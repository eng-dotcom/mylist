package com.example.mylist.domain.validation

import com.example.mylist.domain.model.PurchasedItem
import java.math.BigDecimal
import java.time.LocalDate

/**
 * 通用字段校验结果封装。
 */
data class ValidationResult<out T>(
  val isSuccess: Boolean,
  private val value: T? = null,
  val errorMessage: String? = null
) {
  fun getOrNull(): T? = value

  companion object {
    fun <T> success(value: T): ValidationResult<T> =
      ValidationResult(isSuccess = true, value = value, errorMessage = null)

    fun <T> failure(errorMessage: String): ValidationResult<T> =
      ValidationResult(isSuccess = false, value = null, errorMessage = errorMessage)
  }
}

/**
 * 已购商品输入字段校验器。
 */
object PurchasedItemValidator {

  /**
   * 校验商品名称：去除首尾空白后不能为空。
   */
  fun validateName(rawName: String): ValidationResult<String> {
    val trimmed = rawName.trim()
    if (trimmed.isEmpty()) {
      return ValidationResult.failure("商品名称不能为空")
    }
    return ValidationResult.success(trimmed)
  }

  /**
   * 校验商品数量字符串：必须为纯正整数（不含小数或单位字符，且 >= 1）。
   */
  fun validateQuantity(rawQuantity: String): ValidationResult<Int> {
    val trimmed = rawQuantity.trim()
    if (trimmed.isEmpty()) {
      return ValidationResult.failure("请输入有效的正整数数量")
    }
    val parsed = trimmed.toIntOrNull()
      ?: return ValidationResult.failure("请输入有效的正整数数量")
    return validateQuantityValue(parsed)
  }

  /**
   * 校验商品数量数值：必须 >= 1。
   */
  fun validateQuantityValue(quantity: Int): ValidationResult<Int> {
    if (quantity < 1) {
      return ValidationResult.failure("商品数量必须大于或等于 1")
    }
    return ValidationResult.success(quantity)
  }

  /**
   * 校验可选的商品金额字符串：
   * - null 或空白字符串视为未填写，返回 success(null)
   * - 填写时必须为合法非负数字，且最多保留两位小数
   */
  fun validateAmount(rawAmount: String?): ValidationResult<Double?> {
    val trimmed = rawAmount?.trim().orEmpty()
    if (trimmed.isEmpty()) {
      return ValidationResult.success(null)
    }
    val decimal = try {
      BigDecimal(trimmed)
    } catch (_: NumberFormatException) {
      return ValidationResult.failure("请输入有效的金额数字")
    }
    if (decimal < BigDecimal.ZERO) {
      return ValidationResult.failure("商品金额不能为负数")
    }
    if (decimal.stripTrailingZeros().scale() > 2) {
      return ValidationResult.failure("商品金额最多保留两位小数")
    }
    return ValidationResult.success(decimal.toDouble())
  }

  /**
   * 校验可选的商品金额数值：可为 null，非 null 时必须 >= 0 且非 NaN/Infinite。
   */
  fun validateAmountValue(amount: Double?): ValidationResult<Double?> {
    if (amount == null) {
      return ValidationResult.success(null)
    }
    if (amount.isNaN() || amount.isInfinite()) {
      return ValidationResult.failure("请输入有效的金额数字")
    }
    if (amount < 0.0) {
      return ValidationResult.failure("商品金额不能为负数")
    }
    return ValidationResult.success(amount)
  }

  /**
   * 校验过期日期：必填。
   */
  fun validateExpirationDate(expirationDate: LocalDate?): ValidationResult<LocalDate> {
    if (expirationDate == null) {
      return ValidationResult.failure("请选择过期日期")
    }
    return ValidationResult.success(expirationDate)
  }

  /**
   * 综合校验表单输入并构建 [PurchasedItem]。
   */
  fun validateAndCreate(
    id: Long = 0L,
    rawName: String,
    rawQuantity: String,
    rawAmount: String?,
    expirationDate: LocalDate?,
    createdAtEpochMillis: Long? = null,
    nowMillis: Long = System.currentTimeMillis()
  ): ValidationResult<PurchasedItem> {
    val nameResult = validateName(rawName)
    if (!nameResult.isSuccess) {
      return ValidationResult.failure(nameResult.errorMessage ?: "商品名称不合法")
    }

    val quantityResult = validateQuantity(rawQuantity)
    if (!quantityResult.isSuccess) {
      return ValidationResult.failure(quantityResult.errorMessage ?: "商品数量不合法")
    }

    val amountResult = validateAmount(rawAmount)
    if (!amountResult.isSuccess) {
      return ValidationResult.failure(amountResult.errorMessage ?: "商品金额不合法")
    }

    val dateResult = validateExpirationDate(expirationDate)
    if (!dateResult.isSuccess) {
      return ValidationResult.failure(dateResult.errorMessage ?: "过期日期不合法")
    }

    val createdMillis = createdAtEpochMillis ?: nowMillis
    return ValidationResult.success(
      PurchasedItem(
        id = id,
        name = nameResult.getOrNull()!!,
        quantity = quantityResult.getOrNull()!!,
        amount = amountResult.getOrNull(),
        expirationDate = dateResult.getOrNull()!!,
        createdAtEpochMillis = createdMillis,
        updatedAtEpochMillis = nowMillis
      )
    )
  }
}

