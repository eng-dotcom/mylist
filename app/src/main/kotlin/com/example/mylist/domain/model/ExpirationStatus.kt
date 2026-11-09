package com.example.mylist.domain.model

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 商品保质期视觉状态枚举：
 * - [EXPIRED]：已过期（剩余天数 < 0）
 * - [EXPIRING_SOON]：临期（0 <= 剩余天数 <= 7）
 * - [NORMAL]：正常（剩余天数 > 7）
 */
enum class ExpirationStatus {
  EXPIRED,
  EXPIRING_SOON,
  NORMAL
}

/**
 * 商品保质期状态与展示信息。
 *
 * @property status 过期状态枚举
 * @property daysDelta 距离过期的天数差（负数表示已过期天数，0 表示今天到期，正数表示剩余天数）
 * @property label 用于 UI 展示的中文标签（例如："已过期 3 天"、"今天到期"、"剩余 5 天"）
 */
data class ExpirationInfo(
  val status: ExpirationStatus,
  val daysDelta: Long,
  val label: String
) {
  companion object {
    const val EXPIRING_SOON_THRESHOLD_DAYS = 7L

    fun fromDates(expirationDate: LocalDate, today: LocalDate = LocalDate.now()): ExpirationInfo {
      val daysDelta = ChronoUnit.DAYS.between(today, expirationDate)
      return when {
        daysDelta < 0L -> ExpirationInfo(
          status = ExpirationStatus.EXPIRED,
          daysDelta = daysDelta,
          label = "已过期 ${-daysDelta} 天"
        )
        daysDelta == 0L -> ExpirationInfo(
          status = ExpirationStatus.EXPIRING_SOON,
          daysDelta = 0L,
          label = "今天到期"
        )
        daysDelta <= EXPIRING_SOON_THRESHOLD_DAYS -> ExpirationInfo(
          status = ExpirationStatus.EXPIRING_SOON,
          daysDelta = daysDelta,
          label = "剩余 $daysDelta 天"
        )
        else -> ExpirationInfo(
          status = ExpirationStatus.NORMAL,
          daysDelta = daysDelta,
          label = "剩余 $daysDelta 天"
        )
      }
    }
  }
}

