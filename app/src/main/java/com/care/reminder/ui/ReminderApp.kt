package com.care.reminder.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.care.reminder.alarm.NotificationActionReceiver
import com.care.reminder.alarm.NotificationHelper
import com.care.reminder.alarm.ReminderScheduler
import com.care.reminder.data.ReminderRepository
import com.care.reminder.model.CheckInRecord
import com.care.reminder.model.CheckInStatus
import com.care.reminder.model.ReminderCategory
import com.care.reminder.model.ReminderItem
import com.care.reminder.model.currentTimeText
import com.care.reminder.model.todayDateKey
import com.care.reminder.ui.theme.CareTeal
import com.care.reminder.ui.theme.MedicineRed
import com.care.reminder.ui.theme.MedicineRedBg
import com.care.reminder.ui.theme.SafeGreen
import com.care.reminder.ui.theme.SafeGreenBg
import com.care.reminder.ui.theme.SedentaryAmber
import com.care.reminder.ui.theme.SedentaryAmberBg
import com.care.reminder.ui.theme.WaterBlue
import com.care.reminder.ui.theme.WaterBlueBg
import com.care.reminder.voice.VoiceSpeaker
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReminderApp(
    hasNotificationPermission: Boolean,
    hasExactAlarmPermission: Boolean,
    onRequestNotificationPermission: () -> Unit,
    onRequestExactAlarmPermission: () -> Unit
) {
    val context = LocalContext.current
    val repo = remember { ReminderRepository(context) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var reminders by remember { mutableStateOf(repo.getReminders()) }
    var todayRecords by remember { mutableStateOf(repo.getTodayRecords()) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var selectedCategoryFilter by remember { mutableStateOf<ReminderCategory?>(null) }

    // 弹窗控制：新增/编辑提醒 & 防重复吃药二次确认
    var editingItem by remember { mutableStateOf<ReminderItem?>(null) }
    var showEditDialog by remember { mutableStateOf(false) }
    var duplicateMedicineConfirmItem by remember { mutableStateOf<ReminderItem?>(null) }

    fun refreshData() {
        reminders = repo.getReminders()
        todayRecords = repo.getTodayRecords()
    }

    // 监听来自通知栏按钮或全屏强提醒页的打卡广播，实时刷新界面
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                refreshData()
            }
        }
        val filter = IntentFilter(NotificationActionReceiver.ACTION_DATA_UPDATED)
        ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        onDispose {
            context.unregisterReceiver(receiver)
        }
    }

    val enabledMedicineReminders = reminders.filter {
        it.enabled && it.category == ReminderCategory.MEDICINE
    }
    val completedMedicineCount = enabledMedicineReminders.count { med ->
        todayRecords.any { it.reminderId == med.id && it.status == CheckInStatus.COMPLETED }
    }
    val todayWaterCount = todayRecords.count {
        it.category == ReminderCategory.WATER && it.status == CheckInStatus.COMPLETED
    }
    val todaySedentaryCount = todayRecords.count {
        it.category == ReminderCategory.SEDENTARY && it.status == CheckInStatus.COMPLETED
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "安心健康提醒",
                            fontSize = 23.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                        Text(
                            text = "今日（${todayDateKey()}）· 吃药防忘 · 喝水 · 久坐活动",
                            fontSize = 14.sp,
                            color = Color(0xFFE2E8F0)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CareTeal)
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingItem = null
                    showEditDialog = true
                },
                containerColor = CareTeal,
                contentColor = Color.White
            ) {
                Text(
                    text = "＋ 新增提醒",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // 顶部主导航 Tab：提醒与打卡 vs 今日记录（防重复吃药账本）
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.White,
                contentColor = CareTeal
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Text(
                            text = "📋 今日提醒与打卡",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Text(
                            text = "✅ 今日记录 (${todayRecords.size})",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                )
            }

            if (selectedTab == 0) {
                val filteredReminders = reminders.filter {
                    selectedCategoryFilter == null || it.category == selectedCategoryFilter
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // 权限自检提醒条
                    if (!hasNotificationPermission || !hasExactAlarmPermission) {
                        item {
                            PermissionBannerCard(
                                hasNotificationPermission = hasNotificationPermission,
                                hasExactAlarmPermission = hasExactAlarmPermission,
                                onRequestNotificationPermission = onRequestNotificationPermission,
                                onRequestExactAlarmPermission = onRequestExactAlarmPermission
                            )
                        }
                    }

                    // 今日吃药、喝水、活动总览看板
                    item {
                        TodayOverviewDashboard(
                            completedMedicineCount = completedMedicineCount,
                            totalMedicineCount = enabledMedicineReminders.size,
                            todayWaterCount = todayWaterCount,
                            todaySedentaryCount = todaySedentaryCount
                        )
                    }

                    // 分类筛选栏
                    item {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = selectedCategoryFilter == null,
                                onClick = { selectedCategoryFilter = null },
                                label = { Text("全部 (${reminders.size})", fontSize = 16.sp) }
                            )
                            ReminderCategory.entries.forEach { cat ->
                                val count = reminders.count { it.category == cat }
                                FilterChip(
                                    selected = selectedCategoryFilter == cat,
                                    onClick = { selectedCategoryFilter = cat },
                                    label = { Text("${cat.emoji} ${cat.label} ($count)", fontSize = 16.sp) }
                                )
                            }
                        }
                    }

                    // 提醒卡片列表
                    items(filteredReminders, key = { it.id }) { item ->
                        val completedTodayList = todayRecords.filter {
                            it.reminderId == item.id && it.status == CheckInStatus.COMPLETED
                        }

                        ReminderCard(
                            item = item,
                            completedTodayRecords = completedTodayList,
                            onToggleEnabled = { enabled ->
                                val updated = item.copy(
                                    enabled = enabled,
                                    snoozeUntilMillis = if (enabled) item.snoozeUntilMillis else 0L
                                )
                                repo.upsertReminder(updated)
                                if (enabled) {
                                    ReminderScheduler.scheduleReminder(context, updated)
                                } else {
                                    ReminderScheduler.cancelReminder(context, updated.id)
                                    NotificationHelper.cancelNotification(context, updated.id)
                                }
                                refreshData()
                            },
                            onCompleteClick = {
                                // 防重复吃药检查：如果是吃药提醒，且今天已经点过“我已吃药”，先弹大字警告框确认！
                                if (item.category == ReminderCategory.MEDICINE && completedTodayList.isNotEmpty()) {
                                    duplicateMedicineConfirmItem = item
                                } else {
                                    VoiceSpeaker.stopAll()
                                    NotificationHelper.cancelNotification(context, item.id)
                                    repo.updateSnoozeUntil(item.id, 0L)
                                    val rec = repo.recordAction(item, CheckInStatus.COMPLETED)
                                    ReminderScheduler.scheduleReminder(context, item.copy(snoozeUntilMillis = 0L))
                                    refreshData()
                                    scope.launch {
                                        snackbarHostState.showSnackbar(
                                            "✓ 已记录：${item.title}（${rec.timeText} 已完成）"
                                        )
                                    }
                                }
                            },
                            onSnooze10MinClick = {
                                VoiceSpeaker.stopAll()
                                NotificationHelper.cancelNotification(context, item.id)
                                repo.recordAction(item, CheckInStatus.SNOOZED)
                                val nextMillis = ReminderScheduler.scheduleSnooze(context, item, minutes = 10)
                                val nextTimeStr = currentTimeText(nextMillis)
                                refreshData()
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        "⏰ 已延后 10 分钟，将于 $nextTimeStr 再次提醒：${item.title}"
                                    )
                                }
                            },
                            onSpeakClick = {
                                VoiceSpeaker.speak(
                                    context,
                                    item.buildSpeechText(completedTodayList.isNotEmpty()),
                                    vibrate = false
                                )
                            },
                            onEditClick = {
                                editingItem = item
                                showEditDialog = true
                            }
                        )
                    }
                }
            } else {
                // Tab 1：今日打卡历史与防重服账本
                TodayRecordHistoryList(
                    records = todayRecords,
                    onUndoRecord = { record ->
                        repo.deleteCheckInRecord(record.id)
                        refreshData()
                        scope.launch {
                            snackbarHostState.showSnackbar("已撤销「${record.reminderTitle}」${record.timeText} 的记录")
                        }
                    }
                )
            }
        }
    }

    // 新增 / 编辑弹窗
    if (showEditDialog) {
        ReminderEditDialog(
            initialItem = editingItem,
            onDismiss = { showEditDialog = false },
            onSave = { saved ->
                repo.upsertReminder(saved)
                ReminderScheduler.scheduleReminder(context, saved)
                refreshData()
                showEditDialog = false
                scope.launch {
                    snackbarHostState.showSnackbar("已保存提醒：${saved.title}")
                }
            },
            onDelete = { toDelete ->
                ReminderScheduler.cancelReminder(context, toDelete.id)
                NotificationHelper.cancelNotification(context, toDelete.id)
                repo.deleteReminder(toDelete.id)
                refreshData()
                showEditDialog = false
                scope.launch {
                    snackbarHostState.showSnackbar("已删除提醒：${toDelete.title}")
                }
            }
        )
    }

    // 防重复吃药二次确认弹窗
    duplicateMedicineConfirmItem?.let { medItem ->
        val existingTimes = todayRecords
            .filter { it.reminderId == medItem.id && it.status == CheckInStatus.COMPLETED }
            .joinToString("、") { it.timeText }

        AlertDialog(
            onDismissRequest = { duplicateMedicineConfirmItem = null },
            title = {
                Text(
                    text = "⚠️ 防重复吃药提醒",
                    color = MedicineRed,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            },
            text = {
                Text(
                    text = "系统记录显示：\n您今天在【$existingTimes】已经点过「${medItem.title}」的已吃药按钮了！\n\n请仔细回想或核对药盒，千万不要重复吃药！",
                    fontSize = 20.sp,
                    lineHeight = 28.sp,
                    color = Color(0xFF0F172A)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        duplicateMedicineConfirmItem = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SafeGreen)
                ) {
                    Text("我知道了（不重复记录）", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        repo.recordAction(medItem, CheckInStatus.COMPLETED)
                        refreshData()
                        duplicateMedicineConfirmItem = null
                    }
                ) {
                    Text("仍要补记一次", fontSize = 16.sp, color = Color(0xFF64748B))
                }
            }
        )
    }
}

