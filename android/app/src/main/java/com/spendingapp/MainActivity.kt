package com.spendingapp

import android.Manifest
import android.app.Activity
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import android.content.pm.PackageManager
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.spendingapp.core.database.entity.AccountEntity
import com.spendingapp.core.database.entity.CategoryEntity
import com.spendingapp.core.database.entity.GoalEntity
import com.spendingapp.core.database.entity.TransactionEntity
import com.spendingapp.core.event.DomainEventType
import com.spendingapp.core.domain.BudgetCheckResult
import com.spendingapp.core.model.AccountType
import com.spendingapp.core.model.CategoryType
import com.spendingapp.core.model.GoalPriority
import com.spendingapp.core.model.GoalStatus
import com.spendingapp.core.model.TransactionStatus
import com.spendingapp.core.model.TransactionType
import com.spendingapp.core.repository.BackgroundSyncSettings
import com.spendingapp.core.repository.BudgetRepository
import com.spendingapp.core.repository.BudgetStatus
import com.spendingapp.core.repository.BudgetStatusSummary
import com.spendingapp.core.repository.CategoryMovementSummary
import com.spendingapp.core.repository.CategorySpendingSummary
import com.spendingapp.core.repository.BalanceTrendPoint
import com.spendingapp.core.repository.MonthComparisonSummary
import com.spendingapp.core.repository.MonthlyBarSummary
import com.spendingapp.core.repository.ThemeMode
import com.spendingapp.core.repository.ThemeSettings
import com.spendingapp.core.sync.ImportResult
import com.spendingapp.core.money.MoneyVnd
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        requestNotificationPermissionIfNeeded()
        (application as SpendingApplication).container.autoSyncCoordinator.syncOnAppOpen()
        setContent { SpendingAppRoot(initialTab = intent.notificationStartTab()) }
    }

    private fun requestNotificationPermissionIfNeeded() {
        requestPostNotificationsPermission(this)
    }

    private companion object {
        const val REQUEST_NOTIFICATION_PERMISSION = 1001
    }
}


const val EXTRA_START_TAB = "start_tab"

private fun hasPostNotificationsPermission(context: Context): Boolean {
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
}

private fun requestPostNotificationsPermission(activity: Activity) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    if (hasPostNotificationsPermission(activity)) return
    ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
}

private enum class MainTab(val label: String) {
    Dashboard("Tổng quan"), Transactions("Giao dịch"), Budgets("Hạn mức"), Goals("Mục tiêu"), Settings("Cài đặt")
}

private fun android.content.Intent.notificationStartTab(): MainTab = when (getStringExtra(EXTRA_START_TAB)) {
    MainTab.Budgets.name -> MainTab.Budgets
    MainTab.Goals.name -> MainTab.Goals
    MainTab.Settings.name -> MainTab.Settings
    MainTab.Transactions.name -> MainTab.Transactions
    else -> MainTab.Dashboard
}

@Composable
private fun SpendingAppRoot(initialTab: MainTab = MainTab.Dashboard) {
    MaterialTheme {
        var selectedTab by remember { mutableStateOf(initialTab) }
        Scaffold(
            bottomBar = {
                NavigationBar {
                    MainTab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = selectedTab == tab,
                            onClick = { selectedTab = tab },
                            icon = {},
                            label = { Text(tab.label) },
                        )
                    }
                }
            },
        ) { paddingValues ->
            Surface(Modifier.fillMaxSize().padding(paddingValues), color = AppColors.Background) {
                when (selectedTab) {
                    MainTab.Dashboard -> DashboardScreen(onAddTransaction = { selectedTab = MainTab.Transactions })
                    MainTab.Transactions -> TransactionsScreen()
                    MainTab.Budgets -> BudgetsScreen()
                    MainTab.Goals -> GoalsScreen()
                    MainTab.Settings -> SettingsScreen()
                }
            }
        }
    }
}

@Composable
private fun DashboardScreen(onAddTransaction: () -> Unit) {
    val application = LocalContext.current.applicationContext as SpendingApplication
    val summary by application.container.reportingRepository.observeDashboardSummary().collectAsState(initial = null)
    val accounts by application.container.accountRepository.observeAccounts().collectAsState(initial = emptyList())
    val syncStates by application.container.database.syncStateDao().observeAll().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var syncMessage by remember { mutableStateOf<String?>(null) }
    val dashboardSummary = summary

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.Background),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Xin chào 👋", style = MaterialTheme.typography.titleMedium, color = AppColors.TextSoft)
                Text("Sổ chi tiêu của bạn", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = AppColors.TextStrong)
            }
        }
        item {
            TotalBalanceCircleCard(totalBalance = dashboardSummary?.totalBalance?.coerceAtLeast(0) ?: 0L)
        }
        item {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = AppColors.Card)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Nguồn tiền", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = AppColors.TextStrong)
                        Text("${accounts.size} nguồn", color = AppColors.TextSoft, style = MaterialTheme.typography.bodySmall)
                    }
                    if (accounts.isEmpty()) {
                        Text("Chưa có nguồn tiền. Vào Cài đặt để thêm tiền mặt hoặc ngân hàng.", color = AppColors.TextSoft)
                    } else {
                        accounts.forEach { account -> AccountBalancePill(account) }
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                RoundStatCard("Thu", MoneyVnd(dashboardSummary?.incomeAmount ?: 0L).format(), 0.72f, AppColors.Mint, Modifier.weight(1f))
                RoundStatCard("Chi", MoneyVnd(dashboardSummary?.expenseAmount ?: 0L).format(), 0.48f, AppColors.Peach, Modifier.weight(1f))
                RoundStatCard("Chờ", "${dashboardSummary?.pendingCategoryCount ?: 0} GD", if ((dashboardSummary?.pendingCategoryCount ?: 0) > 0) 0.85f else 0.12f, AppColors.Lavender, Modifier.weight(1f))
            }
        }
        dashboardSummary?.let { summary ->
            item { MonthComparisonCard(summary.monthComparison) }
            item { DashboardInsightsCard(summary.categorySpending, summary.budgetStatuses) }
            item { ReportChartsCard(summary.categorySpending, summary.budgetStatuses, summary.monthlyBars, summary.balanceTrend, summary.featuredGoal) }
        }
        dashboardSummary?.featuredGoal?.let { goal ->
            item { GoalSummaryCard(goal) }
        }
        item {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = AppColors.Card)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Đồng bộ & giao dịch", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = AppColors.TextStrong)
                    Text("Đã lưu local: ${dashboardSummary?.transactionCount ?: 0} giao dịch", color = AppColors.TextSoft)
                    syncStates.firstOrNull { it.source == "sepay_api" }?.let { state ->
                        Text("SePay: ${state.status}${state.lastError?.let { " • $it" } ?: ""}", color = AppColors.TextSoft)
                    }
                    syncMessage?.let { Text(it, color = AppColors.TextStrong) }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CuteButton("Đồng bộ") {
                            val token = application.container.secureTokenStorage.getSePayToken()
                            if (token == null) {
                                syncMessage = "Vui lòng nhập token SePay trong Cài đặt"
                            } else {
                                syncMessage = "Đang đồng bộ..."
                                scope.launch {
                                    runCatching { application.container.sePaySyncService.sync(token) }
                                        .onSuccess { result -> syncMessage = "Xong: mới ${result.imported}, trùng ${result.duplicated}" }
                                        .onFailure { error -> syncMessage = error.message ?: "Đồng bộ thất bại" }
                                }
                            }
                        }
                        CuteButton("Thêm giao dịch", onAddTransaction)
                    }
                }
            }
        }
    }
}

