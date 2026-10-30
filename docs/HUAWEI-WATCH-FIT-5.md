# HUAWEI WATCH FIT 5 适配可行性

核对日期：2026-10-30。适配分支：`feat/huawei-watch-fit-5`。

## 目标与当前状态

目标是脱离手机，在手表上按星期和时段每 30 分钟自动提醒，点“我现在去休息”后重新计时，并能在退出应用、返回表盘后继续工作。

**当前被设备公开能力限制阻塞，尚未实现手表版，也没有生成可安装的手表应用包。** 手机 Android 项目的代码和构建方式保持原状。

## 核对结果

- FIT 系列属于 Lite Wearable，使用 JS / ArkUI.Lite 开发。它与支持 ArkTS 的 HUAWEI WATCH 5 属于不同设备类型；不能将本仓库的 Android APK 直接作为 FIT 5 应用安装。
- 华为开发者论坛的技术支持答复指出，Fit / GT 系列不支持第三方应用脱离手机的系统级本地定时推送，不能直接使用智能穿戴的 `reminderAgentManager`。普通 `setTimeout` / `setInterval` 无法保证应用退后台或退出后执行。
- 手机通过 Wear Engine 或运动健康转发通知依赖手机连接，不满足本分支“必须脱离手机”的要求。
- FIT 5 自带闹钟、计时器；这些系统应用有提醒能力，不代表第三方应用具有相同权限。目前未确认存在 FIT 5 专用、可公开调用的后台闹钟接口。

因此，不能以缩小 Android 界面、改成 Wear OS、照搬 WATCH 5 的 ArkTS 接口或前台计时演示，宣称已经完成本需求。

## 继续开发的前提

需要华为提供或确认 **FIT 5 第三方应用可用的、应用退出后仍能触发的本地定时提醒接口**，以及对应 SDK、签名和真机安装流程。若获得这类能力，再建立 Lite Wearable 工程并迁移时段计算、提醒和确认交互。

真机验收至少包括：手机蓝牙断开；返回表盘并熄屏超过 30 分钟；连续两轮提醒；确认休息后重新计时；跨午夜与星期边界；重启后的恢复。仅前台倒计时成功不能证明适配完成。当前没有进行 FIT 5 真机验证。

## 来源

1. [华为开发文档：穿戴侧应用开发方案](https://developer.huawei.com/consumer/cn/doc/doccenter-dev-faq/arkui-uimana-faq-0002)：区分智能穿戴与轻量级智能穿戴，说明 Fit 系列采用 JS / ArkUI.Lite；文中明确提到 FIT 5。
2. [华为开发者论坛：轻穿戴设备消息推送](https://developer.huawei.com/consumer/cn/forum/topic/0201222718508408522)：技术支持答复说明 Fit / GT 的本地后台定时推送限制。此项证据为技术支持答复，并非 FIT 5 的完整 SDK 能力矩阵。
3. [华为官网：Fit 系列支持的应用](https://consumer.huawei.com/cn/support/content/zh-cn15903440/)：包含 FIT 5，说明通过运动健康的应用市场安装受支持应用。
4. [华为官网：FIT 5 系列功能兼容性清单](https://consumer.huawei.com/cn/support/content/zh-cn16091400/)：列出自带秒表、计时器和闹钟；不构成第三方后台 API 的支持证明。
