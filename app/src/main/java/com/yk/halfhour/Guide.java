package com.yk.halfhour;

import android.app.Activity;
import android.app.Dialog;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 设置向导：一次只处理一步。
 * 先弹说明（要你做什么）→ 你点"去开启"才打开系统窗口/设置页 → 回来后检查结果 →
 * 成功就进入下一步，没成功就说明原因让你再试或跳过。
 */
final class Guide {
    interface Listener {
        void onGuideChanged();
        void onGuideWantsTest();
    }

    private final Activity a;
    private final Listener listener;
    private final Set<String> skipped = new HashSet<>();
    private List<Steps.Step> steps;
    /** true = 走完整流程；false = 只处理用户点的那一项 */
    private boolean chain;
    /** 已经跳去设置页、等用户回来的那一步 */
    private Steps.Step awaiting;
    private Dialog dialog;

    Guide(Activity a, Listener listener) {
        this.a = a;
        this.listener = listener;
    }

    boolean isActive() {
        return awaiting != null || (dialog != null && dialog.isShowing());
    }

    void startAll() {
        skipped.clear();
        chain = true;
        steps = Steps.all(a);
        next();
    }

    void startSingle(Steps.Step s) {
        chain = false;
        steps = Steps.all(a);
        showIntro(s, false);
    }

    /** 从设置页/权限弹窗回到 APP 时调用（onResume 和权限回调都会调，重复调用无害） */
    void onReturn() {
        if (awaiting == null) return;
        Steps.Step s = awaiting;
        awaiting = null;
        listener.onGuideChanged();
        int state = s.state(a);
        if (state == Steps.OK) {
            afterStep();
        } else if (state == Steps.MISSING) {
            showIntro(s, true);
        } else {
            showConfirm(s);
        }
    }

    void dismiss() {
        if (dialog != null) dialog.dismiss();
    }

    private void next() {
        for (Steps.Step s : steps) {
            if (s.satisfied(a) || skipped.contains(s.key)) continue;
            showIntro(s, false);
            return;
        }
        showFinish();
    }

    private void afterStep() {
        listener.onGuideChanged();
        if (chain) next();
    }

    private String stepLabel(Steps.Step s) {
        int i = steps.indexOf(s) + 1;
        return a.getString(R.string.guide_step, i, steps.size());
    }

    /** 第一屏：告诉用户这一步要做什么 */
    private void showIntro(Steps.Step s, boolean retry) {
        Ui.Spec spec = new Ui.Spec();
        spec.step = stepLabel(s);
        spec.title = a.getString(s.titleRes);
        spec.body = s.howTo.get(a);
        if (retry) {
            spec.note = a.getString(R.string.guide_retry);
            spec.noteIsWarning = true;
        }
        if (s.action != null) {
            spec.primary = a.getString(s.check == null ? R.string.guide_go_settings : R.string.guide_go_grant);
            spec.onPrimary = () -> launch(s);
        } else {
            spec.primary = a.getString(R.string.guide_done);
            spec.onPrimary = () -> {
                Steps.markDone(a, s.key);
                afterStep();
            };
        }
        spec.secondary = a.getString(chain ? R.string.guide_skip : R.string.guide_later);
        spec.onSecondary = () -> {
            skipped.add(s.key);
            afterStep();
        };
        dialog = Ui.show(a, spec);
    }

    /** 系统查不到结果的步骤：回来后让用户确认 */
    private void showConfirm(Steps.Step s) {
        Ui.Spec spec = new Ui.Spec();
        spec.step = stepLabel(s);
        spec.title = a.getString(R.string.guide_confirm_title, a.getString(s.titleRes));
        spec.body = s.howTo.get(a);
        spec.note = a.getString(R.string.guide_confirm_note);
        spec.primary = a.getString(R.string.guide_done);
        spec.onPrimary = () -> {
            Steps.markDone(a, s.key);
            afterStep();
        };
        spec.secondary = a.getString(R.string.guide_again);
        spec.onSecondary = () -> launch(s);
        dialog = Ui.show(a, spec);
    }

    private void launch(Steps.Step s) {
        Steps.markAsked(a, s.key);
        awaiting = s;
        s.action.run(a);
    }

    private void showFinish() {
        int left = Steps.pendingCount(a);
        Ui.Spec spec = new Ui.Spec();
        spec.title = a.getString(left == 0 ? R.string.guide_finish_title : R.string.guide_finish_title_partial);
        spec.body = a.getString(new Prefs(a).watchMode()
                ? R.string.watch_test_body : R.string.guide_finish_body);
        if (left > 0) {
            spec.note = a.getString(R.string.guide_finish_left, left);
            spec.noteIsWarning = true;
        }
        spec.primary = a.getString(R.string.guide_test_now);
        spec.onPrimary = listener::onGuideWantsTest;
        spec.secondary = a.getString(R.string.guide_later);
        dialog = Ui.show(a, spec);
    }
}
