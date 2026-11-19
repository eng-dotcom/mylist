package com.example.mylist.domain.model

import com.example.mylist.domain.validation.ValidationResult
import java.time.LocalDate

/**
 * 过期日期录入方式枚举：
 * - [DIRECT_EXPIRATION]：保留原有的直接选择/输入过期日期模式；
 * - [PRODUCTION_AND_SHELF_LIFE]：输入生产日期 + 保质期（天 / 个月 / 年），自动计算过期日期。
 */
enum class ExpirationInputMode(val displayName: String) {
  DIRECT_EXPIRATION("直接输入过期日"),
  PRODUCTION_AND_SHELF_LIFE("生产日期 + 保质期")
}

/**
 * 保质期时长单位枚举。
 */
enum class ShelfLifeUnit(val displayName: String, val maxAllowedValue: Int) {
  DAYS("天", 36500),
  MONTHS("个月", 1200),
  YEARS("年", 100)
}

/**
 * 常用保质期快捷预设项。
 */
data class ShelfLifePreset(
  val label: String,
  val value: Int,
  val unit: ShelfLifeUnit
)

/**
 * 生产日期 + 保质期自动计算过期日期的纯函数领域计算器与校验器。
 */
object ExpirationCalculator {

  /** 常用保质期快捷选项列表 */
  val COMMON_PRESETS: List<ShelfLifePreset> = listOf(
    ShelfLifePreset("7天", 7, ShelfLifeUnit.DAYS),
    ShelfLifePreset("15天", 15, ShelfLifeUnit.DAYS),
    ShelfLifePreset("30天", 30, ShelfLifeUnit.DAYS),
    ShelfLifePreset("3个月", 3, ShelfLifeUnit.MONTHS),
    ShelfLifePreset("6个月", 6, ShelfLifeUnit.MONTHS),
    ShelfLifePreset("12个月", 12, ShelfLifeUnit.MONTHS),
    ShelfLifePreset("18个月", 18, ShelfLifeUnit.MONTHS),
    ShelfLifePreset("2年", 2, ShelfLifeUnit.YEARS)
  )

  /**
   * 校验用户输入的保质期数值字符串。
   */
  fun validateShelfLife(rawShelfLife: String?, unit: ShelfLifeUnit): ValidationResult<Int> {
    val trimmed = rawShelfLife?.trim().orEmpty()
    if (trimmed.isEmpty()) {
      return ValidationResult.failure("请输入保质期时长")
    }
    val parsed = trimmed.toIntOrNull()
      ?: return ValidationResult.failure("保质期必须为正整数")
    if (parsed < 1) {
      return ValidationResult.failure("保质期必须大于或等于 1")
    }
    if (parsed > unit.maxAllowedValue) {
      return ValidationResult.failure("保质期数值超出合理范围")
    }
    return ValidationResult.success(parsed)
  }

  /**
   * 根据生产日期 [productionDate]、保质期数值 [shelfLifeValue] 与单位 [unit] 自动计算过期日期。
   */
  fun calculateExpirationDate(
    productionDate: LocalDate,
    shelfLifeValue: Int,
    unit: ShelfLifeUnit
  ): LocalDate {
    require(shelfLifeValue >= 1) { "shelfLifeValue ($shelfLifeValue) must be >= 1" }
    return when (unit) {
      ShelfLifeUnit.DAYS -> productionDate.plusDays(shelfLifeValue.toLong())
      ShelfLifeUnit.MONTHS -> productionDate.plusMonths(shelfLifeValue.toLong())
      ShelfLifeUnit.YEARS -> productionDate.plusYears(shelfLifeValue.toLong())
    }
  }

  /**
   * 校验生产日期与保质期输入，并在合法时返回自动计算出的过期日期 [LocalDate]。
   */
  fun validateAndCalculate(
    productionDate: LocalDate?,
    rawShelfLife: String?,
    unit: ShelfLifeUnit
  ): ValidationResult<LocalDate> {
    if (productionDate == null) {
      return ValidationResult.failure("请选择生产日期")
    }
    val shelfLifeResult = validateShelfLife(rawShelfLife, unit)
    if (!shelfLifeResult.isSuccess) {
      return ValidationResult.failure(shelfLifeResult.errorMessage ?: "保质期输入无效")
    }
    val calculated = calculateExpirationDate(
      productionDate = productionDate,
      shelfLifeValue = shelfLifeResult.getOrNull()!!,
      unit = unit
    )
    return ValidationResult.success(calculated)
  }
}