@Composable
private fun TransactionsScreen() {
    val application = LocalContext.current.applicationContext as SpendingApplication
    val repository = application.container.transactionRepository
    val accounts by repository.observeAccounts().collectAsState(initial = emptyList())
    val categories by repository.observeCategories().collectAsState(initial = emptyList())
    val transactions by repository.observeTransactions().collectAsState(initial = emptyList())
    val goals by application.container.goalRepository.observeGoals().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var transactionMessage by remember { mutableStateOf<String?>(null) }
    var selectedTypeFilter by remember { mutableStateOf<TransactionType?>(null) }
    var selectedAccountFilterIndex by remember { mutableStateOf(0) }
    var selectedCategoryFilterIndex by remember { mutableStateOf(0) }
    var selectedStatusFilterIndex by remember { mutableStateOf(0) }
    var currentMonthOnly by remember { mutableStateOf(false) }
    val accountFilterOptions = listOf<AccountEntity?>(null) + accounts
    val categoryFilterOptions = listOf<CategoryEntity?>(null) + categories
    val statusFilterOptions = listOf<TransactionStatus?>(null) + TransactionStatus.entries
    val selectedAccountFilter = accountFilterOptions.getOrNull(selectedAccountFilterIndex.coerceIn(0, accountFilterOptions.lastIndex.coerceAtLeast(0)))
    val selectedCategoryFilter = categoryFilterOptions.getOrNull(selectedCategoryFilterIndex.coerceIn(0, categoryFilterOptions.lastIndex.coerceAtLeast(0)))
    val selectedStatusFilter = statusFilterOptions.getOrNull(selectedStatusFilterIndex.coerceIn(0, statusFilterOptions.lastIndex.coerceAtLeast(0)))
    val currentMonthRange = remember { currentMonthRangeMillis() }
    val filteredTransactions = transactions.filter { transaction ->
        (selectedTypeFilter == null || transaction.type == selectedTypeFilter) &&
            (selectedAccountFilter == null || transaction.accountId == selectedAccountFilter.id) &&
            (selectedCategoryFilter == null || transaction.categoryId == selectedCategoryFilter.id) &&
            (selectedStatusFilter == null || transaction.status == selectedStatusFilter) &&
            (!currentMonthOnly || transaction.occurredAt in currentMonthRange.first..currentMonthRange.second)
    }
    val income = filteredTransactions.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
    val expense = filteredTransactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.Background),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Giao dịch", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = AppColors.TextStrong)
                Text("Ghi lại khoản thu chi thật nhanh và dễ thương", color = AppColors.TextSoft)
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MiniMoneyCard("Đã thu", MoneyVnd(income).format(), AppColors.Mint, Modifier.weight(1f))
                MiniMoneyCard("Đã chi", MoneyVnd(expense).format(), AppColors.Peach, Modifier.weight(1f))
            }
        }
        item { AddTransactionCard(accounts = accounts, categories = categories, goals = goals) }
        item {
            TransactionFilterCard(
                selectedType = selectedTypeFilter,
                accountName = selectedAccountFilter?.name ?: "Tất cả nguồn",
                categoryName = selectedCategoryFilter?.name ?: "Tất cả hạng mục",
                statusName = selectedStatusFilter?.label() ?: "Tất cả trạng thái",
                currentMonthOnly = currentMonthOnly,
                onTypeSelected = { selectedTypeFilter = it },
                onPreviousAccount = { selectedAccountFilterIndex = (selectedAccountFilterIndex - 1).floorMod(accountFilterOptions.size) },
                onNextAccount = { selectedAccountFilterIndex = (selectedAccountFilterIndex + 1).floorMod(accountFilterOptions.size) },
                onPreviousCategory = { selectedCategoryFilterIndex = (selectedCategoryFilterIndex - 1).floorMod(categoryFilterOptions.size) },
                onNextCategory = { selectedCategoryFilterIndex = (selectedCategoryFilterIndex + 1).floorMod(categoryFilterOptions.size) },
                onPreviousStatus = { selectedStatusFilterIndex = (selectedStatusFilterIndex - 1).floorMod(statusFilterOptions.size) },
                onNextStatus = { selectedStatusFilterIndex = (selectedStatusFilterIndex + 1).floorMod(statusFilterOptions.size) },
                onToggleCurrentMonth = { currentMonthOnly = !currentMonthOnly },
                onClear = {
                    selectedTypeFilter = null
                    selectedAccountFilterIndex = 0
                    selectedCategoryFilterIndex = 0
                    selectedStatusFilterIndex = 0
                    currentMonthOnly = false
                },
            )
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Lịch sử gần đây", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = AppColors.TextStrong)
                Text("${filteredTransactions.size}/${transactions.size} giao dịch", color = AppColors.TextSoft, style = MaterialTheme.typography.bodySmall)
            }
            transactionMessage?.let { Text(it, color = AppColors.TextStrong, style = MaterialTheme.typography.bodyMedium) }
        }
        if (filteredTransactions.isEmpty()) {
            item { EmptyTransactionsCard(if (transactions.isEmpty()) "Thêm khoản chi đầu tiên để app bắt đầu thống kê cho bạn." else "Không có giao dịch khớp bộ lọc hiện tại.") }
        } else {
            items(filteredTransactions, key = { it.id }) { transaction ->
                TransactionRow(
                    transaction = transaction,
                    accounts = accounts,
                    categories = categories,
                    accountName = accounts.firstOrNull { it.id == transaction.accountId }?.name ?: "Nguồn #${transaction.accountId}",
                    categoryName = transaction.categoryId?.let { categoryId -> categories.firstOrNull { it.id == categoryId }?.name } ?: "Chưa phân loại",
                    linkedGoalName = transaction.linkedGoalId?.let { goalId -> goals.firstOrNull { it.id == goalId }?.name },
                    onSaveEdit = { accountId, categoryId, amount, note, occurredAt ->
                        scope.launch {
                            runCatching { repository.updateTransaction(transaction, accountId, categoryId, amount, note, occurredAt) }
                                .onSuccess { budgetResult ->
                                    application.container.localNotificationService.notifyBudgetResult(budgetResult)
                                    transactionMessage = if (transaction.status == TransactionStatus.PENDING_CATEGORY && categoryId != null) {
                                        budgetResult.budgetMessage("Đã phân loại giao dịch") ?: "Đã phân loại giao dịch"
                                    } else {
                                        "Đã cập nhật giao dịch"
                                    }
                                }
                                .onFailure { error -> transactionMessage = error.message ?: "Không thể sửa giao dịch" }
                        }
                    },
                    onIgnore = {
                        scope.launch {
                            runCatching { repository.ignoreTransaction(transaction) }
                                .onSuccess { transactionMessage = "Đã bỏ qua giao dịch khỏi báo cáo" }
                                .onFailure { error -> transactionMessage = error.message ?: "Không thể bỏ qua giao dịch" }
                        }
                    },
                    onUnlinkGoal = {
                        scope.launch {
                            runCatching { repository.unlinkGoal(transaction) }
                                .onSuccess { transactionMessage = "Đã gỡ mục tiêu khỏi giao dịch" }
                                .onFailure { error -> transactionMessage = error.message ?: "Không thể gỡ mục tiêu" }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun AddTransactionCard(accounts: List<AccountEntity>, categories: List<CategoryEntity>, goals: List<GoalEntity>) {
    val application = LocalContext.current.applicationContext as SpendingApplication
    val scope = rememberCoroutineScope()
    var type by remember { mutableStateOf(TransactionType.EXPENSE) }
    var selectedAccountIndex by remember { mutableStateOf(0) }
    var selectedCategoryIndex by remember { mutableStateOf(0) }
    var selectedGoalIndex by remember { mutableStateOf(0) }
    var selectedTimeIndex by remember { mutableStateOf(0) }
    var amountText by remember { mutableStateOf("") }
    var noteText by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    val timeOptions = remember { transactionTimeOptions() }

    val selectedAccount = accounts.getOrNull(selectedAccountIndex.coerceIn(0, (accounts.size - 1).coerceAtLeast(0)))
    val selectableCategories = categories.filter { category ->
        when (type) {
            TransactionType.INCOME -> category.type.name != "EXPENSE"
            TransactionType.EXPENSE -> category.type.name != "INCOME"
            else -> true
        }
    }
    val selectedCategory = selectableCategories.getOrNull(selectedCategoryIndex.coerceIn(0, (selectableCategories.size - 1).coerceAtLeast(0)))
    val activeGoalOptions = listOf<GoalEntity?>(null) + goals.filter { it.status == GoalStatus.ACTIVE }
    val selectedGoal = activeGoalOptions.getOrNull(selectedGoalIndex.coerceIn(0, activeGoalOptions.lastIndex.coerceAtLeast(0)))
    val canSave = selectedAccount != null && amountText.toLongOrNull()?.let { it > 0 } == true

    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = AppColors.Card)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Thêm giao dịch", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = AppColors.TextStrong)
                    Text("Lưu vào máy của bạn", color = AppColors.TextSoft, style = MaterialTheme.typography.bodySmall)
                }
                Text(if (type == TransactionType.EXPENSE) "🧾" else "🌱", style = MaterialTheme.typography.headlineSmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TransactionTypeChip("Chi", "−", type == TransactionType.EXPENSE, AppColors.Peach) {
                    type = TransactionType.EXPENSE
                    selectedCategoryIndex = 0
                }
                TransactionTypeChip("Thu", "+", type == TransactionType.INCOME, AppColors.Mint) {
                    type = TransactionType.INCOME
                    selectedCategoryIndex = 0
                    selectedGoalIndex = 0
                }
            }
            PickerRow("Nguồn tiền", selectedAccount?.name ?: "Chưa có nguồn tiền", { if (accounts.isNotEmpty()) selectedAccountIndex = (selectedAccountIndex - 1).floorMod(accounts.size) }, { if (accounts.isNotEmpty()) selectedAccountIndex = (selectedAccountIndex + 1).floorMod(accounts.size) })
            PickerRow("Hạng mục", selectedCategory?.name ?: "Chưa có hạng mục", { if (selectableCategories.isNotEmpty()) selectedCategoryIndex = (selectedCategoryIndex - 1).floorMod(selectableCategories.size) }, { if (selectableCategories.isNotEmpty()) selectedCategoryIndex = (selectedCategoryIndex + 1).floorMod(selectableCategories.size) })
            if (type == TransactionType.INCOME) {
                PickerRow("Gắn mục tiêu", selectedGoal?.name ?: "Không gắn mục tiêu", { selectedGoalIndex = (selectedGoalIndex - 1).floorMod(activeGoalOptions.size) }, { selectedGoalIndex = (selectedGoalIndex + 1).floorMod(activeGoalOptions.size) })
                Text("Nếu chọn mục tiêu, khoản thu này sẽ tự cộng vào tiến độ và không làm lệch số dư.", color = AppColors.TextSoft, style = MaterialTheme.typography.bodySmall)
            }
            OutlinedTextField(value = amountText, onValueChange = { amountText = it.filter(Char::isDigit) }, label = { Text("Số tiền VND") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
            PickerRow(
                "Thời gian",
                timeOptions.getOrNull(selectedTimeIndex)?.label ?: "Hôm nay",
                { selectedTimeIndex = (selectedTimeIndex - 1).floorMod(timeOptions.size) },
                { selectedTimeIndex = (selectedTimeIndex + 1).floorMod(timeOptions.size) },
            )
            OutlinedTextField(value = noteText, onValueChange = { noteText = it }, label = { Text("Ghi chú nhẹ nhàng") }, modifier = Modifier.fillMaxWidth())
            Button(
                enabled = canSave,
                onClick = {
                    val account = selectedAccount ?: return@Button
                    val amount = amountText.toLongOrNull() ?: return@Button
                    val occurredAt = timeOptions.getOrNull(selectedTimeIndex)?.timestampMillis ?: System.currentTimeMillis()
                    scope.launch {
                        runCatching {
                            application.container.transactionRepository.addManualTransaction(account.id, selectedCategory?.id, type, amount, noteText, occurredAt, if (type == TransactionType.INCOME) selectedGoal?.id else null)
                        }.onSuccess { result ->
                            if (result is ImportResult.Imported) {
                                application.container.localNotificationService.notifyBudgetResult(result.budgetCheckResult)
                                if (selectedGoal != null && selectedGoal.currentAmount + amount >= selectedGoal.targetAmount) {
                                    application.container.localNotificationService.notifyGoalCompleted(selectedGoal.name)
                                }
                            }
                            amountText = ""
                            noteText = ""
                            message = result.budgetMessage() ?: "Đã lưu giao dịch"
                        }.onFailure { error -> message = error.message ?: "Không thể lưu giao dịch" }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Purple),
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (canSave) "Lưu giao dịch" else "Nhập số tiền để lưu", color = Color.White, fontWeight = FontWeight.SemiBold) }
            message?.let { Text(it, color = AppColors.TextStrong, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}


@Composable
private fun TransactionFilterCard(
    selectedType: TransactionType?,
    accountName: String,
    categoryName: String,
    statusName: String,
    currentMonthOnly: Boolean,
    onTypeSelected: (TransactionType?) -> Unit,
    onPreviousAccount: () -> Unit,
    onNextAccount: () -> Unit,
    onPreviousCategory: () -> Unit,
    onNextCategory: () -> Unit,
    onPreviousStatus: () -> Unit,
    onNextStatus: () -> Unit,
    onToggleCurrentMonth: () -> Unit,
    onClear: () -> Unit,
) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = AppColors.Card)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Bộ lọc", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = AppColors.TextStrong)
                Button(onClick = onClear, colors = ButtonDefaults.buttonColors(containerColor = AppColors.Lavender)) { Text("Xóa lọc", color = AppColors.TextStrong) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingsChip("Tất cả", selectedType == null) { onTypeSelected(null) }
                SettingsChip("Thu", selectedType == TransactionType.INCOME) { onTypeSelected(TransactionType.INCOME) }
                SettingsChip("Chi", selectedType == TransactionType.EXPENSE) { onTypeSelected(TransactionType.EXPENSE) }
            }
            PickerRow("Nguồn", accountName, onPreviousAccount, onNextAccount)
            PickerRow("Hạng mục", categoryName, onPreviousCategory, onNextCategory)
            PickerRow("Trạng thái", statusName, onPreviousStatus, onNextStatus)
            SettingsChip("Chỉ tháng hiện tại", currentMonthOnly, onToggleCurrentMonth)
        }
    }
}

private fun currentMonthRangeMillis(): Pair<Long, Long> {
    val start = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val end = start.clone() as Calendar
    end.add(Calendar.MONTH, 1)
    end.add(Calendar.MILLISECOND, -1)
    return start.timeInMillis to end.timeInMillis
}

private data class TransactionTimeOption(
    val label: String,
    val timestampMillis: Long,
)

private fun transactionTimeOptions(): List<TransactionTimeOption> {
    val now = Calendar.getInstance()
    val today = now.clone() as Calendar
    val yesterday = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, -1) }
    val monthStart = (now.clone() as Calendar).apply {
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 9)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    return listOf(
        TransactionTimeOption("Hôm nay", today.timeInMillis),
        TransactionTimeOption("Hôm qua", yesterday.timeInMillis),
        TransactionTimeOption("Đầu tháng", monthStart.timeInMillis),
    )
}

private fun TransactionStatus.label(): String = when (this) {
    TransactionStatus.PENDING_CATEGORY -> "Chờ phân loại"
    TransactionStatus.CATEGORIZED -> "Đã phân loại"
    TransactionStatus.IGNORED -> "Đã bỏ qua"
    TransactionStatus.DUPLICATED -> "Trùng lặp"
    TransactionStatus.ADJUSTED -> "Đã điều chỉnh"
}

@Composable
private fun TransactionTypeChip(label: String, sign: String, selected: Boolean, color: Color, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = if (selected) color else Color.White.copy(alpha = 0.78f)),
    ) {
        Text("${if (selected) "✓ " else ""}$sign $label", color = if (selected) Color.White else AppColors.TextStrong, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun MiniMoneyCard(title: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = AppColors.Card)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { 1f },
                    color = color,
                    trackColor = color.copy(alpha = 0.16f),
                    strokeWidth = 6.dp,
                    modifier = Modifier.size(44.dp),
                )
                Text(if (title.contains("thu", ignoreCase = true)) "+" else "−", color = color, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, color = AppColors.TextSoft, style = MaterialTheme.typography.bodySmall)
                Text(value, color = AppColors.TextStrong, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun EmptyTransactionsCard(message: String = "Thêm khoản chi đầu tiên để app bắt đầu thống kê cho bạn.") {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = AppColors.Card)) {
        Column(
            Modifier.fillMaxWidth().padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("🌸", style = MaterialTheme.typography.headlineLarge)
            Text("Chưa có giao dịch nào", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = AppColors.TextStrong)
            Text(message, color = AppColors.TextSoft, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
private fun ImportResult.budgetMessage(): String? = when (this) {
    is ImportResult.Imported -> budgetCheckResult.budgetMessage("Đã lưu giao dịch")
    is ImportResult.Duplicate -> null
}

private fun BudgetCheckResult.budgetMessage(prefix: String = "Đã cập nhật giao dịch"): String? = when (this) {
    is BudgetCheckResult.WarningTriggered -> "$prefix • Sắp chạm hạn mức ${MoneyVnd(spentAmount).format()}"
    is BudgetCheckResult.Exceeded -> "$prefix • Đã vượt hạn mức ${MoneyVnd(spentAmount).format()}"
    else -> null
}

@Composable
private fun PickerRow(label: String, value: String, onPrevious: () -> Unit, onNext: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = AppColors.TextStrong, fontWeight = FontWeight.SemiBold)
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.72f))) {
            Row(
                Modifier.fillMaxWidth().padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(onClick = onPrevious, colors = ButtonDefaults.buttonColors(containerColor = AppColors.Purple)) { Text("‹", color = Color.White) }
                Text(value, Modifier.weight(1f), color = AppColors.TextStrong, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Button(onClick = onNext, colors = ButtonDefaults.buttonColors(containerColor = AppColors.Purple)) { Text("›", color = Color.White) }
            }
        }
    }
}

