package com.spendingapp.core.notification

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.spendingapp.MainActivity
import com.spendingapp.core.domain.BudgetCheckResult
import com.spendingapp.core.money.MoneyVnd
import com.spendingapp.core.repository.NotificationSettingsRepository

class LocalNotificationService(
    private val context: Context,
    private val settingsRepository: NotificationSettingsRepository,
) {
    private val notificationManager = context.getSystemService(NotificationManager::class.java)

    fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        notificationManager.createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_BUDGET, "Hạn mức", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Cảnh báo sắp vượt hoặc đã vượt hạn mức chi tiêu"
                },
                NotificationChannel(CHANNEL_GOAL, "Mục tiêu", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Thông báo khi mục tiêu tài chính có tiến độ mới"
                },
                NotificationChannel(CHANNEL_REMINDER, "Nhắc nhở", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Nhắc cập nhật tiền mặt và kiểm tra sổ chi tiêu"
                },
            ),
        )
    }

    fun notifyBudgetResult(result: BudgetCheckResult) {
        val settings = settingsRepository.getSettings()
        if (!settings.allEnabled || !settings.budgetEnabled) return

        when (result) {
            is BudgetCheckResult.WarningTriggered -> notify(
                id = result.budget.id.toInt().coerceAtLeast(1),
                channelId = CHANNEL_BUDGET,
                title = "Sắp chạm hạn mức rồi 🌷",
                text = "Bạn đã chi ${MoneyVnd(result.spentAmount).format()} trong nhóm này.",
            )
            is BudgetCheckResult.Exceeded -> notify(
                id = result.budget.id.toInt().coerceAtLeast(1) + 10_000,
                channelId = CHANNEL_BUDGET,
                title = "Đã vượt hạn mức 🧁",
                text = "Tổng chi hiện tại là ${MoneyVnd(result.spentAmount).format()}.",
            )
            else -> Unit
        }
    }

    fun notifyGoalCompleted(goalName: String) {
        val settings = settingsRepository.getSettings()
        if (!settings.allEnabled || !settings.goalEnabled) return
        notify(
            id = 20_000 + (goalName.hashCode().let { if (it == Int.MIN_VALUE) 0 else kotlin.math.abs(it) } % 10_000),
            channelId = CHANNEL_GOAL,
            title = "Hoàn thành mục tiêu ✨",
            text = goalName,
        )
    }

    fun notifyCashReminder() {
        val settings = settingsRepository.getSettings()
        if (!settings.allEnabled || !settings.cashReminderEnabled) return
        notify(
            id = CASH_REMINDER_NOTIFICATION_ID,
            channelId = CHANNEL_REMINDER,
            title = "Kiểm tra tiền mặt nhé 👛",
            text = "Mở app để cập nhật số dư tiền mặt cho khớp thực tế.",
        )
    }
    private fun notify(id: Int, channelId: String, title: String, text: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(id, notification)
    }

    companion object {
        const val CHANNEL_BUDGET = "budget"
        const val CHANNEL_GOAL = "goal"
        const val CHANNEL_REMINDER = "reminder"
        private const val CASH_REMINDER_NOTIFICATION_ID = 30_001
    }
}

