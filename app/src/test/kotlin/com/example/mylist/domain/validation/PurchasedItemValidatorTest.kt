package com.example.mylist.domain.validation

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Issue #1 单元测试：测试已购商品字段输入校验器 [PurchasedItemValidator]。
 * 覆盖：
 * 1. 正向路径：合法名称、正整数数量、有金额/无金额、合法过期日期。
 * 2. 异常路径：空名称、数量 <= 0、带单位/小数/非数字数量、负数金额、非法金额字符串、未选过期日期。
 * 3. 边界条件：名称首尾带空格、数量刚好为 1、金额为空/空白/0/两位小数/三位小数。
 */
class PurchasedItemValidatorTest {

  private val sampleDate = LocalDate.of(2026, 12, 1)

  // ==================== 1. 正向路径测试 ====================

  @Test
  fun `validateAndCreate succeeds with all valid fields including amount`() {
    val result = PurchasedItemValidator.validateAndCreate(
      rawName = "有机苹果",
      rawQuantity = "6",
      rawAmount = "25.80",
      expirationDate = sampleDate,
      nowMillis = 123456L
    )

    assertTrue(result.isSuccess)
    val item = result.getOrNull()!!
    assertEquals("有机苹果", item.name)
    assertEquals(6, item.quantity)
    assertEquals(25.80, item.amount!!, 0.001)
    assertEquals(sampleDate, item.expirationDate)
    assertEquals(123456L, item.createdAtEpochMillis)
  }

  @Test
  fun `validateAndCreate succeeds when optional amount is omitted`() {
    val result = PurchasedItemValidator.validateAndCreate(
      rawName = "矿泉水",
      rawQuantity = "12",
      rawAmount = "",
      expirationDate = sampleDate,
      nowMillis = 123456L
    )

    assertTrue(result.isSuccess)
    val item = result.getOrNull()!!
    assertEquals("矿泉水", item.name)
    assertEquals(12, item.quantity)
    assertNull(item.amount)
  }

  // ==================== 2. 异常路径测试 ====================

  @Test
  fun `validateName fails when empty or blank`() {
    val emptyResult = PurchasedItemValidator.validateName("")
    val blankResult = PurchasedItemValidator.validateName("   \t\n ")

    assertFalse(emptyResult.isSuccess)
    assertEquals("商品名称不能为空", emptyResult.errorMessage)
    assertFalse(blankResult.isSuccess)
    assertEquals("商品名称不能为空", blankResult.errorMessage)
  }

  @Test
  fun `validateQuantity fails for zero, negative, decimal, or unit suffix`() {
    val zeroResult = PurchasedItemValidator.validateQuantity("0")
    val negativeResult = PurchasedItemValidator.validateQuantity("-3")
    val decimalResult = PurchasedItemValidator.validateQuantity("1.5")
    val unitResult = PurchasedItemValidator.validateQuantity("2盒")
    val emptyResult = PurchasedItemValidator.validateQuantity("")

    assertFalse(zeroResult.isSuccess)
    assertEquals("商品数量必须大于或等于 1", zeroResult.errorMessage)

    assertFalse(negativeResult.isSuccess)
    assertEquals("商品数量必须大于或等于 1", negativeResult.errorMessage)

    assertFalse(decimalResult.isSuccess)
    assertEquals("请输入有效的正整数数量", decimalResult.errorMessage)

    assertFalse(unitResult.isSuccess)
    assertEquals("请输入有效的正整数数量", unitResult.errorMessage)

    assertFalse(emptyResult.isSuccess)
    assertEquals("请输入有效的正整数数量", emptyResult.errorMessage)
  }

  @Test
  fun `validateAmount fails for negative or non-numeric string`() {
    val negativeResult = PurchasedItemValidator.validateAmount("-0.01")
    val textResult = PurchasedItemValidator.validateAmount("abc")

    assertFalse(negativeResult.isSuccess)
    assertEquals("商品金额不能为负数", negativeResult.errorMessage)

    assertFalse(textResult.isSuccess)
    assertEquals("请输入有效的金额数字", textResult.errorMessage)
  }

  @Test
  fun `validateExpirationDate fails when null`() {
    val result = PurchasedItemValidator.validateExpirationDate(null)
    assertFalse(result.isSuccess)
    assertEquals("请选择过期日期", result.errorMessage)
  }

  // ==================== 3. 边界条件测试 ====================

  @Test
  fun `boundary - validateName trims leading and trailing spaces`() {
    val result = PurchasedItemValidator.validateName("  鸡蛋  ")
    assertTrue(result.isSuccess)
    assertEquals("鸡蛋", result.getOrNull())
  }

  @Test
  fun `boundary - validateQuantity accepts minimum positive integer 1`() {
    val result = PurchasedItemValidator.validateQuantity("1")
    assertTrue(result.isSuccess)
    assertEquals(1, result.getOrNull())
  }

  @Test
  fun `boundary - validateAmount accepts null, blank, zero, and up to 2 decimal places`() {
    val nullResult = PurchasedItemValidator.validateAmount(null)
    val blankResult = PurchasedItemValidator.validateAmount("   ")
    val zeroResult = PurchasedItemValidator.validateAmount("0")
    val twoDecimalsResult = PurchasedItemValidator.validateAmount("19.99")

    assertTrue(nullResult.isSuccess)
    assertNull(nullResult.getOrNull())

    assertTrue(blankResult.isSuccess)
    assertNull(blankResult.getOrNull())

    assertTrue(zeroResult.isSuccess)
    assertEquals(0.0, zeroResult.getOrNull()!!, 0.0001)

    assertTrue(twoDecimalsResult.isSuccess)
    assertEquals(19.99, twoDecimalsResult.getOrNull()!!, 0.0001)
  }

  @Test
  fun `boundary - validateAmount rejects more than 2 decimal places`() {
    val threeDecimalsResult = PurchasedItemValidator.validateAmount("19.999")
    assertFalse(threeDecimalsResult.isSuccess)
    assertEquals("商品金额最多保留两位小数", threeDecimalsResult.errorMessage)
  }
}

