# 半小时休息 · iPhone

原生 SwiftUI iPhone 应用（iOS 16+）。本分支 `feat/iphone` 从仓库默认分支 `ccr-cc6a958b-f7ex8z` 的 `2d45dd0` 分出，不包含华为手表分支改动。

## 功能

- 选择每周提醒日期、每天开始/结束时间，支持跨午夜和全天。
- 在有效时段内每 30 分钟通过 iOS 本地通知提醒，应用无需保持打开。
- 长按正式提醒通知，点“我现在去休息”，或在应用内点击同名按钮：清除通知并从点击时刻重新安排 30 分钟；跨出时段时顺延。
- 未确认时，预先安排的半小时通知继续发送。
- 打开应用时保留未来提醒时间并补满队列，避免每次打开都重新计时。修改时段、星期或系统时区则重排。
- 3 秒测试通知不改变正式提醒时间；首页显示权限状态及已安排到的日期。

## iOS 的实际限制

iOS 不允许普通应用在后台无限持续振动、强制点亮屏幕或全屏闪烁。此版本使用标准通知声音，是否振动取决于手机声音/触感、静音、专注模式等设置，不能绕过这些设置。

每次最多预排 60 条正式通知，另有 1 条队列结束后的续排提示及最多 1 条测试通知。请每天打开一次应用续排；通知动作也会重排。应用长期未打开且无人点击通知动作时，队列耗尽后不会继续提醒。后台刷新不能作为可靠的无限续排保证，因此没有伪装成常驻服务。

关闭提醒会删除待发和已送达通知。撤销系统通知权限后，请重新授权并回到应用安排。卸载会丢失设置和提醒。手动改变系统时间、重启、专注模式和通知动作需要在真实 iPhone 上验证。

## 打开与安装

1. 在 Mac 安装 Xcode 和 XcodeGen：`brew install xcodegen`。
2. 进入 `ios/`，运行 `xcodegen generate`，打开 `HalfHour.xcodeproj`。
3. 在 Xcode 的 Signing & Capabilities 选择自己的 Apple 开发团队；必要时把 `com.yk.halfhour.ios` 改成自己的唯一 Bundle ID。
4. 连接 iPhone，在手机打开开发者模式（如系统要求），选择设备后 Run。首次启动开启提醒并允许通知。

需要 Apple 签名才能安装到真实 iPhone。没有配置用户的签名证书、开发团队或 App Store Connect，因此本仓库不会输出假装可直接安装的 IPA，也未上传 TestFlight/App Store。个人免费签名的有效期和设备限制以 Apple 当前规则为准。

## 构建与验证

```
swift test --package-path ios/Core
cd ios
xcodegen generate
xcodebuild -project HalfHour.xcodeproj -scheme HalfHour \
  -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO build
```

GitHub Actions 的 **Build iPhone** 在 macOS 执行时段单元测试并编译模拟器应用。构建产物 `half-hour-iphone` 包含源码 tar、生成的 Xcode 工程和模拟器 `.app` 压缩包。将源码解压后，把生成的 `HalfHour.xcodeproj` 放入 `ios/`，即可用 Xcode 打开；模拟器包不能直接装到真机。

测试覆盖时段开始、结束排除、星期、周末、跨午夜、全天、秒数、无可用日期、短时段、重新打开保留倒计时、确认休息重排、关闭及夏令时。尚未在真实 iPhone 上验证通知送达、触感或锁屏通知动作。

## 目录

- `ios/HalfHour/`：SwiftUI 界面、本地通知及通知动作。
- `ios/Core/`：独立 Swift Package，保存可测试的时间计算。
- `ios/project.yml`：XcodeGen 工程配置。
- `app/` 和根目录 Gradle 文件：默认分支继承的 Android 原始实现，供参考；本分支的 CI 构建 iPhone 版本。