@Composable
private fun TransactionRow(
    transaction: TransactionEntity,
    accounts: List<AccountEntity>,
    categories: List<CategoryEntity>,
    accountName: String,
    categoryName: String,
    linkedGoalName: String?,
    onSaveEdit: (Long, Long?, Long, String?, Long) -> Unit,
    onIgnore: () -> Unit,
    onUnlinkGoal: () -> Unit,
) {
    val isExpense = transaction.type == TransactionType.EXPENSE
    val sign = if (isExpense) "−" else "+"
    val color = if (isExpense) AppColors.Peach else AppColors.Mint
    val amount = MoneyVnd(transaction.amount).format()
    var isEditing by remember(transaction.id) { mutableStateOf(false) }
    var editAmountText by remember(transaction.id, transaction.amount) { mutableStateOf(transaction.amount.toString()) }
    var editNoteText by remember(transaction.id, transaction.description) { mutableStateOf(transaction.description.orEmpty()) }
    var editDateText by remember(transaction.id, transaction.occurredAt) { mutableStateOf(formatDate(transaction.occurredAt)) }
    var editDateError by remember(transaction.id) { mutableStateOf<String?>(null) }
    val accountOptions = accounts.ifEmpty { listOf(AccountEntity(id = transaction.accountId, name = accountName, balance = 0L, type = AccountType.CASH)) }
    var editAccountIndex by remember(transaction.id, accounts) { mutableStateOf(accountOptions.indexOfFirst { it.id == transaction.accountId }.coerceAtLeast(0)) }
    val categoryOptions = listOf<CategoryEntity?>(null) + categories
    var editCategoryIndex by remember(transaction.id, categories) { mutableStateOf(categoryOptions.indexOfFirst { it?.id == transaction.categoryId }.coerceAtLeast(0)) }
    val editAccount = accountOptions.getOrNull(editAccountIndex.coerceIn(0, accountOptions.lastIndex.coerceAtLeast(0)))
    val editCategory = categoryOptions.getOrNull(editCategoryIndex.coerceIn(0, categoryOptions.lastIndex.coerceAtLeast(0)))
    val parsedEditDate = parseDateTime(editDateText)
    val canSaveEdit = editAccount != null && editAmountText.toLongOrNull()?.let { it > 0 } == true && parsedEditDate != null

    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = AppColors.Card)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(progress = { 1f }, color = color, trackColor = color.copy(alpha = 0.16f), strokeWidth = 5.dp, modifier = Modifier.size(42.dp))
                    Text(if (isExpense) "🧾" else "🌱", style = MaterialTheme.typography.bodySmall)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(categoryName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = AppColors.TextStrong)
                    Text(accountName, color = AppColors.TextSoft, style = MaterialTheme.typography.bodySmall)
                    linkedGoalName?.let { Text("Mục tiêu: $it", color = AppColors.Purple, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold) }
                    transaction.description?.let { Text(it, color = AppColors.TextSoft, style = MaterialTheme.typography.bodySmall) }
                    Text(formatDate(transaction.occurredAt), color = AppColors.TextSoft, style = MaterialTheme.typography.bodySmall)
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("$sign$amount", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
                    Button(onClick = { isEditing = !isEditing }, colors = ButtonDefaults.buttonColors(containerColor = AppColors.Lavender)) { Text(if (isEditing) "Đóng" else "Sửa", color = AppColors.TextStrong) }
                    Button(onClick = onIgnore, colors = ButtonDefaults.buttonColors(containerColor = AppColors.Peach.copy(alpha = 0.82f))) { Text("Bỏ qua", color = Color.White) }
                    if (linkedGoalName != null && transaction.type == TransactionType.INCOME) {
                        Button(onClick = onUnlinkGoal, colors = ButtonDefaults.buttonColors(containerColor = AppColors.Lavender)) { Text("Gỡ", color = AppColors.TextStrong) }
                    }
                }
            }
            if (isEditing) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    PickerRow("Nguồn", editAccount?.name ?: accountName, { editAccountIndex = (editAccountIndex - 1).floorMod(accountOptions.size) }, { editAccountIndex = (editAccountIndex + 1).floorMod(accountOptions.size) })
                    PickerRow("Hạng mục", editCategory?.name ?: "Chưa phân loại", { editCategoryIndex = (editCategoryIndex - 1).floorMod(categoryOptions.size) }, { editCategoryIndex = (editCategoryIndex + 1).floorMod(categoryOptions.size) })
                    OutlinedTextField(value = editAmountText, onValueChange = { editAmountText = it.filter(Char::isDigit) }, label = { Text("Số tiền mới") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = editNoteText, onValueChange = { editNoteText = it }, label = { Text("Ghi chú") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = editDateText, onValueChange = { editDateText = it; editDateError = null }, label = { Text("Ngày giờ (dd/MM/yyyy HH:mm)") }, modifier = Modifier.fillMaxWidth())
                    editDateError?.let { Text(it, color = AppColors.Pink, style = MaterialTheme.typography.bodySmall) }
                    Button(
                        enabled = canSaveEdit,
                        onClick = {
                            val selectedAccount = editAccount ?: return@Button
                            val newAmount = editAmountText.toLongOrNull() ?: return@Button
                            val newOccurredAt = parsedEditDate ?: run {
                                editDateError = "Ngày giờ chưa đúng định dạng dd/MM/yyyy HH:mm"
                                return@Button
                            }
                            onSaveEdit(selectedAccount.id, editCategory?.id, newAmount, editNoteText, newOccurredAt)
                            isEditing = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.Purple),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Lưu chỉnh sửa", color = Color.White, fontWeight = FontWeight.SemiBold) }
                }
            }
        }
    }
}

