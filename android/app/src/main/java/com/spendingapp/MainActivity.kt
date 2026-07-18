package com.spendingapp

import android.os.Bundle
import androidx.activity.ComponentActivity
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
import com.spendingapp.core.domain.BudgetCheckResult
import com.spendingapp.core.model.AccountType
import com.spendingapp.core.model.GoalPriority
import com.spendingapp.core.model.GoalStatus
import com.spendingapp.core.model.TransactionType
import com.spendingapp.core.repository.BudgetRepository
import com.spendingapp.core.sync.ImportResult
import com.spendingapp.core.money.MoneyVnd
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SpendingAppRoot() }
    }
}

private enum class MainTab(val label: String) {
    Dashboard("Tổng quan"), Transactions("Giao dịch"), Budgets("Hạn mức"), Goals("Mục tiêu"), Settings("Cài đặt")
}

@Composable
private fun SpendingAppRoot() {
    MaterialTheme {
        var selectedTab by remember { mutableStateOf(MainTab.Dashboard) }
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
    val totalBalance by application.container.database.accountDao().observeTotalBalance().collectAsState(initial = 0L)
    val accounts by application.container.accountRepository.observeAccounts().collectAsState(initial = emptyList())
    val transactions by application.container.transactionRepository.observeTransactions().collectAsState(initial = emptyList())
    val syncStates by application.container.database.syncStateDao().observeAll().collectAsState(initial = emptyList())
    val goals by application.container.goalRepository.observeGoals().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var syncMessage by remember { mutableStateOf<String?>(null) }
    val income = transactions.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
    val expense = transactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
    val pending = transactions.count { it.categoryId == null }
    val featuredGoal = goals.firstOrNull { it.status == GoalStatus.ACTIVE }

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
            TotalBalanceCircleCard(totalBalance = totalBalance.coerceAtLeast(0))
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Nguồn tiền", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = AppColors.TextStrong)
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
                RoundStatCard("Thu", MoneyVnd(income).format(), 0.72f, AppColors.Mint, Modifier.weight(1f))
                RoundStatCard("Chi", MoneyVnd(expense).format(), 0.48f, AppColors.Peach, Modifier.weight(1f))
                RoundStatCard("Chờ", pending.toString(), if (pending > 0) 0.85f else 0.12f, AppColors.Lavender, Modifier.weight(1f))
            }
        }
        featuredGoal?.let { goal ->
            item { GoalSummaryCard(goal) }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Đồng bộ & giao dịch", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Đã lưu local: ${transactions.size} giao dịch", color = AppColors.TextSoft)
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

    LazyColumn(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text("Giao dịch", style = MaterialTheme.typography.headlineMedium) }
        item { AddTransactionCard(accounts = accounts, categories = categories) }
        item { Text("Lịch sử", style = MaterialTheme.typography.titleLarge) }
        if (transactions.isEmpty()) {
            item { Text("Chưa có giao dịch nào. Hãy thêm giao dịch đầu tiên để bắt đầu theo dõi chi tiêu.") }
        } else {
            items(transactions, key = { it.id }) { transaction ->
                TransactionRow(
                    transaction = transaction,
                    accountName = accounts.firstOrNull { it.id == transaction.accountId }?.name ?: "Nguồn #${transaction.accountId}",
                    categoryName = transaction.categoryId?.let { categoryId -> categories.firstOrNull { it.id == categoryId }?.name } ?: "Chưa phân loại",
                )
            }
        }
    }
}

