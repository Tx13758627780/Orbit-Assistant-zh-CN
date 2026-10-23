package com.orbit.assistant;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
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
import java.util.List;

public class NotificationsActivity extends Activity {
    private LinearLayout page;
    private TextView subtitle;

    /** Interactive Back for this page. Its classification lives in OrbitNavigation. */
    private OrbitPredictiveBack navigation;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        UiKit.syncTheme(this);
        Window w = getWindow();
        w.setStatusBarColor(UiKit.BG);
        w.setNavigationBarColor(UiKit.BG);
        rebuild();
    }

    @Override protected void onResume() {
        super.onResume();
        UiPresence.enter(this);
        rebuild();
    }

    @Override protected void onPause() {
        UiPresence.leave(this);
        super.onPause();
    }

    private void rebuild() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        OrbitBackground.applyPage(scroll);

        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        int p = UiKit.dp(this, 18);
        page.setPadding(p, UiKit.dp(this, 18), p, UiKit.dp(this, 48));
        scroll.addView(page, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);

        ImageButton back = new ImageButton(this);
        back.setImageResource(com.orbit.assistant.R.drawable.ic_back);
        back.setImageTintList(ColorStateList.valueOf(UiKit.accent(this)));
        back.setBackground(UiKit.ripple(UiKit.SURFACE, UiKit.accent(this), 18, this));
        back.setPadding(UiKit.dp(this, 12), UiKit.dp(this, 12),
                UiKit.dp(this, 12), UiKit.dp(this, 12));
        UiKit.pressScale(back);
        back.setOnClickListener(v -> navigation.performBack());
        top.addView(back, new LinearLayout.LayoutParams(UiKit.dp(this, 48), UiKit.dp(this, 48)));

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setPadding(UiKit.dp(this, 8), 0, 0, 0);
        titles.addView(UiKit.text(this, OrbitText.get(R.string.ui_notifications_788011, "Notifications"), 25, UiKit.TEXT, true));
        subtitle = UiKit.text(this, "", 12, UiKit.MUTED, false);
        titles.addView(subtitle);
        top.addView(titles, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        page.addView(top);

        addAccessCard();
        addPrivacyCard();
        addAppFilters();
        addRecentHistory();

        setContentView(scroll);
        UiKit.applyActivityInsets(this, scroll, true);
        navigation = OrbitPredictiveBack.install(this);
        refreshSubtitle();
    }

    private void addAccessCard() {
        page.addView(sectionTitle(OrbitText.get(R.string.ui_access_c01a38, "ACCESS")));
        LinearLayout card = card();

        boolean enabled = NotificationAccess.enabled(this);
        TextView status = UiKit.text(this,
                enabled ? OrbitText.get(R.string.ui_notification_access_is_enabled_9739b4, "Notification access is enabled") : OrbitText.get(R.string.ui_notification_access_is_not_enabled_dcb274, "Notification access is not enabled"),
                15, enabled ? UiKit.SUCCESS : UiKit.TEXT, true);
        card.addView(status);

        TextView desc = UiKit.text(this,
                enabled
                        ? OrbitText.get(R.string.ui_orbit_can_now_build_a_private_local_histor_c8355b, "Orbit can now build a private local history from notifications that arrive on this phone.")
                        : OrbitText.get(R.string.ui_android_requires_you_to_explicitly_grant_n_b38c59, "Android requires you to explicitly grant Notification access before Orbit can see notification content."),
                13, UiKit.MUTED, false);
        desc.setPadding(0, UiKit.dp(this, 7), 0, UiKit.dp(this, 12));
        card.addView(desc);

        Button access = primaryButton(enabled ? OrbitText.get(R.string.ui_open_notification_access_settings_930926, "Open notification access settings")
                : OrbitText.get(R.string.ui_open_notification_access_e4b582, "Open notification access"));
        access.setOnClickListener(v -> {
            try { startActivity(NotificationAccess.settingsIntent()); }
            catch (Exception e) { Toast.makeText(this,
                    OrbitText.get(R.string.ui_could_not_open_notification_access_setting_d632c0, "Could not open notification access settings"), Toast.LENGTH_SHORT).show(); }
        });
        card.addView(access, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, UiKit.dp(this, 48)));

        page.addView(card, cardLp());
    }

    private void addPrivacyCard() {
        page.addView(sectionTitle(OrbitText.get(R.string.ui_privacy_retention_9fc30e, "PRIVACY & RETENTION")));
        LinearLayout card = card();

        OrbitSwitch ai = new OrbitSwitch(this);
        ai.setChecked(Prefs.notificationAiEnabled(this), false);
        ai.setOnCheckedChangeListener((button, checked) ->
                Prefs.get(this).edit().putBoolean(Prefs.NOTIFICATION_AI_ENABLED, checked).apply());
        card.addView(UiKit.switchRow(this, OrbitText.get(R.string.ui_use_relevant_notification_history_in_ai_an_594775, "Use relevant notification history in AI answers"), null, ai));

        TextView help = UiKit.text(this,
                OrbitText.get(R.string.ui_orbit_stores_notification_history_locally_28c1ce, "Orbit stores notification history locally. It only supplies that history to the AI when your prompt is clearly asking about notifications, missed messages, or what you missed."),
                12, UiKit.MUTED, false);
        help.setPadding(0, UiKit.dp(this, 7), 0, UiKit.dp(this, 12));
        card.addView(help);

        Button retention = secondaryButton(OrbitText.get(R.string.ui_keep_history_7565ef, "Keep history: ") +
                retentionLabel(Prefs.notificationRetentionDays(this)));
        retention.setOnClickListener(v -> {
            String[] labels = {OrbitText.get(R.string.ui_1_day_fa665d, "1 day"), OrbitText.get(R.string.ui_3_days_360719, "3 days"), OrbitText.get(R.string.ui_7_days_7f920b, "7 days"), OrbitText.get(R.string.ui_14_days_60acc3, "14 days"), OrbitText.get(R.string.ui_30_days_ffd728, "30 days")};
            int[] values = {1,3,7,14,30};
            int selected = 2;
            int current = Prefs.notificationRetentionDays(this);
            for (int i = 0; i < values.length; i++) if (values[i] == current) selected = i;
            final int selectedIndex = selected;
            UiKit.showOrbitMenu(this, retention, labels, selectedIndex, (index, label) -> {
                Prefs.get(this).edit().putInt(Prefs.NOTIFICATION_RETENTION_DAYS,
                        values[index]).apply();
                NotificationStore.all(this); // prune immediately
                rebuild();
            });
        });
        card.addView(retention, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, UiKit.dp(this, 46)));

        TextView warning = UiKit.text(this,
                OrbitText.get(R.string.ui_for_sensitive_apps_such_as_banking_authent_5b4c24, "For sensitive apps such as banking, authenticator, password-manager, or private-work apps, exclude them below. Excluded apps are not kept in Orbit notification history."),
                12, UiKit.MUTED, false);
        warning.setPadding(0, UiKit.dp(this, 10), 0, 0);
        card.addView(warning);

        Button clear = secondaryButton(OrbitText.get(R.string.ui_clear_local_notification_history_246e2a, "Clear local notification history"));
        LinearLayout.LayoutParams clearLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, UiKit.dp(this, 46));
        clearLp.setMargins(0, UiKit.dp(this, 12), 0, 0);
        card.addView(clear, clearLp);
        clear.setOnClickListener(v -> {
            AlertDialog dialog = new AlertDialog.Builder(this)
                    .setTitle(OrbitText.get(R.string.ui_clear_notification_history_7ab64e, "Clear notification history?"))
                    .setMessage(OrbitText.get(R.string.ui_this_deletes_the_locally_saved_notificatio_6ef23c, "This deletes the locally saved notification contents. Your app include/exclude choices stay unchanged."))
                    .setNegativeButton(OrbitText.get(R.string.ui_cancel_19766e, "Cancel"), null)
                    .setPositiveButton(OrbitText.get(R.string.ui_clear_83b12c, "Clear"), (d,w) -> {
                        NotificationStore.clear(this);
                        rebuild();
                    }).create();
            styleOrbitDialog(dialog);
            dialog.show();
        });

        page.addView(card, cardLp());
    }

    private void addAppFilters() {
        List<NotificationStore.AppSummary> apps = NotificationStore.appSummaries(this);
        if (apps.isEmpty()) return;

        page.addView(sectionTitle(OrbitText.get(R.string.ui_apps_075ab5, "APPS")));
        for (NotificationStore.AppSummary app : apps) {
            LinearLayout row = card();
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);

            LinearLayout info = new LinearLayout(this);
            info.setOrientation(LinearLayout.VERTICAL);
            info.addView(UiKit.text(this,
                    app.appLabel.isEmpty() ? app.packageName : app.appLabel,
                    14, UiKit.TEXT, true));
            String count = app.count == 0 ? OrbitText.get(R.string.ui_no_saved_notifications_d500f2, "No saved notifications")
                    : app.count + (app.count == 1 ? OrbitText.get(R.string.ui_saved_notification_77a240, " saved notification") : OrbitText.get(R.string.ui_saved_notifications_bbbd90, " saved notifications"));
            TextView meta = UiKit.text(this, count, 11, UiKit.MUTED, false);
            meta.setPadding(0, UiKit.dp(this, 4), 0, 0);
            info.addView(meta);
            row.addView(info, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

            Button toggle = smallButton(app.blocked ? OrbitText.get(R.string.ui_excluded_bef1fd, "Excluded") : OrbitText.get(R.string.ui_included_ba829a, "Included"));
            if (!app.blocked) toggle.setTextColor(UiKit.accent(this));
            toggle.setOnClickListener(v -> {
                NotificationStore.setBlocked(this, app.packageName, !app.blocked);
                Toast.makeText(this,
                        app.blocked ? app.appLabel + OrbitText.get(R.string.ui_included_28da65, " included")
                                : app.appLabel + OrbitText.get(R.string.ui_excluded_and_its_saved_notifications_remov_2d9b49, " excluded and its saved notifications removed"),
                        Toast.LENGTH_SHORT).show();
                rebuild();
            });
            row.addView(toggle, new LinearLayout.LayoutParams(
                    UiKit.dp(this, 92), UiKit.dp(this, 40)));
            page.addView(row, cardLp());
        }
    }

    private void addRecentHistory() {
        page.addView(sectionTitle(OrbitText.get(R.string.ui_recent_history_fd2037, "RECENT HISTORY")));
        List<NotificationStore.Item> items = NotificationStore.all(this);

        if (items.isEmpty()) {
            LinearLayout empty = card();
            empty.addView(UiKit.text(this, OrbitText.get(R.string.ui_nothing_captured_yet_afb3be, "Nothing captured yet"), 15, UiKit.TEXT, true));
            TextView body = UiKit.text(this,
                    NotificationAccess.enabled(this)
                            ? OrbitText.get(R.string.ui_new_notifications_will_appear_here_as_they_3ca4d3, "New notifications will appear here as they arrive.")
                            : OrbitText.get(R.string.ui_grant_notification_access_first_orbit_cann_5b7401, "Grant Notification access first. Orbit cannot read past notifications retroactively."),
                    12, UiKit.MUTED, false);
            body.setPadding(0, UiKit.dp(this, 6), 0, 0);
            empty.addView(body);
            page.addView(empty, cardLp());
            return;
        }

        int shown = 0;
        for (NotificationStore.Item n : items) {
            if (shown >= 35) break;
            LinearLayout card = card();

            LinearLayout head = new LinearLayout(this);
            head.setGravity(Gravity.CENTER_VERTICAL);
            head.addView(UiKit.text(this,
                    n.appLabel.isEmpty() ? n.packageName : n.appLabel,
                    12, UiKit.accent(this), true),
                    new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            head.addView(UiKit.text(this,
                    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                            .format(n.postedAt),
                    10, UiKit.MUTED, false));
            card.addView(head);

            String title = !n.conversationTitle.isEmpty() ? n.conversationTitle : n.title;
            if (!title.isEmpty()) {
                TextView t = UiKit.text(this, title, 14, UiKit.TEXT, true);
                t.setPadding(0, UiKit.dp(this, 6), 0, 0);
                card.addView(t);
            }

            String body = n.compactBody();
            if (!body.isEmpty()) {
                TextView b = UiKit.text(this, body, 12, UiKit.MUTED, false);
                b.setPadding(0, UiKit.dp(this, 4), 0, 0);
                card.addView(b);
            }

            page.addView(card, cardLp());
            shown++;
        }
    }

    private void refreshSubtitle() {
        if (subtitle == null) return;
        subtitle.setText(NotificationStore.count(this) +
                (NotificationStore.count(this) == 1 ? OrbitText.get(R.string.ui_saved_notification_77a240, " saved notification") : OrbitText.get(R.string.ui_saved_notifications_bbbd90, " saved notifications")));
    }

    private String retentionLabel(int days) {
        return days == 1 ? OrbitText.get(R.string.ui_1_day_fa665d, "1 day") : days + OrbitText.get(R.string.ui_days_b1c503, " days");
    }

    private TextView sectionTitle(String text) {
        TextView t = UiKit.text(this, text, 11, UiKit.MUTED, true);
        t.setLetterSpacing(.16f);
        t.setPadding(UiKit.dp(this, 5), UiKit.dp(this, 18), 0, UiKit.dp(this, 9));
        return t;
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(UiKit.dp(this, 16), UiKit.dp(this, 14),
                UiKit.dp(this, 16), UiKit.dp(this, 14));
        c.setBackground(UiKit.outlined(UiKit.SURFACE,
                UiKit.withAlpha(UiKit.accent(this), 34), 20, this));
        return c;
    }

    private LinearLayout.LayoutParams cardLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, UiKit.dp(this, 10));
        return lp;
    }

    private Button primaryButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(13);
        b.setAllCaps(false);
        b.setTextColor(UiKit.onAccent(this));
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setStateListAnimator(null);
        b.setBackground(UiKit.ripple(UiKit.accent(this), UiKit.onAccent(this), 16, this));
        UiKit.pressScale(b);
        return b;
    }

    private Button secondaryButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(13);
        b.setAllCaps(false);
        b.setTextColor(UiKit.TEXT);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setStateListAnimator(null);
        b.setBackground(UiKit.rippleOutlined(UiKit.SURFACE,
                UiKit.withAlpha(UiKit.accent(this), 65), UiKit.accent(this), 16, this));
        UiKit.pressScale(b);
        return b;
    }

    private Button smallButton(String text) {
        Button b = secondaryButton(text);
        b.setTextSize(11);
        return b;
    }

    private void styleOrbitDialog(AlertDialog dialog) {
        UiKit.styleOrbitDialog(dialog, this, false);
    }

    private void tintDialogText(View view) {
        if (view == null) return;

        if (view instanceof TextView && !(view instanceof Button)) {
            ((TextView) view).setTextColor(UiKit.TEXT);
        }

        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                tintDialogText(group.getChildAt(i));
            }
        }
    }
}