@Composable
private fun RoundStatCard(title: String, value: String, progress: Float, color: Color, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = AppColors.Card)) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    color = color,
                    trackColor = color.copy(alpha = 0.16f),
                    strokeWidth = 7.dp,
                    modifier = Modifier.size(54.dp),
                )
                Text(title, style = MaterialTheme.typography.labelMedium, color = AppColors.TextSoft, fontWeight = FontWeight.SemiBold)
            }
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = AppColors.TextStrong)
        }
    }
}

@Composable
private fun CuteButton(text: String, onClick: () -> Unit) {
    Button(onClick = onClick, colors = ButtonDefaults.buttonColors(containerColor = AppColors.Purple)) {
        Text(text, color = Color.White, fontWeight = FontWeight.SemiBold)
    }
}

private object AppColors {
    val Background = Color(0xFFFFF7FB)
    val Purple = Color(0xFF8B5CF6)
    val Pink = Color(0xFFFF7AB6)
    val Lavender = Color(0xFFA78BFA)
    val Mint = Color(0xFF43D9B8)
    val Peach = Color(0xFFFFA06B)
    val TextStrong = Color(0xFF282238)
    val TextSoft = Color(0xFF70687C)
    val Card = Color(0xFFFFEFF8)
}

@Composable
private fun MonthComparisonCard(comparison: MonthComparisonSummary) {
    val hasPreviousMonthData = comparison.previousIncomeAmount > 0 || comparison.previousExpenseAmount > 0
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = AppColors.Card)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("So với tháng trước", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = AppColors.TextStrong)
            if (!hasPreviousMonthData) {
                Text(
                    "Chưa có dữ liệu tháng trước, số liệu đang tính từ 0đ.",
                    color = AppColors.TextSoft,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MonthDeltaPill("Thu", comparison.incomeDeltaAmount, AppColors.Mint, Modifier.weight(1f))
                MonthDeltaPill("Chi", comparison.expenseDeltaAmount, AppColors.Peach, Modifier.weight(1f))
            }
            comparison.biggestIncreaseCategory?.let { movement ->
                CategoryMovementLine("Tăng mạnh", movement, AppColors.Peach)
            }
            comparison.biggestDecreaseCategory?.let { movement ->
                CategoryMovementLine("Giảm nhiều", movement, AppColors.Mint)
            }
        }
    }
}

@Composable
private fun MonthDeltaPill(label: String, deltaAmount: Long, color: Color, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.72f))) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, color = AppColors.TextSoft, style = MaterialTheme.typography.bodySmall)
            Text(deltaAmount.formatDeltaMoney(), color = if (deltaAmount >= 0) color else AppColors.Mint, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
        }
    }
}

@Composable
private fun CategoryMovementLine(label: String, movement: CategoryMovementSummary, color: Color) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, color = AppColors.TextSoft, style = MaterialTheme.typography.bodySmall)
            Text(movement.categoryName, color = AppColors.TextStrong, fontWeight = FontWeight.SemiBold)
        }
        Text(movement.deltaAmount.formatDeltaMoney(), color = color, fontWeight = FontWeight.Bold)
    }
}

private fun Long.formatDeltaMoney(): String {
    val sign = if (this >= 0) "+" else "−"
    return "$sign${MoneyVnd(kotlin.math.abs(this)).format()}"
}
@Composable
private fun ReportChartsCard(categorySpending: List<CategorySpendingSummary>, budgetStatuses: List<BudgetStatusSummary>, monthlyBars: List<MonthlyBarSummary>, balanceTrend: List<BalanceTrendPoint>, featuredGoal: GoalEntity?) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = AppColors.Card)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Biểu đồ nhanh", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = AppColors.TextStrong)
            if (categorySpending.isEmpty() && budgetStatuses.isEmpty() && monthlyBars.isEmpty() && balanceTrend.isEmpty() && featuredGoal == null) {
                Text("Thêm giao dịch, hạn mức hoặc mục tiêu để xem biểu đồ.", color = AppColors.TextSoft)
            }
            if (monthlyBars.isNotEmpty()) {
                val maxMonthlyAmount = monthlyBars.maxOf { maxOf(it.incomeAmount, it.expenseAmount) }.coerceAtLeast(1L)
                Text("Thu/chi 3 tháng", color = AppColors.TextStrong, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                monthlyBars.forEach { item -> MonthlyBarLine(item, maxMonthlyAmount) }
            }
            if (balanceTrend.isNotEmpty()) {
                val maxBalanceAmount = balanceTrend.maxOf { kotlin.math.abs(it.balanceAmount) }.coerceAtLeast(1L)
                Text("Xu hướng số dư", color = AppColors.TextStrong, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                balanceTrend.forEach { point ->
                    ChartLine(point.label, kotlin.math.abs(point.balanceAmount).toFloat() / maxBalanceAmount.toFloat(), MoneyVnd(point.balanceAmount).format(), AppColors.Purple)
                }
            }
            if (categorySpending.isNotEmpty()) {
                val totalSpending = categorySpending.sumOf { it.amount }.coerceAtLeast(1L)
                Text("Tỷ trọng chi tiêu", color = AppColors.TextStrong, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                categorySpending.forEachIndexed { index, item ->
                    val color = listOf(AppColors.Peach, AppColors.Pink, AppColors.Lavender)[index.coerceIn(0, 2)]
                    ChartLine(item.categoryName, item.amount.toFloat() / totalSpending.toFloat(), MoneyVnd(item.amount).format(), color)
                }
            }
            if (budgetStatuses.isNotEmpty()) {
                Text("Tiến độ hạn mức", color = AppColors.TextStrong, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                budgetStatuses.take(3).forEach { budget ->
                    ChartLine(budget.categoryName, budget.percentUsed.coerceIn(0f, 1f), "${(budget.percentUsed * 100).toInt()}%", if (budget.status == BudgetStatus.EXCEEDED) AppColors.Pink else AppColors.Mint)
                }
            }
            featuredGoal?.let { goal ->
                val progress = if (goal.targetAmount <= 0) 0f else goal.currentAmount.toFloat() / goal.targetAmount.toFloat()
                Text("Tiến độ mục tiêu", color = AppColors.TextStrong, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                ChartLine(goal.name, progress.coerceIn(0f, 1f), "${(progress * 100).toInt()}%", AppColors.Purple)
            }
        }
    }
}

@Composable
private fun MonthlyBarLine(item: MonthlyBarSummary, maxAmount: Long) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(item.monthLabel, color = AppColors.TextSoft, style = MaterialTheme.typography.bodySmall)
        CircularProgressIndicator(
            progress = { item.incomeAmount.toFloat() / maxAmount.toFloat() },
            color = AppColors.Mint,
            trackColor = AppColors.Mint.copy(alpha = 0.12f),
            strokeWidth = 5.dp,
            modifier = Modifier.size(34.dp),
        )
        CircularProgressIndicator(
            progress = { item.expenseAmount.toFloat() / maxAmount.toFloat() },
            color = AppColors.Peach,
            trackColor = AppColors.Peach.copy(alpha = 0.12f),
            strokeWidth = 5.dp,
            modifier = Modifier.size(34.dp),
        )
        Text("Thu ${MoneyVnd(item.incomeAmount).format()} • Chi ${MoneyVnd(item.expenseAmount).format()}", Modifier.weight(1f), color = AppColors.TextSoft, style = MaterialTheme.typography.bodySmall)
    }
}
@Composable
private fun ChartLine(label: String, progress: Float, value: String, color: Color) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            color = color,
            trackColor = color.copy(alpha = 0.14f),
            strokeWidth = 6.dp,
            modifier = Modifier.size(38.dp),
        )
        Text(label, Modifier.weight(1f), color = AppColors.TextSoft, style = MaterialTheme.typography.bodyMedium)
        Text(value, color = AppColors.TextStrong, fontWeight = FontWeight.Bold)
    }
}
@Composable
private fun DashboardInsightsCard(categorySpending: List<CategorySpendingSummary>, budgetStatuses: List<BudgetStatusSummary>) {
    val warningBudgets = budgetStatuses.filter { it.status != BudgetStatus.OK }
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = AppColors.Card)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Góc nhìn tháng này", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = AppColors.TextStrong)
            if (categorySpending.isEmpty() && budgetStatuses.isEmpty()) {
                Text("Chưa đủ dữ liệu để phân tích. Thêm giao dịch hoặc hạn mức để xem gợi ý.", color = AppColors.TextSoft)
            }
            if (warningBudgets.isNotEmpty()) {
                Text("Cảnh báo hạn mức", color = AppColors.Pink, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                warningBudgets.take(2).forEach { budget -> BudgetStatusLine(budget) }
            }
            if (categorySpending.isNotEmpty()) {
                Text("Chi nhiều nhất", color = AppColors.TextStrong, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                categorySpending.forEach { item ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(item.categoryName, color = AppColors.TextSoft)
                        Text(MoneyVnd(item.amount).format(), color = AppColors.TextStrong, fontWeight = FontWeight.Bold)
                    }
                }
            }
            if (budgetStatuses.isNotEmpty() && warningBudgets.isEmpty()) {
                Text("Hạn mức", color = AppColors.TextStrong, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                budgetStatuses.take(2).forEach { budget -> BudgetStatusLine(budget) }
            }
        }
    }
}

