package com.example.mylist.domain.model

import java.time.LocalDate
import java.util.Locale

/**
 * 已购商品领域模型。
 *
 * @property id 主键 ID（新建未持久化时为 0L）
 * @property name 商品名称（必填，非空）
 * @property quantity 商品数量（必填，纯正整数 >= 1，不含单位）
 * @property amount 商品金额（选填，null 表示未填写；若填写则 >= 0.0）
 * @property expirationDate 过期日期（必填）
 * @property createdAtEpochMillis 创建时间戳（毫秒）
 * @property updatedAtEpochMillis 更新时间戳（毫秒）
 */
data class PurchasedItem(
  val id: Long = 0L,
  val name: String,
  val quantity: Int,
  val amount: Double? = null,
  val expirationDate: LocalDate,
  val createdAtEpochMillis: Long = System.currentTimeMillis(),
  val updatedAtEpochMillis: Long = createdAtEpochMillis
) {
  /**
   * 计算相对于 [today] 的保质期状态与提示文案。
   */
  fun expirationInfo(today: LocalDate = LocalDate.now()): ExpirationInfo {
    return ExpirationInfo.fromDates(expirationDate = expirationDate, today = today)
  }

  /**
   * 获取相对于 [today] 的过期状态枚举。
   */
  fun expirationStatus(today: LocalDate = LocalDate.now()): ExpirationStatus {
    return expirationInfo(today).status
  }

  /**
   * 获取相对于 [today] 的剩余天数（负数代表已过期天数）。
   */
  fun daysUntilExpiration(today: LocalDate = LocalDate.now()): Long {
    return expirationInfo(today).daysDelta
  }

  /**
   * 格式化金额展示（未填写时返回 null，填写时返回如 "¥25.80"）。
   */
  fun formattedAmount(): String? {
    val value = amount ?: return null
    return String.format(Locale.US, "¥%.2f", value)
  }
}

