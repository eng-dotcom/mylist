package com.example.mylist.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mylist.data.repository.PurchasedItemRepository
import com.example.mylist.domain.model.PurchasedItem
import com.example.mylist.domain.query.CategoryGroup
import com.example.mylist.domain.query.ItemQueryEngine
import com.example.mylist.domain.query.SortOption
import com.example.mylist.domain.query.StatusFilter
import com.example.mylist.domain.query.SummaryStats
import com.example.mylist.domain.validation.PurchasedItemValidator
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * 已购商品主界面 UI 状态封装。
 */
data class PurchasedItemUiState(
  val items: List<PurchasedItem> = emptyList(),
  val categoryGroups: List<CategoryGroup> = ItemQueryEngine.groupByCategory(items),
  val isGroupedByCategory: Boolean = true,
  val expandedItemIds: Set<Long> = emptySet(),
  val collapsedCategories: Set<String> = emptySet(),
  val allItemsCount: Int = 0,
  val searchQuery: String = "",
  val selectedCategory: String? = null,
  val availableCategories: List<String> = PurchasedItem.PRESET_CATEGORIES,
  val statusFilter: StatusFilter = StatusFilter.ALL,
  val sortOption: SortOption = SortOption.EXPIRATION_ASC,
  val summaryStats: SummaryStats = SummaryStats(),
  val pendingUndoItem: PurchasedItem? = null,
  val errorMessage: String? = null,
  val isLoading: Boolean = false
)

/**
 * 已购商品管理主视图模型，负责协调仓库层数据流、搜索/分类与状态筛选/按类别分组展示/多维排序、顶部统计计算与 Undo 撤销交互。
 */