@Composable
private fun BudgetStatusLine(budget: BudgetStatusSummary) {
    val color = when (budget.status) {
        BudgetStatus.OK -> AppColors.Mint
        BudgetStatus.WARNING -> AppColors.Peach
        BudgetStatus.EXCEEDED -> AppColors.Pink
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(
            progress = { budget.percentUsed.coerceIn(0f, 1f) },
            color = color,
            trackColor = color.copy(alpha = 0.16f),
            strokeWidth = 6.dp,
            modifier = Modifier.size(42.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(budget.categoryName, color = AppColors.TextStrong, fontWeight = FontWeight.SemiBold)
            Text("${MoneyVnd(budget.spentAmount).format()} / ${MoneyVnd(budget.limitAmount).format()}", color = AppColors.TextSoft, style = MaterialTheme.typography.bodySmall)
        }
        Text(budget.status.label(), color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
    }
}

private fun BudgetStatus.label(): String = when (this) {
    BudgetStatus.OK -> "Ổn"
    BudgetStatus.WARNING -> "Gần vượt"
    BudgetStatus.EXCEEDED -> "Vượt"
}
@Composable
private fun TotalBalanceCircleCard(totalBalance: Long) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(listOf(AppColors.Purple, AppColors.Pink)))
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                    Text("Tổng tiền hiện có", color = Color.White.copy(alpha = 0.92f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("Tổng từ tiền mặt, ngân hàng và nguồn khác", color = Color.White.copy(alpha = 0.78f), style = MaterialTheme.typography.bodySmall)
                }
                Text("VND", color = Color.White.copy(alpha = 0.72f), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    progress = { 1f },
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.22f),
                    strokeWidth = 10.dp,
                    modifier = Modifier.size(154.dp),
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(MoneyVnd(totalBalance).format(), color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                    Text("đang có sẵn", color = Color.White.copy(alpha = 0.82f), style = MaterialTheme.typography.bodySmall)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Số dư khả dụng", color = Color.White.copy(alpha = 0.82f), style = MaterialTheme.typography.bodyMedium)
                Text(MoneyVnd(totalBalance).format(), color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun AccountBalancePill(account: AccountEntity) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { 1f },
                    color = if (account.type == AccountType.CASH) AppColors.Mint else AppColors.Lavender,
                    trackColor = AppColors.Lavender.copy(alpha = 0.12f),
                    strokeWidth = 5.dp,
                    modifier = Modifier.size(42.dp),
                )
                Text(if (account.type == AccountType.CASH) "💵" else "🏦", style = MaterialTheme.typography.bodySmall)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(account.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = AppColors.TextStrong)
                Text(account.type.label(), style = MaterialTheme.typography.bodySmall, color = AppColors.TextSoft)
            }
            Text(MoneyVnd(account.balance.coerceAtLeast(0)).format(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = AppColors.TextStrong)
        }
    }
}


private fun CategoryType.label(): String = when (this) {
    CategoryType.INCOME -> "Thu"
    CategoryType.EXPENSE -> "Chi"
    CategoryType.BOTH -> "Thu/Chi"
}

private fun AccountType.label(): String = when (this) {
    AccountType.BANK -> "Ngân hàng"
    AccountType.CASH -> "Tiền mặt"
    AccountType.WALLET -> "Ví điện tử"
    AccountType.SAVING -> "Tiết kiệm"
    AccountType.INVESTMENT -> "Đầu tư"
}

@Composable
private fun AccountBalanceLine(account: AccountEntity) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column {
            Text(account.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = AppColors.TextStrong)
            Text(account.type.name, style = MaterialTheme.typography.bodySmall, color = AppColors.TextSoft)
        }
        Text(MoneyVnd(account.balance.coerceAtLeast(0)).format(), fontWeight = FontWeight.Bold, color = AppColors.TextStrong)
    }
}

@Composable
private fun BudgetsScreen() {
    val application = LocalContext.current.applicationContext as SpendingApplication
    val scope = rememberCoroutineScope()
    val month = remember { BudgetRepository.currentMonth() }
    val categories by application.container.budgetRepository.observeExpenseCategories().collectAsState(initial = emptyList())
    val budgets by application.container.budgetRepository.observeBudgetOverview(month).collectAsState(initial = emptyList())
    var selectedCategoryIndex by remember { mutableStateOf(0) }
    var selectedGoalIndex by remember { mutableStateOf(0) }
    var limitText by remember { mutableStateOf("") }
    var thresholdText by remember { mutableStateOf("80") }
    var message by remember { mutableStateOf<String?>(null) }
    val expenseCategories = categories.filter { it.type.name != "INCOME" }
    val selectedCategory = expenseCategories.getOrNull(selectedCategoryIndex.coerceIn(0, (expenseCategories.size - 1).coerceAtLeast(0)))

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.Background),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Hạn mức", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = AppColors.TextStrong)
                Text("Tháng $month • Theo dõi chi tiêu từng hạng mục", color = AppColors.TextSoft)
            }
        }
        item {
            SettingsSectionCard(title = "Đặt hạn mức", emoji = "🎯", description = "Chọn hạng mục và giới hạn chi tiêu trong tháng.") {
                PickerRow(
                    label = "Hạng mục",
                    value = selectedCategory?.name ?: "Chưa có hạng mục",
                    onPrevious = { if (expenseCategories.isNotEmpty()) selectedCategoryIndex = (selectedCategoryIndex - 1).floorMod(expenseCategories.size) },
                    onNext = { if (expenseCategories.isNotEmpty()) selectedCategoryIndex = (selectedCategoryIndex + 1).floorMod(expenseCategories.size) },
                )
                OutlinedTextField(
                    value = limitText,
                    onValueChange = { limitText = it.filter(Char::isDigit) },
                    label = { Text("Hạn mức VND") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = thresholdText,
                    onValueChange = { thresholdText = it.filter(Char::isDigit).take(3) },
                    label = { Text("Cảnh báo khi đạt %") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                CuteButton("Lưu hạn mức") {
                    val category = selectedCategory ?: return@CuteButton
                    val limit = limitText.toLongOrNull() ?: return@CuteButton
                    val threshold = thresholdText.toIntOrNull() ?: 80
                    scope.launch {
                        runCatching { application.container.budgetRepository.saveBudget(category.id, month, limit, threshold) }
                            .onSuccess { limitText = ""; message = "Đã lưu hạn mức ${category.name}" }
                            .onFailure { error -> message = error.message ?: "Không thể lưu hạn mức" }
                    }
                }
                message?.let { Text(it, color = AppColors.TextStrong) }
            }
        }
        item {
            Text("Hạn mức tháng này", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = AppColors.TextStrong)
        }
        if (budgets.isEmpty()) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Chưa có hạn mức nào", style = MaterialTheme.typography.titleMedium, color = AppColors.TextStrong)
                        Text("Thêm hạn mức đầu tiên để app cảnh báo khi bạn sắp vượt ngân sách.", color = AppColors.TextSoft)
                    }
                }
            }
        } else {
            items(budgets, key = { it.budget.id }) { item -> BudgetOverviewCard(item) }
        }
    }
}

@Composable
private fun BudgetOverviewCard(item: com.spendingapp.core.repository.BudgetOverviewItem) {
    val percent = item.percentUsed.coerceAtLeast(0f)
    val color = when {
        percent >= 1f -> AppColors.Peach
        percent >= item.budget.warningThresholdPercent / 100f -> AppColors.Pink
        else -> AppColors.Mint
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(item.category?.name ?: "Hạng mục #${item.budget.categoryId}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = AppColors.TextStrong)
                    Text("Ngưỡng cảnh báo ${item.budget.warningThresholdPercent}%", color = AppColors.TextSoft, style = MaterialTheme.typography.bodySmall)
                }
                Text("${(percent * 100).toInt()}%", color = color, fontWeight = FontWeight.Bold)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    progress = { percent.coerceIn(0f, 1f) },
                    color = color,
                    trackColor = color.copy(alpha = 0.16f),
                    strokeWidth = 8.dp,
                    modifier = Modifier.size(56.dp),
                )
                Column {
                    Text("Đã chi ${MoneyVnd(item.spentAmount).format()}", color = AppColors.TextStrong, fontWeight = FontWeight.SemiBold)
                    Text("Hạn mức ${MoneyVnd(item.budget.limitAmount).format()}", color = AppColors.TextSoft)
                }
            }
        }
    }
}

