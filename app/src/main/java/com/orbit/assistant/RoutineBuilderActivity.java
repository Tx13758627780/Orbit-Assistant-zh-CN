package com.orbit.assistant;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/** Describes a routine in plain language and turns it into a draft for the normal editor. */
public class RoutineBuilderActivity extends Activity {
    private static final String STATE_DESCRIPTION = "description";
    private static final String STATE_DRAFT = "draft";
    private static final String STATE_NOTICE = "notice";

    private EditText description;
    private LinearLayout resultArea;
    private Button buildButton;
    private OrbitThinkingView thinking;
    private LinearLayout thinkingRow;

    private boolean building;
    private RoutineDraft draft;
    private String automationNotice = "";

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
        // Nothing here is stored until the routine is saved, so Back has always discarded a typed
        // description without asking. That is unchanged. What is new is that the page only slides
        // away when there is nothing to discard: once there is, Back still leaves, but it does so
        // without the animation that says "leaving is settled".
        navigation = OrbitPredictiveBack.install(this, new OrbitPredictiveBack.Screen() {
            @Override public boolean canNavigate() { return !hasUnsavedWork(); }
            @Override public void navigateBack() { finish(); }
            @Override public String screenName() {
                return OrbitNavigation.labelFor(RoutineBuilderActivity.class);
            }
        });

