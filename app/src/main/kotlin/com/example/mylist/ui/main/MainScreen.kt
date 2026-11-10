package com.example.mylist.ui.main

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mylist.domain.model.ExpirationStatus
import com.example.mylist.domain.model.PurchasedItem
import com.example.mylist.domain.query.CategoryGroup
import com.example.mylist.domain.query.ItemQueryEngine
import com.example.mylist.domain.query.SortOption
import com.example.mylist.domain.query.StatusFilter
import com.example.mylist.domain.query.SummaryStats
import com.example.mylist.ui.theme.MyListTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 已购商品主屏幕（连接 [PurchasedItemViewModel] 状态流）。
 */
@Composable
fun MainScreen(
  viewModel: PurchasedItemViewModel,
  modifier: Modifier = Modifier
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val snackbarHostState = remember { SnackbarHostState() }
  var activeFormState by remember { mutableStateOf<ItemFormState?>(null) }

  // 监听待撤销删除商品，弹出底部 Snackbar 支持一键 Undo
  LaunchedEffect(uiState.pendingUndoItem) {
    val deletedItem = uiState.pendingUndoItem ?: return@LaunchedEffect
    val result = snackbarHostState.showSnackbar(
      message = "已删除「${deletedItem.name}」",
      actionLabel = "撤销",
      duration = SnackbarDuration.Short
    )
    if (result == SnackbarResult.ActionPerformed) {
      viewModel.undoDelete()
    } else {
      viewModel.dismissUndo()
    }
  }

  MainScreenContent(
    uiState = uiState,
    snackbarHostState = snackbarHostState,
    onSearchQueryChange = viewModel::updateSearchQuery,
    onCategoryFilterChange = viewModel::updateCategoryFilter,
    onToggleGroupByCategory = viewModel::toggleGroupByCategory,
    onToggleItemExpanded = viewModel::toggleItemExpanded,
    onToggleCategoryCollapsed = viewModel::toggleCategoryCollapsed,
    onStatusFilterChange = viewModel::updateStatusFilter,
    onSortOptionChange = viewModel::updateSortOption,
    onAddClick = {
      activeFormState = ItemFormState.forNewItem()
    },
    onEditItem = { item ->
      activeFormState = ItemFormState.fromExistingItem(item)
    },
    onDeleteItem = viewModel::deleteItem,
    onIncrementQuantity = viewModel::incrementQuantity,
    onDecrementQuantity = viewModel::decrementQuantity,
    modifier = modifier
  )

  activeFormState?.let { currentForm ->
    AddEditItemDialog(
      formState = currentForm,
      availableCategories = uiState.availableCategories,
      onFormChange = { activeFormState = it },
      onDismiss = {
        viewModel.clearError()
        activeFormState = null
      },
      onConfirm = {
        val validated = currentForm.validate()
        if (validated.errorMessage != null) {
          activeFormState = validated
        } else {
          val success = if (currentForm.isEditMode) {
            viewModel.updateItem(
              id = currentForm.editingItemId!!,
              rawName = currentForm.name,
              rawCategory = currentForm.category,
              rawQuantity = currentForm.quantity,
              rawAmount = currentForm.amount,
              expirationDate = currentForm.expirationDate,
              createdAtEpochMillis = currentForm.createdAtEpochMillis ?: System.currentTimeMillis()
            )
          } else {
            viewModel.addItem(
              rawName = currentForm.name,
              rawCategory = currentForm.category,
              rawQuantity = currentForm.quantity,
              rawAmount = currentForm.amount,
              expirationDate = currentForm.expirationDate
            )
          }
          if (success) {
            activeFormState = null
          }
        }
      }
    )
  }
}