@Composable
private fun GoalsScreen() {
    val application = LocalContext.current.applicationContext as SpendingApplication
    val scope = rememberCoroutineScope()
    val goals by application.container.goalRepository.observeGoals().collectAsState(initial = emptyList())
    var nameText by remember { mutableStateOf("") }
    var targetText by remember { mutableStateOf("") }
    var priority by remember { mutableStateOf(GoalPriority.MEDIUM) }
    var message by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.Background),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Mục tiêu", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = AppColors.TextStrong)
                Text("Theo dõi những khoản bạn muốn đạt được", color = AppColors.TextSoft)
            }
        }
        item {
            SettingsSectionCard(title = "Tạo mục tiêu mới", emoji = "🌱", description = "Đặt số tiền cần đạt và mức ưu tiên.") {
                OutlinedTextField(nameText, { nameText = it }, label = { Text("Tên mục tiêu") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    value = targetText,
                    onValueChange = { targetText = it.filter(Char::isDigit) },
                    label = { Text("Số tiền cần đạt VND") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingsChip("Cao", priority == GoalPriority.HIGH) { priority = GoalPriority.HIGH }
                    SettingsChip("Vừa", priority == GoalPriority.MEDIUM) { priority = GoalPriority.MEDIUM }
                    SettingsChip("Thấp", priority == GoalPriority.LOW) { priority = GoalPriority.LOW }
                }
                CuteButton("Lưu mục tiêu") {
                    val target = targetText.toLongOrNull() ?: return@CuteButton
                    scope.launch {
                        runCatching { application.container.goalRepository.createGoal(nameText, target, priority) }
                            .onSuccess { nameText = ""; targetText = ""; priority = GoalPriority.MEDIUM; message = "Đã tạo mục tiêu" }
                            .onFailure { error -> message = error.message ?: "Không thể tạo mục tiêu" }
                    }
                }
                message?.let { Text(it, color = AppColors.TextStrong) }
            }
        }
        item {
            Text("Mục tiêu của bạn", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = AppColors.TextStrong)
        }
        if (goals.isEmpty()) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Chưa có mục tiêu nào", style = MaterialTheme.typography.titleMedium, color = AppColors.TextStrong)
                        Text("Ví dụ: quỹ khẩn cấp, du lịch, mua điện thoại, trả nợ.", color = AppColors.TextSoft)
                    }
                }
            }
        } else {
            items(goals, key = { it.id }) { goal -> GoalCard(goal) }
        }
    }
}

@Composable
private fun GoalCard(goal: GoalEntity) {
    val application = LocalContext.current.applicationContext as SpendingApplication
    val scope = rememberCoroutineScope()
    var progressText by remember(goal.id) { mutableStateOf(goal.currentAmount.toString()) }
    var localMessage by remember(goal.id) { mutableStateOf<String?>(null) }
    val percent = if (goal.targetAmount <= 0) 0f else goal.currentAmount.toFloat() / goal.targetAmount.toFloat()
    val color = when (goal.priority) {
        GoalPriority.HIGH -> AppColors.Pink
        GoalPriority.MEDIUM -> AppColors.Purple
        GoalPriority.LOW -> AppColors.Mint
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(goal.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = AppColors.TextStrong)
                    Text("${goal.priority.label()} • ${goal.status.label()}", color = AppColors.TextSoft, style = MaterialTheme.typography.bodySmall)
                }
                Text("${(percent * 100).toInt()}%", color = color, fontWeight = FontWeight.Bold)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    progress = { percent.coerceIn(0f, 1f) },
                    color = color,
                    trackColor = color.copy(alpha = 0.16f),
                    strokeWidth = 8.dp,
                    modifier = Modifier.size(64.dp),
                )
                Column {
                    Text("Đã có ${MoneyVnd(goal.currentAmount).format()}", color = AppColors.TextStrong, fontWeight = FontWeight.SemiBold)
                    Text("Mục tiêu ${MoneyVnd(goal.targetAmount).format()}", color = AppColors.TextSoft)
                }
            }
            OutlinedTextField(
                value = progressText,
                onValueChange = { progressText = it.filter(Char::isDigit) },
                label = { Text("Cập nhật số tiền đã tích lũy") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CuteButton("Cập nhật") {
                    val newAmount = progressText.toLongOrNull() ?: return@CuteButton
                    scope.launch {
                        runCatching { application.container.goalRepository.updateProgress(goal, newAmount) }
                            .onSuccess { result ->
                                if (result.completedJustNow) {
                                    application.container.localNotificationService.notifyGoalCompleted(goal.name)
                                }
                                localMessage = "Đã cập nhật tiến độ"
                            }
                            .onFailure { error -> localMessage = error.message ?: "Không thể cập nhật" }
                    }
                }
                if (goal.status == GoalStatus.PAUSED) {
                    Button(onClick = { scope.launch { application.container.goalRepository.resumeGoal(goal) } }, colors = ButtonDefaults.buttonColors(containerColor = AppColors.Mint)) { Text("Tiếp tục", color = Color.White) }
                } else if (goal.status != GoalStatus.COMPLETED) {
                    Button(onClick = { scope.launch { application.container.goalRepository.pauseGoal(goal) } }, colors = ButtonDefaults.buttonColors(containerColor = AppColors.Peach)) { Text("Tạm dừng", color = Color.White) }
                }
            }
            localMessage?.let { Text(it, color = AppColors.TextStrong) }
        }
    }
}

@Composable
private fun GoalSummaryCard(goal: GoalEntity) {
    val percent = if (goal.targetAmount <= 0) 0f else goal.currentAmount.toFloat() / goal.targetAmount.toFloat()
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(18.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(
                progress = { percent.coerceIn(0f, 1f) },
                color = AppColors.Pink,
                trackColor = AppColors.Pink.copy(alpha = 0.16f),
                strokeWidth = 8.dp,
                modifier = Modifier.size(72.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Mục tiêu nổi bật 🌷", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = AppColors.TextStrong)
                Text(goal.name, color = AppColors.TextStrong, fontWeight = FontWeight.SemiBold)
                Text("${MoneyVnd(goal.currentAmount).format()} / ${MoneyVnd(goal.targetAmount).format()} • ${(percent * 100).toInt()}%", color = AppColors.TextSoft)
            }
        }
    }
}

private fun GoalPriority.label(): String = when (this) {
    GoalPriority.HIGH -> "Ưu tiên cao"
    GoalPriority.MEDIUM -> "Ưu tiên vừa"
    GoalPriority.LOW -> "Ưu tiên thấp"
}

private fun GoalStatus.label(): String = when (this) {
    GoalStatus.ACTIVE -> "Đang thực hiện"
    GoalStatus.COMPLETED -> "Hoàn thành"
    GoalStatus.PAUSED -> "Tạm dừng"
    GoalStatus.CANCELLED -> "Đã hủy"
}

private fun ThemeMode.label(): String = when (this) {
    ThemeMode.SYSTEM -> "Theo thiết bị"
    ThemeMode.LIGHT -> "Sáng"
    ThemeMode.DARK -> "Tối"
}

@Composable
private fun PlaceholderScreen(title: String, description: String) {
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Text(description)
    }
}

