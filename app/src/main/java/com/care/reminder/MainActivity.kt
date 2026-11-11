package com.care.reminder

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.care.reminder.alarm.NotificationHelper
import com.care.reminder.alarm.ReminderScheduler
import com.care.reminder.alarm.XiaomiPermissionHelper
import com.care.reminder.ui.ReminderApp
import com.care.reminder.ui.theme.CareReminderTheme

class MainActivity : ComponentActivity() {

    private var hasNotificationPermission by mutableStateOf(true)
    private var hasExactAlarmPermission by mutableStateOf(true)
    private var isIgnoringBatteryOptimizations by mutableStateOf(true)
    private var refreshTick by mutableIntStateOf(0)

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            hasNotificationPermission = granted
            refreshTick++
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. 建立高优通知渠道
        NotificationHelper.ensureChannel(this)
        // 2. 冷启动自愈巡检：检查持久化的下次触发时间，若被系统打断或过期则立即重注册 setAlarmClock
        ReminderScheduler.verifyAndHealAll(this)
        refreshPermissionStates()

        // 首次进入主动请求 Android 13+ 通知权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            CareReminderTheme {
                ReminderApp(
                    hasNotificationPermission = hasNotificationPermission,
                    hasExactAlarmPermission = hasExactAlarmPermission,
                    isIgnoringBatteryOptimizations = isIgnoringBatteryOptimizations,
                    externalRefreshTick = refreshTick,
                    onRequestNotificationPermission = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            XiaomiPermissionHelper.openNotificationSettings(this)
                        }
                    },
                    onRequestExactAlarmPermission = {
                        openExactAlarmSettings()
                    },
                    onRequestIgnoreBatteryOptimizations = {
                        XiaomiPermissionHelper.requestIgnoreBatteryOptimizations(this)
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 每次用户打开/切回主界面时执行一次持久化闹钟自愈巡检
        ReminderScheduler.verifyAndHealAll(this)
        refreshPermissionStates()
        refreshTick++
    }

    private fun refreshPermissionStates() {
        hasNotificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        hasExactAlarmPermission = ReminderScheduler.canScheduleExactAlarms(this)
        isIgnoringBatteryOptimizations = XiaomiPermissionHelper.isIgnoringBatteryOptimizations(this)
    }

    private fun openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } catch (_: Exception) {
                XiaomiPermissionHelper.openAppDetailsSettings(this)
            }
        }
    }
}
