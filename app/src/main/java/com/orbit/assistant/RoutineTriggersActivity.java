package com.orbit.assistant;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Build;
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

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Lists and manages automatic time and location triggers for one saved routine. */
public class RoutineTriggersActivity extends Activity {
    public static final String EXTRA_ROUTINE_ID = "routine_id";
    private static final int REQ_TRIGGER_ALERTS = 863;
    private static final int REQ_FINE_LOCATION = 865;
    private static final int REQ_BACKGROUND_LOCATION = 866;

    private String routineId;
    private RoutineStore.Routine routine;
    private LinearLayout timeTriggerList;
    private LinearLayout locationTriggerList;
    /** The LOCATION TRIGGERS heading, hidden in the Play edition when there is nothing to show. */
    private TextView locationSectionTitle;
    private LinearLayout precisionCard;
    private LinearLayout locationAccessCard;

    /** Interactive Back for this page. Its classification lives in OrbitNavigation. */
    private OrbitPredictiveBack navigation;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        UiKit.syncTheme(this);
        routineId = getIntent().getStringExtra(EXTRA_ROUTINE_ID);
        routine = RoutineStore.findById(this, routineId);
        if (routine == null) { finish(); return; }
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
        routine = RoutineStore.findById(this, routineId);
        if (routine == null) { finish(); return; }
        RoutineTriggerScheduler.rescheduleAll(this);
        refresh();
    }

    @Override protected void onPause() { UiPresence.leave(this); super.onPause(); }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_TRIGGER_ALERTS) refreshPrecisionCard();
        if (requestCode == REQ_FINE_LOCATION || requestCode == REQ_BACKGROUND_LOCATION) {
            RoutineTriggerScheduler.rescheduleAll(this);
            refreshLocationAccessCard();
            refresh();
        }
    }

    private View buildContent() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        OrbitBackground.applyPage(scroll);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        int p = UiKit.dp(this, 20);
        page.setPadding(p, UiKit.dp(this, 26), p, UiKit.dp(this, 48));
        scroll.addView(page, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        ImageButton back = iconButton(R.drawable.ic_back, OrbitText.get(R.string.ui_back_76900f, "Back"));
        back.setOnClickListener(v -> navigation.performBack());
        LinearLayout.LayoutParams backLp = new LinearLayout.LayoutParams(UiKit.dp(this, 48), UiKit.dp(this, 48));
        backLp.rightMargin = UiKit.dp(this, 12);
        header.addView(back, backLp);
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.addView(UiKit.text(this, OrbitText.get(R.string.ui_automatic_triggers_95752b, "Automatic triggers"), 25, UiKit.TEXT, true));
        titles.addView(UiKit.text(this, routine.name, 12, UiKit.MUTED, false));
        header.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        page.addView(header);

        TextView intro = UiKit.text(this, OrbitDistribution.supportsLocationTriggers()
                ? OrbitText.get(R.string.ui_run_this_routine_automatically_on_a_schedu_eb36c5, "Run this routine automatically on a schedule or when your phone arrives at or leaves a saved area. Every trigger keeps the routine's existing step order and uses Orbit's shared Action Engine.")
                : OrbitText.get(R.string.ui_run_this_routine_automatically_on_a_schedu_06bb0a, "Run this routine automatically on a schedule. Every trigger keeps the routine's existing step order and uses Orbit's shared Action Engine."),
                14, UiKit.MUTED, false);
        intro.setLineSpacing(0, 1.13f);
        LinearLayout.LayoutParams introLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        introLp.setMargins(UiKit.dp(this, 2), UiKit.dp(this, 16), UiKit.dp(this, 2), UiKit.dp(this, 14));
        page.addView(intro, introLp);

        precisionCard = card();
        page.addView(precisionCard, cardLp());

        locationAccessCard = card();
        page.addView(locationAccessCard, cardLp());

        Button addTime = primaryButton(OrbitText.get(R.string.ui_new_time_trigger_f8755a, "+  New time trigger"));
        addTime.setOnClickListener(v -> openNewTimeTrigger());
        page.addView(addTime, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UiKit.dp(this, 50)));

        // Only where location triggers exist. The Play edition explains their absence in
        // locationAccessCard instead of offering a button that could never arm anything.
        if (OrbitDistribution.supportsLocationTriggers()) {
            Button addLocation = secondaryButton(OrbitText.get(R.string.ui_new_location_trigger_912b8c, "+  New location trigger"));
            addLocation.setOnClickListener(v -> openNewLocationTrigger());
            LinearLayout.LayoutParams locationAddLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UiKit.dp(this, 50));
            locationAddLp.topMargin = UiKit.dp(this, 9);
            page.addView(addLocation, locationAddLp);
        }

        page.addView(sectionTitle(OrbitText.get(R.string.ui_time_triggers_de6826, "TIME TRIGGERS")));
        timeTriggerList = new LinearLayout(this);
        timeTriggerList.setOrientation(LinearLayout.VERTICAL);
        page.addView(timeTriggerList);

        locationSectionTitle = sectionTitle(OrbitText.get(R.string.ui_location_triggers_9ba7c4, "LOCATION TRIGGERS"));
        page.addView(locationSectionTitle);
        locationTriggerList = new LinearLayout(this);
        locationTriggerList.setOrientation(LinearLayout.VERTICAL);
        page.addView(locationTriggerList);
        return scroll;
    }

    private void openNewTimeTrigger() {
        if (!canAddTrigger()) return;
        startActivity(new Intent(this, TimeTriggerEditorActivity.class)
                .putExtra(TimeTriggerEditorActivity.EXTRA_ROUTINE_ID, routineId));
    }

    private void openNewLocationTrigger() {
        if (!OrbitDistribution.supportsLocationTriggers() || !canAddTrigger()) return;
        startActivity(new Intent(this, LocationTriggerEditorActivity.class)
                .putExtra(LocationTriggerEditorActivity.EXTRA_ROUTINE_ID, routineId));
    }

    private boolean canAddTrigger() {
        if (RoutineTriggerStore.listForRoutine(this, routineId).size() >= RoutineTriggerStore.MAX_TRIGGERS_PER_ROUTINE) {
            Toast.makeText(this, OrbitText.get(R.string.ui_this_routine_already_has_the_maximum_numbe_101630, "This routine already has the maximum number of triggers."), Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    private void refresh() {
        refreshPrecisionCard();
        refreshLocationAccessCard();
        if (timeTriggerList == null || locationTriggerList == null) return;
        timeTriggerList.removeAllViews();
        locationTriggerList.removeAllViews();
        List<RoutineTriggerStore.Trigger> times = new ArrayList<>();
        List<RoutineTriggerStore.Trigger> locations = new ArrayList<>();
        for (RoutineTriggerStore.Trigger trigger : RoutineTriggerStore.listForRoutine(this, routineId)) {
            if (RoutineTriggerStore.TYPE_LOCATION.equals(trigger.type)) locations.add(trigger);
            else times.add(trigger);
        }
        renderTriggerGroup(timeTriggerList, times, false);
        // In the Play edition an empty location section would invite something it cannot do.
        // Saved location triggers, such as ones brought over from a GitHub install, are still
        // listed so they can be seen and deleted, and nothing about them is changed.
        boolean showLocations = OrbitDistribution.supportsLocationTriggers() || !locations.isEmpty();
        if (locationSectionTitle != null) {
            locationSectionTitle.setVisibility(showLocations ? View.VISIBLE : View.GONE);
        }
        locationTriggerList.setVisibility(showLocations ? View.VISIBLE : View.GONE);
        if (showLocations) renderTriggerGroup(locationTriggerList, locations, true);
    }

    private void renderTriggerGroup(LinearLayout list, List<RoutineTriggerStore.Trigger> triggers, boolean location) {
        if (triggers.isEmpty()) {
            LinearLayout empty = card();
            empty.addView(UiKit.text(this, location ? OrbitText.get(R.string.ui_no_location_triggers_e7a7c2, "No location triggers") : OrbitText.get(R.string.ui_no_time_triggers_39ff6f, "No time triggers"), 16, UiKit.TEXT, true));
            TextView note = UiKit.text(this, location
                            ? OrbitText.get(R.string.ui_add_an_arrive_or_leave_trigger_for_places_217eff, "Add an arrive or leave trigger for places such as Home, Work, or the gym.")
                            : OrbitText.get(R.string.ui_add_a_schedule_such_as_daily_weekdays_week_7b516e, "Add a schedule such as daily, weekdays, weekly, biweekly/fortnightly, or a custom interval."),
                    13, UiKit.MUTED, false);
            note.setPadding(0, UiKit.dp(this, 6), 0, 0);
            empty.addView(note);
            list.addView(empty, cardLp());
            return;
        }
        for (RoutineTriggerStore.Trigger trigger : triggers) list.addView(triggerCard(trigger), cardLp());
    }

    private void refreshPrecisionCard() {
        if (precisionCard == null) return;
        precisionCard.removeAllViews();
        boolean exact = RoutineTriggerScheduler.canScheduleExact(this);
        precisionCard.addView(UiKit.text(this, exact ? OrbitText.get(R.string.ui_precise_timing_ready_43ab09, "Precise timing ready") : OrbitText.get(R.string.ui_approximate_timing_4d83e6, "Approximate timing"), 14,
                exact ? UiKit.SUCCESS : UiKit.TEXT, true));
        TextView detail = UiKit.text(this, exact
                        ? OrbitText.get(R.string.ui_android_allows_orbit_to_schedule_time_trig_3dedbe, "Android allows Orbit to schedule time triggers at the selected minute.")
                        : OrbitText.get(R.string.ui_time_triggers_still_run_but_android_may_de_c3eaea, "Time triggers still run, but Android may delay them, particularly while the phone is idle. Allow Alarms & reminders for minute-level scheduling."),
                12, UiKit.MUTED, false);
        detail.setPadding(0, UiKit.dp(this, 5), 0, 0);
        precisionCard.addView(detail);
        if (!exact) {
            Button allow = secondaryButton(OrbitText.get(R.string.ui_allow_precise_timing_2db0b4, "Allow precise timing"));
            allow.setOnClickListener(v -> RoutineTriggerScheduler.openExactAlarmAccess(this));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UiKit.dp(this, 42));
            lp.topMargin = UiKit.dp(this, 10);
            precisionCard.addView(allow, lp);
        }
        boolean notifications = RoutineTriggerNotifier.notificationsAllowed(this);
        boolean runtimeNotifications = RoutineTriggerNotifier.runtimePermissionGranted(this);
        TextView notifyState = UiKit.text(this, notifications
                        ? OrbitText.get(R.string.ui_trigger_alerts_ready_orbit_can_notify_you_142b2c, "Trigger alerts ready · Orbit can notify you when an automatic routine needs a foreground step.")
                        : OrbitText.get(R.string.ui_trigger_alerts_are_off_fully_background_sa_e5a691, "Trigger alerts are off · fully background-safe routines can still run. Routines that need a tap are skipped rather than partially applied."),
                11, UiKit.MUTED, false);
        notifyState.setPadding(0, UiKit.dp(this, 9), 0, 0);
        precisionCard.addView(notifyState);
        if (!notifications) {
            Button alertSettings;
            if (!runtimeNotifications && Build.VERSION.SDK_INT >= 33) {
                alertSettings = secondaryButton(OrbitText.get(R.string.ui_allow_trigger_alerts_186f92, "Allow trigger alerts"));
                alertSettings.setOnClickListener(v -> requestPermissions(
                        new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_TRIGGER_ALERTS));
            } else {
                alertSettings = secondaryButton(OrbitText.get(R.string.ui_open_trigger_alert_settings_6b7d11, "Open trigger alert settings"));
                alertSettings.setOnClickListener(v -> RoutineTriggerNotifier.openAlertSettings(this));
            }
            LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, UiKit.dp(this, 42));
            nlp.topMargin = UiKit.dp(this, 9);
            precisionCard.addView(alertSettings, nlp);
        }
    }

    private void refreshLocationAccessCard() {
        if (locationAccessCard == null) return;
        locationAccessCard.removeAllViews();
        if (!OrbitDistribution.supportsLocationTriggers()) {
            locationAccessCard.addView(UiKit.text(this, OrbitText.get(R.string.ui_location_triggers_24a5d9, "Location triggers"), 14, UiKit.TEXT, true));
            TextView unavailable = UiKit.text(this, OrbitDistribution.LOCATION_TRIGGERS_UNAVAILABLE,
                    12, UiKit.MUTED, false);
            unavailable.setPadding(0, UiKit.dp(this, 6), 0, 0);
            locationAccessCard.addView(unavailable);
            return;
        }
        boolean fine = RoutineLocationTriggerScheduler.hasFineLocation(this);
        boolean background = RoutineLocationTriggerScheduler.hasBackgroundLocation(this);
        boolean locationOn = RoutineLocationTriggerScheduler.isLocationEnabled(this);
        boolean ready = fine && background && locationOn;
        locationAccessCard.addView(UiKit.text(this, ready ? OrbitText.get(R.string.ui_location_triggers_ready_f87c42, "Location triggers ready") : OrbitText.get(R.string.ui_location_setup_needed_b0c695, "Location setup needed"), 14,
                ready ? UiKit.SUCCESS : UiKit.TEXT, true));
        String detail;
        String button;
        if (!fine) {
            detail = OrbitText.get(R.string.ui_arrive_leave_automation_needs_precise_loca_e7b837, "Arrive/leave automation needs precise location so Orbit can detect a saved radius.");
            button = OrbitText.get(R.string.ui_allow_precise_location_30f69a, "Allow precise location");
        } else if (!background) {
            String option = RoutineLocationTriggerScheduler.backgroundPermissionLabel(this);
            detail = OrbitText.get(R.string.ui_to_work_while_orbit_is_closed_android_requ_c1a4ab, "To work while Orbit is closed, Android requires Location permission set to “") + option + "”.";
            button = OrbitText.get(R.string.ui_allow_background_location_0c9108, "Allow background location");
        } else if (!locationOn) {
            detail = OrbitText.get(R.string.ui_android_location_services_are_off_turn_the_ee7327, "Android location services are off. Turn them on to monitor arrive/leave triggers.");
            button = OrbitText.get(R.string.ui_turn_on_location_78f4ce, "Turn on location");
        } else {
            detail = OrbitText.get(R.string.ui_precise_and_background_location_are_availa_3130e6, "Precise and background location are available. Enabled location triggers can be monitored while Orbit is closed.");
            button = OrbitText.get(R.string.ui_manage_location_access_77b092, "Manage location access");
        }
        TextView note = UiKit.text(this, detail, 12, UiKit.MUTED, false);
        note.setPadding(0, UiKit.dp(this, 5), 0, UiKit.dp(this, 10));
        locationAccessCard.addView(note);
        Button manage = secondaryButton(button);
        manage.setOnClickListener(v -> setupLocationAccess());
        locationAccessCard.addView(manage, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UiKit.dp(this, 42)));
    }

    private void setupLocationAccess() {
        if (!RoutineLocationTriggerScheduler.hasFineLocation(this)) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION}, REQ_FINE_LOCATION);
            return;
        }
        if (!RoutineLocationTriggerScheduler.hasBackgroundLocation(this)) {
            if (Build.VERSION.SDK_INT == 29) {
                requestPermissions(new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION}, REQ_BACKGROUND_LOCATION);
            } else {
                showBackgroundLocationExplanation();
            }
            return;
        }
        if (!RoutineLocationTriggerScheduler.isLocationEnabled(this)) {
            RoutineLocationTriggerScheduler.openLocationServices(this);
            return;
        }
        RoutineLocationTriggerScheduler.openAppLocationSettings(this);
    }

    private void showBackgroundLocationExplanation() {
        String option = RoutineLocationTriggerScheduler.backgroundPermissionLabel(this);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(OrbitText.get(R.string.ui_allow_background_location_0c9108, "Allow background location"))
                .setMessage(OrbitText.get(R.string.ui_android_requires_background_access_for_loc_34d542, "Android requires background access for location automation. Open Orbit's app settings, choose Permissions → Location, then select “") + option + "”.")
                .setNegativeButton(OrbitText.get(R.string.ui_not_now_a0e63d, "Not now"), null)
                .setPositiveButton(OrbitText.get(R.string.ui_open_settings_ca381c, "Open settings"), (d, w) -> RoutineLocationTriggerScheduler.openAppLocationSettings(this))
                .create();
        styleOrbitDialog(dialog, false);
        dialog.show();
    }

    private View triggerCard(RoutineTriggerStore.Trigger trigger) {
        LinearLayout card = card();
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        TextView schedule = UiKit.text(this, RoutineTriggerSchedule.summary(trigger), 15, UiKit.TEXT, true);
        text.addView(schedule);
        TextView next = UiKit.text(this, triggerStateLabel(trigger), 12, UiKit.MUTED, false);
        next.setPadding(0, UiKit.dp(this, 4), 0, 0);
        text.addView(next);
        top.addView(text, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        if (RoutineTriggerStore.TYPE_LOCATION.equals(trigger.type)
                && !OrbitDistribution.supportsLocationTriggers()) {
            // A saved location trigger in the Play edition: no switch, because nothing it could
            // switch on would ever run, and its stored on/off state is left for a GitHub install.
            // Deleting it is the one thing that still means something.
            ImageButton more = iconButton(R.drawable.ic_more, OrbitText.get(R.string.ui_trigger_options_8c60d4, "Trigger options"));
            more.setOnClickListener(v -> UiKit.showOrbitMenu(this, more, new String[]{OrbitText.get(R.string.ui_delete_e2d0a5, "Delete")}, -1,
                    (index, label) -> confirmDelete(trigger)));
            top.addView(more, new LinearLayout.LayoutParams(UiKit.dp(this, 44), UiKit.dp(this, 44)));
            card.addView(top);
            return card;
        }

        OrbitSwitch enabled = new OrbitSwitch(this);
        enabled.setChecked(trigger.enabled, false);
        enabled.setContentDescription(trigger.enabled ? OrbitText.get(R.string.ui_disable_trigger_0dfb14, "Disable trigger") : OrbitText.get(R.string.ui_enable_trigger_1e3e32, "Enable trigger"));
        enabled.setOnCheckedChangeListener((button, checked) -> {
            RoutineTriggerStore.Trigger latest = RoutineTriggerStore.findById(this, trigger.id);
            if (latest == null || latest.enabled == checked) return;
            RoutineTriggerStore.Trigger changed = latest.withEnabled(checked);
            if (checked && RoutineTriggerStore.hasEnabledScheduleConflict(this, changed)) {
                Toast.makeText(this, OrbitText.get(R.string.ui_an_enabled_trigger_with_this_exact_setup_a_396e82, "An enabled trigger with this exact setup already exists."), Toast.LENGTH_LONG).show();
                refresh();
                return;
            }
            if (RoutineTriggerStore.upsert(this, changed)) {
                if (checked) {
                    if (RoutineTriggerStore.TYPE_LOCATION.equals(changed.type)) {
                        boolean scheduled = RoutineLocationTriggerScheduler.schedule(this, changed);
                        if (!scheduled && !RoutineLocationTriggerScheduler.ready(this)) {
                            Toast.makeText(this, OrbitText.get(R.string.ui_trigger_enabled_but_location_setup_is_stil_a5cee9, "Trigger enabled, but Location setup is still required before Android can monitor it."), Toast.LENGTH_LONG).show();
                        }
                    } else {
                        long scheduled = RoutineTriggerScheduler.schedule(this, changed);
                        if (scheduled <= 0L && RoutineTriggerStore.MODE_ONCE.equals(changed.mode)) {
                            Toast.makeText(this, OrbitText.get(R.string.ui_that_one_time_schedule_has_already_passed_dcdcf5, "That one-time schedule has already passed. Edit its date or time to enable it again."), Toast.LENGTH_LONG).show();
                        }
                    }
                } else {
                    RoutineTriggerScheduler.cancel(this, changed.id);
                }
                refresh();
            }
        });
        // No press-scale here: the thumb and track already carry the interaction, and stacking a
        // second animation on top of them reads as busy.
        top.addView(enabled, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ImageButton more = iconButton(R.drawable.ic_more, OrbitText.get(R.string.ui_trigger_options_8c60d4, "Trigger options"));
        more.setOnClickListener(v -> showTriggerMenu(more, trigger));
        top.addView(more, new LinearLayout.LayoutParams(UiKit.dp(this, 44), UiKit.dp(this, 44)));
        card.addView(top);

        if (!trigger.lastResult.isEmpty()) {
            TextView status = UiKit.text(this, OrbitText.get(R.string.ui_last_d7e6ea, "Last: ") + trigger.lastResult, 11,
                    trigger.lastResult.toLowerCase(Locale.US).contains("completed") ? UiKit.SUCCESS : UiKit.MUTED, false);
            status.setPadding(0, UiKit.dp(this, 8), 0, 0);
            card.addView(status);
        }
        return card;
    }

    private String triggerStateLabel(RoutineTriggerStore.Trigger trigger) {
        if (RoutineTriggerStore.TYPE_LOCATION.equals(trigger.type)) {
            if (!OrbitDistribution.supportsLocationTriggers()) {
                return OrbitDistribution.LOCATION_TRIGGER_UNAVAILABLE_STATE;
            }
            if (!trigger.enabled) return OrbitText.get(R.string.ui_off_ca7981, "Off");
            if (!RoutineLocationTriggerScheduler.hasFineLocation(this)) return OrbitText.get(R.string.ui_needs_precise_location_access_0152ab, "Needs precise location access");
            if (!RoutineLocationTriggerScheduler.hasBackgroundLocation(this)) return OrbitText.get(R.string.ui_needs_background_location_access_975455, "Needs background location access");
            if (!RoutineLocationTriggerScheduler.isLocationEnabled(this)) return OrbitText.get(R.string.ui_phone_location_is_off_75c161, "Phone location is off");
            return OrbitText.get(R.string.ui_ready_for_background_monitoring_2d5ef1, "Ready for background monitoring");
        }
        return nextTimeLabel(trigger);
    }

    private String nextTimeLabel(RoutineTriggerStore.Trigger t) {
        if (!t.enabled) {
            if (RoutineTriggerStore.MODE_ONCE.equals(t.mode) && t.lastRunAt > 0L) return OrbitText.get(R.string.ui_finished_7804f7, "Finished");
            return OrbitText.get(R.string.ui_off_ca7981, "Off");
        }
        if (t.nextRunAt <= 0) return RoutineTriggerStore.MODE_ONCE.equals(t.mode) ? OrbitText.get(R.string.ui_finished_7804f7, "Finished") : OrbitText.get(R.string.ui_not_scheduled_b3e247, "Not scheduled");
        ZonedDateTime z = Instant.ofEpochMilli(t.nextRunAt).atZone(ZoneId.systemDefault());
        LocalDate d = z.toLocalDate();
        LocalDate today = LocalDate.now();
        String day;
        if (d.equals(today)) day = OrbitText.get(R.string.ui_today_2b065c, "Today");
        else if (d.equals(today.plusDays(1))) day = OrbitText.get(R.string.ui_tomorrow_456a73, "Tomorrow");
        else day = z.format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US));
        return OrbitText.get(R.string.ui_next_ec19ea, "Next · ") + day + OrbitText.get(R.string.ui_at_086a51, " at ") + RoutineActionCatalog.timeLabel(z.getHour(), z.getMinute());
    }

    private void showTriggerMenu(View anchor, RoutineTriggerStore.Trigger trigger) {
        String[] labels = {OrbitText.get(R.string.ui_edit_464c4f, "Edit"), OrbitText.get(R.string.ui_duplicate_02cdaa, "Duplicate"), OrbitText.get(R.string.ui_delete_e2d0a5, "Delete")};
        UiKit.showOrbitMenu(this, anchor, labels, -1, (index, label) -> {
            if (index == 0) openEditor(trigger);
            else if (index == 1) duplicate(trigger);
            else confirmDelete(trigger);
        });
    }

    private void duplicate(RoutineTriggerStore.Trigger source) {
        if (!canAddTrigger()) return;
        RoutineTriggerStore.Trigger copy;
        if (RoutineTriggerStore.TYPE_LOCATION.equals(source.type)) {
            copy = RoutineTriggerStore.createLocation(source.routineId, source.locationName,
                    source.latitude, source.longitude, source.radiusMeters,
                    source.locationTransition).withEnabled(false);
        } else {
            copy = RoutineTriggerStore.createTime(source.routineId, source.mode,
                    source.hour, source.minute, source.startYear, source.startMonth, source.startDay,
                    source.weekdayMask, source.intervalCount, source.intervalUnit).withEnabled(false);
        }
        if (RoutineTriggerStore.upsert(this, copy)) {
            Toast.makeText(this, OrbitText.get(R.string.ui_trigger_duplicated_off_until_you_enable_it_c068a2, "Trigger duplicated · off until you enable it."), Toast.LENGTH_SHORT).show();
            refresh();
        }
    }

    private void confirmDelete(RoutineTriggerStore.Trigger trigger) {
        String kind = RoutineTriggerStore.TYPE_LOCATION.equals(trigger.type) ? OrbitText.get(R.string.ui_location_trigger_0d6a73, "location trigger") : OrbitText.get(R.string.ui_time_trigger_8407f2, "time trigger");
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(OrbitText.get(R.string.ui_delete_85941f, "Delete ") + kind + "?")
                .setMessage(RoutineTriggerSchedule.summary(trigger))
                .setNegativeButton(OrbitText.get(R.string.ui_cancel_19766e, "Cancel"), null)
                .setPositiveButton(OrbitText.get(R.string.ui_delete_e2d0a5, "Delete"), (d, w) -> {
                    RoutineTriggerScheduler.cancel(this, trigger.id);
                    RoutineTriggerStore.delete(this, trigger.id);
                    refresh();
                }).create();
        styleOrbitDialog(dialog, true);
        dialog.show();
    }

    private void openEditor(RoutineTriggerStore.Trigger trigger) {
        if (RoutineTriggerStore.TYPE_LOCATION.equals(trigger.type)) {
            startActivity(new Intent(this, LocationTriggerEditorActivity.class)
                    .putExtra(LocationTriggerEditorActivity.EXTRA_ROUTINE_ID, routineId)
                    .putExtra(LocationTriggerEditorActivity.EXTRA_TRIGGER_ID, trigger.id));
        } else {
            startActivity(new Intent(this, TimeTriggerEditorActivity.class)
                    .putExtra(TimeTriggerEditorActivity.EXTRA_ROUTINE_ID, routineId)
                    .putExtra(TimeTriggerEditorActivity.EXTRA_TRIGGER_ID, trigger.id));
        }
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(UiKit.dp(this, 17), UiKit.dp(this, 15), UiKit.dp(this, 17), UiKit.dp(this, 15));
        c.setBackground(UiKit.outlined(UiKit.SURFACE, UiKit.withAlpha(UiKit.accent(this), 40), 20, this));
        c.setElevation(UiKit.dp(this, 2));
        return c;
    }

    private LinearLayout.LayoutParams cardLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, UiKit.dp(this, 10));
        return lp;
    }

    private TextView sectionTitle(String s) {
        TextView t = UiKit.text(this, s, 12, UiKit.MUTED, true);
        t.setLetterSpacing(0.16f);
        t.setPadding(UiKit.dp(this, 5), UiKit.dp(this, 14), 0, UiKit.dp(this, 10));
        return t;
    }

    private ImageButton iconButton(int res, String desc) {
        ImageButton b = new ImageButton(this);
        b.setImageResource(res);
        b.setImageTintList(ColorStateList.valueOf(UiKit.accent(this)));
        b.setBackground(UiKit.ripple(UiKit.SURFACE_2, UiKit.accent(this), 18, this));
        b.setContentDescription(desc);
        b.setPadding(UiKit.dp(this, 11), UiKit.dp(this, 11), UiKit.dp(this, 11), UiKit.dp(this, 11));
        UiKit.pressScale(b);
        return b;
    }

    private Button primaryButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(UiKit.onAccent(this));
        b.setTextSize(14);
        b.setAllCaps(false);
        b.setBackground(UiKit.ripple(UiKit.accent(this), UiKit.onAccent(this), 15, this));
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setStateListAnimator(null);
        UiKit.pressScale(b);
        return b;
    }

    private Button secondaryButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(UiKit.TEXT);
        b.setTextSize(14);
        b.setAllCaps(false);
        b.setBackground(UiKit.rippleOutlined(UiKit.SURFACE_2, Color.rgb(53,58,72), UiKit.accent(this), 15, this));
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setStateListAnimator(null);
        UiKit.pressScale(b);
        return b;
    }

    private void styleOrbitDialog(AlertDialog dialog, boolean destructive) {
        UiKit.styleOrbitDialog(dialog, this, destructive);
    }

    private void tint(View v) {
        if (v == null) return;
        if (v instanceof TextView && !(v instanceof Button)) ((TextView)v).setTextColor(UiKit.TEXT);
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup)v).getChildCount(); i++) tint(((ViewGroup)v).getChildAt(i));
    }
}
