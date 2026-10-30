import SwiftUI
import UserNotifications
import HalfHourCore

@MainActor
final class ReminderStore: ObservableObject {
    static let shared = ReminderStore()
    @Published private(set) var settings = ReminderSettings()
    @Published private(set) var dates: [Date] = []
    @Published private(set) var permission = "尚未授权"
    @Published private(set) var problem: String?
    @Published private(set) var busy = false
    private let center = UNUserNotificationCenter.current()
    private var tail: Task<Void, Never>?
    private var zone = TimeZone.current.identifier
    private let defaults = UserDefaults.standard
    private let settingsKey = "iphone.settings"
    private let datesKey = "iphone.dates"

    private init() {
        if let data = defaults.data(forKey: settingsKey), let decoded = try? JSONDecoder().decode(ReminderSettings.self, from: data) {
            settings = decoded
        }
        dates = (defaults.array(forKey: datesKey) as? [Double] ?? []).map(Date.init(timeIntervalSince1970:))
        zone = defaults.string(forKey: "iphone.zone") ?? zone
    }

    @discardableResult
    private func enqueue(_ body: @escaping @MainActor () async -> Void) -> Task<Void, Never> {
        let previous = tail
        let task = Task { @MainActor in
            await previous?.value
            self.busy = true
            await body()
            self.busy = false
        }
        tail = task
        return task
    }

    func edit(_ change: @escaping (inout ReminderSettings) -> Void) {
        enqueue {
            change(&self.settings)
            self.dates = []
            await self.rebuild(requestPermission: self.settings.enabled)
        }
    }

    func refresh() {
        enqueue {
            if self.zone != TimeZone.current.identifier { self.dates = [] }
            await self.rebuild(requestPermission: false)
        }
    }

    func rest() async {
        await enqueue {
            guard self.settings.enabled else { return }
            self.dates = []
            await self.rebuild(requestPermission: false)
        }.value
    }

    func test() {
        enqueue {
            do {
                guard try await self.authorize(request: true) else {
                    self.problem = "请到 iPhone 设置中允许本应用发送通知。"
                    return
                }
                let content = UNMutableNotificationContent()
                content.title = "半小时休息 · 测试"
                content.body = "这是一条测试通知。正式提醒时间没有改变。"
                content.sound = .default
                try await self.center.add(UNNotificationRequest(identifier: "test", content: content,
                    trigger: UNTimeIntervalNotificationTrigger(timeInterval: 3, repeats: false)))
                self.problem = "测试通知将在 3 秒后出现；声音和振动取决于 iPhone 系统设置。"
            } catch { self.problem = "测试发送失败：\(error.localizedDescription)" }
        }
    }

    private func authorize(request: Bool) async throws -> Bool {
        var state = await center.notificationSettings()
        if state.authorizationStatus == .notDetermined && request {
            _ = try await center.requestAuthorization(options: [.alert, .sound, .badge])
            state = await center.notificationSettings()
        }
        let allowed = state.authorizationStatus == .authorized || state.authorizationStatus == .provisional
        permission = allowed ? (state.soundSetting == .enabled ? "通知已允许" : "通知已允许，声音已关闭") : "通知未允许"
        return allowed
    }

    private func persist() {
        defaults.set(try? JSONEncoder().encode(settings), forKey: settingsKey)
        defaults.set(dates.map(\.timeIntervalSince1970), forKey: datesKey)
        zone = TimeZone.current.identifier
        defaults.set(zone, forKey: "iphone.zone")
    }

    private func rebuild(requestPermission: Bool) async {
        problem = nil
        do {
            let allowed = try await authorize(request: requestPermission)
            center.removeAllPendingNotificationRequests()
            center.removeAllDeliveredNotifications()
            guard settings.enabled && allowed else {
                dates = []
                if settings.enabled && !allowed { problem = "通知权限未开启，当前无法提醒。请到系统设置开启，再回到本应用。" }
                persist()
                return
            }
            dates = Schedule.refill(existing: dates, now: Date(), settings: settings)
            if dates.isEmpty { problem = "没有可用提醒：请选择星期，并确保时段超过 30 分钟（开始等于结束表示全天）。" }
            var utc = Calendar(identifier: .gregorian)
            utc.timeZone = TimeZone(secondsFromGMT: 0)!
            for (index, date) in dates.enumerated() {
                let content = UNMutableNotificationContent()
                content.title = "该休息了"
                content.body = "起来走走，喝口水。长按通知，点「我现在去休息」重新计时。"
                content.sound = .default
                content.categoryIdentifier = "REST"
                var parts = utc.dateComponents([.year, .month, .day, .hour, .minute, .second], from: date)
                parts.calendar = utc
                parts.timeZone = utc.timeZone
                try await center.add(UNNotificationRequest(identifier: "rest-\(index)", content: content,
                    trigger: UNCalendarNotificationTrigger(dateMatching: parts, repeats: false)))
            }
            if let end = dates.last {
                let content = UNMutableNotificationContent()
                content.title = "请打开应用续排提醒"
                content.body = "这一批半小时提醒已结束。打开「半小时休息」继续安排后续通知。"
                content.sound = .default
                try await center.add(UNNotificationRequest(identifier: "refill", content: content,
                    trigger: UNTimeIntervalNotificationTrigger(timeInterval: max(1, end.timeIntervalSinceNow + 60), repeats: false)))
            }
            persist()
        } catch {
            center.removeAllPendingNotificationRequests()
            dates = []
            problem = "提醒安排失败：\(error.localizedDescription)。请重新打开应用重试。"
            persist()
        }
    }
}
