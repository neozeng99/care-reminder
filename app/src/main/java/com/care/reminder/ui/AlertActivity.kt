package com.care.reminder.ui

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.care.reminder.alarm.NotificationActionReceiver
import com.care.reminder.alarm.NotificationHelper
import com.care.reminder.alarm.ReminderScheduler
import com.care.reminder.data.ReminderRepository
import com.care.reminder.model.CheckInStatus
import com.care.reminder.model.ReminderCategory
import com.care.reminder.model.ReminderItem
import com.care.reminder.model.currentTimeText
import com.care.reminder.ui.theme.CareReminderTheme
import com.care.reminder.ui.theme.MedicineRed
import com.care.reminder.ui.theme.SafeGreen
import com.care.reminder.ui.theme.SafeGreenBg
import com.care.reminder.ui.theme.SedentaryAmber
import com.care.reminder.ui.theme.WaterBlue
import com.care.reminder.voice.VoiceSpeaker

/**
 * 锁屏/全屏强提醒弹窗界面
 * 专为老年人设计：超大字体、只保留最核心的「我已完成」和「延后10分钟」两个超大按钮
 */
class AlertActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        turnScreenOnAndKeyguardOff()

        val reminderId = intent?.getStringExtra(ReminderScheduler.EXTRA_REMINDER_ID)
        val repo = ReminderRepository(this)
        val item = reminderId?.let { repo.getReminderById(it) }

        if (item == null) {
            finish()
            return
        }

        val todayCompleted = repo.getTodayCompletedRecordsFor(item.id)

        setContent {
            CareReminderTheme {
                AlertFullScreenContent(
                    item = item,
                    alreadyCompletedTimes = todayCompleted.map { it.timeText },
                    onComplete = {
                        VoiceSpeaker.stopAll()
                        NotificationHelper.cancelNotification(this, item.id)
                        repo.updateSnoozeUntil(item.id, 0L)
                        repo.recordAction(item, CheckInStatus.COMPLETED)
                        ReminderScheduler.scheduleReminder(this, item.copy(snoozeUntilMillis = 0L))
                        sendBroadcast(
                            Intent(NotificationActionReceiver.ACTION_DATA_UPDATED).setPackage(packageName)
                        )
                        finish()
                    },
                    onSnooze10Min = {
                        VoiceSpeaker.stopAll()
                        NotificationHelper.cancelNotification(this, item.id)
                        repo.recordAction(item, CheckInStatus.SNOOZED)
                        ReminderScheduler.scheduleSnooze(this, item, minutes = 10)
                        sendBroadcast(
                            Intent(NotificationActionReceiver.ACTION_DATA_UPDATED).setPackage(packageName)
                        )
                        finish()
                    },
                    onReplayVoice = {
                        VoiceSpeaker.speak(
                            this,
                            item.buildSpeechText(todayCompleted.isNotEmpty()),
                            vibrate = false
                        )
                    }
                )
            }
        }
    }

    private fun turnScreenOnAndKeyguardOff() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            keyguardManager.requestDismissKeyguard(this, null)
        }
    }
}

@Composable
private fun AlertFullScreenContent(
    item: ReminderItem,
    alreadyCompletedTimes: List<String>,
    onComplete: () -> Unit,
    onSnooze10Min: () -> Unit,
    onReplayVoice: () -> Unit
) {
    val nowText = remember { currentTimeText() }
    val accentColor = when (item.category) {
        ReminderCategory.MEDICINE -> MedicineRed
        ReminderCategory.WATER -> WaterBlue
        ReminderCategory.SEDENTARY -> SedentaryAmber
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF0F172A)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 顶部时间与类别标识
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .background(accentColor, RoundedCornerShape(50))
                        .padding(horizontal = 24.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = "${item.category.emoji} ${item.category.label}",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = nowText,
                    color = Color.White,
                    fontSize = 54.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            // 中间核心提醒内容卡片
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = item.title,
                        color = Color(0xFF0F172A),
                        fontSize = 32.sp,
                        lineHeight = 40.sp,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center
                    )

                    if (item.subtitle.isNotBlank()) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = item.subtitle,
                            color = Color(0xFF334155),
                            fontSize = 22.sp,
                            lineHeight = 30.sp,
                            textAlign = TextAlign.Center
                        )
                    }

                    // 防重复吃药醒目提示
                    if (alreadyCompletedTimes.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(18.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(SafeGreenBg, RoundedCornerShape(16.dp))
                                .padding(16.dp)
                        ) {
                            Text(
                                text = "⚠️ 注意：今天 ${alreadyCompletedTimes.joinToString("、")} 已经打卡过，请核对是否已吃过药，切勿重复服药！",
                                color = SafeGreen,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                    OutlinedButton(
                        onClick = onReplayVoice,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "🔊 再读一遍语音提醒",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 6.dp)
                        )
                    }
                }
            }

            // 底部超大操作按钮：已完成 vs 延后10分钟
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(
                    onClick = onComplete,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(74.dp),
                    shape = RoundedCornerShape(22.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SafeGreen)
                ) {
                    Text(
                        text = "✓ ${item.category.actionConfirmText}（记录今日完成）",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                }

                Button(
                    onClick = onSnooze10Min,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SedentaryAmber)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "⏰ 延后 10 分钟再提醒我",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}
