package com.example.mylist.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import kotlin.math.max
import kotlin.math.min

/**
 * 【年 / 月 / 日】三列联动日期选择器的纯状态与计算模型。
 *
 * 解决系统默认 Material 3 DatePicker 点击顶部年月（如 2026-11）只弹出纯年份列表的问题，
 * 始终提供“年”、“月”、“日”三列同步联动选择，并在切换年/月时自动处理大小月与闰年2月天数钳制。
 */
data class YearMonthDayPickerModel(
  val selectedYear: Int,
  val selectedMonth: Int,
  val selectedDay: Int,
  val minYear: Int = DEFAULT_MIN_YEAR,
  val maxYear: Int = DEFAULT_MAX_YEAR
) {
  init {
    require(minYear <= maxYear) { "minYear ($minYear) must be <= maxYear ($maxYear)" }
  }

  /** 第一列：可选年份列表 */
  val availableYears: List<Int>
    get() = (minYear..maxYear).toList()

  /** 第二列：可选月份列表（1..12） */
  val availableMonths: List<Int>
    get() = (1..12).toList()

  /** 第三列：当前选中年月下的可选日期列表（1..28/29/30/31） */
  val availableDays: List<Int>
    get() = (1..daysInMonth(selectedYear, selectedMonth)).toList()

  /**
   * 选择新的年份，并自动根据该年当前月份的最大天数钳制 [selectedDay]（例如闰年 2月29日 切换到平年自动变为 2月28日）。
   */
  fun selectYear(year: Int): YearMonthDayPickerModel {
    val clampedYear = year.coerceIn(minYear, maxYear)
    val maxDay = daysInMonth(clampedYear, selectedMonth)
    val clampedDay = selectedDay.coerceIn(1, maxDay)
    return copy(
      selectedYear = clampedYear,
      selectedDay = clampedDay
    )
  }

  /**
   * 选择新的月份（1..12），并自动根据新月份的最大天数钳制 [selectedDay]（例如 1月31日 切换到 11月自动变为 11月30日）。
   */
  fun selectMonth(month: Int): YearMonthDayPickerModel {
    val clampedMonth = month.coerceIn(1, 12)
    val maxDay = daysInMonth(selectedYear, clampedMonth)
    val clampedDay = selectedDay.coerceIn(1, maxDay)
    return copy(
      selectedMonth = clampedMonth,
      selectedDay = clampedDay
    )
  }

  /**
   * 选择新的日期（日），自动限制在当前年月合法天数范围内。
   */
  fun selectDay(day: Int): YearMonthDayPickerModel {
    val maxDay = daysInMonth(selectedYear, selectedMonth)
    val clampedDay = day.coerceIn(1, maxDay)
    return copy(selectedDay = clampedDay)
  }

  /**
   * 转换为标准 [LocalDate] 对象。
   */
  fun toLocalDate(): LocalDate {
    val maxDay = daysInMonth(selectedYear, selectedMonth)
    return LocalDate.of(selectedYear, selectedMonth, selectedDay.coerceIn(1, maxDay))
  }

  /**
   * 格式化为 `yyyy-MM-dd` 字符串。
   */
  fun formattedDate(): String = toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE)

  companion object {
    const val DEFAULT_MIN_YEAR = 2020
    const val DEFAULT_MAX_YEAR = 2040

    /**
     * 计算指定年、月的最大天数（自动识别平年/闰年 2 月及大小月）。
     */
    fun daysInMonth(year: Int, month: Int): Int {
      val safeMonth = month.coerceIn(1, 12)
      return YearMonth.of(year, safeMonth).lengthOfMonth()
    }

    /**
     * 从初始 [LocalDate] 构建三列选择器模型；若初始年份超出默认范围则自动扩展年份范围。
     */
    fun fromLocalDate(
      initialDate: LocalDate,
      minYear: Int = DEFAULT_MIN_YEAR,
      maxYear: Int = DEFAULT_MAX_YEAR
    ): YearMonthDayPickerModel {
      val effectiveMinYear = min(minYear, initialDate.year)
      val effectiveMaxYear = max(maxYear, initialDate.year)
      val maxDay = daysInMonth(initialDate.year, initialDate.monthValue)
      return YearMonthDayPickerModel(
        selectedYear = initialDate.year,
        selectedMonth = initialDate.monthValue,
        selectedDay = initialDate.dayOfMonth.coerceIn(1, maxDay),
        minYear = effectiveMinYear,
        maxYear = effectiveMaxYear
      )
    }
  }
}

/**
 * 【年 / 月 / 日】三列并排联动选择弹窗。
 *
 * 界面同时呈现“年”、“月”、“日”三列滚动选择列表，点击任意年、月、日立即联动刷新并高亮显示，
 * 彻底解决默认日历控件点击年月标题只显示年份列表的问题。
 */