        if (savedInstanceState != null) {
            // The typed description and any generated draft survive a recreation, and no request
            // is re-sent as a side effect of restoring them.
            description.setText(savedInstanceState.getString(STATE_DESCRIPTION, ""));
            automationNotice = savedInstanceState.getString(STATE_NOTICE, "");
            draft = RoutineDraft.fromPayload(this, savedInstanceState.getString(STATE_DRAFT, ""));
            if (draft != null) showDraft();
        }
    }

    /** The shared Back navigation this page installed. For tests. */
    OrbitPredictiveBack navigationForTest() { return navigation; }

    /** True while this screen holds typing or a generated draft that leaving would discard. */
    private boolean hasUnsavedWork() {
        if (draft != null) return true;
        return description != null && !description.getText().toString().trim().isEmpty();
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        out.putString(STATE_DESCRIPTION,
                description == null ? "" : description.getText().toString());
        out.putString(STATE_NOTICE, automationNotice);
        out.putString(STATE_DRAFT, draft == null ? "" : draft.toPayload());
        super.onSaveInstanceState(out);
    }

    @Override protected void onDestroy() {
        if (thinking != null) thinking.stop();
        super.onDestroy();
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
        LinearLayout.LayoutParams backLp = new LinearLayout.LayoutParams(
                UiKit.dp(this, 48), UiKit.dp(this, 48));
        backLp.rightMargin = UiKit.dp(this, 12);
        header.addView(back, backLp);
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.addView(UiKit.text(this, OrbitText.get(R.string.ui_create_with_orbit_b3052f, "Create with Orbit"), 25, UiKit.TEXT, true));
        titles.addView(UiKit.text(this, OrbitText.get(R.string.ui_describe_a_routine_a7a2e7, "Describe a routine"), 12, UiKit.MUTED, false));
        header.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        page.addView(header);

        TextView intro = UiKit.text(this,
                OrbitText.get(R.string.ui_describe_what_you_want_the_routine_to_do_a_39e23a, "Describe what you want the routine to do and Orbit will turn it into steps using ")
                        + OrbitText.get(R.string.ui_the_actions_it_already_supports_nothing_ru_961c02, "the actions it already supports. Nothing runs and nothing is saved until ")
                        + OrbitText.get(R.string.ui_you_review_it_and_press_save_routine_b28e49, "you review it and press Save routine."),
                14, UiKit.MUTED, false);
        intro.setLineSpacing(0, 1.13f);
        LinearLayout.LayoutParams introLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        introLp.setMargins(UiKit.dp(this, 2), UiKit.dp(this, 16), UiKit.dp(this, 2), UiKit.dp(this, 14));
        page.addView(intro, introLp);

        description = new EditText(this);
        description.setHint(OrbitText.get(R.string.ui_turn_on_do_not_disturb_set_brightness_to_2_d8bf01, "Turn on Do Not Disturb, set brightness to 20%, and lower media volume"));
        description.setHintTextColor(UiKit.MUTED);
        description.setTextColor(UiKit.TEXT);
        description.setTextSize(15);
        description.setGravity(Gravity.TOP | Gravity.START);
        description.setMinLines(4);
        description.setMaxLines(8);
        description.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        description.setPadding(UiKit.dp(this, 14), UiKit.dp(this, 12),
                UiKit.dp(this, 14), UiKit.dp(this, 12));
        description.setBackground(UiKit.outlined(UiKit.SURFACE,
                UiKit.withAlpha(UiKit.accent(this), 46), 18, this));
        description.setContentDescription(OrbitText.get(R.string.ui_routine_description_f84985, "Routine description"));
        page.addView(description, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        buildButton = primaryButton(OrbitText.get(R.string.ui_build_draft_b5e249, "Build draft"));
        buildButton.setOnClickListener(v -> startBuild());
        LinearLayout.LayoutParams buildLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, UiKit.dp(this, 50));
        buildLp.setMargins(0, UiKit.dp(this, 14), 0, 0);
        page.addView(buildButton, buildLp);

        page.addView(sectionTitle(OrbitText.get(R.string.ui_examples_04a81e, "EXAMPLES")));
        for (String example : new String[]{
                OrbitText.get(R.string.ui_bedtime_turn_on_do_not_disturb_set_brightn_9f8ff5, "Bedtime: turn on Do Not Disturb, set brightness to 15%, and lower media volume"),
                OrbitText.get(R.string.ui_focus_mode_do_not_disturb_on_and_brightnes_7eb842, "Focus mode: Do Not Disturb on and brightness at 35%"),
                OrbitText.get(R.string.ui_gaming_flashlight_off_media_volume_80_brig_3297d5, "Gaming: flashlight off, media volume 80%, brightness 90%")}) {
            page.addView(exampleRow(example));
        }

        resultArea = new LinearLayout(this);
        resultArea.setOrientation(LinearLayout.VERTICAL);
        page.addView(resultArea);

        return scroll;
    }

    private View exampleRow(String text) {
        Button row = secondaryButton(text);
        row.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        row.setPadding(UiKit.dp(this, 14), UiKit.dp(this, 10), UiKit.dp(this, 14), UiKit.dp(this, 10));
        row.setOnClickListener(v -> {
            description.setText(text);
            description.setSelection(description.length());
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, UiKit.dp(this, 8));
        row.setLayoutParams(lp);
        return row;
    }

    private void startBuild() {
        // One request at a time, so repeated taps cannot stack planning calls.
        if (building) return;
        String text = description.getText().toString().trim();
        if (text.isEmpty()) {
            showMessage(OrbitText.get(R.string.ui_describe_the_routine_you_want_orbit_to_bui_2985cc, "Describe the routine you want Orbit to build."));
            return;
        }
        building = true;
        draft = null;
        buildButton.setEnabled(false);
        buildButton.setAlpha(0.55f);
        showThinking();

        RoutinePlanner.build(this, text, new RoutinePlanner.Callback() {
            @Override public void onDraft(RoutineDraft result, String notice) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    finishBuilding();
                    draft = result;
                    automationNotice = notice == null ? "" : notice;
                    showDraft();
                });
            }

            @Override public void onError(String message) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    finishBuilding();
                    showMessage(message);
                });
            }
        });
    }

    /** Always returns the screen to a usable state, whatever the request did. */
    private void finishBuilding() {
        building = false;
        if (buildButton != null) {
            buildButton.setEnabled(true);
            buildButton.setAlpha(1f);
            buildButton.setText(OrbitText.get(R.string.ui_build_draft_b5e249, "Build draft"));
        }
        stopThinking();
    }

    private void showThinking() {
        resultArea.removeAllViews();
        thinkingRow = card();
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        thinking = new OrbitThinkingView(this);
        thinking.applyAccent(UiKit.SURFACE);
        // Decorative: the adjacent label is what carries the state for screen readers.
        thinking.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        row.addView(thinking, new LinearLayout.LayoutParams(UiKit.dp(this, 30), UiKit.dp(this, 30)));
        TextView label = UiKit.text(this, OrbitText.get(R.string.ui_building_your_routine_817e37, "Building your routine…"), 15, UiKit.TEXT, false);
        label.setPadding(UiKit.dp(this, 12), 0, 0, 0);
        row.addView(label);
        thinkingRow.addView(row);
        resultArea.addView(thinkingRow, cardLp());
        thinking.start();
    }

    private void stopThinking() {
        if (thinking != null) thinking.stop();
        thinking = null;
        if (thinkingRow != null && thinkingRow.getParent() == resultArea) {
            resultArea.removeView(thinkingRow);
        }
        thinkingRow = null;
    }

    private void showMessage(String message) {
        resultArea.removeAllViews();
        LinearLayout card = card();
        card.addView(UiKit.text(this, OrbitText.get(R.string.ui_couldn_t_build_that_routine_e328fb, "Couldn't build that routine"), 16, UiKit.TEXT, true));
        TextView note = UiKit.text(this, message, 13, UiKit.MUTED, false);
        note.setLineSpacing(0, 1.1f);
        note.setPadding(0, UiKit.dp(this, 6), 0, 0);
        card.addView(note);
        resultArea.addView(card, cardLp());
        UiKit.enterContent(card);
    }

    private void showDraft() {
        resultArea.removeAllViews();
        if (draft == null) return;

        LinearLayout card = card();
        card.addView(UiKit.text(this, draft.name, 17, UiKit.TEXT, true));

        if (draft.hasTrigger()) {
            TextView automaticLabel = UiKit.text(this, OrbitText.get(R.string.ui_automatic_7f15ac, "AUTOMATIC"), 11, UiKit.MUTED, true);
            automaticLabel.setLetterSpacing(0.14f);
            automaticLabel.setPadding(0, UiKit.dp(this, 10), 0, UiKit.dp(this, 3));
            card.addView(automaticLabel);
            card.addView(UiKit.text(this, draft.trigger.summary(this), 13, UiKit.TEXT, false));
            String readiness = draft.trigger.readiness(this);
            if (!readiness.isEmpty() && !"Ready".equals(readiness)) {
                TextView state = UiKit.text(this, readiness, 12, UiKit.DANGER, false);
                state.setPadding(0, UiKit.dp(this, 3), 0, 0);
                card.addView(state);
            }
            TextView inactive = UiKit.text(this,
                    OrbitText.get(R.string.ui_nothing_is_scheduled_until_you_review_and_645944, "Nothing is scheduled until you review and save the routine."),
                    12, UiKit.MUTED, false);
            inactive.setPadding(0, UiKit.dp(this, 4), 0, 0);
            card.addView(inactive);
            TextView stepsLabel = UiKit.text(this, OrbitText.get(R.string.ui_steps_979cfa, "STEPS"), 11, UiKit.MUTED, true);
            stepsLabel.setLetterSpacing(0.14f);
            stepsLabel.setPadding(0, UiKit.dp(this, 12), 0, UiKit.dp(this, 3));
            card.addView(stepsLabel);
        }

        TextView steps = UiKit.text(this,
                draft.actions.size() == 1 ? OrbitText.get(R.string.ui_1_step_3ce601, "1 step") : draft.actions.size() + OrbitText.get(R.string.ui_steps_947f2e, " steps"),
                12, UiKit.MUTED, false);
        steps.setPadding(0, UiKit.dp(this, 3), 0, UiKit.dp(this, 10));
        card.addView(steps);

        List<String> summaries = draft.stepSummaries();
        for (int i = 0; i < summaries.size(); i++) {
            TextView step = UiKit.text(this, "✓  " + summaries.get(i), 13, UiKit.TEXT, false);
            step.setContentDescription(OrbitText.get(R.string.ui_step_474a98, "Step ") + (i + 1) + ": " + summaries.get(i));
            step.setPadding(0, UiKit.dp(this, 4), 0, UiKit.dp(this, 4));
            card.addView(step);
        }
        resultArea.addView(card, cardLp());

        if (!draft.warnings.isEmpty()) {
            LinearLayout warn = card();
            warn.addView(UiKit.text(this, OrbitText.get(R.string.ui_needs_attention_c1ebc7, "Needs attention"), 15, UiKit.DANGER, true));
            for (String warning : draft.warnings) {
                TextView line = UiKit.text(this, "•  " + warning, 13, UiKit.MUTED, false);
                line.setPadding(0, UiKit.dp(this, 5), 0, 0);
                warn.addView(line);
            }
            resultArea.addView(warn, cardLp());
        }

        if (!automationNotice.isEmpty()) {
            LinearLayout notice = card();
            notice.addView(UiKit.text(this, OrbitText.get(R.string.ui_scheduling_ed5c58, "Scheduling"), 15, UiKit.TEXT, true));
            TextView line = UiKit.text(this, automationNotice, 13, UiKit.MUTED, false);
            line.setLineSpacing(0, 1.1f);
            line.setPadding(0, UiKit.dp(this, 5), 0, 0);
            notice.addView(line);
            resultArea.addView(notice, cardLp());
        }

        Button review = primaryButton(OrbitText.get(R.string.ui_review_routine_e3d4f8, "Review routine"));
        review.setOnClickListener(v -> {
            // Opens the ordinary editor with an unsaved draft. Nothing is stored until Save.
            startActivity(new Intent(this, RoutineEditorActivity.class)
                    .putExtra(RoutineEditorActivity.EXTRA_ROUTINE_DRAFT, draft.toPayload()));
        });
        LinearLayout.LayoutParams reviewLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, UiKit.dp(this, 50));
        reviewLp.setMargins(0, UiKit.dp(this, 4), 0, UiKit.dp(this, 8));
        resultArea.addView(review, reviewLp);

        Button change = secondaryButton(OrbitText.get(R.string.ui_change_description_3be1d2, "Change description"));
        change.setOnClickListener(v -> {
            draft = null;
            automationNotice = "";
            resultArea.removeAllViews();
            description.requestFocus();
        });
        resultArea.addView(change, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, UiKit.dp(this, 46)));
        UiKit.enterContent(card);
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
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, UiKit.dp(this, 14), 0, UiKit.dp(this, 4));
        return lp;
    }

    private TextView sectionTitle(String text) {
        TextView t = UiKit.text(this, text, 12, UiKit.MUTED, true);
        t.setLetterSpacing(0.16f);
        t.setPadding(UiKit.dp(this, 5), UiKit.dp(this, 20), 0, UiKit.dp(this, 10));
        return t;
    }

    private ImageButton iconButton(int res, String descriptionText) {
        ImageButton b = new ImageButton(this);
        b.setImageResource(res);
        b.setImageTintList(ColorStateList.valueOf(UiKit.accent(this)));
        b.setBackground(UiKit.ripple(UiKit.SURFACE_2, UiKit.accent(this), 18, this));
        b.setContentDescription(descriptionText);
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
        b.setMinHeight(0); b.setMinimumHeight(0); b.setStateListAnimator(null);
        UiKit.pressScale(b);
        return b;
    }

    private Button secondaryButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(UiKit.TEXT);
        b.setTextSize(13);
        b.setAllCaps(false);
        b.setBackground(UiKit.rippleOutlined(UiKit.SURFACE_2,
                UiKit.withAlpha(UiKit.accent(this), 70), UiKit.accent(this), 15, this));
        b.setMinHeight(0); b.setMinimumHeight(0); b.setStateListAnimator(null);
        UiKit.pressScale(b);
        return b;
    }
}