/**
 * 纯状态渲染的主界面内容组件，使用 [BoxWithConstraints] 实现响应式屏幕自适应布局。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreenContent(
  uiState: PurchasedItemUiState,
  snackbarHostState: SnackbarHostState,
  onSearchQueryChange: (String) -> Unit,
  onStatusFilterChange: (StatusFilter) -> Unit,
  onSortOptionChange: (SortOption) -> Unit,
  onAddClick: () -> Unit,
  onEditItem: (PurchasedItem) -> Unit,
  onDeleteItem: (PurchasedItem) -> Unit,
  onIncrementQuantity: (PurchasedItem) -> Unit,
  onDecrementQuantity: (PurchasedItem) -> Unit,
  modifier: Modifier = Modifier,
  onCategoryFilterChange: (String?) -> Unit = {},
  onToggleGroupByCategory: () -> Unit = {},
  onToggleItemExpanded: ((Long) -> Unit)? = null,
  onToggleCategoryCollapsed: ((String) -> Unit)? = null,
  today: LocalDate = LocalDate.now()
) {
  var sortMenuExpanded by remember { mutableStateOf(false) }
  var localExpansionState by remember { mutableStateOf(ItemExpansionState()) }

  val effectiveExpandedItemIds = if (onToggleItemExpanded != null) {
    uiState.expandedItemIds
  } else {
    localExpansionState.expandedItemIds
  }

  val effectiveCollapsedCategories = if (onToggleCategoryCollapsed != null) {
    uiState.collapsedCategories
  } else {
    localExpansionState.collapsedCategories
  }

  val handleToggleItem: (Long) -> Unit = { itemId ->
    if (onToggleItemExpanded != null) {
      onToggleItemExpanded(itemId)
    } else {
      localExpansionState = localExpansionState.toggleItem(itemId)
    }
  }

  val handleToggleCategory: (CategoryGroup) -> Unit = { group ->
    if (onToggleCategoryCollapsed != null) {
      onToggleCategoryCollapsed(group.category)
    } else {
      localExpansionState = localExpansionState.toggleCategory(
        category = group.category,
        itemsInCategory = group.items.map { it.id }
      )
    }
  }

  Scaffold(
    modifier = modifier.fillMaxSize(),
    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    topBar = {
      TopAppBar(
        title = {
          Column {
            Text(
              text = "我的已购商品清单",
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.Bold
            )
            Text(
              text = "${if (uiState.isGroupedByCategory) "按类别分组 · " else ""}${uiState.sortOption.displayName}",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
            )
          }
        },
        actions = {
          TextButton(onClick = onToggleGroupByCategory) {
            Text(
              text = if (uiState.isGroupedByCategory) "▣ 分类视图" else "☰ 列表视图",
              color = MaterialTheme.colorScheme.onPrimaryContainer,
              fontWeight = FontWeight.SemiBold
            )
          }
          Box {
            TextButton(onClick = { sortMenuExpanded = true }) {
              Text(
                text = "排序 ▾",
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.SemiBold
              )
            }
            DropdownMenu(
              expanded = sortMenuExpanded,
              onDismissRequest = { sortMenuExpanded = false }
            ) {
              SortOption.entries.forEach { option ->
                DropdownMenuItem(
                  text = {
                    Text(
                      text = if (option == uiState.sortOption) "✓ ${option.displayName}" else option.displayName,
                      fontWeight = if (option == uiState.sortOption) FontWeight.Bold else FontWeight.Normal
                    )
                  },
                  onClick = {
                    onSortOptionChange(option)
                    sortMenuExpanded = false
                  }
                )
              }
            }
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = MaterialTheme.colorScheme.primaryContainer,
          titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
      )
    },
    floatingActionButton = {
      FloatingActionButton(
        onClick = onAddClick,
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary
      ) {
        Text(
          text = "＋ 新增",
          modifier = Modifier.padding(horizontal = 16.dp),
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.Bold
        )
      }
    }
  ) { innerPadding ->
    BoxWithConstraints(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
    ) {
      val layoutConfig = AdaptiveLayoutConfig.fromScreenWidthDp(maxWidth.value.toInt())
      val horizontalPadding = layoutConfig.horizontalPaddingDp.dp
      val spacing = layoutConfig.cardSpacingDp.dp

      LazyVerticalGrid(
        columns = GridCells.Fixed(layoutConfig.gridColumns),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
          start = horizontalPadding,
          end = horizontalPadding,
          top = 12.dp,
          bottom = 96.dp
        ),
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalArrangement = Arrangement.spacedBy(spacing)
      ) {
        // 1. 顶部统计概览卡片（跨全部列）
        item(span = { GridItemSpan(maxLineSpan) }) {
          SummaryDashboardCard(
            stats = uiState.summaryStats,
            isWideScreen = layoutConfig.category != ScreenSizeCategory.COMPACT
          )
        }

        // 2. 搜索栏、状态与分类快捷筛选（跨全部列）
        item(span = { GridItemSpan(maxLineSpan) }) {
          SearchAndFilterSection(
            searchQuery = uiState.searchQuery,
            selectedCategory = uiState.selectedCategory,
            availableCategories = uiState.availableCategories,
            isGroupedByCategory = uiState.isGroupedByCategory,
            selectedFilter = uiState.statusFilter,
            summaryStats = uiState.summaryStats,
            onSearchQueryChange = onSearchQueryChange,
            onCategoryFilterChange = onCategoryFilterChange,
            onToggleGroupByCategory = onToggleGroupByCategory,
            onStatusFilterChange = onStatusFilterChange
          )
        }

        // 3. 商品列表（支持按类别分组展示或平铺展示）或空状态提示
        if (uiState.items.isEmpty()) {
          item(span = { GridItemSpan(maxLineSpan) }) {
            EmptyStateCard(
              hasAnyItemsInDb = uiState.allItemsCount > 0,
              onAddClick = onAddClick,
              onResetFilters = {
                onSearchQueryChange("")
                onCategoryFilterChange(null)
                onStatusFilterChange(StatusFilter.ALL)
              }
            )
          }
        } else if (uiState.isGroupedByCategory) {
          val groups = uiState.categoryGroups.ifEmpty {
            ItemQueryEngine.groupByCategory(uiState.items, today)
          }
          groups.forEach { group ->
            val isCollapsed = effectiveCollapsedCategories.contains(group.category)
            item(
              key = "category_header_${group.category}",
              span = { GridItemSpan(maxLineSpan) }
            ) {
              CategoryGroupHeader(
                group = group,
                isCollapsed = isCollapsed,
                onToggleCollapse = { handleToggleCategory(group) }
              )
            }
            if (!isCollapsed) {
              items(
                items = group.items,
                key = { it.id }
              ) { item ->
                PurchasedItemCard(
                  item = item,
                  today = today,
                  isExpanded = effectiveExpandedItemIds.contains(item.id),
                  onToggleExpand = { handleToggleItem(item.id) },
                  onIncrement = { onIncrementQuantity(item) },
                  onDecrement = { onDecrementQuantity(item) },
                  onEdit = { onEditItem(item) },
                  onDelete = { onDeleteItem(item) }
                )
              }
            }
          }
        } else {
          items(
            items = uiState.items,
            key = { it.id }
          ) { item ->
            PurchasedItemCard(
              item = item,
              today = today,
              isExpanded = effectiveExpandedItemIds.contains(item.id),
              onToggleExpand = { handleToggleItem(item.id) },
              onIncrement = { onIncrementQuantity(item) },
              onDecrement = { onDecrementQuantity(item) },
              onEdit = { onEditItem(item) },
              onDelete = { onDeleteItem(item) }
            )
          }
        }
      }
    }
  }
}

/**
 * 按类别展示时的分类分组标题栏（显示分类名称、分类下条目数与总件数、临期/过期高亮徽章，支持点击折叠/展开）。
 */