@Composable
private fun PermissionBannerCard(
    hasNotificationPermission: Boolean,
    hasExactAlarmPermission: Boolean,
    onRequestNotificationPermission: () -> Unit,
    onRequestExactAlarmPermission: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = SedentaryAmberBg)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "🔔 请开启关键提醒权限，确保准点响铃",
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF92400E)
            )
            if (!hasNotificationPermission) {
                Button(
                    onClick = onRequestNotificationPermission,
                    colors = ButtonDefaults.buttonColors(containerColor = SedentaryAmber),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("点击开启「通知栏弹窗权限」", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
            }
            if (!hasExactAlarmPermission) {
                Button(
                    onClick = onRequestExactAlarmPermission,
                    colors = ButtonDefaults.buttonColors(containerColor = SedentaryAmber),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("点击开启「准点闹钟权限」", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun TodayOverviewDashboard(
    completedMedicineCount: Int,
    totalMedicineCount: Int,
    todayWaterCount: Int,
    todaySedentaryCount: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "今日健康打卡总览",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                val allMedDone = totalMedicineCount > 0 && completedMedicineCount >= totalMedicineCount
                Box(
                    modifier = Modifier
                        .background(
                            if (allMedDone) SafeGreenBg else MedicineRedBg,
                            RoundedCornerShape(50)
                        )
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (allMedDone) "✓ 今日药已吃齐" else "待吃药 ${totalMedicineCount - completedMedicineCount} 次",
                        color = if (allMedDone) SafeGreen else MedicineRed,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SummaryStatTile(
                    modifier = Modifier.weight(1f),
                    emoji = "💊",
                    label = "今日吃药",
                    valueText = "$completedMedicineCount / $totalMedicineCount",
                    bgColor = if (completedMedicineCount >= totalMedicineCount && totalMedicineCount > 0) SafeGreenBg else MedicineRedBg,
                    valueColor = if (completedMedicineCount >= totalMedicineCount && totalMedicineCount > 0) SafeGreen else MedicineRed
                )
                SummaryStatTile(
                    modifier = Modifier.weight(1f),
                    emoji = "💧",
                    label = "今日喝水",
                    valueText = "${todayWaterCount} 次",
                    bgColor = WaterBlueBg,
                    valueColor = WaterBlue
                )
                SummaryStatTile(
                    modifier = Modifier.weight(1f),
                    emoji = "🚶",
                    label = "起身活动",
                    valueText = "${todaySedentaryCount} 次",
                    bgColor = SedentaryAmberBg,
                    valueColor = SedentaryAmber
                )
            }
        }
    }
}

@Composable
private fun SummaryStatTile(
    modifier: Modifier,
    emoji: String,
    label: String,
    valueText: String,
    bgColor: Color,
    valueColor: Color
) {
    Column(
        modifier = modifier
            .background(bgColor, RoundedCornerShape(16.dp))
            .padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "$emoji $label", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF334155))
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = valueText, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = valueColor)
    }
}

