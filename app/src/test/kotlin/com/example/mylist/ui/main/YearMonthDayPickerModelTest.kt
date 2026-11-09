package com.example.mylist.ui.main

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Issue #6 单元测试：测试【年 / 月 / 日】三列联动日期选择器的核心状态与日期联动模型 [YearMonthDayPickerModel]。
 * 覆盖：
 * 1. 正向路径：年、月、日三列可选项生成，指定初始日期回显，修改年/月/日后生成正确的 LocalDate。
 * 2. 异常路径：传入越界的年份、月份（< 1 或 > 12）或日期（< 1 或 > 31）时安全钳制（Clamp），绝不抛出 DateTimeException。
 * 3. 边界条件：大小月切换（1月31日 -> 4月自动钳制为30日，1月31日 -> 平年2月自动钳制为28日）、
 *    闰年2月29日切换至平年2月（2024-02-29 -> 2026年自动钳制为 2026-02-28）。
 */
class YearMonthDayPickerModelTest {

  // ==================== 1. 正向路径测试 ====================

  @Test
  fun `fromLocalDate initializes year, month, and day columns accurately`() {
    val initialDate = LocalDate.of(2026, 11, 9)
    val model = YearMonthDayPickerModel.fromLocalDate(
      initialDate = initialDate,
      minYear = 2020,
      maxYear = 2035
    )

    assertEquals(2026, model.selectedYear)
    assertEquals(11, model.selectedMonth)
    assertEquals(9, model.selectedDay)
    assertEquals(initialDate, model.toLocalDate())

    // 验证三列数据：年列、月列(1..12)、2026年11月的日列(1..30)
    assertEquals((2020..2035).toList(), model.availableYears)
    assertEquals((1..12).toList(), model.availableMonths)
    assertEquals((1..30).toList(), model.availableDays)
  }

  @Test
  fun `selecting year, month, and day updates three columns and produces expected LocalDate`() {
    val model = YearMonthDayPickerModel.fromLocalDate(LocalDate.of(2026, 11, 9))
      .selectYear(2027)
      .selectMonth(12)
      .selectDay(25)

    assertEquals(2027, model.selectedYear)
    assertEquals(12, model.selectedMonth)
    assertEquals(25, model.selectedDay)
    assertEquals(31, model.availableDays.size)
    assertEquals(LocalDate.of(2027, 12, 25), model.toLocalDate())
    assertEquals("2027-12-25", model.formattedDate())
  }

  // ==================== 2. 异常路径测试 ====================

  @Test
  fun `out of range year, month, or day inputs are safely clamped without throwing exception`() {
    val base = YearMonthDayPickerModel.fromLocalDate(
      initialDate = LocalDate.of(2026, 5, 15),
      minYear = 2020,
      maxYear = 2035
    )

    val clampedLow = base
      .selectYear(1800)
      .selectMonth(0)
      .selectDay(-5)
    assertEquals(2020, clampedLow.selectedYear)
    assertEquals(1, clampedLow.selectedMonth)
    assertEquals(1, clampedLow.selectedDay)
    assertEquals(LocalDate.of(2020, 1, 1), clampedLow.toLocalDate())

    val clampedHigh = base
      .selectYear(2999)
      .selectMonth(99)
      .selectDay(99)
    assertEquals(2035, clampedHigh.selectedYear)
    assertEquals(12, clampedHigh.selectedMonth)
    assertEquals(31, clampedHigh.selectedDay)
    assertEquals(LocalDate.of(2035, 12, 31), clampedHigh.toLocalDate())
  }

  // ==================== 3. 边界条件测试 ====================

  @Test
  fun `boundary - switching from 31-day month to 30-day or 28-day month clamps selectedDay`() {
    val jan31 = YearMonthDayPickerModel.fromLocalDate(LocalDate.of(2026, 1, 31))
    assertEquals(31, jan31.selectedDay)

    // 切换到 11 月（小月 30 天），日期应自动从 31 钳制为 30
    val novModel = jan31.selectMonth(11)
    assertEquals(30, novModel.availableDays.size)
    assertEquals(30, novModel.selectedDay)
    assertEquals(LocalDate.of(2026, 11, 30), novModel.toLocalDate())

    // 切换到 2026 年 2 月（平年 28 天），日期应自动钳制为 28
    val febModel = jan31.selectMonth(2)
    assertEquals(28, febModel.availableDays.size)
    assertEquals(28, febModel.selectedDay)
    assertEquals(LocalDate.of(2026, 2, 28), febModel.toLocalDate())
  }

  @Test
  fun `boundary - leap year Feb 29 clamps to Feb 28 when switching to non-leap year`() {
    val leapFeb29 = YearMonthDayPickerModel.fromLocalDate(LocalDate.of(2024, 2, 29))
    assertEquals(29, leapFeb29.availableDays.size)
    assertEquals(29, leapFeb29.selectedDay)

    // 从闰年 2024 切换到平年 2026，2月天数变为 28，selectedDay 自动从 29 钳制为 28
    val nonLeapFeb = leapFeb29.selectYear(2026)
    assertEquals(28, nonLeapFeb.availableDays.size)
    assertEquals(28, nonLeapFeb.selectedDay)
    assertEquals(LocalDate.of(2026, 2, 28), nonLeapFeb.toLocalDate())

    // 切换到另一个闰年 2028，2月有 29 天，允许选择 29 日
    val leap2028 = nonLeapFeb.selectYear(2028).selectDay(29)
    assertEquals(29, leap2028.availableDays.size)
    assertEquals(LocalDate.of(2028, 2, 29), leap2028.toLocalDate())
  }

  @Test
  fun `boundary - initialDate outside default year bounds automatically expands availableYears`() {
    val farFutureDate = LocalDate.of(2050, 6, 18)
    val model = YearMonthDayPickerModel.fromLocalDate(
      initialDate = farFutureDate,
      minYear = 2020,
      maxYear = 2035
    )
    assertTrue(model.availableYears.contains(2050))
    assertEquals(farFutureDate, model.toLocalDate())
  }
}

