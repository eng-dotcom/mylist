package com.example.mylist.ui.main

import com.example.mylist.domain.model.PurchasedItem
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Issue #4 单元测试：测试自适应屏幕大小布局配置 [AdaptiveLayoutConfig] 与新增/编辑表单状态 [ItemFormState]。
 * 覆盖：
 * 1. 正向路径：手机（Compact）、折叠屏/小平板（Medium）、大平板（Expanded）自适应列数与边距计算；新建/编辑表单初始化与校验。
 * 2. 异常路径：非法/负数屏幕宽度回退处理；表单输入空名称、非法数量、负数金额时的错误提示。
 * 3. 边界条件：599dp/600dp/839dp/840dp 断点边界；编辑商品金额为 null 或带小数时的表单回填格式。
 */
class AdaptiveLayoutAndFormTest {

  private val sampleDate = LocalDate.of(2026, 11, 9)

  // ==================== 1. 正向路径测试 ====================

  @Test
  fun `compact screen width configures single column layout`() {
    val config = AdaptiveLayoutConfig.fromScreenWidthDp(390)
    assertEquals(ScreenSizeCategory.COMPACT, config.category)
    assertEquals(1, config.gridColumns)
    assertEquals(16, config.horizontalPaddingDp)
  }

  @Test
  fun `medium and expanded screen widths configure multi-column grid layout`() {
    val mediumConfig = AdaptiveLayoutConfig.fromScreenWidthDp(700)
    assertEquals(ScreenSizeCategory.MEDIUM, mediumConfig.category)
    assertEquals(2, mediumConfig.gridColumns)
    assertEquals(24, mediumConfig.horizontalPaddingDp)

    val expandedConfig = AdaptiveLayoutConfig.fromScreenWidthDp(960)
    assertEquals(ScreenSizeCategory.EXPANDED, expandedConfig.category)
    assertEquals(3, expandedConfig.gridColumns)
    assertEquals(32, expandedConfig.horizontalPaddingDp)
  }

  @Test
  fun `ItemFormState initializes correctly for new item and existing item with amount`() {
    val newForm = ItemFormState.forNewItem(defaultDate = sampleDate)
    assertFalse(newForm.isEditMode)
    assertEquals("", newForm.name)
    assertEquals("1", newForm.quantity)
    assertEquals("", newForm.amount)
    assertEquals(sampleDate, newForm.expirationDate)

    val existingItem = PurchasedItem(
      id = 10L,
      name = "明治牛奶",
      quantity = 3,
      amount = 24.5,
      expirationDate = sampleDate.plusDays(5),
      createdAtEpochMillis = 9000L
    )
    val editForm = ItemFormState.fromExistingItem(existingItem)
    assertTrue(editForm.isEditMode)
    assertEquals(10L, editForm.editingItemId)
    assertEquals("明治牛奶", editForm.name)
    assertEquals("3", editForm.quantity)
    assertEquals("24.50", editForm.amount)
    assertEquals(sampleDate.plusDays(5), editForm.expirationDate)
  }

  // ==================== 2. 异常路径测试 ====================

  @Test
  fun `negative or zero screen width safely falls back to COMPACT layout`() {
    val zeroConfig = AdaptiveLayoutConfig.fromScreenWidthDp(0)
    val negativeConfig = AdaptiveLayoutConfig.fromScreenWidthDp(-100)

    assertEquals(ScreenSizeCategory.COMPACT, zeroConfig.category)
    assertEquals(1, zeroConfig.gridColumns)
    assertEquals(ScreenSizeCategory.COMPACT, negativeConfig.category)
    assertEquals(1, negativeConfig.gridColumns)
  }

  @Test
  fun `ItemFormState validate returns error message when fields are invalid`() {
    val invalidNameForm = ItemFormState.forNewItem(sampleDate).copy(name = "  ", quantity = "2")
    val validated1 = invalidNameForm.validate()
    assertEquals("商品名称不能为空", validated1.errorMessage)

    val invalidQtyForm = ItemFormState.forNewItem(sampleDate).copy(name = "牛奶", quantity = "0")
    val validated2 = invalidQtyForm.validate()
    assertEquals("商品数量必须大于或等于 1", validated2.errorMessage)

    val invalidAmountForm = ItemFormState.forNewItem(sampleDate).copy(
      name = "牛奶",
      quantity = "1",
      amount = "-3.5"
    )
    val validated3 = invalidAmountForm.validate()
    assertEquals("商品金额不能为负数", validated3.errorMessage)
  }