@Composable
private fun ReminderCard(
    item: ReminderItem,
    completedTodayRecords: List<CheckInRecord>,
    onToggleEnabled: (Boolean) -> Unit,
    onCompleteClick: () -> Unit,
    onSnooze10MinClick: () -> Unit,
    onSpeakClick: () -> Unit,
    onEditClick: () -> Unit
) {
    val isCompletedToday = completedTodayRecords.isNotEmpty()
    val latestTime = completedTodayRecords.firstOrNull()?.timeText
    val nowMillis = System.currentTimeMillis()
    val isSnoozedActive = item.snoozeUntilMillis > nowMillis

    val borderColor = when {
        item.category == ReminderCategory.MEDICINE && isCompletedToday -> SafeGreen
        item.category == ReminderCategory.MEDICINE && !isCompletedToday -> MedicineRed.copy(alpha = 0.45f)
        else -> Color(0xFFE2E8F0)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(2.dp, borderColor, RoundedCornerShape(22.dp)),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 第一行：分类标签 + 提醒规则 + 启用开关
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val badgeBg = when (item.category) {
                        ReminderCategory.MEDICINE -> MedicineRedBg
                        ReminderCategory.WATER -> WaterBlueBg
                        ReminderCategory.SEDENTARY -> SedentaryAmberBg
                    }
                    val badgeFg = when (item.category) {
                        ReminderCategory.MEDICINE -> MedicineRed
                        ReminderCategory.WATER -> WaterBlue
                        ReminderCategory.SEDENTARY -> SedentaryAmber
                    }
                    Box(
                        modifier = Modifier
                            .background(badgeBg, RoundedCornerShape(10.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "${item.category.emoji} ${item.category.label}",
                            color = badgeFg,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = item.scheduleSummaryText(),
                        color = Color(0xFF475569),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Switch(
                    checked = item.enabled,
                    onCheckedChange = onToggleEnabled
                )
            }

            // 第二行：大字标题与副标题（剂量/动作说明）
            Column {
                Text(
                    text = item.title,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (item.enabled) Color(0xFF0F172A) else Color(0xFF94A3B8)
                )
                if (item.subtitle.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = item.subtitle,
                        fontSize = 18.sp,
                        color = Color(0xFF475569)
                    )
                }
            }

            // 第三行：今日打卡状态横幅（核心防重复吃药视觉提示！）
            if (item.category == ReminderCategory.MEDICINE) {
                if (isCompletedToday) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SafeGreenBg, RoundedCornerShape(14.dp))
                            .padding(14.dp)
                    ) {
                        Text(
                            text = "✅ 今日已吃药（${completedTodayRecords.joinToString("、") { it.timeText }} 已确认）· 请勿重复服药",
                            color = SafeGreen,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MedicineRedBg, RoundedCornerShape(14.dp))
                            .padding(14.dp)
                    ) {
                        Text(
                            text = "⏳ 今天还没打卡吃药（吃过药后请点下方绿色按钮）",
                            color = MedicineRed,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            } else if (isCompletedToday) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SafeGreenBg, RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = "✓ 今日已完成 ${completedTodayRecords.size} 次（最近一次：$latestTime）",
                        color = SafeGreen,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // 若当前处于“延后10分钟”倒计时状态，明确显示下次响铃时间
            if (isSnoozedActive) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SedentaryAmberBg, RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = "⏰ 已设置延后，将于 ${currentTimeText(item.snoozeUntilMillis)} 再次提醒",
                        color = Color(0xFF92400E),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // 第四行：超大操作按钮（「我已吃药/喝水/活动」+「延后10分钟」）
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onCompleteClick,
                    modifier = Modifier
                        .weight(1.35f)
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (item.category == ReminderCategory.MEDICINE && isCompletedToday) {
                            Color(0xFF16A34A)
                        } else {
                            CareTeal
                        }
                    )
                ) {
                    Text(
                        text = if (item.category == ReminderCategory.MEDICINE && isCompletedToday) {
                            "✓ 今日已吃过"
                        } else {
                            "✓ ${item.category.actionConfirmText}"
                        },
                        fontSize = 19.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                }

                OutlinedButton(
                    onClick = onSnooze10MinClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = "⏰ 延后10分",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = SedentaryAmber
                    )
                }
            }

            // 第五行：语音播报试听 & 编辑设置
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(onClick = onSpeakClick) {
                    Text("🔊 语音念一遍", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
                TextButton(onClick = onEditClick) {
                    Text("⚙️ 修改时间/说明", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun TodayRecordHistoryList(
    records: List<CheckInRecord>,
    onUndoRecord: (CheckInRecord) -> Unit
) {
    if (records.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("📋", fontSize = 48.sp)
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "今天还没有打卡记录",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "当您点击「我已吃药」「我已喝水」或「我已活动」后，这里会清楚记下几点几分完成，再也不用担心忘记今天吃过药没有。",
                    fontSize = 17.sp,
                    color = Color(0xFF64748B),
                    textAlign = TextAlign.Center
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SafeGreenBg)
            ) {
                Text(
                    text = "💡 小贴士：不确定今天吃过药没有？看下面带时间点的绿色记录就一清二楚。如果是不小心点错了，可以点右侧「撤销」。",
                    modifier = Modifier.padding(14.dp),
                    fontSize = 16.sp,
                    color = SafeGreen,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        items(records, key = { it.id }) { rec ->
            val isCompleted = rec.status == CheckInStatus.COMPLETED
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .background(
                                    if (isCompleted) SafeGreenBg else SedentaryAmberBg,
                                    RoundedCornerShape(14.dp)
                                )
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = rec.timeText,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isCompleted) SafeGreen else SedentaryAmber
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = "${rec.category.emoji} ${rec.reminderTitle}",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (isCompleted) "✓ ${rec.category.actionConfirmText}（已确认）" else "⏰ 点击了延后 10 分钟",
                                fontSize = 16.sp,
                                color = if (isCompleted) SafeGreen else SedentaryAmber,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    TextButton(onClick = { onUndoRecord(rec) }) {
                        Text(
                            text = "撤销",
                            fontSize = 16.sp,
                            color = MedicineRed,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
