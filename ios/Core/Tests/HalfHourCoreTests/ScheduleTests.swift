import XCTest
@testable import HalfHourCore

final class ScheduleTests: XCTestCase {
    var calendar: Calendar { var c = Calendar(identifier: .gregorian); c.timeZone = TimeZone(identifier: "Asia/Shanghai")!; return c }
    func date(_ day: Int, _ h: Int, _ m: Int) -> Date {
        calendar.date(from: DateComponents(year: 2026, month: 10, day: day, hour: h, minute: m))!
    }
    var settings: ReminderSettings { var s = ReminderSettings(); s.enabled = true; return s }
    func testInsideWindow() { XCTAssertEqual(Schedule.next(after: date(26, 10, 0), settings: settings, calendar: calendar), date(26, 10, 30)) }
    func testBeforeWindow() { XCTAssertEqual(Schedule.next(after: date(26, 7, 0), settings: settings, calendar: calendar), date(26, 9, 30)) }
    func testExactEndExcluded() { XCTAssertEqual(Schedule.next(after: date(26, 17, 30), settings: settings, calendar: calendar), date(27, 9, 30)) }
    func testWeekendSkipped() { XCTAssertEqual(Schedule.next(after: date(30, 17, 45), settings: settings, calendar: calendar), date(30, 9, 30).addingTimeInterval(3 * 86400)) }
    func testSecondsPreserved() {
        let from = date(26, 10, 0).addingTimeInterval(17)
        XCTAssertEqual(Schedule.next(after: from, settings: settings, calendar: calendar), from.addingTimeInterval(1800))
    }
    func testOvernightUsesStartWeekday() {
        var s = settings; s.days = 1 << 6; s.startMinute = 22 * 60; s.endMinute = 2 * 60
        XCTAssertEqual(Schedule.next(after: date(25, 23, 50), settings: s, calendar: calendar), date(26, 0, 20))
    }
    func testAllDay() {
        var s = settings; s.startMinute = 0; s.endMinute = 0
        XCTAssertEqual(Schedule.next(after: date(26, 23, 50), settings: s, calendar: calendar), date(27, 0, 20))
    }
    func testNoDays() { var s = settings; s.days = 0; XCTAssertNil(Schedule.next(after: date(26, 10, 0), settings: s, calendar: calendar)) }
    func testTooShort() { var s = settings; s.endMinute = s.startMinute + 30; XCTAssertNil(Schedule.next(after: date(26, 7, 0), settings: s, calendar: calendar)) }
    func testReopenPreservesCountdownAndRefills() {
        let initial = Schedule.refill(existing: [], now: date(26, 10, 0), settings: settings, calendar: calendar)
        let reopened = Schedule.refill(existing: initial, now: date(26, 10, 10), settings: settings, calendar: calendar)
        XCTAssertEqual(initial, reopened)
        XCTAssertEqual(initial.count, 60)
        let later = Schedule.refill(existing: initial, now: date(26, 10, 40), settings: settings, calendar: calendar)
        XCTAssertEqual(later.first, date(26, 11, 0))
        XCTAssertEqual(later.count, 60)
    }
    func testRestResetsCountdown() {
        let plan = Schedule.refill(existing: [], now: date(26, 10, 43), settings: settings, calendar: calendar)
        XCTAssertEqual(plan.first, date(26, 11, 13))
    }
    func testDisabledClearsQueue() {
        var s = settings; s.enabled = false
        XCTAssertTrue(Schedule.refill(existing: [date(26, 11, 0)], now: date(26, 10, 0), settings: s, calendar: calendar).isEmpty)
    }
    func testDSTUsesElapsedHalfHour() {
        var c = calendar; c.timeZone = TimeZone(identifier: "America/New_York")!
        var s = settings; s.days = 127; s.startMinute = 0; s.endMinute = 0
        let from = c.date(from: DateComponents(year: 2026, month: 3, day: 8, hour: 1, minute: 45))!
        let next = Schedule.next(after: from, settings: s, calendar: c)!
        XCTAssertEqual(next.timeIntervalSince(from), 1800)
        XCTAssertEqual(c.component(.hour, from: next), 3)
        XCTAssertEqual(c.component(.minute, from: next), 15)
    }
}