@Composable
fun CategoryGroupHeader(
  group: CategoryGroup,
  isCollapsed: Boolean,
  onToggleCollapse: () -> Unit,
  modifier: Modifier = Modifier
) {
  Surface(
    onClick = onToggleCollapse,
    modifier = modifier
      .fillMaxWidth()
      .padding(top = 4.dp),
    shape = RoundedCornerShape(12.dp),
    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.75f)
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 14.dp, vertical = 10.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Text(
          text = if (isCollapsed) "▸" else "▾",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSecondaryContainer
        )
        Text(
          text = group.category,
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSecondaryContainer
        )
        Text(
          text = "${group.itemCount} 项 · 共 ${group.totalQuantity} 件",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
        )
      }

      Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        if (group.expiredCount > 0) {
          Surface(
            color = Color(0xFFFFEBEE),
            shape = RoundedCornerShape(999.dp)
          ) {
            Text(
              text = "已过期 ${group.expiredCount}",
              color = Color(0xFFC62828),
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.Bold,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
            )
          }
        }
        if (group.expiringSoonCount > 0) {
          Surface(
            color = Color(0xFFFFF3E0),
            shape = RoundedCornerShape(999.dp)
          ) {
            Text(
              text = "临期 ${group.expiringSoonCount}",
              color = Color(0xFFE65100),
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.Bold,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
            )
          }
        }
      }
    }
  }
}

