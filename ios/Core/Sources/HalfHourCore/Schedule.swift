import Foundation

public struct ReminderSettings: Codable, Equatable {
    public var enabled = false
    public var days = 0b0011111
    public var startMinute = 9 * 60
    public var endMinute = 18 * 60
    public init() {}
}

public enum Schedule {
    public static let interval: TimeInterval = 30 * 60
    public static let capacity = 60

    public static func inWindow(_ date: Date, settings s: ReminderSettings, calendar c: Calendar) -> Bool {
        let day = (c.component(.weekday, from: date) + 5) % 7
        let previous = (day + 6) % 7
        let minute = c.component(.hour, from: date) * 60 + c.component(.minute, from: date)
        let today = s.days & (1 << day) != 0
        if s.startMinute == s.endMinute { return today }
        if s.startMinute < s.endMinute { return today && minute >= s.startMinute && minute < s.endMinute }
        return (today && minute >= s.startMinute) || (s.days & (1 << previous) != 0 && minute < s.endMinute)
    }

    public static func next(after from: Date, settings s: ReminderSettings, calendar c: Calendar = .current) -> Date? {
        guard s.days & 127 != 0, (0..<1440).contains(s.startMinute), (0..<1440).contains(s.endMinute),
              var minute = c.dateInterval(of: .minute, for: from)?.start else { return nil }
        var opened: Date? = inWindow(from, settings: s, calendar: c) ? from : nil
        for _ in 0...(8 * 24 * 60) {
            guard let following = c.date(byAdding: .minute, value: 1, to: minute) else { return nil }
            if inWindow(minute, settings: s, calendar: c) {
                if opened == nil { opened = minute }
                let due = max(opened!, from).addingTimeInterval(interval)
                if due < following { return max(due, minute) }
            } else { opened = nil }
            minute = following
        }
        return nil
    }

    /// Retain future dates when reopening the app, so merely opening it never resets the countdown.
    public static func refill(existing: [Date], now: Date, settings: ReminderSettings,
                              calendar: Calendar = .current) -> [Date] {
        guard settings.enabled else { return [] }
        var result = Array(Set(existing.filter { $0 > now && inWindow($0, settings: settings, calendar: calendar) }))
            .sorted().prefix(capacity).map { $0 }
        var cursor = result.last ?? now
        while result.count < capacity, let next = next(after: cursor, settings: settings, calendar: calendar) {
            result.append(next)
            cursor = next
        }
        return result
    }
}
