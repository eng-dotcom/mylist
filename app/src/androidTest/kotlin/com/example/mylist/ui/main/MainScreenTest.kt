package com.example.mylist.ui.main

import androidx.activity.ComponentActivity
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.mylist.domain.model.PurchasedItem
import com.example.mylist.domain.query.ItemQueryEngine
import com.example.mylist.ui.theme.MyListTheme
import java.time.LocalDate
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** UI tests for [com.example.mylist.ui.main.MainScreenContent]. */
class MainScreenTest {

  @get:Rule
  val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  private val sampleToday = LocalDate.of(2026, 11, 9)
  private val sampleItems = listOf(
    PurchasedItem(
      id = 1L,
      name = "有机纯牛奶",
      category = "饮品乳品",
      quantity = 2,
      amount = 25.0,
      expirationDate = sampleToday.plusDays(3)
    )
  )

  @Before
  fun setup() {
    composeTestRule.setContent {
      MyListTheme {
        MainScreenContent(
          uiState = PurchasedItemUiState(
            items = sampleItems,
            allItemsCount = sampleItems.size,
            summaryStats = ItemQueryEngine.computeSummaryStats(sampleItems, sampleToday)
          ),
          snackbarHostState = remember { SnackbarHostState() },
          onSearchQueryChange = {},
          onStatusFilterChange = {},
          onSortOptionChange = {},
          onAddClick = {},
          onEditItem = {},
          onDeleteItem = {},
          onIncrementQuantity = {},
          onDecrementQuantity = {},
          today = sampleToday
        )
      }
    }
  }

  @Test
  fun categoryIsCollapsedByDefault_andExpandsOnClick_thenItemExpandsOnClick() {
    composeTestRule.onNodeWithText("我的已购商品清单").assertExists()

    // 1. 分类永远默认折叠：商品名称初始不显示
    composeTestRule.onNodeWithText("有机纯牛奶").assertDoesNotExist()

    // 2. 点击分类标题展开该大类：仅显示商品名称与剩余天数，不显示过期日期与金额
    composeTestRule.onNodeWithText("1 项 · 共 2 件").performClick()
    composeTestRule.onNodeWithText("有机纯牛奶").assertExists()
    composeTestRule.onNodeWithText("剩余 3 天").assertExists()
    composeTestRule.onNodeWithText("过期日期：2026-11-12").assertDoesNotExist()
    composeTestRule.onNodeWithText("金额：¥25.00").assertDoesNotExist()

    // 3. 点击商品栏后展开完整详情
    composeTestRule.onNodeWithText("有机纯牛奶").performClick()
    composeTestRule.onNodeWithText("过期日期：2026-11-12").assertExists()
    composeTestRule.onNodeWithText("金额：¥25.00").assertExists()
  }
}
