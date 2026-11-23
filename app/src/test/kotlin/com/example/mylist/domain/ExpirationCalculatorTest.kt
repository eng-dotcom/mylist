package com.example.mylist.domain

import com.example.mylist.data.local.InMemoryPurchasedItemDao
import com.example.mylist.data.repository.DefaultPurchasedItemRepository
import com.example.mylist.domain.model.ExpirationCalculator
import com.example.mylist.domain.model.ExpirationInputMode
import com.example.mylist.domain.model.ShelfLifeUnit
import com.example.mylist.ui.main.ItemFormState
import com.example.mylist.ui.main.PurchasedItemViewModel
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Issue #10 单元测试：测试“生产日期 + 保质期（天/月/年）自动计算过期日期”与“直接输入过期日期”双模式逻辑。
 * 覆盖：
 * 1. 正向路径：
 *    - 按天（DAYS）、按月（MONTHS）、按年（YEARS）根据生产日期自动准确计算过期日期；
 *    - 表单在【直接输入过期日】与【生产日期 + 保质期】两种模式间切换、联动更新生产日期/保质期/单位，以及直接修改过期日期；
 *    - 点击保质期输入框时自动清空初始缺省值。
 * 2. 异常路径：
 *    - 保质期为空、非数字、0、负数或超出合理上限（如 > 100年 / > 1200个月 / > 36500天）时拦截并返回清晰错误提示；
 *    - 在【生产日期 + 保质期】模式下保质期非法时提交表单，表单校验拦截并提示错误。
 * 3. 边界条件：
 *    - 跨月底计算（如 1月31日 + 1个月 -> 2月28日/29日）；
 *    - 闰年 2月29日 + 1年 -> 平年 2月28日；
 *    - 从【生产日期 + 保质期】计算得出过期日期后切换回【直接输入过期日】，保留已算出的过期日期并支持继续手动直接修改。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ExpirationCalculatorTest {

  private val testDispatcher = UnconfinedTestDispatcher()
  private val baseProductionDate = LocalDate.of(2026, 11, 1)

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  // ==================== 1. 正向路径测试 ====================

  @Test
  fun `calculateExpirationDate accurately computes expiration by days, months, and years`() {
    // 按天计算：2026-11-01 + 15 天 = 2026-11-16
    val byDays = ExpirationCalculator.calculateExpirationDate(
      productionDate = baseProductionDate,
      shelfLifeValue = 15,
      unit = ShelfLifeUnit.DAYS
    )
    assertEquals(LocalDate.of(2026, 11, 16), byDays)

    // 按月计算：2026-11-01 + 6 个月 = 2027-05-01
    val byMonths = ExpirationCalculator.calculateExpirationDate(
      productionDate = baseProductionDate,
      shelfLifeValue = 6,
      unit = ShelfLifeUnit.MONTHS
    )
    assertEquals(LocalDate.of(2027, 5, 1), byMonths)

    // 按年计算：2026-11-01 + 2 年 = 2028-11-01
    val byYears = ExpirationCalculator.calculateExpirationDate(
      productionDate = baseProductionDate,
      shelfLifeValue = 2,
      unit = ShelfLifeUnit.YEARS
    )
    assertEquals(LocalDate.of(2028, 11, 1), byYears)
  }

  @Test
  fun `ItemFormState auto-calculates expirationDate when productionDate, shelfLifeValue, or unit changes`() {
    var form = ItemFormState.forNewItem(
      defaultDate = LocalDate.of(2026, 11, 26),
      defaultProductionDate = LocalDate.of(2026, 11, 19)
    ).copy(name = "鲜牛奶")

    // 默认模式为直接输入过期日期
    assertEquals(ExpirationInputMode.DIRECT_EXPIRATION, form.expirationInputMode)
    assertEquals(LocalDate.of(2026, 11, 26), form.expirationDate)

    // 切换到【生产日期 + 保质期】模式（初始生产日 2026-11-19，默认保质期 7 天 -> 2026-11-26）
    form = form.switchExpirationInputMode(ExpirationInputMode.PRODUCTION_AND_SHELF_LIFE)
    assertEquals(ExpirationInputMode.PRODUCTION_AND_SHELF_LIFE, form.expirationInputMode)
    assertEquals(LocalDate.of(2026, 11, 26), form.expirationDate)

    // 点击保质期输入框时，初始缺省值 "7" 自动变成空白 ""
    form = form.onShelfLifeFocus()
    assertEquals("", form.shelfLifeValue)

    // 输入保质期 "6" 并切换单位为 "个月"，修改生产日期为 2026-10-15 -> 过期日期自动计算为 2027-04-15
    form = form
      .updateProductionDate(LocalDate.of(2026, 10, 15))
      .updateShelfLifeValue("6")
      .updateShelfLifeUnit(ShelfLifeUnit.MONTHS)

    assertEquals(LocalDate.of(2027, 4, 15), form.expirationDate)
    assertNull(form.validate().errorMessage)
  }

  @Test
  fun `ItemFormState preserves direct expiration date input mode and preset shelf life shortcuts`() {
    var form = ItemFormState.forNewItem(
      defaultDate = LocalDate.of(2026, 11, 26),
      defaultProductionDate = LocalDate.of(2026, 11, 10)
    ).copy(name = "酸奶")

    // 原有的直接输入过期日期功能完整保留
    form = form.updateDirectExpirationDate(LocalDate.of(2026, 12, 31))
    assertEquals(LocalDate.of(2026, 12, 31), form.expirationDate)
    assertNull(form.validate().errorMessage)

    // 切换到生产日期+保质期并使用快捷预设（如 180 天）
    form = form
      .switchExpirationInputMode(ExpirationInputMode.PRODUCTION_AND_SHELF_LIFE)
      .applyShelfLifePreset(180, ShelfLifeUnit.DAYS)
    assertEquals(LocalDate.of(2026, 11, 10).plusDays(180), form.expirationDate)
  }

  // ==================== 2. 异常路径测试 ====================

  @Test
  fun `validateShelfLife rejects blank, non-numeric, zero, negative, or excessively large values`() {
    val blankResult = ExpirationCalculator.validateShelfLife("   ", ShelfLifeUnit.DAYS)
    assertFalse(blankResult.isSuccess)
    assertEquals("请输入保质期时长", blankResult.errorMessage)

    val nonNumericResult = ExpirationCalculator.validateShelfLife("abc", ShelfLifeUnit.MONTHS)
    assertFalse(nonNumericResult.isSuccess)
    assertEquals("保质期必须为正整数", nonNumericResult.errorMessage)

    val zeroResult = ExpirationCalculator.validateShelfLife("0", ShelfLifeUnit.DAYS)
    assertFalse(zeroResult.isSuccess)
    assertEquals("保质期必须大于或等于 1", zeroResult.errorMessage)

    val negativeResult = ExpirationCalculator.validateShelfLife("-30", ShelfLifeUnit.DAYS)
    assertFalse(negativeResult.isSuccess)
    assertEquals("保质期必须大于或等于 1", negativeResult.errorMessage)

    val tooManyYears = ExpirationCalculator.validateShelfLife("101", ShelfLifeUnit.YEARS)
    assertFalse(tooManyYears.isSuccess)
    assertEquals("保质期数值超出合理范围", tooManyYears.errorMessage)

    val tooManyDays = ExpirationCalculator.validateShelfLife("40000", ShelfLifeUnit.DAYS)
    assertFalse(tooManyDays.isSuccess)
    assertEquals("保质期数值超出合理范围", tooManyDays.errorMessage)
  }

  @Test
  fun `ItemFormState validate fails with error message when shelfLifeValue is explicitly cleared after edit in production mode`() {
    val invalidShelfLifeForm = ItemFormState.forNewItem(
      defaultDate = LocalDate.of(2026, 11, 26),
      defaultProductionDate = LocalDate.of(2026, 11, 19)
    )
      .copy(name = "面包")
      .switchExpirationInputMode(ExpirationInputMode.PRODUCTION_AND_SHELF_LIFE)
      .onShelfLifeFocus()
      .updateShelfLifeValue("") // 用户主动输入/清空为空字符串

    val validated = invalidShelfLifeForm.validate()
    assertEquals("请输入保质期时长", validated.errorMessage)
  }

  @Test
  fun `submitting productionDate and shelfLife through validated formState to ViewModel succeeds end-to-end`() = runTest {
    val today = LocalDate.of(2026, 11, 23)
    val repository = DefaultPurchasedItemRepository(InMemoryPurchasedItemDao())
    val viewModel = PurchasedItemViewModel(repository = repository, todayProvider = { today })

    // 模拟用户打开新增弹窗：聚焦过数量框（未改数量即移开焦点），切换到生产日期+保质期模式，选择生产日期并输入保质期
    val form = ItemFormState.forNewItem(
      defaultDate = today.plusDays(7),
      defaultProductionDate = today
    )
      .copy(name = "鲜牛奶")
      .onQuantityFocus() // 聚焦时清空为 ""
      .onQuantityBlur()  // 移开焦点未输入时自动恢复缺省 "1"
      .switchExpirationInputMode(ExpirationInputMode.PRODUCTION_AND_SHELF_LIFE)
      .updateProductionDate(LocalDate.of(2026, 11, 20))
      .onShelfLifeFocus()
      .updateShelfLifeValue("15")
      .updateShelfLifeUnit(ShelfLifeUnit.DAYS)

    val validated = form.validate()
    assertNull(validated.errorMessage)
    assertEquals("1", validated.quantity)
    assertEquals(LocalDate.of(2026, 12, 5), validated.expirationDate)

    val added = viewModel.addItem(
      rawName = validated.name,
      rawCategory = validated.category,
      rawQuantity = validated.quantity,
      rawAmount = validated.amount,
      expirationDate = validated.expirationDate
    )
    assertTrue(added)
    assertEquals(1, viewModel.uiState.value.items.size)
    val saved = viewModel.uiState.value.items.first()
    assertEquals("鲜牛奶", saved.name)
    assertEquals(1, saved.quantity)
    assertEquals(LocalDate.of(2026, 12, 5), saved.expirationDate)
  }

  @Test
  fun `boundary - focusing quantity or shelfLife without typing still allows direct submit by falling back to defaults`() = runTest {
    val today = LocalDate.of(2026, 11, 23)
    val repository = DefaultPurchasedItemRepository(InMemoryPurchasedItemDao())
    val viewModel = PurchasedItemViewModel(repository = repository, todayProvider = { today })

    // 边界：用户点击了保质期输入框（触发 onShelfLifeFocus 变空白），但未输入字符就直接点击“确认添加”
    val form = ItemFormState.forNewItem(
      defaultDate = today.plusDays(7),
      defaultProductionDate = LocalDate.of(2026, 11, 10)
    )
      .copy(name = "日配酸奶")
      .onQuantityFocus()
      .switchExpirationInputMode(ExpirationInputMode.PRODUCTION_AND_SHELF_LIFE)
      .onShelfLifeFocus()

    val validated = form.validate()
    assertNull(validated.errorMessage)
    assertEquals("1", validated.quantity)
    assertEquals("7", validated.shelfLifeValue)
    assertEquals(LocalDate.of(2026, 11, 17), validated.expirationDate)

    val added = viewModel.addItem(
      rawName = validated.name,
      rawCategory = validated.category,
      rawQuantity = validated.quantity,
      rawAmount = validated.amount,
      expirationDate = validated.expirationDate
    )
    assertTrue(added)
    assertEquals(LocalDate.of(2026, 11, 17), viewModel.uiState.value.items.first().expirationDate)
  }

  // ==================== 3. 边界条件测试 ====================

  @Test
  fun `boundary - end of month and leap year production dates clamp accurately when adding months or years`() {
    // 平年 1月31日 + 1个月 -> 2月28日
    val jan31ToFeb = ExpirationCalculator.calculateExpirationDate(
      productionDate = LocalDate.of(2026, 1, 31),
      shelfLifeValue = 1,
      unit = ShelfLifeUnit.MONTHS
    )
    assertEquals(LocalDate.of(2026, 2, 28), jan31ToFeb)

    // 闰年 1月31日 + 1个月 -> 2月29日
    val leapJan31ToFeb = ExpirationCalculator.calculateExpirationDate(
      productionDate = LocalDate.of(2024, 1, 31),
      shelfLifeValue = 1,
      unit = ShelfLifeUnit.MONTHS
    )
    assertEquals(LocalDate.of(2024, 2, 29), leapJan31ToFeb)

    // 闰年 2月29日 + 1年 -> 平年 2月28日
    val leapFeb29Plus1Year = ExpirationCalculator.calculateExpirationDate(
      productionDate = LocalDate.of(2024, 2, 29),
      shelfLifeValue = 1,
      unit = ShelfLifeUnit.YEARS
    )
    assertEquals(LocalDate.of(2025, 2, 28), leapFeb29Plus1Year)
  }

  @Test
  fun `boundary - switching from production mode back to direct mode retains calculated expirationDate`() {
    val form = ItemFormState.forNewItem(
      defaultDate = LocalDate.of(2026, 11, 26),
      defaultProductionDate = LocalDate.of(2026, 11, 1)
    )
      .copy(name = "坚果礼盒")
      .switchExpirationInputMode(ExpirationInputMode.PRODUCTION_AND_SHELF_LIFE)
      .updateShelfLifeValue("8")
      .updateShelfLifeUnit(ShelfLifeUnit.MONTHS)

    // 自动算出过期日为 2027-07-01
    assertEquals(LocalDate.of(2027, 7, 1), form.expirationDate)

    // 切回【直接输入过期日】模式，已算出的 2027-07-01 保留，且即使保质期框此前被清空也不再阻塞直接模式校验
    val backToDirect = form
      .updateShelfLifeValue("")
      .switchExpirationInputMode(ExpirationInputMode.DIRECT_EXPIRATION)
    assertEquals(ExpirationInputMode.DIRECT_EXPIRATION, backToDirect.expirationInputMode)
    assertEquals(LocalDate.of(2027, 7, 1), backToDirect.expirationDate)
    assertNull(backToDirect.validate().errorMessage)
  }
}
