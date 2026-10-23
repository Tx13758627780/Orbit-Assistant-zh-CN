package com.orbit.assistant;

import android.Manifest;
import android.app.Activity;
import android.content.res.ColorStateList;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Current Orbit capability and Android-access dashboard. */
public final class CapabilitiesActivity extends Activity {
    private static final int REQ_TRIGGER_ALERTS = 864;
    private static final int REQ_MICROPHONE = 870;
    private static final int REQ_LOCATION = 871;
    private static final int REQ_CAMERA = 872;
    private static final int REQ_CONTACTS = 873;
    private static final int REQ_CALENDAR = 876;
    private static final int REQ_ROUTINE_FINE_LOCATION = 874;
    private static final int REQ_ROUTINE_BACKGROUND_LOCATION = 875;

    private TextView microphoneState;
    private Button microphoneManage;
    private TextView notificationAccessState;
    private Button notificationAccessManage;
    private TextView locationState;
    private Button locationManage;
    private TextView flashlightState;
    private Button flashlightManage;
    private TextView contactsState;
    private Button contactsManage;
    private TextView calendarState;
    private Button calendarManage;
    private TextView brightnessState;
    private TextView dndState;
    private Button brightnessSetup;
    private Button dndSetup;
    private TextView preciseTimingState;
    private TextView triggerAlertsState;
    private Button preciseTimingManage;
    private Button triggerAlertsManage;
    private TextView locationAutomationState;
    private Button locationAutomationManage;
    /** Interactive Back for this page. Its classification lives in OrbitNavigation. */
    private OrbitPredictiveBack navigation;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        UiKit.syncTheme(this);
        View root = build();
        setContentView(root);
        UiKit.applyActivityInsets(this, root, false);
        navigation = OrbitPredictiveBack.install(this);
    }

    @Override protected void onResume() {
        super.onResume();
        UiPresence.enter(this);
        refreshRuntimeAccessRows();
        refreshNotificationAccessRow();
        refreshSpecialAccessRows();
        refreshRoutineAutomationRows();
        RoutineTriggerScheduler.rescheduleAll(this);
    }
    @Override protected void onPause() { UiPresence.leave(this); super.onPause(); }

    private View build() {
        ScrollView scroll = new ScrollView(this);
        OrbitBackground.applyPage(scroll);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(UiKit.dp(this,18), UiKit.dp(this,10), UiKit.dp(this,18), UiKit.dp(this,40));
        scroll.addView(page, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        ImageButton back = iconButton(R.drawable.ic_back, OrbitText.get(R.string.ui_back_76900f, "Back"));
        back.setOnClickListener(v -> navigation.performBack());
        header.addView(back, new LinearLayout.LayoutParams(UiKit.dp(this,48), UiKit.dp(this,48)));

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.addView(UiKit.text(this, OrbitText.get(R.string.ui_capabilities_9460f1, "Capabilities"), 24, UiKit.TEXT, true));
        titles.addView(UiKit.text(this, OrbitText.get(R.string.ui_permissions_access_4ddc4a, "Permissions & access"), 12, UiKit.MUTED, false));
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        titleLp.setMargins(UiKit.dp(this,14),0,0,0);
        header.addView(titles, titleLp);
        page.addView(header);

        TextView intro = UiKit.text(this,
                OrbitText.get(R.string.ui_see_what_orbit_can_do_now_and_manage_the_a_d88fce, "See what Orbit can do now and manage the Android permissions or special access each capability needs."),
                13, UiKit.MUTED, false);
        intro.setPadding(UiKit.dp(this,4), UiKit.dp(this,14), UiKit.dp(this,4), UiKit.dp(this,4));
        page.addView(intro);

        page.addView(section(OrbitText.get(R.string.ui_voice_beta_934152, "VOICE BETA")));
        LinearLayout voice = card();
        voice.addView(runtimePermissionRow(OrbitText.get(R.string.ui_microphone_186352, "Microphone"), Manifest.permission.RECORD_AUDIO, REQ_MICROPHONE));
        voice.addView(status(OrbitText.get(R.string.ui_pause_friendly_listening_b79cb6, "Pause-friendly listening"), Prefs.voicePauseFriendly(this)));
        voice.addView(status(OrbitText.get(R.string.ui_spoken_replies_9195d3, "Spoken replies"), Prefs.speak(this)));
        voice.addView(status(OrbitText.get(R.string.ui_hands_free_follow_ups_0f1e4f, "Hands-free follow-ups"), Prefs.autoListen(this)));
        page.addView(voice, cardLp());

        page.addView(section(OrbitText.get(R.string.ui_context_a8bf80, "CONTEXT")));
        LinearLayout context = card();
        context.addView(status(OrbitText.get(R.string.ui_screen_text_495381, "Screen text"), Prefs.screenContext(this)));
        context.addView(status(OrbitText.get(R.string.ui_screenshots_067348, "Screenshots"), Prefs.screenshot(this)));
        TextView screenshotNote = UiKit.text(this,
                OrbitText.get(R.string.ui_visual_context_previews_and_screen_region_d0c0da, "Visual context, previews, and screen-region selection."),
                11, UiKit.MUTED, false);
        screenshotNote.setPadding(UiKit.dp(this, 4), 0, 0, UiKit.dp(this, 7));
        context.addView(screenshotNote);
        context.addView(notificationAccessRow());
        context.addView(runtimePermissionRow(OrbitText.get(R.string.ui_approximate_location_a31621, "Approximate location"), Manifest.permission.ACCESS_COARSE_LOCATION, REQ_LOCATION));
        context.addView(status(OrbitText.get(R.string.ui_per_app_behavior_18c11a, "Per-app behavior"), true));
        page.addView(context, cardLp());

        page.addView(section(OrbitText.get(R.string.ui_ai_personalization_c9670d, "AI & PERSONALIZATION")));
        LinearLayout ai = card();
        ai.addView(status(OrbitText.get(R.string.ui_chatgpt_account_3a477c, "ChatGPT account"), ChatGptAuth.getAccountInfo(this) != null));
        ai.addView(status(OrbitText.get(R.string.ui_direct_model_and_strength_choice_4d4a80, "Direct model and strength choice"), true));
        ai.addView(status(OrbitText.get(R.string.ui_orbit_memory_05d7a8, "Orbit Memory"), Prefs.memoryEnabled(this)));
        ai.addView(status(OrbitText.get(R.string.ui_attachments_pdf_e4de6e, "Attachments & PDF"), true));
        page.addView(ai, cardLp());

        page.addView(section(OrbitText.get(R.string.ui_device_actions_3f92d4, "DEVICE ACTIONS")));
        LinearLayout actions = card();
        actions.addView(runtimePermissionRow(OrbitText.get(R.string.ui_flashlight_control_2b1b26, "Flashlight control"), Manifest.permission.CAMERA, REQ_CAMERA));
        actions.addView(status(OrbitText.get(R.string.ui_timers_alarms_06528f, "Timers & alarms"), true));
        actions.addView(status(OrbitText.get(R.string.ui_open_apps_1ec82e, "Open apps"), true));
        actions.addView(runtimePermissionRow(OrbitText.get(R.string.ui_contact_lookup_e8d9da, "Contact lookup"), Manifest.permission.READ_CONTACTS, REQ_CONTACTS));
        // Orbit writes events itself once this is granted, rather than only opening the composer.
        // Nothing here requests it on its own; the row exists so the state is visible and
        // manageable, and Orbit still asks at the moment a Calendar action is confirmed.
        actions.addView(runtimePermissionRow(OrbitText.get(R.string.ui_calendar_events_a0aa61, "Calendar events"), Manifest.permission.WRITE_CALENDAR, REQ_CALENDAR));
        actions.addView(status(OrbitText.get(R.string.ui_chained_action_engine_347cbf, "Chained action engine"), true));
        actions.addView(specialAccessRow(OrbitText.get(R.string.ui_brightness_control_fcfe68, "Brightness control"), true));
        actions.addView(specialAccessRow(OrbitText.get(R.string.ui_do_not_disturb_control_eaafd1, "Do Not Disturb control"), false));
        actions.addView(status(OrbitText.get(R.string.ui_background_completions_7ed92c, "Background completions"), true));
        page.addView(actions, cardLp());

        page.addView(section(OrbitText.get(R.string.ui_routine_automation_c10959, "ROUTINE AUTOMATION")));
        LinearLayout automation = card();
        automation.addView(statusWithText(OrbitText.get(R.string.ui_time_triggers_decf21, "Time triggers"), OrbitText.get(R.string.ui_available_e67444, "Available"), true));
        boolean locationTriggers = OrbitDistribution.supportsLocationTriggers();
        automation.addView(statusWithText(OrbitText.get(R.string.ui_location_triggers_24a5d9, "Location triggers"),
                locationTriggers ? OrbitText.get(R.string.ui_available_e67444, "Available") : OrbitText.get(R.string.ui_not_available_67a926, "Not available"), locationTriggers));
        automation.addView(routineAutomationAccessRow(OrbitText.get(R.string.ui_precise_timing_ffd890, "Precise timing"), true));
        // No location setup row in the Play edition: there is no background location to set up.
        if (locationTriggers) automation.addView(locationAutomationAccessRow());
        automation.addView(routineAutomationAccessRow(OrbitText.get(R.string.ui_trigger_alerts_db53cd, "Trigger alerts"), false));
        TextView automationNote = UiKit.text(this, locationTriggers
                ? OrbitText.get(R.string.ui_precise_timing_uses_android_s_alarms_remin_32ed0b, "Precise timing uses Android's Alarms & reminders access for time triggers and Orbit reminders. Location triggers require precise plus background location, while Trigger alerts let Orbit hand off any automatic routine that needs a visible app or confirmation.")
                : OrbitText.get(R.string.ui_precise_timing_uses_android_s_alarms_remin_ce903f, "Precise timing uses Android's Alarms & reminders access for time triggers and Orbit reminders, while Trigger alerts let Orbit hand off any automatic routine that needs a visible app or confirmation. ")
                        + OrbitDistribution.LOCATION_TRIGGERS_UNAVAILABLE,
                12, UiKit.MUTED, false);
        automationNote.setPadding(0, UiKit.dp(this,10), 0, 0);
        automation.addView(automationNote);
        page.addView(automation, cardLp());

        return scroll;
    }

    private boolean permission(String permission) {
        return CapabilityAccessHelper.permissionGranted(this, permission);
    }

    private LinearLayout runtimePermissionRow(String name, String permission, int requestCode) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(UiKit.dp(this, 48));
        row.setPadding(0, UiKit.dp(this, 2), 0, UiKit.dp(this, 2));

        TextView label = UiKit.text(this, name, 14, UiKit.TEXT, false);
        row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView state = UiKit.text(this, OrbitText.get(R.string.ui_checking_ec963f, "Checking…"), 12, UiKit.MUTED, true);
        row.addView(state);

        Button manage = compactSetupButton(OrbitText.get(R.string.ui_set_up_4da10f, "Set up"));
        LinearLayout.LayoutParams manageLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, UiKit.dp(this, 36));
        manageLp.setMargins(UiKit.dp(this, 10), 0, 0, 0);
        row.addView(manage, manageLp);

        if (requestCode == REQ_MICROPHONE) {
            microphoneState = state;
            microphoneManage = manage;
        } else if (requestCode == REQ_LOCATION) {
            locationState = state;
            locationManage = manage;
        } else if (requestCode == REQ_CAMERA) {
            flashlightState = state;
            flashlightManage = manage;
        } else if (requestCode == REQ_CONTACTS) {
            contactsState = state;
            contactsManage = manage;
        } else if (requestCode == REQ_CALENDAR) {
            calendarState = state;
            calendarManage = manage;
        }

        if (requestCode == REQ_CALENDAR) {
            manage.setOnClickListener(v ->
                    CapabilityAccessHelper.requestOrManageCalendar(this, requestCode));
        } else {
            manage.setOnClickListener(v -> CapabilityAccessHelper.requestOrManageRuntimePermission(
                    this, permission, requestCode));
        }
        refreshRuntimeAccessRows();
        return row;
    }

    private LinearLayout notificationAccessRow() {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(UiKit.dp(this, 48));
        row.setPadding(0, UiKit.dp(this, 2), 0, UiKit.dp(this, 2));

        TextView label = UiKit.text(this, OrbitText.get(R.string.ui_notification_intelligence_b32b4b, "Notification intelligence"), 14, UiKit.TEXT, false);
        row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        notificationAccessState = UiKit.text(this, OrbitText.get(R.string.ui_checking_ec963f, "Checking…"), 12, UiKit.MUTED, true);
        row.addView(notificationAccessState);

        notificationAccessManage = compactSetupButton(OrbitText.get(R.string.ui_set_up_4da10f, "Set up"));
        LinearLayout.LayoutParams manageLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, UiKit.dp(this, 36));
        manageLp.setMargins(UiKit.dp(this, 10), 0, 0, 0);
        row.addView(notificationAccessManage, manageLp);
        notificationAccessManage.setOnClickListener(v ->
                CapabilityAccessHelper.openNotificationIntelligence(this));
        refreshNotificationAccessRow();
        return row;
    }

    private void refreshRuntimeAccessRows() {
        refreshRuntimeRow(microphoneState, microphoneManage, permission(Manifest.permission.RECORD_AUDIO));
        refreshRuntimeRow(locationState, locationManage, permission(Manifest.permission.ACCESS_COARSE_LOCATION));
        refreshRuntimeRow(flashlightState, flashlightManage, permission(Manifest.permission.CAMERA));
        refreshRuntimeRow(contactsState, contactsManage, permission(Manifest.permission.READ_CONTACTS));
        // Both halves are needed before Orbit can choose a calendar and verify what it wrote,
        // so a partial grant reads as "Needs access" rather than as ready.
        refreshRuntimeRow(calendarState, calendarManage, OrbitCalendarStore.hasAccess(this));
    }

    private void refreshRuntimeRow(TextView state, Button manage, boolean ready) {
        if (state != null) {
            state.setText(ready ? OrbitText.get(R.string.ui_ready_5fa7aa, "Ready") : OrbitText.get(R.string.ui_needs_access_67b0e1, "Needs access"));
            state.setTextColor(ready ? UiKit.SUCCESS : UiKit.MUTED);
        }
        if (manage != null) manage.setText(ready ? OrbitText.get(R.string.ui_manage_5a2344, "Manage") : OrbitText.get(R.string.ui_set_up_4da10f, "Set up"));
    }

    private void refreshNotificationAccessRow() {
        boolean ready = NotificationAccess.enabled(this);
        if (notificationAccessState != null) {
            notificationAccessState.setText(ready ? OrbitText.get(R.string.ui_ready_5fa7aa, "Ready") : OrbitText.get(R.string.ui_needs_access_67b0e1, "Needs access"));
            notificationAccessState.setTextColor(ready ? UiKit.SUCCESS : UiKit.MUTED);
        }
        if (notificationAccessManage != null) notificationAccessManage.setText(ready ? OrbitText.get(R.string.ui_manage_5a2344, "Manage") : OrbitText.get(R.string.ui_set_up_4da10f, "Set up"));
    }

    private LinearLayout specialAccessRow(String name, boolean brightness) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(UiKit.dp(this, 48));
        row.setPadding(0, UiKit.dp(this, 2), 0, UiKit.dp(this, 2));

        TextView label = UiKit.text(this, name, 14, UiKit.TEXT, false);
        row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView state = UiKit.text(this, OrbitText.get(R.string.ui_checking_ec963f, "Checking…"), 12, UiKit.MUTED, true);
        row.addView(state);

        Button setup = compactSetupButton(OrbitText.get(R.string.ui_set_up_4da10f, "Set up"));
        LinearLayout.LayoutParams setupLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, UiKit.dp(this, 36));
        setupLp.setMargins(UiKit.dp(this, 10), 0, 0, 0);
        row.addView(setup, setupLp);

        if (brightness) {
            brightnessState = state;
            brightnessSetup = setup;
            setup.setOnClickListener(v -> CapabilityAccessHelper.openWriteSettings(this));
        } else {
            dndState = state;
            dndSetup = setup;
            setup.setOnClickListener(v -> CapabilityAccessHelper.openDndAccess(this));
        }
        refreshSpecialAccessRows();
        return row;
    }

    private LinearLayout routineAutomationAccessRow(String name, boolean preciseTiming) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(UiKit.dp(this, 48));
        row.setPadding(0, UiKit.dp(this, 2), 0, UiKit.dp(this, 2));

        TextView label = UiKit.text(this, name, 14, UiKit.TEXT, false);
        row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView state = UiKit.text(this, OrbitText.get(R.string.ui_checking_ec963f, "Checking…"), 12, UiKit.MUTED, true);
        row.addView(state);

        Button manage = compactSetupButton(OrbitText.get(R.string.ui_set_up_4da10f, "Set up"));
        LinearLayout.LayoutParams manageLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, UiKit.dp(this, 36));
        manageLp.setMargins(UiKit.dp(this, 10), 0, 0, 0);
        row.addView(manage, manageLp);

        if (preciseTiming) {
            preciseTimingState = state;
            preciseTimingManage = manage;
            manage.setOnClickListener(v -> CapabilityAccessHelper.openExactAlarmAccess(this));
        } else {
            triggerAlertsState = state;
            triggerAlertsManage = manage;
            manage.setOnClickListener(v -> CapabilityAccessHelper.requestOrManageTriggerAlerts(
                    this, REQ_TRIGGER_ALERTS));
        }
        refreshRoutineAutomationRows();
        return row;
    }

    private LinearLayout locationAutomationAccessRow() {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(UiKit.dp(this, 48));
        row.setPadding(0, UiKit.dp(this, 2), 0, UiKit.dp(this, 2));

        TextView label = UiKit.text(this, OrbitText.get(R.string.ui_location_automation_3a5979, "Location automation"), 14, UiKit.TEXT, false);
        row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        locationAutomationState = UiKit.text(this, OrbitText.get(R.string.ui_checking_ec963f, "Checking…"), 12, UiKit.MUTED, true);
        row.addView(locationAutomationState);

        locationAutomationManage = compactSetupButton(OrbitText.get(R.string.ui_set_up_4da10f, "Set up"));
        LinearLayout.LayoutParams manageLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, UiKit.dp(this, 36));
        manageLp.setMargins(UiKit.dp(this, 10), 0, 0, 0);
        row.addView(locationAutomationManage, manageLp);
        locationAutomationManage.setOnClickListener(v -> CapabilityAccessHelper.setupLocationAutomation(
                this, REQ_ROUTINE_FINE_LOCATION, REQ_ROUTINE_BACKGROUND_LOCATION));
        refreshRoutineAutomationRows();
        return row;
    }

    private void refreshRoutineAutomationRows() {
        boolean fineLocation = RoutineLocationTriggerScheduler.hasFineLocation(this);
        boolean backgroundLocation = RoutineLocationTriggerScheduler.hasBackgroundLocation(this);
        boolean locationOn = RoutineLocationTriggerScheduler.isLocationEnabled(this);
        boolean locationReady = fineLocation && backgroundLocation && locationOn;
        if (locationAutomationState != null) {
            String state = CapabilityAccessHelper.locationAutomationStatus(this);
            locationAutomationState.setText(state);
            locationAutomationState.setTextColor(locationReady ? UiKit.SUCCESS : UiKit.MUTED);
        }
        if (locationAutomationManage != null) locationAutomationManage.setText(locationReady ? OrbitText.get(R.string.ui_manage_5a2344, "Manage") : OrbitText.get(R.string.ui_set_up_4da10f, "Set up"));

        boolean exact = RoutineTriggerScheduler.canScheduleExact(this);
        if (preciseTimingState != null) {
            preciseTimingState.setText(exact ? OrbitText.get(R.string.ui_ready_5fa7aa, "Ready") : OrbitText.get(R.string.ui_approximate_78b9b2, "Approximate"));
            preciseTimingState.setTextColor(exact ? UiKit.SUCCESS : UiKit.MUTED);
        }
        if (preciseTimingManage != null) {
            if (Build.VERSION.SDK_INT < 31) {
                preciseTimingManage.setVisibility(View.GONE);
            } else {
                preciseTimingManage.setVisibility(View.VISIBLE);
                preciseTimingManage.setText(exact ? OrbitText.get(R.string.ui_manage_5a2344, "Manage") : OrbitText.get(R.string.ui_set_up_4da10f, "Set up"));
            }
        }

        boolean alerts = RoutineTriggerNotifier.notificationsAllowed(this);
        if (triggerAlertsState != null) {
            triggerAlertsState.setText(alerts ? OrbitText.get(R.string.ui_ready_5fa7aa, "Ready") : OrbitText.get(R.string.ui_needs_access_67b0e1, "Needs access"));
            triggerAlertsState.setTextColor(alerts ? UiKit.SUCCESS : UiKit.MUTED);
        }
        if (triggerAlertsManage != null) {
            triggerAlertsManage.setText(alerts ? OrbitText.get(R.string.ui_manage_5a2344, "Manage") : OrbitText.get(R.string.ui_set_up_4da10f, "Set up"));
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_TRIGGER_ALERTS || requestCode == REQ_ROUTINE_FINE_LOCATION ||
                requestCode == REQ_ROUTINE_BACKGROUND_LOCATION) {
            RoutineTriggerScheduler.rescheduleAll(this);
            refreshRoutineAutomationRows();
        }
        if (requestCode == REQ_MICROPHONE || requestCode == REQ_LOCATION ||
                requestCode == REQ_CAMERA || requestCode == REQ_CONTACTS ||
                requestCode == REQ_CALENDAR) {
            refreshRuntimeAccessRows();
        }
    }

    private void tintDialogText(View v) {
        if (v == null) return;
        if (v instanceof TextView && !(v instanceof Button)) ((TextView) v).setTextColor(UiKit.TEXT);
        if (v instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) v;
            for (int i = 0; i < group.getChildCount(); i++) tintDialogText(group.getChildAt(i));
        }
    }

    private Button compactSetupButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(11);
        b.setTextColor(UiKit.accent(this));
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setStateListAnimator(null);
        b.setPadding(UiKit.dp(this, 11), 0, UiKit.dp(this, 11), 0);
        b.setBackground(UiKit.rippleOutlined(UiKit.SURFACE_2,
                UiKit.withAlpha(UiKit.accent(this), 72), UiKit.accent(this), 12, this));
        UiKit.pressScale(b);
        return b;
    }

    private void refreshSpecialAccessRows() {
        boolean brightnessReady = OrbitPermissionHelper.canWriteSystemSettings(this);
        if (brightnessState != null) {
            brightnessState.setText(brightnessReady ? OrbitText.get(R.string.ui_ready_5fa7aa, "Ready") : OrbitText.get(R.string.ui_needs_access_67b0e1, "Needs access"));
            brightnessState.setTextColor(brightnessReady ? UiKit.SUCCESS : UiKit.MUTED);
        }
        if (brightnessSetup != null) {
            brightnessSetup.setVisibility(View.VISIBLE);
            brightnessSetup.setText(brightnessReady ? OrbitText.get(R.string.ui_manage_5a2344, "Manage") : OrbitText.get(R.string.ui_set_up_4da10f, "Set up"));
        }

        boolean dndReady = OrbitPermissionHelper.hasDndAccess(this);
        if (dndState != null) {
            dndState.setText(dndReady ? OrbitText.get(R.string.ui_ready_5fa7aa, "Ready") : OrbitText.get(R.string.ui_needs_access_67b0e1, "Needs access"));
            dndState.setTextColor(dndReady ? UiKit.SUCCESS : UiKit.MUTED);
        }
        if (dndSetup != null) {
            dndSetup.setVisibility(View.VISIBLE);
            dndSetup.setText(dndReady ? OrbitText.get(R.string.ui_manage_5a2344, "Manage") : OrbitText.get(R.string.ui_set_up_4da10f, "Set up"));
        }
    }

    private LinearLayout status(String name, boolean enabled) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(UiKit.dp(this, 48));
        row.setPadding(0, UiKit.dp(this, 2), 0, UiKit.dp(this, 2));
        TextView label = UiKit.text(this, name, 14, UiKit.TEXT, false);
        row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView state = UiKit.text(this, enabled ? OrbitText.get(R.string.ui_ready_5fa7aa, "Ready") : OrbitText.get(R.string.ui_off_unavailable_3ededb, "Off / unavailable"), 12,
                enabled ? UiKit.SUCCESS : UiKit.MUTED, true);
        row.addView(state);
        return row;
    }

    private LinearLayout statusWithText(String name, String stateText, boolean positive) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(UiKit.dp(this, 48));
        row.setPadding(0, UiKit.dp(this, 2), 0, UiKit.dp(this, 2));
        TextView label = UiKit.text(this, name, 14, UiKit.TEXT, false);
        row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView state = UiKit.text(this, stateText, 12, positive ? UiKit.SUCCESS : UiKit.MUTED, true);
        row.addView(state);
        return row;
    }

    private TextView section(String text) {
        TextView t = UiKit.text(this, text, 11, UiKit.MUTED, true);
        t.setLetterSpacing(0.13f);
        t.setPadding(UiKit.dp(this,4), UiKit.dp(this,18), 0, UiKit.dp(this,8));
        return t;
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(UiKit.dp(this,16), UiKit.dp(this,10), UiKit.dp(this,16), UiKit.dp(this,10));
        c.setBackground(UiKit.outlined(UiKit.SURFACE,
                UiKit.withAlpha(UiKit.accent(this),38),20,this));
        return c;
    }

    private LinearLayout.LayoutParams cardLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0,0,0,UiKit.dp(this,2));
        return lp;
    }

    private ImageButton iconButton(int res, String desc) {
        ImageButton b = new ImageButton(this);
        b.setImageResource(res);
        b.setImageTintList(ColorStateList.valueOf(UiKit.accent(this)));
        b.setBackground(UiKit.ripple(UiKit.SURFACE,UiKit.accent(this),18,this));
        b.setContentDescription(desc);
        b.setPadding(UiKit.dp(this,11),UiKit.dp(this,11),UiKit.dp(this,11),UiKit.dp(this,11));
        UiKit.pressScale(b);
        return b;
    }
}
