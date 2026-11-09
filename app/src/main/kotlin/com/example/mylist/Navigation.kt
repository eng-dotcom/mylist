package com.example.mylist

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.mylist.data.local.AppDatabase
import com.example.mylist.data.repository.DefaultPurchasedItemRepository
import com.example.mylist.ui.main.MainScreen
import com.example.mylist.ui.main.PurchasedItemViewModel

@Composable
fun MainNavigation() {
  val context = LocalContext.current.applicationContext
  val repository = remember(context) {
    val dao = AppDatabase.getInstance(context).purchasedItemDao()
    DefaultPurchasedItemRepository(dao)
  }
  val backStack = rememberNavBackStack(Main)

  NavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },
    entryProvider = entryProvider {
      entry<Main> {
        val viewModel: PurchasedItemViewModel = viewModel {
          PurchasedItemViewModel(repository = repository)
        }
        MainScreen(viewModel = viewModel)
      }
    }
  )
}
