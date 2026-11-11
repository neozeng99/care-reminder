package com.care.reminder.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.care.reminder.model.ReminderCategory
import com.care.reminder.model.ReminderItem
import com.care.reminder.model.ScheduleType
import com.care.reminder.ui.theme.MedicineRed

/**
 * 新增 / 编辑提醒弹窗
 * 内置常用快捷模板（早中晚吃药、喝水、久坐活动），长辈或家属点选即可快速配置
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReminderEditDialog(
    initialItem: ReminderItem?,
    onDismiss: () -> Unit,
    onSave: (ReminderItem) -> Unit,
    onDelete: ((ReminderItem) -> Unit)? = null
) {
    val isEditing = initialItem != null
    var category by remember { mutableStateOf(initialItem?.category ?: ReminderCategory.MEDICINE) }
    var title by remember { mutableStateOf(initialItem?.title ?: "早餐后吃降压药") }
    var subtitle by remember { mutableStateOf(initialItem?.subtitle ?: "1片 · 温水送服") }
    var scheduleType by remember {
        mutableStateOf(
            initialItem?.scheduleType ?: when (category) {
                ReminderCategory.MEDICINE -> ScheduleType.FIXED_TIME
                ReminderCategory.WATER, ReminderCategory.SEDENTARY -> ScheduleType.INTERVAL
            }
        )
    }
    var fixedTime by remember { mutableStateOf(initialItem?.fixedTime ?: "08:00") }
    var intervalMinutes by remember { mutableIntStateOf(initialItem?.intervalMinutes ?: 60) }
    var activeStartHour by remember { mutableIntStateOf(initialItem?.activeStartHour ?: 8) }
    var activeEndHour by remember { mutableIntStateOf(initialItem?.activeEndHour ?: 21) }
    var voiceEnabled by remember { mutableStateOf(initialItem?.voiceEnabled ?: true) }

    val commonTimes = listOf("07:00", "08:00", "09:00", "12:00", "12:30", "18:00", "19:00", "21:00")
    val commonIntervals = listOf(30, 45, 60, 90, 120)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isEditing) "修改提醒设置" else "新增健康提醒",
                fontSize = 22.sp,
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
                Text("1. 选择提醒类型", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ReminderCategory.entries.forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = {
                                category = cat
                                if (!isEditing) {
                                    when (cat) {
                                        ReminderCategory.MEDICINE -> {
                                            title = "早餐后吃药"
                                            subtitle = "1片 · 温水送服"
                                            scheduleType = ScheduleType.FIXED_TIME
                                            fixedTime = "08:00"
                                        }
                                        ReminderCategory.WATER -> {
                                            title = "喝一杯温开水"
                                            subtitle = "约 200ml，小口慢饮"
                                            scheduleType = ScheduleType.INTERVAL
                                            intervalMinutes = 60
                                        }
                                        ReminderCategory.SEDENTARY -> {
                                            title = "久坐起身活动筋骨"
                                            subtitle = "站起来走动 5 分钟，伸展腰背"
                                            scheduleType = ScheduleType.INTERVAL
                                            intervalMinutes = 45
                                        }
                                    }
                                }
                            },
                            label = {
                                Text("${cat.emoji} ${cat.label}", fontSize = 16.sp)
                            }
                        )
                    }
                }

                // 快捷模板
                Text("快捷填入常用词：", fontSize = 15.sp, color = Color(0xFF475569))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val presets = when (category) {
                        ReminderCategory.MEDICINE -> listOf(
                            Triple("早餐后吃降压药", "1片 · 温水送服", "08:00"),
                            Triple("午餐后吃降糖药", "饭后半小时服用", "12:30"),
                            Triple("晚餐后吃药/钙片", "按医嘱温水送服", "19:00"),
                            Triple("睡前服药", "服药后安心休息", "21:00")
                        )
                        ReminderCategory.WATER -> listOf(
                            Triple("喝一杯温开水", "约 200ml，小口慢饮", "08:00"),
                            Triple("晨起空腹温水", "润肠通便护血管", "07:30")
                        )
                        ReminderCategory.SEDENTARY -> listOf(
                            Triple("久坐起身活动筋骨", "走动 5 分钟，转转肩膀", "09:00"),
                            Triple("起身眺望远方与踮脚", "放松眼睛与下肢血液循环", "10:00")
                        )
                    }
                    presets.forEach { (presetTitle, presetSub, presetTime) ->
                        FilterChip(
                            selected = title == presetTitle,
                            onClick = {
                                title = presetTitle
                                subtitle = presetSub
                                if (scheduleType == ScheduleType.FIXED_TIME) {
                                    fixedTime = presetTime
                                }
                            },
                            label = { Text(presetTitle, fontSize = 14.sp) }
                        )
                    }
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("提醒标题（如：早餐后降压药）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = subtitle,
                    onValueChange = { subtitle = it },
                    label = { Text("详细说明/剂量（如：1片 温水送服）") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(4.dp))
                Text("2. 提醒时间方式", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ScheduleType.entries.forEach { type ->
                        FilterChip(
                            selected = scheduleType == type,
                            onClick = { scheduleType = type },
                            label = { Text(type.label, fontSize = 16.sp) }
                        )
                    }
                }

                if (scheduleType == ScheduleType.FIXED_TIME) {
                    OutlinedTextField(
                        value = fixedTime,
                        onValueChange = { fixedTime = it },
                        label = { Text("每天提醒时间（格式 HH:mm，如 08:00）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("点选常用时间：", fontSize = 15.sp, color = Color(0xFF475569))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        commonTimes.forEach { t ->
                            FilterChip(
                                selected = fixedTime == t,
                                onClick = { fixedTime = t },
                                label = { Text(t, fontSize = 15.sp) }
                            )
                        }
                    }
                } else {
                    Text("每隔多少分钟提醒一次：", fontSize = 16.sp)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        commonIntervals.forEach { mins ->
                            FilterChip(
                                selected = intervalMinutes == mins,
                                onClick = { intervalMinutes = mins },
                                label = { Text("每 ${mins} 分钟", fontSize = 15.sp) }
                            )
                        }
                    }
                    Text(
                        text = "白天生效时段：%02d:00 - %02d:00（夜间免打扰不响铃）".format(
                            activeStartHour,
                            activeEndHour
                        ),
                        fontSize = 15.sp,
                        color = Color(0xFF475569)
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("中文语音大声播报", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text("响铃时自动用中文念出提醒内容", fontSize = 14.sp, color = Color(0xFF64748B))
                    }
                    Switch(
                        checked = voiceEnabled,
                        onCheckedChange = { voiceEnabled = it }
                    )
                }

                if (isEditing && onDelete != null && initialItem != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = { onDelete(initialItem) },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MedicineRed),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("删除此条提醒", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val normalizedTime = normalizeTimeText(fixedTime)
                    val saved = ReminderItem(
                        id = initialItem?.id ?: java.util.UUID.randomUUID().toString(),
                        title = title.ifBlank { category.label },
                        subtitle = subtitle.trim(),
                        category = category,
                        scheduleType = scheduleType,
                        fixedTime = normalizedTime,
                        intervalMinutes = intervalMinutes.coerceIn(10, 360),
                        activeStartHour = activeStartHour,
                        activeEndHour = activeEndHour,
                        enabled = initialItem?.enabled ?: true,
                        voiceEnabled = voiceEnabled,
                        snoozeUntilMillis = initialItem?.snoozeUntilMillis ?: 0L
                    )
                    onSave(saved)
                }
            ) {
                Text("保存设置", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", fontSize = 17.sp)
            }
        }
    )
}

private fun normalizeTimeText(raw: String): String {
    val clean = raw.replace("：", ":").trim()
    val parts = clean.split(":")
    val h = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: 8
    val m = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0
    return "%02d:%02d".format(h, m)
}
