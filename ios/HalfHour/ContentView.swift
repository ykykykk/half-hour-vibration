import SwiftUI
import HalfHourCore

struct ContentView: View {
    @StateObject private var store = ReminderStore.shared
    private let weekdays = ["一", "二", "三", "四", "五", "六", "日"]

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    VStack(alignment: .leading, spacing: 10) {
                        Label("每半小时，给自己一点休息", systemImage: "leaf.fill")
                            .font(.subheadline).foregroundStyle(.secondary)
                        if let next = store.dates.first {
                            Text(next, style: .time).font(.system(size: 48, weight: .semibold, design: .rounded))
                            Text("下次提醒 · \(next.formatted(date: .abbreviated, time: .omitted))").foregroundStyle(.secondary)
                        } else {
                            Text(store.settings.enabled ? "等待安排" : "暂停中").font(.largeTitle.bold())
                        }
                    }.padding(.vertical, 8)
                    Toggle("开启提醒", isOn: Binding(get: { store.settings.enabled }, set: { enabled in
                        store.edit { $0.enabled = enabled }
                    }))
                }
                Section("提醒日期") {
                    HStack(spacing: 3) {
                        ForEach(0..<7) { i in
                            let selected = store.settings.days & (1 << i) != 0
                            Button {
                                store.edit { $0.days ^= 1 << i }
                            } label: {
                                Text(weekdays[i]).font(.body.weight(.semibold))
                                    .frame(maxWidth: .infinity, minHeight: 44)
                                    .background(selected ? Color.accentColor : Color.secondary.opacity(0.1), in: RoundedRectangle(cornerRadius: 12))
                                    .foregroundStyle(selected ? Color.white : Color.primary)
                            }.buttonStyle(.plain)
                                .accessibilityLabel("星期\(weekdays[i])")
                                .accessibilityValue(selected ? "已选" : "未选")
                        }
                    }
                }
                Section {
                    DatePicker("开始", selection: timeBinding(start: true), displayedComponents: .hourAndMinute)
                    DatePicker("结束", selection: timeBinding(start: false), displayedComponents: .hourAndMinute)
                } header: { Text("提醒时段") } footer: {
                    Text("开始等于结束表示全天；结束早于开始表示跨午夜，星期按开始那天计算。时段开始满 30 分钟后首次提醒。")
                }
                Section {
                    Button {
                        Task { await store.rest() }
                    } label: {
                        Label("我现在去休息", systemImage: "checkmark.circle.fill")
                            .frame(maxWidth: .infinity).font(.headline).padding(.vertical, 8)
                    }.disabled(!store.settings.enabled || store.dates.isEmpty)
                    Button("发送测试通知（3 秒后）") { store.test() }
                } footer: {
                    Text("点休息后清除当前通知，从现在重新计时 30 分钟；超出时段会顺延。未确认时，已安排的半小时通知仍会继续。")
                }
                Section("通知与续排") {
                    Label(store.permission, systemImage: "bell.badge")
                    if let last = store.dates.last {
                        Text("已安排 \(store.dates.count) 次，至 \(last.formatted(date: .abbreviated, time: .shortened))")
                    }
                    Button("打开 iPhone 通知设置") {
                        if let url = URL(string: UIApplication.openSettingsURLString) { UIApplication.shared.open(url) }
                    }
                    Text("请每天打开一次应用续排。iOS 限制待发通知数量，本应用每次安排最多 60 次；到期还会发一条续排提示。")
                        .font(.footnote).foregroundStyle(.secondary)
                    Text("iPhone 后台无法持续振动、强制亮屏或闪屏。声音和振动受静音、专注模式及系统设置控制。")
                        .font(.footnote).foregroundStyle(.secondary)
                }
                if let problem = store.problem {
                    Section { Text(problem).foregroundStyle(.secondary) }
                }
            }
            .navigationTitle("半小时休息")
            .tint(.green)
            .disabled(store.busy)
            .overlay { if store.busy { ProgressView().padding().background(.regularMaterial, in: Capsule()) } }
        }
    }

    private func timeBinding(start: Bool) -> Binding<Date> {
        Binding(get: {
            let value = start ? store.settings.startMinute : store.settings.endMinute
            return Calendar.current.date(from: DateComponents(year: 2001, month: 1, day: 1, hour: value / 60, minute: value % 60))!
        }, set: { value in
            let c = Calendar.current
            let minute = c.component(.hour, from: value) * 60 + c.component(.minute, from: value)
            store.edit { if start { $0.startMinute = minute } else { $0.endMinute = minute } }
        })
    }
}
