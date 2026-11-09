package com.example.mylist.ui.main

import androidx.activity.ComponentActivity
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
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
  fun itemAndSummaryDashboard_areDisplayed() {
    composeTestRule.onNodeWithText("我的已购商品清单").assertExists()
    composeTestRule.onNodeWithText("有机纯牛奶").assertExists()
    composeTestRule.onNodeWithText("剩余 3 天").assertExists()
  }
}
