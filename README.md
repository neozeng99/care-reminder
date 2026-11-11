# 安心健康提醒 (CareReminder) — 适老化安卓吃药/喝水/久坐提醒 App

专为长辈与日常健康管理打造的原生 Android 应用（Kotlin + Jetpack Compose），核心解决 **「老人按时吃药防忘、防止重复吃药、定时喝水、久坐起身活动」** 场景。

---

## 一、核心功能特性

1. **三大健康提醒场景（开箱内置默认方案）**
   - 💊 **吃药提醒（定点提醒）**：内置早（08:00）、中（12:30）、晚（19:00）三餐后吃药提醒，支持自定义药名与剂量说明。
   - 💧 **喝水提醒（间隔循环）**：默认每 60 分钟提醒喝一杯温水（08:00 - 21:00 白天生效，夜间自动免打扰）。
   - 🚶 **久坐起身活动（间隔循环）**：默认每 45 分钟提醒起身活动筋骨（09:00 - 20:00 生效）。

2. **今日打卡与防重复吃药保护（核心安全设计）**
   - **醒目状态标识**：吃过药点击「✓ 我已吃药」后，卡片立即变为醒目绿色横幅：`✅ 今日已吃药（08:12 已确认）· 请勿重复服药`。
   - **二次误触拦截**：如果当天已经点过「我已吃药」，老人忘记了再次点击时，会弹出大字红色警告框：**「⚠️ 防重复吃药提醒：系统记录显示您今天在 08:12 已经吃过药了，请核对药盒切勿重复服药！」**
   - **提前服药免打扰**：若老人在闹钟响铃前已经主动打卡「我已吃药」，当天该次闹钟到点会自动静默跳过，避免再次响铃误导老人吃第二遍。
   - **今日记录账本（支持撤销）**：独立「✅ 今日记录」标签页，按时间倒序展示今天每一次吃药、喝水、活动的精确时间点；如不小心误触可一键「撤销」。

3. **一键「延后 10 分钟」与锁屏全屏强提醒**
   - 闹钟触发时自动点亮屏幕弹出超大字体强提醒界面（`AlertActivity`），通知栏与全屏页均提供两个超大按钮：
     - **「✓ 我已吃药 / 我已喝水 / 我已活动」**
     - **「⏰ 延后 10 分钟再提醒」**（手头有事无法马上吃药时，10 分钟后自动再次准点响铃）。

4. **适老化中文语音播报 (TTS)**
   - 响铃时自动使用系统中文语音（0.88 倍慢速清晰播报两遍），例如：*“温馨提醒，现在是吃药时间：早餐后吃降压药，1片，温水送服。完成后请点击屏幕上的我已吃药按钮。”*
   - 每张卡片均支持随时点击「🔊 语音念一遍」试听。

---

## 二、项目目录结构

```text
care-reminder/
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/care/reminder/
│       │   ├── MainActivity.kt                  # 主入口与动态权限引导
│       │   ├── model/ReminderModels.kt          # 提醒项、分类、打卡记录领域模型
│       │   ├── data/ReminderRepository.kt       # 本地持久化与今日防重服查询
│       │   ├── alarm/
│       │   │   ├── ReminderScheduler.kt         # AlarmManager 精确闹钟与延后10分钟调度
│       │   │   ├── ReminderReceiver.kt          # 闹钟触发广播、防重服静默跳过与拉起弹窗
│       │   │   ├── NotificationHelper.kt        # 高优通知渠道与通知栏快捷打卡按钮
│       │   │   ├── NotificationActionReceiver.kt# 通知栏「已完成/延后10分钟」处理
│       │   │   └── BootReceiver.kt              # 手机重启后自动恢复所有闹钟
│       │   ├── voice/VoiceSpeaker.kt            # 适老慢速中文 TTS 语音播报与震动响铃
│       │   └── ui/
│       │       ├── ReminderApp.kt               # 主界面（看板、卡片、防重服弹窗、今日账本）
│       │       ├── AlertActivity.kt             # 锁屏点亮全屏大字强提醒界面
│       │       ├── ReminderEditDialog.kt        # 新增/编辑提醒弹窗（内置快捷词条）
│       │       └── theme/CareTheme.kt           # 适老化大字号高对比度主题
│       └── res/
└── .github/workflows/build-apk.yml              # 云端自动打包 APK 工作流
```

---

## 三、如何编译安装到安卓手机

### 方式 1：使用 Android Studio 打开（推荐本地连手机调试）
1. 打开 **Android Studio** -> **Open** -> 选择 `/Users/neo/Downloads/aiprojects/care-reminder` 目录。
2. 等待 Gradle 自动同步完成。
3. 用数据线连接安卓手机（开启「开发者选项 -> USB 调试」），点击顶部绿色 **Run ('app')** 按钮即可直接安装到手机；或点击菜单 **Build -> Build Bundle(s) / APK(s) -> Build APK(s)** 生成安装包。

### 方式 2：使用命令行编译（需安装 JDK 17 与 Android SDK）
```bash
# macOS 可通过 Homebrew 安装 OpenJDK 17 与 Android 命令行工具
brew install --cask temurin@17 android-commandlinetools

cd /Users/neo/Downloads/aiprojects/care-reminder
gradle assembleDebug
# 编译产物路径：app/build/outputs/apk/debug/app-debug.apk
```

### 方式 3：使用内置 GitHub Actions 免本地环境云端打包
项目已内置 `.github/workflows/build-apk.yml`，推送到 GitHub 仓库后，在仓库的 **Actions -> Build CareReminder APK** 页面即可直接下载编译好的 `CareReminder-debug-apk.zip` 发送到手机安装。

---

## 四、给老人手机安装后的关键设置建议（防后台被杀）

国产安卓手机（小米澎湃/MIUI、华为鸿蒙、OPPO、vivo、荣耀）对后台管控较严，装好后建议帮长辈花 1 分钟开启以下 3 项设置：
1. **允许通知与锁屏弹窗**：打开 App 顶部如有黄色权限提示条，点击一键授权「通知栏弹窗权限」和「准点闹钟权限」。
2. **开启自启动与后台无限制**：长按桌面「安心健康提醒」图标 -> **应用信息** -> **省电策略/电池优化** -> 设为 **「无限制」**，并开启 **「允许自启动」**。
3. **开启锁屏显示/后台弹出界面**（小米/vivo/OPPO 手机）：在「应用信息 -> 权限管理」中允许 **「锁屏显示」** 与 **「后台弹出界面」**，这样锁屏时也能直接弹出超大字体的「我已吃药 / 延后10分钟」全屏提醒页。