/**
 * 顶部统计概览卡片：展示商品总数、临期数、已过期数、以及已填写金额总计。
 */
@Composable
fun SummaryDashboardCard(
  stats: SummaryStats,
  isWideScreen: Boolean,
  modifier: Modifier = Modifier
) {
  Card(
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)
    )
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "库存与花费概览",
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSecondaryContainer
        )
        Text(
          text = "共 ${stats.totalItemCount} 项 (${stats.totalQuantityCount} 件)",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSecondaryContainer
        )
      }

      if (isWideScreen) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          SummaryMetricItem(
            label = "商品总件数",
            value = "${stats.totalQuantityCount}",
            valueColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f)
          )
          SummaryMetricItem(
            label = "7天内临期",
            value = "${stats.expiringSoonCount}",
            valueColor = Color(0xFFE65100),
            modifier = Modifier.weight(1f)
          )
          SummaryMetricItem(
            label = "已过期",
            value = "${stats.expiredCount}",
            valueColor = Color(0xFFC62828),
            modifier = Modifier.weight(1f)
          )
          SummaryMetricItem(
            label = "记录总金额",
            value = stats.formattedTotalSpent(),
            valueColor = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.weight(1f)
          )
        }
      } else {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          SummaryMetricItem(
            label = "7天内临期",
            value = "${stats.expiringSoonCount} 项",
            valueColor = Color(0xFFE65100),
            modifier = Modifier.weight(1f)
          )
          SummaryMetricItem(
            label = "已过期",
            value = "${stats.expiredCount} 项",
            valueColor = Color(0xFFC62828),
            modifier = Modifier.weight(1f)
          )
          SummaryMetricItem(
            label = "记录总金额",
            value = stats.formattedTotalSpent(),
            valueColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1.2f)
          )
        }
      }
    }
  }
}

@Composable
private fun SummaryMetricItem(
  label: String,
  value: String,
  valueColor: Color,
  modifier: Modifier = Modifier
) {
  Surface(
    modifier = modifier,
    shape = RoundedCornerShape(12.dp),
    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
      horizontalAlignment = Alignment.Start
    ) {
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
      Spacer(modifier = Modifier.height(2.dp))
      Text(
        text = value,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = valueColor,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
    }
  }
}

/**
 * 搜索输入框、保质期状态过滤标签栏与分类快捷筛选栏。
 */