@Composable
private fun AddTransactionCard(accounts: List<AccountEntity>, categories: List<CategoryEntity>) {
    val application = LocalContext.current.applicationContext as SpendingApplication
    val scope = rememberCoroutineScope()
    var type by remember { mutableStateOf(TransactionType.EXPENSE) }
    var selectedAccountIndex by remember { mutableStateOf(0) }
    var selectedCategoryIndex by remember { mutableStateOf(0) }
    var amountText by remember { mutableStateOf("") }
    var noteText by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }

    val selectedAccount = accounts.getOrNull(selectedAccountIndex.coerceIn(0, (accounts.size - 1).coerceAtLeast(0)))
    val selectableCategories = categories.filter { category ->
        when (type) {
            TransactionType.INCOME -> category.type.name != "EXPENSE"
            TransactionType.EXPENSE -> category.type.name != "INCOME"
            else -> true
        }
    }
    val selectedCategory = selectableCategories.getOrNull(selectedCategoryIndex.coerceIn(0, (selectableCategories.size - 1).coerceAtLeast(0)))

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Thêm giao dịch thủ công", style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { type = TransactionType.EXPENSE; selectedCategoryIndex = 0 }) { Text(if (type == TransactionType.EXPENSE) "✓ Chi" else "Chi") }
                Button(onClick = { type = TransactionType.INCOME; selectedCategoryIndex = 0 }) { Text(if (type == TransactionType.INCOME) "✓ Thu" else "Thu") }
            }
            PickerRow("Nguồn tiền", selectedAccount?.name ?: "Chưa có nguồn tiền", { if (accounts.isNotEmpty()) selectedAccountIndex = (selectedAccountIndex - 1).floorMod(accounts.size) }, { if (accounts.isNotEmpty()) selectedAccountIndex = (selectedAccountIndex + 1).floorMod(accounts.size) })
            PickerRow("Hạng mục", selectedCategory?.name ?: "Chưa có hạng mục", { if (selectableCategories.isNotEmpty()) selectedCategoryIndex = (selectedCategoryIndex - 1).floorMod(selectableCategories.size) }, { if (selectableCategories.isNotEmpty()) selectedCategoryIndex = (selectedCategoryIndex + 1).floorMod(selectableCategories.size) })
            OutlinedTextField(value = amountText, onValueChange = { amountText = it.filter(Char::isDigit) }, label = { Text("Số tiền VND") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = noteText, onValueChange = { noteText = it }, label = { Text("Ghi chú") }, modifier = Modifier.fillMaxWidth())
            Button(
                enabled = selectedAccount != null && amountText.toLongOrNull()?.let { it > 0 } == true,
                onClick = {
                    val account = selectedAccount ?: return@Button
                    val amount = amountText.toLongOrNull() ?: return@Button
                    scope.launch {
                        runCatching {
                            application.container.transactionRepository.addManualTransaction(account.id, selectedCategory?.id, type, amount, noteText, System.currentTimeMillis())
                        }.onSuccess { result ->
                            amountText = ""
                            noteText = ""
                            message = result.budgetMessage() ?: "Đã lưu giao dịch"
                        }.onFailure { error -> message = error.message ?: "Không thể lưu giao dịch" }
                    }
                },
            ) { Text("Lưu giao dịch") }
            message?.let { Text(it) }
        }
    }
}

private fun ImportResult.budgetMessage(): String? = when (this) {
    is ImportResult.Imported -> when (val budgetResult = budgetCheckResult) {
        is BudgetCheckResult.WarningTriggered -> "Đã lưu giao dịch • Sắp chạm hạn mức ${MoneyVnd(budgetResult.spentAmount).format()}"
        is BudgetCheckResult.Exceeded -> "Đã lưu giao dịch • Đã vượt hạn mức ${MoneyVnd(budgetResult.spentAmount).format()}"
        else -> null
    }
    is ImportResult.Duplicate -> null
}
@Composable
private fun PickerRow(label: String, value: String, onPrevious: () -> Unit, onNext: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onPrevious) { Text("‹") }
            Text(value, Modifier.weight(1f).padding(top = 12.dp))
            Button(onClick = onNext) { Text("›") }
        }
    }
}

@Composable
private fun TransactionRow(transaction: TransactionEntity, accountName: String, categoryName: String) {
    val sign = if (transaction.type == TransactionType.EXPENSE) "-" else "+"
    val amount = MoneyVnd(transaction.amount).format()
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(categoryName, style = MaterialTheme.typography.titleMedium)
                Text("$sign$amount", style = MaterialTheme.typography.titleMedium)
            }
            Text(accountName)
            transaction.description?.let { Text(it) }
            Text(formatDate(transaction.occurredAt), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun CuteGradientCard(content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .background(Brush.linearGradient(listOf(AppColors.Purple, AppColors.Pink)))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            content = content,
        )
    }
}

@Composable
private fun RoundStatCard(title: String, value: String, progress: Float, color: Color, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, color = color, trackColor = color.copy(alpha = 0.18f), strokeWidth = 7.dp)
                Text(title, style = MaterialTheme.typography.labelMedium, color = AppColors.TextSoft)
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
}