  // ==================== 3. 边界条件测试 ====================

  @Test
  fun `boundary - exact breakpoint widths 599, 600, 839, and 840`() {
    assertEquals(1, AdaptiveLayoutConfig.fromScreenWidthDp(599).gridColumns)
    assertEquals(2, AdaptiveLayoutConfig.fromScreenWidthDp(600).gridColumns)
    assertEquals(2, AdaptiveLayoutConfig.fromScreenWidthDp(839).gridColumns)
    assertEquals(3, AdaptiveLayoutConfig.fromScreenWidthDp(840).gridColumns)
  }

  @Test
  fun `boundary - ItemFormState fromExistingItem with null amount leaves amount input empty`() {
    val itemWithoutAmount = PurchasedItem(
      id = 11L,
      name = "矿泉水",
      quantity = 1,
      amount = null,
      expirationDate = sampleDate
    )
    val editForm = ItemFormState.fromExistingItem(itemWithoutAmount)
    assertEquals("", editForm.amount)
    val validated = editForm.validate()
    assertNull(validated.errorMessage)
  }

  // ==================== 4. Issue #8：点击分类与数量输入框自动清空缺省值测试 ====================

  @Test
  fun `onCategoryFocus and onQuantityFocus clear default values to blank for new item`() {
    val newForm = ItemFormState.forNewItem(defaultDate = sampleDate)
    assertEquals("未分类", newForm.category)
    assertEquals("1", newForm.quantity)

    // 点击（聚焦）分类输入框：缺省值 "未分类" 自动变成空白 ""
    val afterCategoryFocus = newForm.onCategoryFocus()
    assertEquals("", afterCategoryFocus.category)

    // 点击（聚焦）数量输入框：缺省值 "1" 自动变成空白 ""
    val afterQuantityFocus = afterCategoryFocus.onQuantityFocus()
    assertEquals("", afterQuantityFocus.quantity)
  }

  @Test
  fun `onCategoryFocus and onQuantityFocus do not overwrite user-entered values`() {
    val userTypedForm = ItemFormState.forNewItem(defaultDate = sampleDate)
      .onCategoryFocus()
      .updateCategory("进口水果")
      .onQuantityFocus()
      .updateQuantity("6")

    assertEquals("进口水果", userTypedForm.category)
    assertEquals("6", userTypedForm.quantity)

    // 再次点击（聚焦）输入框时，用户已输入的内容不会被清空
    val refocusedForm = userTypedForm
      .onCategoryFocus()
      .onQuantityFocus()
    assertEquals("进口水果", refocusedForm.category)
    assertEquals("6", refocusedForm.quantity)

    // 即使用户手动输入了 "1"，也不再视为初始缺省值，再次聚焦不会清空
    val manualOneForm = ItemFormState.forNewItem(defaultDate = sampleDate)
      .onQuantityFocus()
      .updateQuantity("1")
      .onQuantityFocus()
    assertEquals("1", manualOneForm.quantity)
  }

  @Test
  fun `boundary - edit mode preserves existing quantity on focus and blank category normalizes to 未分类`() {
    val existingItem = PurchasedItem(
      id = 25L,
      name = "苏打水",
      category = "未分类",
      quantity = 1,
      amount = 5.0,
      expirationDate = sampleDate.plusDays(30)
    )
    val editForm = ItemFormState.fromExistingItem(existingItem)

    // 编辑已有商品时，数量 "1" 是商品真实库存，聚焦时不自动清空；若分类仍为缺省 "未分类"，聚焦时自动清空便于补录分类
    val focusedEditForm = editForm.onQuantityFocus().onCategoryFocus()
    assertEquals("1", focusedEditForm.quantity)
    assertEquals("", focusedEditForm.category)

    // 边界：新增商品时聚焦分类变成空白 "" 后若未输入新分类直接校验，自动归为 "未分类" 并通过校验
    val blankCategoryNewForm = ItemFormState.forNewItem(sampleDate)
      .copy(name = "抽纸")
      .onCategoryFocus()
    assertEquals("", blankCategoryNewForm.category)
    assertNull(blankCategoryNewForm.validate().errorMessage)
  }
}

