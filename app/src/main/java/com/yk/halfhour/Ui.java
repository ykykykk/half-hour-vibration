package com.yk.halfhour;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.TextView;

/** 统一样式的圆角弹窗。 */
final class Ui {
    private Ui() {}

    static final class Spec {
        String step;
        String title;
        CharSequence body;
        String note;
        boolean noteIsWarning;
        String primary;
        Runnable onPrimary;
        String secondary;
        Runnable onSecondary;
    }

    static Dialog show(Activity a, Spec s) {
        Dialog d = new Dialog(a);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        d.setContentView(R.layout.dialog_guide);
        d.setCancelable(false);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        setOrHide(d.findViewById(R.id.g_step), s.step);
        ((TextView) d.findViewById(R.id.g_title)).setText(s.title);
        setOrHide(d.findViewById(R.id.g_body), s.body);
        TextView note = d.findViewById(R.id.g_note);
        setOrHide(note, s.note);
        note.setBackgroundResource(s.noteIsWarning ? R.drawable.note_warn : R.drawable.note_info);
        note.setTextColor(a.getColor(s.noteIsWarning ? R.color.warn_text : R.color.info_text));

        TextView primary = d.findViewById(R.id.g_primary);
        primary.setText(s.primary);
        primary.setOnClickListener(v -> {
            d.dismiss();
            if (s.onPrimary != null) s.onPrimary.run();
        });
        TextView secondary = d.findViewById(R.id.g_secondary);
        setOrHide(secondary, s.secondary);
        secondary.setOnClickListener(v -> {
            d.dismiss();
            if (s.onSecondary != null) s.onSecondary.run();
        });
        d.show();
        return d;
    }

    private static void setOrHide(TextView v, CharSequence text) {
        if (text == null || text.length() == 0) {
            v.setVisibility(View.GONE);
        } else {
            v.setVisibility(View.VISIBLE);
            v.setText(text);
        }
    }
}