@Composable
fun SearchAndFilterSection(
  searchQuery: String,
  selectedCategory: String?,
  availableCategories: List<String>,
  isGroupedByCategory: Boolean,
  selectedFilter: StatusFilter,
  summaryStats: SummaryStats,
  onSearchQueryChange: (String) -> Unit,
  onCategoryFilterChange: (String?) -> Unit,
  onToggleGroupByCategory: () -> Unit,
  onStatusFilterChange: (StatusFilter) -> Unit,
  modifier: Modifier = Modifier
) {
  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    OutlinedTextField(
      value = searchQuery,
      onValueChange = onSearchQueryChange,
      modifier = Modifier.fillMaxWidth(),
      singleLine = true,
      shape = RoundedCornerShape(12.dp),
      label = { Text("搜索已购商品名称或分类") },
      placeholder = { Text("输入名称或分类关键字...") },
      trailingIcon = {
        if (searchQuery.isNotEmpty()) {
          TextButton(onClick = { onSearchQueryChange("") }) {
            Text("清空")
          }
        }
      }
    )

    // 保质期状态过滤标签行
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      StatusFilter.entries.forEach { filter ->
        val count = when (filter) {
          StatusFilter.ALL -> summaryStats.totalItemCount
          StatusFilter.EXPIRING_SOON -> summaryStats.expiringSoonCount
          StatusFilter.EXPIRED -> summaryStats.expiredCount
          StatusFilter.NORMAL -> summaryStats.normalCount
        }
        val selected = filter == selectedFilter
        FilterChip(
          selected = selected,
          onClick = { onStatusFilterChange(filter) },
          label = {
            Text(
              text = "${filter.displayName} ($count)",
              fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
            )
          },
          colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
          )
        )
      }
    }

    // 分类快捷过滤与按类别分组开关行
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      FilterChip(
        selected = isGroupedByCategory,
        onClick = onToggleGroupByCategory,
        label = {
          Text(
            text = if (isGroupedByCategory) "✓ 按类别分组" else "按类别分组",
            fontWeight = if (isGroupedByCategory) FontWeight.Bold else FontWeight.Normal
          )
        },
        colors = FilterChipDefaults.filterChipColors(
          selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
          selectedLabelColor = MaterialTheme.colorScheme.onTertiaryContainer
        )
      )
      Text(
        text = "| 分类筛选：",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
      FilterChip(
        selected = selectedCategory == null,
        onClick = { onCategoryFilterChange(null) },
        label = { Text("全部分类") }
      )
      availableCategories.forEach { category ->
        val isSelected = selectedCategory == category
        FilterChip(
          selected = isSelected,
          onClick = {
            onCategoryFilterChange(if (isSelected) null else category)
          },
          label = { Text(category) }
        )
      }
    }
  }
}

/**
 * 单个已购商品卡片组件：
 * - 默认收起态（`isExpanded = false`）：仅展示**商品名称**和**剩余过期天数（或已过期天数）**，隐藏过期日期、金额、数量及操作按钮。
 * - 点击展开态（`isExpanded = true`）：点击商品栏后展开显示分类、过期日期、购买金额、数量快捷 `−`/`＋` 按钮以及编辑/删除操作；再次点击商品栏收起。
 */
@Composable
fun PurchasedItemCard(
  item: PurchasedItem,
  today: LocalDate,
  onIncrement: () -> Unit,
  onDecrement: () -> Unit,
  onEdit: () -> Unit,
  onDelete: () -> Unit,
  modifier: Modifier = Modifier,
  isExpanded: Boolean = false,
  onToggleExpand: () -> Unit = {}
) {
  val displayModel = ItemCardDisplayModel.fromItem(
    item = item,
    isExpanded = isExpanded,
    today = today
  )
  val (badgeBgColor, badgeTextColor) = when (displayModel.expirationStatus) {
    ExpirationStatus.EXPIRED -> Pair(
      Color(0xFFFFEBEE),
      Color(0xFFC62828)
    )
    ExpirationStatus.EXPIRING_SOON -> Pair(
      Color(0xFFFFF3E0),
      Color(0xFFE65100)
    )
    ExpirationStatus.NORMAL -> Pair(
      Color(0xFFE8F5E9),
      Color(0xFF2E7D32)
    )
  }

  Card(
    onClick = onToggleExpand,
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(14.dp),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surface
    ),
    elevation = CardDefaults.cardElevation(defaultElevation = if (displayModel.isExpanded) 3.dp else 1.dp)
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      // 默认常驻行：仅展示商品名称 + 剩余过期天数（或已过期天数）
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = displayModel.name,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Surface(
          color = badgeBgColor,
          shape = RoundedCornerShape(999.dp)
        ) {
          Text(
            text = displayModel.expirationBadgeText,
            color = badgeTextColor,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
          )
        }
      }

      // 展开详情区：只有点击商品栏展开后，才展示过期日期、金额、数量及编辑/删除按钮
      if (displayModel.isExpanded) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Surface(
              color = MaterialTheme.colorScheme.tertiaryContainer,
              shape = RoundedCornerShape(6.dp)
            ) {
              Text(
                text = displayModel.category,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
              )
            }
            Text(
              text = displayModel.expirationDateText.orEmpty(),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
          Text(
            text = displayModel.amountText.orEmpty(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (item.amount != null) FontWeight.SemiBold else FontWeight.Normal,
            color = if (item.amount != null) {
              MaterialTheme.colorScheme.primary
            } else {
              MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            }
          )
        }

        // 数量快捷加减与编辑/删除操作栏
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            Text(
              text = "数量：",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Surface(
              onClick = onDecrement,
              shape = CircleShape,
              color = MaterialTheme.colorScheme.secondaryContainer,
              modifier = Modifier.size(32.dp)
            ) {
              Box(contentAlignment = Alignment.Center) {
                Text(
                  text = "−",
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.onSecondaryContainer
                )
              }
            }
            Text(
              text = displayModel.quantityText.orEmpty(),
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              modifier = Modifier.padding(horizontal = 6.dp)
            )
            Surface(
              onClick = onIncrement,
              shape = CircleShape,
              color = MaterialTheme.colorScheme.primaryContainer,
              modifier = Modifier.size(32.dp)
            ) {
              Box(contentAlignment = Alignment.Center) {
                Text(
                  text = "＋",
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.onPrimaryContainer
                )
              }
            }
          }

          if (displayModel.showActions) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
              TextButton(onClick = onEdit) {
                Text("编辑")
              }
              TextButton(onClick = onDelete) {
                Text(
                  text = "删除",
                  color = MaterialTheme.colorScheme.error
                )
              }
            }
          }
        }
      }
    }
  }
}