@Composable
private fun TotalBalanceCircleCard(totalBalance: Long) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .background(Brush.linearGradient(listOf(AppColors.Purple, AppColors.Pink)))
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Tổng tiền hiện có", color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.titleMedium)
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { 1f },
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.24f),
                    strokeWidth = 10.dp,
                    modifier = Modifier.size(148.dp),
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(MoneyVnd(totalBalance).format(), color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("tất cả nguồn", color = Color.White.copy(alpha = 0.78f), style = MaterialTheme.typography.bodySmall)
                }
            }
            Text("Tiền mặt + ngân hàng + nguồn khác", color = Color.White.copy(alpha = 0.82f), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun AccountBalancePill(account: AccountEntity) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = { 1f },
                        color = if (account.type == AccountType.CASH) AppColors.Mint else AppColors.Lavender,
                        trackColor = AppColors.Lavender.copy(alpha = 0.12f),
                        strokeWidth = 5.dp,
                        modifier = Modifier.size(38.dp),
                    )
                    Text(if (account.type == AccountType.CASH) "💵" else "🏦", style = MaterialTheme.typography.bodySmall)
                }
                Column {
                    Text(account.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = AppColors.TextStrong)
                    Text(account.type.name, style = MaterialTheme.typography.bodySmall, color = AppColors.TextSoft)
                }
            }
            Text(MoneyVnd(account.balance.coerceAtLeast(0)).format(), fontWeight = FontWeight.Bold, color = AppColors.TextStrong)
        }
    }
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
                            .onSuccess { localMessage = "Đã cập nhật tiến độ" }
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

@Composable
private fun PlaceholderScreen(title: String, description: String) {
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Text(description)
    }
}

@Composable
private fun SettingsScreen() {
    val application = LocalContext.current.applicationContext as SpendingApplication
    val scope = rememberCoroutineScope()
    val accounts by application.container.accountRepository.observeAccounts().collectAsState(initial = emptyList())
    var accountType by remember { mutableStateOf(AccountType.BANK) }
    var nameText by remember { mutableStateOf("") }
    var providerText by remember { mutableStateOf("") }
    var balanceText by remember { mutableStateOf("") }
    var selectedUpdateAccountIndex by remember { mutableStateOf(0) }
    var updateBalanceText by remember { mutableStateOf("") }
    var updateReasonText by remember { mutableStateOf("Cập nhật số dư thực tế") }
    var tokenText by remember { mutableStateOf("") }
    var tokenSaved by remember { mutableStateOf(application.container.secureTokenStorage.hasSePayToken()) }
    var webhookUrlText by remember { mutableStateOf(application.container.webhookSettingsRepository.getWebhookUrl()) }
    var webhookApiKeyText by remember { mutableStateOf("") }
    var webhookApiKeySaved by remember { mutableStateOf(application.container.secureTokenStorage.hasWebhookApiKey()) }
    var webhookMessage by remember { mutableStateOf<String?>(null) }
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
                Text("Cài đặt", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = AppColors.TextStrong)
                Text("Quản lý nguồn tiền, đồng bộ và quyền riêng tư", color = AppColors.TextSoft)
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
            SettingsSectionCard(title = "Cập nhật số dư", emoji = "🧾", description = "Điều chỉnh số dư thực tế và lưu BalanceLog để truy vết.") {
                val selectedUpdateAccount = accounts.getOrNull(selectedUpdateAccountIndex.coerceIn(0, (accounts.size - 1).coerceAtLeast(0)))
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
                OutlinedTextField(updateReasonText, { updateReasonText = it }, label = { Text("Lý do cập nhật") }, modifier = Modifier.fillMaxWidth())
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
            SettingsSectionCard(title = "SePay pull sync", emoji = "🔐", description = "Token được mã hóa trên thiết bị, dùng để kéo giao dịch khi bạn bấm Đồng bộ.") {
                Text(if (tokenSaved) "Trạng thái: đã lưu token" else "Trạng thái: chưa có token", color = AppColors.TextSoft)
                OutlinedTextField(tokenText, { tokenText = it }, label = { Text("SePay API token") }, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CuteButton("Lưu token") {
                        if (tokenText.isNotBlank()) {
                            application.container.secureTokenStorage.saveSePayToken(tokenText)
                            tokenText = ""
                            tokenSaved = true
                            message = "Đã lưu token SePay"
                        }
                    }
                    Button(onClick = {
                        application.container.secureTokenStorage.clearSePayToken()
                        tokenSaved = false
                        message = "Đã xóa token SePay"
                    }, colors = ButtonDefaults.buttonColors(containerColor = AppColors.Peach)) { Text("Xóa", color = Color.White) }
                }
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
                        if (webhookApiKeyText.isNotBlank()) {
                            application.container.secureTokenStorage.saveWebhookApiKey(webhookApiKeyText)
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