@Composable
private fun SettingsScreen() {
    val currentContext = LocalContext.current
    val application = currentContext.applicationContext as SpendingApplication
    val scope = rememberCoroutineScope()
    val accounts by application.container.accountRepository.observeAccounts().collectAsState(initial = emptyList())
    val categories by application.container.categoryRepository.observeCategories().collectAsState(initial = emptyList())
    var accountType by remember { mutableStateOf(AccountType.BANK) }
    var nameText by remember { mutableStateOf("") }
    var providerText by remember { mutableStateOf("") }
    var balanceText by remember { mutableStateOf("") }
    var selectedUpdateAccountIndex by remember { mutableStateOf(0) }
    var updateBalanceText by remember { mutableStateOf("") }
    var updateReasonText by remember { mutableStateOf("Cập nhật số dư thực tế") }
    var categoryType by remember { mutableStateOf(CategoryType.EXPENSE) }
    var categoryNameText by remember { mutableStateOf("") }
    var categoryIconText by remember { mutableStateOf("🌸") }
    var categoryColorText by remember { mutableStateOf("#FF7AB6") }
    var selectedCategoryManageIndex by remember { mutableStateOf(0) }
    var categoryEditNameText by remember { mutableStateOf("") }
    var categoryEditIconText by remember { mutableStateOf("") }
    var categoryEditColorText by remember { mutableStateOf("") }
    var tokenText by remember { mutableStateOf("") }
    var tokenSaved by remember { mutableStateOf(application.container.secureTokenStorage.hasSePayToken()) }
    var webhookUrlText by remember { mutableStateOf(application.container.webhookSettingsRepository.getWebhookUrl()) }
    var webhookApiKeyText by remember { mutableStateOf("") }
    var webhookApiKeySaved by remember { mutableStateOf(application.container.secureTokenStorage.hasWebhookApiKey()) }
    var webhookMessage by remember { mutableStateOf<String?>(null) }
    val notificationSettings by application.container.notificationSettingsRepository.observeSettings().collectAsState()
    val backgroundSyncSettings by application.container.backgroundSyncSettingsRepository.observeSettings().collectAsState()
    val themeSettings by application.container.themeSettingsRepository.observeSettings().collectAsState()
    var message by remember { mutableStateOf<String?>(null) }
    var sePayMessage by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.Background),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Cài đặt", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = AppColors.TextStrong)
                Text("Quản lý nguồn tiền, đồng bộ và quyền riêng tư", color = AppColors.TextSoft)
            }
        }
        item {
            SettingsSectionCard(title = "Giao diện", emoji = "🎨", description = "Lưu lựa chọn theme local trên thiết bị.") {
                Text("Theme hiện tại: ${themeSettings.mode.label()}", color = AppColors.TextSoft)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        SettingsChip(mode.label(), themeSettings.mode == mode) {
                            application.container.themeSettingsRepository.saveSettings(ThemeSettings(mode))
                            scope.launch { application.container.domainEventPublisher.publish(DomainEventType.SETTINGS_UPDATED, "settings", actionId = "theme_mode") }
                        }
                    }
                }
                Text("MVP đang dùng palette dễ thương cố định; lựa chọn này được lưu để áp dụng mở rộng dark/light theme sau.", color = AppColors.TextSoft, style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            SettingsSectionCard(title = "Ví & nguồn tiền", emoji = "👛", description = "Thêm tiền mặt, tài khoản ngân hàng hoặc nguồn tiết kiệm.") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingsChip("Ngân hàng", accountType == AccountType.BANK) { accountType = AccountType.BANK }
                    SettingsChip("Tiền mặt", accountType == AccountType.CASH) { accountType = AccountType.CASH }
                }
                OutlinedTextField(nameText, { nameText = it }, label = { Text("Tên nguồn tiền") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(providerText, { providerText = it }, label = { Text("Nhà cung cấp/ngân hàng") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    value = balanceText,
                    onValueChange = { balanceText = it.filter(Char::isDigit) },
                    label = { Text("Số dư ban đầu VND") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                CuteButton("Lưu nguồn tiền") {
                    scope.launch {
                        runCatching { application.container.accountRepository.createAccount(nameText, accountType, balanceText.toLongOrNull() ?: 0L, providerText) }
                            .onSuccess { nameText = ""; providerText = ""; balanceText = ""; message = "Đã thêm nguồn tiền" }
                            .onFailure { error -> message = error.message ?: "Không thể thêm nguồn tiền" }
                    }
                }
                message?.let { Text(it, color = AppColors.TextStrong) }
            }
        }
        item {
            SettingsSectionCard(title = "Hạng mục", emoji = "🏷️", description = "Tạo, sửa hoặc ẩn hạng mục local trên thiết bị.") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingsChip("Chi", categoryType == CategoryType.EXPENSE) { categoryType = CategoryType.EXPENSE }
                    SettingsChip("Thu", categoryType == CategoryType.INCOME) { categoryType = CategoryType.INCOME }
                    SettingsChip("Cả hai", categoryType == CategoryType.BOTH) { categoryType = CategoryType.BOTH }
                }
                OutlinedTextField(categoryNameText, { categoryNameText = it }, label = { Text("Tên hạng mục") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(categoryIconText, { categoryIconText = it }, label = { Text("Icon/emoji") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(categoryColorText, { categoryColorText = it }, label = { Text("Màu HEX") }, modifier = Modifier.fillMaxWidth())
                CuteButton("Tạo hạng mục") {
                    scope.launch {
                        runCatching { application.container.categoryRepository.createCategory(categoryNameText, categoryType, categoryIconText, categoryColorText) }
                            .onSuccess { categoryNameText = ""; message = "Đã tạo hạng mục" }
                            .onFailure { error -> message = error.message ?: "Không thể tạo hạng mục" }
                    }
                }
                val selectedCategory = categories.getOrNull(selectedCategoryManageIndex.coerceIn(0, (categories.size - 1).coerceAtLeast(0)))
                PickerRow(
                    label = "Hạng mục cần sửa/ẩn",
                    value = selectedCategory?.let { "${it.icon ?: "🏷️"} ${it.name}" } ?: "Chưa có hạng mục",
                    onPrevious = { if (categories.isNotEmpty()) selectedCategoryManageIndex = (selectedCategoryManageIndex - 1).floorMod(categories.size) },
                    onNext = { if (categories.isNotEmpty()) selectedCategoryManageIndex = (selectedCategoryManageIndex + 1).floorMod(categories.size) },
                )
                OutlinedTextField(categoryEditNameText, { categoryEditNameText = it }, label = { Text("Tên mới") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(categoryEditIconText, { categoryEditIconText = it }, label = { Text("Icon mới") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(categoryEditColorText, { categoryEditColorText = it }, label = { Text("Màu mới") }, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CuteButton("Lưu sửa") {
                        val category = selectedCategory ?: return@CuteButton
                        scope.launch {
                            runCatching {
                                application.container.categoryRepository.updateCategory(
                                    category = category,
                                    name = categoryEditNameText.ifBlank { category.name },
                                    icon = categoryEditIconText.ifBlank { category.icon },
                                    color = categoryEditColorText.ifBlank { category.color },
                                )
                            }.onSuccess {
                                categoryEditNameText = ""; categoryEditIconText = ""; categoryEditColorText = ""; message = "Đã sửa hạng mục"
                            }.onFailure { error -> message = error.message ?: "Không thể sửa hạng mục" }
                        }
                    }
                    Button(onClick = {
                        val category = selectedCategory ?: return@Button
                        scope.launch {
                            runCatching { application.container.categoryRepository.archiveCategory(category) }
                                .onSuccess { message = "Đã ẩn hạng mục" }
                                .onFailure { error -> message = error.message ?: "Không thể ẩn hạng mục" }
                        }
                    }, colors = ButtonDefaults.buttonColors(containerColor = AppColors.Peach)) { Text("Ẩn", color = Color.White) }
                }
                if (categories.isEmpty()) {
                    Text("Chưa có hạng mục nào.", color = AppColors.TextSoft)
                } else {
                    categories.take(8).forEach { category ->
                        Text("${category.icon ?: "🏷️"} ${category.name} • ${category.type.label()}", color = AppColors.TextStrong)
                    }
                }
            }
        }
        item {
            SettingsSectionCard(title = "Cập nhật số dư", emoji = "🧾", description = "Điều chỉnh số dư thực tế và lưu BalanceLog để truy vết.") {
                val selectedUpdateAccount = accounts.getOrNull(selectedUpdateAccountIndex.coerceIn(0, (accounts.size - 1).coerceAtLeast(0)))
                val parsedUpdateBalance = updateBalanceText.toLongOrNull()
                val updateBalanceDelta = parsedUpdateBalance?.let { newBalance -> selectedUpdateAccount?.let { newBalance - it.balance } }
                PickerRow(
                    label = "Nguồn cần cập nhật",
                    value = selectedUpdateAccount?.let { "${it.name} • ${MoneyVnd(it.balance.coerceAtLeast(0)).format()}" } ?: "Chưa có nguồn tiền",
                    onPrevious = { if (accounts.isNotEmpty()) selectedUpdateAccountIndex = (selectedUpdateAccountIndex - 1).floorMod(accounts.size) },
                    onNext = { if (accounts.isNotEmpty()) selectedUpdateAccountIndex = (selectedUpdateAccountIndex + 1).floorMod(accounts.size) },
                )
                OutlinedTextField(
                    value = updateBalanceText,
                    onValueChange = { updateBalanceText = it.filter(Char::isDigit) },
                    label = { Text("Số dư thực tế mới") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                updateBalanceDelta?.let { delta ->
                    Text(
                        if (delta == 0L) "Không có chênh lệch so với số dư hiện tại." else "Chênh lệch: ${delta.formatDeltaMoney()}",
                        color = if (delta == 0L) AppColors.TextSoft else AppColors.Purple,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                OutlinedTextField(updateReasonText, { updateReasonText = it }, label = { Text("Lý do cập nhật") }, modifier = Modifier.fillMaxWidth())
                if ((updateBalanceDelta ?: 0L) != 0L && updateReasonText.isBlank()) {
                    Text("Vui lòng nhập lý do để lưu lịch sử điều chỉnh.", color = AppColors.Pink, style = MaterialTheme.typography.bodySmall)
                }
                CuteButton("Cập nhật số dư") {
                    val account = selectedUpdateAccount ?: return@CuteButton
                    val newBalance = updateBalanceText.toLongOrNull() ?: return@CuteButton
                    scope.launch {
                        runCatching { application.container.accountRepository.updateAccountBalance(account.id, newBalance, updateReasonText) }
                            .onSuccess { updateBalanceText = ""; message = "Đã cập nhật số dư ${account.name}" }
                            .onFailure { error -> message = error.message ?: "Không thể cập nhật số dư" }
                    }
                }
            }
        }
        item {
            SettingsSectionCard(title = "Thông báo", emoji = "🔔", description = "Bật/tắt cảnh báo local. Dữ liệu vẫn nằm trên thiết bị của bạn.") {
                val notificationPermissionGranted = hasPostNotificationsPermission(currentContext)
                Text(if (notificationPermissionGranted) "Quyền thiết bị: đã cho phép" else "Quyền thiết bị: cần cấp quyền thông báo", color = AppColors.TextSoft)
                CuteButton("Xin quyền thông báo từ thiết bị") {
                    (currentContext as? Activity)?.let(::requestPostNotificationsPermission)
                }
                Text(if (notificationSettings.allEnabled) "Thông báo tổng: đang bật" else "Thông báo tổng: đang tắt", color = AppColors.TextSoft)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingsChip("Tất cả", notificationSettings.allEnabled) {
                        application.container.notificationSettingsRepository.update { it.copy(allEnabled = !it.allEnabled) }
                        scope.launch { application.container.domainEventPublisher.publish(DomainEventType.SETTINGS_UPDATED, "settings", actionId = "notifications_all") }
                    }
                    SettingsChip("Hạn mức", notificationSettings.budgetEnabled) {
                        application.container.notificationSettingsRepository.update { it.copy(budgetEnabled = !it.budgetEnabled) }
                        scope.launch { application.container.domainEventPublisher.publish(DomainEventType.SETTINGS_UPDATED, "settings", actionId = "notifications_budget") }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingsChip("Mục tiêu", notificationSettings.goalEnabled) {
                        application.container.notificationSettingsRepository.update { it.copy(goalEnabled = !it.goalEnabled) }
                        scope.launch { application.container.domainEventPublisher.publish(DomainEventType.SETTINGS_UPDATED, "settings", actionId = "notifications_goal") }
                    }
                    SettingsChip("Nhắc tiền mặt", notificationSettings.cashReminderEnabled) {
                        application.container.notificationSettingsRepository.update { it.copy(cashReminderEnabled = !it.cashReminderEnabled) }
                        scope.launch { application.container.domainEventPublisher.publish(DomainEventType.SETTINGS_UPDATED, "settings", actionId = "notifications_cash_reminder") }
                        if (notificationSettings.cashReminderEnabled) {
                            application.container.cashReminderScheduler.schedule(notificationSettings.cashReminderIntervalDays)
                        } else {
                            application.container.cashReminderScheduler.cancel()
                        }
                    }
                }
                Text("Lịch nhắc tiền mặt", color = AppColors.TextStrong, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1, 3, 7).forEach { days ->
                        SettingsChip("$days ngày", notificationSettings.cashReminderIntervalDays == days) {
                            application.container.notificationSettingsRepository.update { it.copy(cashReminderIntervalDays = days, cashReminderEnabled = true) }
                            scope.launch { application.container.domainEventPublisher.publish(DomainEventType.SETTINGS_UPDATED, "settings", actionId = "cash_reminder_interval") }
                            application.container.cashReminderScheduler.schedule(days)
                        }
                    }
                }
                Text(
                    if (notificationSettings.cashReminderEnabled) "App sẽ nhắc mỗi ${notificationSettings.cashReminderIntervalDays} ngày khi thiết bị cho phép notification."
                    else "Bật nhắc tiền mặt để app tự nhắc bạn kiểm tra số dư thực tế.",
                    color = AppColors.TextSoft,
                )
                Text("Khi Android yêu cầu quyền notification, hãy cho phép để nhận cảnh báo.", color = AppColors.TextSoft)
            }
        }
        item {
            SettingsSectionCard(title = "SePay pull sync", emoji = "🔐", description = "Token được mã hóa trên thiết bị, dùng để kéo giao dịch khi bạn bấm Đồng bộ.") {
                Text(if (tokenSaved) "Trạng thái: đã lưu token" else "Trạng thái: chưa có token", color = AppColors.TextSoft)
                Text("Token chỉ lưu mã hóa trên thiết bị này, dùng khi bạn sync thủ công hoặc app tự sync.", color = AppColors.TextSoft, style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(tokenText, { tokenText = it }, label = { Text("SePay API token") }, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CuteButton("Lưu token") {
                        if (tokenText.isNotBlank()) {
                            application.container.secureTokenStorage.saveSePayToken(tokenText)
                            scope.launch { application.container.domainEventPublisher.publish(DomainEventType.TOKEN_SAVED, "security", actionId = "sepay_token") }
                            tokenText = ""
                            tokenSaved = true
                            message = "Đã lưu token SePay"
                        }
                    }
                    CuteButton("Test kết nối") {
                        val token = tokenText.takeIf { it.isNotBlank() } ?: application.container.secureTokenStorage.getSePayToken()
                        if (token.isNullOrBlank()) {
                            sePayMessage = "Bạn cần nhập hoặc lưu token trước khi test"
                            return@CuteButton
                        }
                        sePayMessage = "Đang kiểm tra SePay..."
                        scope.launch {
                            runCatching { application.container.sePaySyncService.sync(token) }
                                .onSuccess { result -> sePayMessage = "Kết nối OK • mới ${result.imported}, trùng ${result.duplicated}" }
                                .onFailure { error -> sePayMessage = error.message ?: "Không thể kết nối SePay" }
                        }
                    }
                    Button(onClick = {
                        application.container.secureTokenStorage.clearSePayToken()
                        scope.launch { application.container.domainEventPublisher.publish(DomainEventType.TOKEN_CLEARED, "security", actionId = "sepay_token") }
                        tokenSaved = false
                        message = "Đã xóa token SePay"
                    }, colors = ButtonDefaults.buttonColors(containerColor = AppColors.Peach)) { Text("Xóa", color = Color.White) }
                }
                sePayMessage?.let { Text(it, color = AppColors.TextStrong) }
            }
        }
        item {
            SettingsSectionCard(title = "Đồng bộ nền", emoji = "⏱️", description = "WorkManager sẽ sync khi Android cho phép, không phải realtime tuyệt đối.") {
                Text(if (backgroundSyncSettings.enabled) "Đồng bộ nền: đang bật" else "Đồng bộ nền: đang tắt", color = AppColors.TextSoft)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingsChip("Bật", backgroundSyncSettings.enabled) {
                        saveBackgroundSyncSettings(application, scope, backgroundSyncSettings.copy(enabled = !backgroundSyncSettings.enabled))
                    }
                    SettingsChip("Chỉ Wi‑Fi", backgroundSyncSettings.wifiOnly) {
                        saveBackgroundSyncSettings(application, scope, backgroundSyncSettings.copy(wifiOnly = !backgroundSyncSettings.wifiOnly, enabled = true))
                    }
                }
                Text("Khoảng sync", color = AppColors.TextStrong, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(6, 12, 24).forEach { hours ->
                        SettingsChip("${hours}h", backgroundSyncSettings.intervalHours == hours) {
                            saveBackgroundSyncSettings(application, scope, backgroundSyncSettings.copy(intervalHours = hours, enabled = true))
                        }
                    }
                }
                Text("Android có thể trì hoãn worker để tiết kiệm pin; app không hứa cập nhật realtime.", color = AppColors.TextSoft, style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            SettingsSectionCard(title = "Webhook URL optional", emoji = "🌐", description = "Dành cho public endpoint riêng. Không bắt buộc cho app local-first.") {
                Text(if (webhookApiKeySaved) "API Key webhook: đã lưu" else "API Key webhook: chưa lưu", color = AppColors.TextSoft)
                OutlinedTextField(webhookUrlText, { webhookUrlText = it }, label = { Text("Public webhook URL") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(webhookApiKeyText, { webhookApiKeyText = it }, label = { Text("Webhook API Key optional") }, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CuteButton("Lưu") {
                        application.container.webhookSettingsRepository.saveWebhookUrl(webhookUrlText)
                        scope.launch { application.container.domainEventPublisher.publish(DomainEventType.WEBHOOK_SETTINGS_UPDATED, "webhook", actionId = "webhook_url") }
                        if (webhookApiKeyText.isNotBlank()) {
                            application.container.secureTokenStorage.saveWebhookApiKey(webhookApiKeyText)
                            scope.launch { application.container.domainEventPublisher.publish(DomainEventType.TOKEN_SAVED, "security", actionId = "webhook_api_key") }
                            webhookApiKeyText = ""
                            webhookApiKeySaved = true
                        }
                        webhookMessage = "Đã lưu webhook URL/API key"
                    }
                    CuteButton("Test POST") {
                        webhookMessage = "Đang test webhook..."
                        scope.launch {
                            runCatching { application.container.webhookEndpointTester.testPost(webhookUrlText, application.container.secureTokenStorage.getWebhookApiKey()) }
                                .onSuccess { result -> webhookMessage = "HTTP ${result.statusCode}: ${if (result.success) "OK" else "Không OK"} ${result.message}" }
                                .onFailure { error -> webhookMessage = error.message ?: "Test webhook thất bại" }
                        }
                    }
                }
                webhookMessage?.let { Text(it, color = AppColors.TextStrong) }
            }
        }
        item {
            SettingsSectionCard(title = "Bảo mật app", emoji = "🛡️", description = "Chuẩn bị khóa app bằng PIN/biometric ở milestone bảo mật.") {
                Text("Hiện tại token SePay và API key webhook đã lưu trong storage mã hóa của thiết bị.", color = AppColors.TextSoft)
                Text("PIN/biometric và auto-lock sẽ được triển khai ở task T-0902.", color = AppColors.TextSoft)
                Text("Không chia sẻ APK đã cấu hình token cho người khác.", color = AppColors.TextSoft)
            }
        }
        item {
            SettingsSectionCard(title = "Sao lưu & xuất dữ liệu", emoji = "📦", description = "Entry cho backup/export local-first, chưa gửi dữ liệu lên server.") {
                Text("Backup mã hóa và import lại dữ liệu sẽ triển khai ở task T-0904.", color = AppColors.TextSoft)
                Text("Khi có backup, bạn sẽ tự giữ file và mật khẩu backup.", color = AppColors.TextSoft)
                Text("MVP hiện vẫn ưu tiên dữ liệu nằm trên máy.", color = AppColors.TextSoft)
            }
        }
        item {
            SettingsSectionCard(title = "Quyền riêng tư", emoji = "🌿", description = "Minh bạch dữ liệu của bạn đang nằm ở đâu.") {
                Text("Database tài chính chính nằm trên thiết bị Android của bạn.", color = AppColors.TextSoft)
                Text("Token SePay/API key là do bạn nhập và có thể xóa trong Cài đặt.", color = AppColors.TextSoft)
                Text("App không cần server trung tâm trong MVP; webhook URL là hạ tầng riêng nếu bạn tự có.", color = AppColors.TextSoft)
                Text("Xóa token/webhook tại đây; dữ liệu giao dịch local sẽ có luồng backup/xóa riêng ở milestone bảo mật.", color = AppColors.TextSoft)
            }
        }
        item {
            SettingsSectionCard(title = "Nguồn tiền hiện có", emoji = "📌", description = "Tổng tiền trên dashboard được cộng từ các nguồn này.") {
                if (accounts.isEmpty()) {
                    Text("Chưa có nguồn tiền nào.", color = AppColors.TextSoft)
                } else {
                    accounts.forEach { AccountBalancePill(it) }
                }
            }
        }
    }
}


private fun saveBackgroundSyncSettings(
    application: SpendingApplication,
    scope: kotlinx.coroutines.CoroutineScope,
    settings: BackgroundSyncSettings,
): BackgroundSyncSettings {
    val saved = application.container.backgroundSyncSettingsRepository.saveSettings(settings)
    application.container.sePayBackgroundSyncScheduler.apply(saved)
    scope.launch { application.container.domainEventPublisher.publish(DomainEventType.SETTINGS_UPDATED, "settings", actionId = "background_sync") }
    return saved
}

@Composable
private fun SettingsSectionCard(
    title: String,
    emoji: String,
    description: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(emoji, style = MaterialTheme.typography.headlineSmall)
                Column {
                    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = AppColors.TextStrong)
                    Text(description, style = MaterialTheme.typography.bodySmall, color = AppColors.TextSoft)
                }
            }
            content()
        }
    }
}

@Composable
private fun SettingsChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = if (selected) AppColors.Purple else AppColors.Lavender.copy(alpha = 0.35f)),
    ) { Text(if (selected) "✓ $text" else text, color = if (selected) Color.White else AppColors.TextStrong) }
}

private fun Int.floorMod(size: Int): Int = Math.floorMod(this, size)

private fun formatDate(timestamp: Long): String = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("vi", "VN")).format(Date(timestamp))

private fun parseDateTime(value: String): Long? = runCatching {
    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("vi", "VN")).apply { isLenient = false }.parse(value.trim())?.time
}.getOrNull()









