@Composable
fun YearMonthDayPickerDialog(
  initialDate: LocalDate,
  onDismissRequest: () -> Unit,
  onDateSelected: (LocalDate) -> Unit,
  today: LocalDate = LocalDate.now()
) {
  var pickerModel by remember(initialDate) {
    mutableStateOf(
      YearMonthDayPickerModel.fromLocalDate(
        initialDate = initialDate,
        minYear = min(YearMonthDayPickerModel.DEFAULT_MIN_YEAR, today.year - 5),
        maxYear = max(YearMonthDayPickerModel.DEFAULT_MAX_YEAR, today.year + 15)
      )
    )
  }

  AlertDialog(
    onDismissRequest = onDismissRequest,
    title = {
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
          text = "选择过期日期（年 / 月 / 日）",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold
        )
        Surface(
          color = MaterialTheme.colorScheme.primaryContainer,
          shape = RoundedCornerShape(8.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          Text(
            text = "已选：${pickerModel.selectedYear}年 ${pickerModel.selectedMonth}月 ${pickerModel.selectedDay}日 (${pickerModel.formattedDate()})",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            textAlign = TextAlign.Center,
            modifier = Modifier
              .fillMaxWidth()
              .padding(vertical = 8.dp, horizontal = 12.dp)
          )
        }
      }
    },
    text = {
      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        // 快捷保质期偏移按钮
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          val presets = listOf(
            "今天" to today,
            "+7天" to today.plusDays(7),
            "+30天" to today.plusDays(30),
            "+1年" to today.plusYears(1)
          )
          presets.forEach { (label, targetDate) ->
            val isSelected = pickerModel.toLocalDate() == targetDate
            FilterChip(
              selected = isSelected,
              onClick = {
                pickerModel = YearMonthDayPickerModel.fromLocalDate(
                  initialDate = targetDate,
                  minYear = pickerModel.minYear,
                  maxYear = max(pickerModel.maxYear, targetDate.year)
                )
              },
              label = {
                Text(
                  text = label,
                  style = MaterialTheme.typography.labelSmall
                )
              },
              modifier = Modifier.weight(1f)
            )
          }
        }

        // 三列标题栏：年 | 月 | 日
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .background(
              color = MaterialTheme.colorScheme.surfaceVariant,
              shape = RoundedCornerShape(8.dp)
            )
            .padding(vertical = 6.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "年",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1.2f)
          )
          Text(
            text = "月",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f)
          )
          Text(
            text = "日",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f)
          )
        }

        // 三列联动滚动选择区：年列 | 月列 | 日列
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .height(240.dp),
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          // 第一列：年
          PickerColumn(
            items = pickerModel.availableYears,
            selectedItem = pickerModel.selectedYear,
            itemLabel = { "${it}年" },
            onSelectItem = { year -> pickerModel = pickerModel.selectYear(year) },
            modifier = Modifier
              .weight(1.2f)
              .fillMaxHeight()
          )

          // 第二列：月
          PickerColumn(
            items = pickerModel.availableMonths,
            selectedItem = pickerModel.selectedMonth,
            itemLabel = { "${it}月" },
            onSelectItem = { month -> pickerModel = pickerModel.selectMonth(month) },
            modifier = Modifier
              .weight(1f)
              .fillMaxHeight()
          )

          // 第三列：日
          PickerColumn(
            items = pickerModel.availableDays,
            selectedItem = pickerModel.selectedDay,
            itemLabel = { "${it}日" },
            onSelectItem = { day -> pickerModel = pickerModel.selectDay(day) },
            modifier = Modifier
              .weight(1f)
              .fillMaxHeight()
          )
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          onDateSelected(pickerModel.toLocalDate())
        }
      ) {
        Text("确定")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismissRequest) {
        Text("取消")
      }
    }
  )
}

/**
 * 单列可滚动选择器组件，自动将当前选中项滚动至可视区域中心并高亮。
 */
@Composable
private fun PickerColumn(
  items: List<Int>,
  selectedItem: Int,
  itemLabel: (Int) -> String,
  onSelectItem: (Int) -> Unit,
  modifier: Modifier = Modifier
) {
  val listState = rememberLazyListState()

  LaunchedEffect(items, selectedItem) {
    val selectedIndex = items.indexOf(selectedItem)
    if (selectedIndex >= 0) {
      // 向上偏移 2 行使选中项尽量处于列表中间位置
      val targetScrollIndex = max(0, selectedIndex - 2)
      listState.scrollToItem(targetScrollIndex)
    }
  }

  Surface(
    modifier = modifier,
    shape = RoundedCornerShape(10.dp),
    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
  ) {
    LazyColumn(
      state = listState,
      contentPadding = PaddingValues(vertical = 8.dp, horizontal = 4.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
      items(
        items = items,
        key = { it }
      ) { value ->
        val isSelected = value == selectedItem
        Surface(
          onClick = { onSelectItem(value) },
          shape = RoundedCornerShape(8.dp),
          color = if (isSelected) {
            MaterialTheme.colorScheme.primary
          } else {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.01f)
          },
          modifier = Modifier.fillMaxWidth()
        ) {
          Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
              .fillMaxWidth()
              .padding(vertical = 10.dp, horizontal = 4.dp)
          ) {
            Text(
              text = itemLabel(value),
              style = MaterialTheme.typography.bodyMedium,
              fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
              color = if (isSelected) {
                MaterialTheme.colorScheme.onPrimary
              } else {
                MaterialTheme.colorScheme.onSurface
              },
              textAlign = TextAlign.Center
            )
          }
        }
      }
    }
  }
}

