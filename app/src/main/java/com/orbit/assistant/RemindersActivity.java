package com.orbit.assistant;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;

/** Simple manager for pending one-time reminders created through Orbit. */
public final class RemindersActivity extends Activity {
    private LinearLayout reminderList;

    /** Interactive Back for this page. Its classification lives in OrbitNavigation. */
    private OrbitPredictiveBack navigation;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        UiKit.syncTheme(this);
        Window w = getWindow();
        w.setStatusBarColor(UiKit.BG);
        w.setNavigationBarColor(UiKit.BG);
        View content = buildContent();
        setContentView(content);
        UiKit.applyActivityInsets(this, content, true);
        navigation = OrbitPredictiveBack.install(this);
    }

    @Override protected void onResume() {
        super.onResume();
        UiPresence.enter(this);
        ReminderScheduler.rescheduleAll(this);
        refresh();
    }

    @Override protected void onPause() {
        UiPresence.leave(this);
        super.onPause();
    }

    private View buildContent() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        OrbitBackground.applyPage(scroll);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        int p = UiKit.dp(this, 20);
        page.setPadding(p, UiKit.dp(this, 26), p, UiKit.dp(this, 48));
        scroll.addView(page, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        ImageButton back = iconButton(R.drawable.ic_back, OrbitText.get(R.string.ui_back_76900f, "Back"));
        back.setOnClickListener(v -> navigation.performBack());
        LinearLayout.LayoutParams backLp = new LinearLayout.LayoutParams(UiKit.dp(this, 48), UiKit.dp(this, 48));
        backLp.rightMargin = UiKit.dp(this, 12);
        header.addView(back, backLp);

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.addView(UiKit.text(this, OrbitText.get(R.string.ui_reminders_7971dd, "Reminders"), 26, UiKit.TEXT, true));
        titles.addView(UiKit.text(this, OrbitText.get(R.string.ui_scheduled_tasks_21c4a0, "Scheduled tasks"), 12, UiKit.MUTED, false));
        header.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        page.addView(header);

        TextView intro = UiKit.text(this,
                OrbitText.get(R.string.ui_reminders_you_create_through_orbit_live_he_0b0fcb, "Reminders you create through Orbit live here until they fire or you cancel them."),
                14, UiKit.MUTED, false);
        intro.setLineSpacing(0, 1.13f);
        LinearLayout.LayoutParams introLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        introLp.setMargins(UiKit.dp(this, 2), UiKit.dp(this, 16), UiKit.dp(this, 2), UiKit.dp(this, 14));
        page.addView(intro, introLp);

        TextView timing = UiKit.text(this,
                RoutineTriggerScheduler.canScheduleExact(this)
                        ? OrbitText.get(R.string.ui_precise_timing_is_available_fcbf92, "Precise timing is available.")
                        : OrbitText.get(R.string.ui_approximate_timing_is_active_enable_alarms_372fdd, "Approximate timing is active. Enable Alarms & reminders for minute-level delivery."),
                12, UiKit.MUTED, false);
        LinearLayout timingCard = card();
        timingCard.addView(timing);
        page.addView(timingCard, cardLp());

        TextView section = UiKit.text(this, OrbitText.get(R.string.ui_pending_reminders_aeb51c, "PENDING REMINDERS"), 11, UiKit.MUTED, true);
        section.setLetterSpacing(0.13f);
        section.setPadding(UiKit.dp(this, 4), UiKit.dp(this, 20), 0, UiKit.dp(this, 8));
        page.addView(section);

        reminderList = new LinearLayout(this);
        reminderList.setOrientation(LinearLayout.VERTICAL);
        page.addView(reminderList);
        return scroll;
    }

    private void refresh() {
        if (reminderList == null) return;
        reminderList.removeAllViews();
        List<ReminderStore.Item> items = ReminderStore.list(this);
        if (items.isEmpty()) {
            LinearLayout empty = card();
            empty.addView(UiKit.text(this, OrbitText.get(R.string.ui_no_pending_reminders_5aa1fc, "No pending reminders"), 16, UiKit.TEXT, true));
            TextView note = UiKit.text(this,
                    OrbitText.get(R.string.ui_ask_orbit_something_like_remind_me_to_wash_cbb747, "Ask Orbit something like “Remind me to wash clothes tomorrow at 10 AM.”"),
                    13, UiKit.MUTED, false);
            note.setPadding(0, UiKit.dp(this, 6), 0, 0);
            empty.addView(note);
            reminderList.addView(empty, cardLp());
            return;
        }

        DateFormat format = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT);
        for (ReminderStore.Item item : items) {
            LinearLayout card = card();
            TextView message = UiKit.text(this, item.message, 16, UiKit.TEXT, true);
            card.addView(message);
            TextView when = UiKit.text(this, format.format(new Date(item.triggerAt)), 12, UiKit.MUTED, false);
            when.setPadding(0, UiKit.dp(this, 5), 0, 0);
            card.addView(when);
            Button cancel = secondaryButton(OrbitText.get(R.string.ui_cancel_reminder_948e11, "Cancel reminder"));
            LinearLayout.LayoutParams cancelLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, UiKit.dp(this, 44));
            cancelLp.setMargins(0, UiKit.dp(this, 12), 0, 0);
            card.addView(cancel, cancelLp);
            cancel.setOnClickListener(v -> {
                ReminderScheduler.cancel(this, item.id);
                Toast.makeText(this, OrbitText.get(R.string.ui_reminder_cancelled_005e1c, "Reminder cancelled"), Toast.LENGTH_SHORT).show();
                refresh();
            });
            reminderList.addView(card, cardLp());
        }
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(UiKit.dp(this, 18), UiKit.dp(this, 16), UiKit.dp(this, 18), UiKit.dp(this, 16));
        c.setBackground(UiKit.outlined(UiKit.SURFACE,
                UiKit.withAlpha(UiKit.accent(this), 38), 22, this));
        c.setElevation(UiKit.dp(this, 2));
        return c;
    }

    private LinearLayout.LayoutParams cardLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, UiKit.dp(this, 10));
        return lp;
    }

    private Button secondaryButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(13);
        b.setTextColor(UiKit.accent(this));
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setStateListAnimator(null);
        b.setBackground(UiKit.rippleOutlined(UiKit.SURFACE_2,
                UiKit.withAlpha(UiKit.accent(this), 72), UiKit.accent(this), 14, this));
        UiKit.pressScale(b);
        return b;
    }

    private ImageButton iconButton(int res, String desc) {
        ImageButton b = new ImageButton(this);
        b.setImageResource(res);
        b.setImageTintList(ColorStateList.valueOf(UiKit.accent(this)));
        b.setBackground(UiKit.ripple(UiKit.SURFACE, UiKit.accent(this), 18, this));
        b.setContentDescription(desc);
        b.setPadding(UiKit.dp(this, 11), UiKit.dp(this, 11), UiKit.dp(this, 11), UiKit.dp(this, 11));
        UiKit.pressScale(b);
        return b;
    }
}