class PurchasedItemViewModel(
  private val repository: PurchasedItemRepository,
  private val todayProvider: () -> LocalDate = { LocalDate.now() }
) : ViewModel() {

  private val searchQueryFlow = MutableStateFlow("")
  private val categoryFilterFlow = MutableStateFlow<String?>(null)
  private val groupByCategoryFlow = MutableStateFlow(true)
  private val expansionStateFlow = MutableStateFlow(ItemExpansionState())
  private val statusFilterFlow = MutableStateFlow(StatusFilter.ALL)
  private val sortOptionFlow = MutableStateFlow(SortOption.EXPIRATION_ASC)
  private val pendingUndoFlow = MutableStateFlow<PurchasedItem?>(null)
  private val errorFlow = MutableStateFlow<String?>(null)

  private val _uiState = MutableStateFlow(PurchasedItemUiState(isLoading = true))
  val uiState: StateFlow<PurchasedItemUiState> = _uiState.asStateFlow()

  init {
    viewModelScope.launch {
      combine(
        repository.getAllItemsStream(),
        combine(searchQueryFlow, categoryFilterFlow, groupByCategoryFlow) { q, cat, grouped ->
          Triple(q, cat, grouped)
        },
        combine(statusFilterFlow, sortOptionFlow) { filter, sort -> filter to sort },
        expansionStateFlow,
        combine(pendingUndoFlow, errorFlow) { undo, err -> undo to err }
      ) { allItems, (query, categoryFilter, isGrouped), (filter, sort), expansionState, (undoItem, errorMsg) ->
        val today = todayProvider()
        val availableCategories = ItemQueryEngine.extractAvailableCategories(
          items = allItems,
          includePresets = true
        )
        val filteredAndSorted = ItemQueryEngine.filterAndSort(
          items = allItems,
          searchQuery = query,
          statusFilter = filter,
          sortOption = sort,
          today = today,
          categoryFilter = categoryFilter
        )
        val categoryGroups = ItemQueryEngine.groupByCategory(
          items = filteredAndSorted,
          today = today
        )
        val stats = ItemQueryEngine.computeSummaryStats(
          items = allItems,
          today = today
        )
        PurchasedItemUiState(
          items = filteredAndSorted,
          categoryGroups = categoryGroups,
          isGroupedByCategory = isGrouped,
          expandedItemIds = expansionState.expandedItemIds,
          collapsedCategories = expansionState.collapsedCategories,
          allItemsCount = allItems.size,
          searchQuery = query,
          selectedCategory = categoryFilter,
          availableCategories = availableCategories,
          statusFilter = filter,
          sortOption = sort,
          summaryStats = stats,
          pendingUndoItem = undoItem,
          errorMessage = errorMsg,
          isLoading = false
        )
      }.collect { newState ->
        _uiState.value = newState
      }
    }
  }

  fun updateSearchQuery(query: String) {
    searchQueryFlow.value = query
  }

  fun updateCategoryFilter(category: String?) {
    categoryFilterFlow.value = category?.takeIf { it.isNotBlank() }
  }

  fun toggleGroupByCategory() {
    groupByCategoryFlow.value = !groupByCategoryFlow.value
  }

  fun setGroupByCategory(grouped: Boolean) {
    groupByCategoryFlow.value = grouped
  }

  /**
   * 点击某个商品栏时，切换该商品详情区（过期日期、金额、数量、操作按钮）的展开/收起状态。
   */
  fun toggleItemExpanded(itemId: Long) {
    expansionStateFlow.value = expansionStateFlow.value.toggleItem(itemId)
  }

  /**
   * 点击某个大类标题栏时，切换该大类的折叠/展开状态；若收起大类则同时收起该大类下已展开的商品详情。
   */
  fun toggleCategoryCollapsed(category: String) {
    val itemIdsInCategory = _uiState.value.items
      .filter { it.category.equals(category.trim(), ignoreCase = true) }
      .map { it.id }
    expansionStateFlow.value = expansionStateFlow.value.toggleCategory(
      category = category,
      itemsInCategory = itemIdsInCategory
    )
  }

  fun updateStatusFilter(filter: StatusFilter) {
    statusFilterFlow.value = filter
  }

  fun updateSortOption(option: SortOption) {
    sortOptionFlow.value = option
  }

  fun clearError() {
    errorFlow.value = null
  }

  fun dismissUndo() {
    pendingUndoFlow.value = null
  }

  /**
   * 校验表单输入并新增商品（支持自定义分类或从已有分类选择）。
   */
  fun addItem(
    rawName: String,
    rawQuantity: String,
    rawAmount: String?,
    expirationDate: LocalDate?,
    rawCategory: String? = null
  ): Boolean {
    val validation = PurchasedItemValidator.validateAndCreate(
      rawName = rawName,
      rawCategory = rawCategory,
      rawQuantity = rawQuantity,
      rawAmount = rawAmount,
      expirationDate = expirationDate
    )
    if (!validation.isSuccess) {
      errorFlow.value = validation.errorMessage
      return false
    }

    val validItem = validation.getOrNull()!!
    errorFlow.value = null
    viewModelScope.launch {
      repository.addItem(
        name = validItem.name,
        category = validItem.category,
        quantity = validItem.quantity,
        amount = validItem.amount,
        expirationDate = validItem.expirationDate
      ).onFailure { e ->
        errorFlow.value = e.message ?: "添加商品失败"
      }
    }
    return true
  }

  /**
   * 校验表单输入并更新已有商品（含商品分类）。
   */
  fun updateItem(
    id: Long,
    rawName: String,
    rawQuantity: String,
    rawAmount: String?,
    expirationDate: LocalDate?,
    createdAtEpochMillis: Long,
    rawCategory: String? = null
  ): Boolean {
    val validation = PurchasedItemValidator.validateAndCreate(
      id = id,
      rawName = rawName,
      rawCategory = rawCategory,
      rawQuantity = rawQuantity,
      rawAmount = rawAmount,
      expirationDate = expirationDate,
      createdAtEpochMillis = createdAtEpochMillis
    )
    if (!validation.isSuccess) {
      errorFlow.value = validation.errorMessage
      return false
    }

    val validItem = validation.getOrNull()!!
    errorFlow.value = null
    viewModelScope.launch {
      repository.updateItem(validItem).onFailure { e ->
        errorFlow.value = e.message ?: "更新商品失败"
      }
    }
    return true
  }

  /**
   * 列表项快捷 +1 数量。
   */
  fun incrementQuantity(item: PurchasedItem) {
    viewModelScope.launch {
      repository.adjustQuantity(id = item.id, delta = 1).onFailure { e ->
        errorFlow.value = e.message
      }
    }
  }

  /**
   * 列表项快捷 -1 数量。若当前数量为 1，继续减 1 会直接删除该商品并进入可撤销（Undo）状态。
   */
  fun decrementQuantity(item: PurchasedItem) {
    if (item.quantity <= 1) {
      deleteItem(item)
      return
    }
    viewModelScope.launch {
      repository.adjustQuantity(id = item.id, delta = -1).onFailure { e ->
        errorFlow.value = e.message
      }
    }
  }

  /**
   * 删除商品并记录待撤销状态以弹出底部 Snackbar。
   */
  fun deleteItem(item: PurchasedItem) {
    expansionStateFlow.value = expansionStateFlow.value.removeItem(item.id)
    viewModelScope.launch {
      repository.deleteItem(item)
        .onSuccess {
          pendingUndoFlow.value = item
        }
        .onFailure { e ->
          errorFlow.value = e.message ?: "删除商品失败"
        }
    }
  }

  /**
   * 撤销最近一次删除操作，将商品恢复回列表。
   */
  fun undoDelete() {
    val itemToRestore = pendingUndoFlow.value ?: return
    viewModelScope.launch {
      repository.restoreItem(itemToRestore)
        .onSuccess {
          pendingUndoFlow.value = null
        }
        .onFailure { e ->
          errorFlow.value = e.message ?: "恢复商品失败"
        }
    }
  }
}