/**
 * 列表为空时的引导卡片。
 */
@Composable
fun EmptyStateCard(
  hasAnyItemsInDb: Boolean,
  onAddClick: () -> Unit,
  onResetFilters: () -> Unit,
  modifier: Modifier = Modifier
) {
  Card(
    modifier = modifier
      .fillMaxWidth()
      .padding(vertical = 24.dp),
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    )
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      Text(
        text = if (hasAnyItemsInDb) "没有找到匹配的已购商品" else "还没有记录任何已购商品",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold
      )
      Text(
        text = if (hasAnyItemsInDb) {
          "可以尝试清空搜索词或切换分类/状态筛选标签"
        } else {
          "点击右下角「＋ 新增」按钮，开始管理商品的分类、数量、金额与过期日期"
        },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
      if (hasAnyItemsInDb) {
        OutlinedButton(onClick = onResetFilters) {
          Text("重置搜索与筛选")
        }
      } else {
        Button(onClick = onAddClick) {
          Text("＋ 立即添加第一件商品")
        }
      }
    }
  }
}

/**
 * 新增 / 编辑商品表单弹窗（支持输入新分类或从已有分类选择）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditItemDialog(
  formState: ItemFormState,
  availableCategories: List<String>,
  onFormChange: (ItemFormState) -> Unit,
  onDismiss: () -> Unit,
  onConfirm: () -> Unit
) {
  var showDatePicker by remember { mutableStateOf(false) }
  var categoryDropdownExpanded by remember { mutableStateOf(false) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(
        text = if (formState.isEditMode) "编辑已购商品" else "新增已购商品",
        fontWeight = FontWeight.Bold
      )
    },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        if (formState.errorMessage != null) {
          Surface(
            color = MaterialTheme.colorScheme.errorContainer,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            Text(
              text = formState.errorMessage,
              color = MaterialTheme.colorScheme.onErrorContainer,
              style = MaterialTheme.typography.bodySmall,
              modifier = Modifier.padding(10.dp)
            )
          }
        }

        OutlinedTextField(
          value = formState.name,
          onValueChange = { onFormChange(formState.copy(name = it, errorMessage = null)) },
          label = { Text("商品名称 *") },
          placeholder = { Text("例如：鲜牛奶、全麦面包") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )

        // 分类输入与已有分类选择区
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
              value = formState.category,
              onValueChange = { onFormChange(formState.updateCategory(it)) },
              label = { Text("商品分类（可输入新分类或选择已有）") },
              placeholder = { Text("例如：食品生鲜、饮品乳品") },
              singleLine = true,
              trailingIcon = {
                TextButton(onClick = { categoryDropdownExpanded = true }) {
                  Text("已有 ▾")
                }
              },
              modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focusState ->
                  if (focusState.isFocused) {
                    val updated = formState.onCategoryFocus()
                    if (updated != formState) {
                      onFormChange(updated)
                    }
                  }
                }
            )

            DropdownMenu(
              expanded = categoryDropdownExpanded,
              onDismissRequest = { categoryDropdownExpanded = false }
            ) {
              availableCategories.forEach { existingCat ->
                DropdownMenuItem(
                  text = { Text(existingCat) },
                  onClick = {
                    onFormChange(formState.updateCategory(existingCat))
                    categoryDropdownExpanded = false
                  }
                )
              }
            }
          }

          Row(
            modifier = Modifier
              .fillMaxWidth()
              .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = "快捷选择：",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            availableCategories.forEach { categoryOption ->
              val isSelected = formState.category.trim() == categoryOption
              FilterChip(
                selected = isSelected,
                onClick = {
                  onFormChange(formState.updateCategory(categoryOption))
                },
                label = { Text(categoryOption, style = MaterialTheme.typography.labelSmall) }
              )
            }
          }
        }

        OutlinedTextField(
          value = formState.quantity,
          onValueChange = { onFormChange(formState.updateQuantity(it)) },
          label = { Text("商品数量 (正整数) *") },
          placeholder = { Text("例如：1") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          singleLine = true,
          modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focusState ->
              if (focusState.isFocused) {
                val updated = formState.onQuantityFocus()
                if (updated != formState) {
                  onFormChange(updated)
                }
              }
            }
        )

        OutlinedTextField(
          value = formState.amount,
          onValueChange = { onFormChange(formState.copy(amount = it, errorMessage = null)) },
          label = { Text("购买金额 (选填，可不填)") },
          placeholder = { Text("例如：19.90，留空表示不填") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )

        Surface(
          onClick = { showDatePicker = true },
          shape = RoundedCornerShape(10.dp),
          color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column {
              Text(
                text = "过期日期 (年 / 月 / 日) *",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
              Text(
                text = "${formState.expirationDate.year}年${formState.expirationDate.monthValue}月${formState.expirationDate.dayOfMonth}日 (${
                  formState.expirationDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
                })",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
              )
            }
            FilledTonalButton(onClick = { showDatePicker = true }) {
              Text("选择日期")
            }
          }
        }
      }
    },
    confirmButton = {
      Button(onClick = onConfirm) {
        Text(if (formState.isEditMode) "保存修改" else "确认添加")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("取消")
      }
    }
  )

  if (showDatePicker) {
    YearMonthDayPickerDialog(
      initialDate = formState.expirationDate,
      onDismissRequest = { showDatePicker = false },
      onDateSelected = { selectedDate ->
        onFormChange(formState.copy(expirationDate = selectedDate, errorMessage = null))
        showDatePicker = false
      }
    )
  }
}

// ==================== Compose Previews ====================

private val previewToday = LocalDate.of(2026, 11, 9)
private val previewItems = listOf(
  PurchasedItem(
    id = 1L,
    name = "希腊酸奶",
    category = "饮品乳品",
    quantity = 2,
    amount = 18.50,
    expirationDate = previewToday.minusDays(1)
  ),
  PurchasedItem(
    id = 2L,
    name = "有机纯牛奶",
    category = "饮品乳品",
    quantity = 1,
    amount = 29.90,
    expirationDate = previewToday.plusDays(3)
  ),
  PurchasedItem(
    id = 3L,
    name = "苏打矿泉水",
    category = "日用百货",
    quantity = 6,
    amount = null,
    expirationDate = previewToday.plusDays(30)
  )
)

@Preview(name = "Phone Portrait (Compact)", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
fun MainScreenPhonePreview() {
  MyListTheme {
    MainScreenContent(
      uiState = PurchasedItemUiState(
        items = previewItems,
        allItemsCount = previewItems.size,
        summaryStats = ItemQueryEngine.computeSummaryStats(previewItems, previewToday)
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
      today = previewToday
    )
  }
}

@Preview(name = "Tablet Adaptive (Medium 2-Col)", showBackground = true, widthDp = 720, heightDp = 1024)
@Composable
fun MainScreenTabletPreview() {
  MyListTheme {
    MainScreenContent(
      uiState = PurchasedItemUiState(
        items = previewItems,
        allItemsCount = previewItems.size,
        summaryStats = ItemQueryEngine.computeSummaryStats(previewItems, previewToday)
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
      today = previewToday
    )
  }
}
