package com.orbit.assistant;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;


public class SettingsActivity extends Activity implements UiKit.AppearanceListener {
    private static final int REQ_AUDIO = 71;
    private static final int REQ_CAMERA = 72;
    private static final int REQ_CONTACTS = 73;
    private static final int REQ_NOTIFICATIONS = 74;
    private static final int REQ_LOCATION = 75;
    private static final int REQ_EXPORT_BACKUP = 76;
    private static final int REQ_RESTORE_BACKUP = 77;
    private static final int REQ_ASSISTANT_SETTINGS = 78;
    private static final String TAG_CARD = "orbit_card";
    private static final String TAG_SECTION_PREFIX = "orbit_settings_section:";
    static final String EXTRA_SECTION = "settings_section";
    /**
     * The control a detail page should scroll to and pulse when it opens, or absent.
     *
     * <p>What turns a search result from "took me to Look &amp; Feel" into "took me to AMOLED".
     * Carried as a control key rather than a position, because positions change every time a
     * control is added and a stale one would land somebody in the wrong place silently.
     */
    static final String EXTRA_FOCUS = "settings_focus";
    private static final String SECTION_ASSISTANT = "assistant";
    static final String SECTION_AI = "ai";
    private static final String SECTION_VOICE = "voice";
    static final String SECTION_DATA = "data";
    private static final String SECTION_DECK = "deck";
    private static final String SECTION_CONVERSATIONS = "conversations";
    static final String SECTION_ROUTINES = "routines";
    private static final String SECTION_EXTENSIONS = "extensions";
    static final String SECTION_APPEARANCE = "appearance";
    private static final String SECTION_UPDATES = "updates";
    static final String SECTION_ADVANCED = "advanced";

    /** The ChatGPT account card, where "Sign in with ChatGPT" lives. */
    static Intent chatGptAccountIntent(Context context) {
        return new Intent(context, SettingsActivity.class).putExtra(EXTRA_SECTION, SECTION_AI)
                .putExtra(EXTRA_FOCUS, SettingsSearchIndex.KEY_CHATGPT_ACCOUNT);
    }

    public static Intent assistantSetupIntent(Context context) {
        return new Intent(context, SettingsActivity.class).putExtra(EXTRA_SECTION, SECTION_ASSISTANT);
    }
    private TextView assistantStatus;
    private Button assistantAction;
    /** The Vault card's way into Smart Vault, hidden with the Vault itself. */
    private Button smartVaultButton;
    private OverlayPreviewView overlayPreview;
    private TextView quickRoutineSelection;
    private TextView chatGptStatus;
    private LinearLayout providerDetails;
    private TextView providerRowStatus;
    /** Which provider the details block is currently built for, so onResume can catch switches. */
    private String providerDetailsFor;
    private TextView chatGptHelp;
    private Button chatGptSignIn;
    private Button chatGptSignOut;
    /** "Having trouble? Use code sign-in": the device-code fallback, always one tap away. */
    private Button chatGptCodeSignIn;
    private ScrollView settingsScroll;
    private String appliedAppearance = "";
    /** Accent/AMOLED only — the appearance that forces a rebuild. */
    private String appliedStructuralAppearance = "";
    private boolean rebuildingAppearance;
    private int leloTapCount = 0;
    private long lastLeloTapMs = 0L;
    private String settingsSection = "";
    /**
     * Where each searchable control ended up in the tree Orbit just built.
     *
     * <p>Rebuilt with the page, so an appearance change that rebuilds Settings in place cannot
     * leave the map pointing at detached views. Empty on the hub, which has no controls of its own.
     */
    private final java.util.Map<String, View> searchTargets = new java.util.HashMap<>();
    /** The control this page was opened to show, consumed once it has been shown. */
    private String pendingFocusKey = "";
    /** The hub's search field and its results, or null on a detail page. */
    private EditText searchField;
    private LinearLayout searchResults;
    private ImageButton searchClear;
    /** The hub's category cards, put aside while results are showing. */
    private LinearLayout categoryList;
    private final ChatGptAuth.LoginCallback chatGptLoginCallback = new ChatGptAuth.LoginCallback() {
        @Override public void onSuccess(ChatGptAuth.AccountInfo account) {
            runOnUiThread(() -> {
                if (!canShowAuthResult()) return;
                updateChatGptStatus();
                Toast.makeText(SettingsActivity.this,
                        OrbitText.get(R.string.ui_chatgpt_connected_to_orbit_61c512, "ChatGPT connected to Orbit"), Toast.LENGTH_LONG).show();
            });
        }

        @Override public void onError(String message) {
            runOnUiThread(() -> {
                if (!canShowAuthResult()) return;
                updateChatGptStatus();
                showOrbitMessageDialog(OrbitText.get(R.string.ui_chatgpt_sign_in_could_not_complete_2ffa82, "ChatGPT sign-in could not complete"), message);
            });
        }
    };

    /** Hears how a browser sign-in ended. Attached while this screen is in front. */
    private final ChatGptBrowserAuth.Listener chatGptBrowserListener = new ChatGptBrowserAuth.Listener() {
         public void onSignedIn(ChatGptAuth.AccountInfo account) {
            if (!canShowAuthResult()) return;
            updateChatGptStatus();
            Toast.makeText(SettingsActivity.this, OrbitText.get(R.string.ui_chatgpt_connected_to_orbit_61c512, "ChatGPT connected to Orbit"), Toast.LENGTH_LONG).show();
        }

         public void onFailed(String message, boolean offerCodeSignIn) {
            if (!canShowAuthResult()) return;
            updateChatGptStatus();
            showBrowserSignInFailure(message, offerCodeSignIn);
        }

         public void onCancelled() {
            if (canShowAuthResult()) updateChatGptStatus();
        }
    };

    /** Interactive Back for this page. Its classification lives in OrbitNavigation. */
    private OrbitPredictiveBack navigation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        UiKit.syncTheme(this);
        settingsSection = normalizeSection(getIntent() == null ? null : getIntent().getStringExtra(EXTRA_SECTION));
        String focus = getIntent() == null ? null : getIntent().getStringExtra(EXTRA_FOCUS);
        pendingFocusKey = focus == null ? "" : focus.trim();
        Window w = getWindow();
        w.setStatusBarColor(UiKit.BG);
        w.setNavigationBarColor(UiKit.BG);
        // Paint the window itself in Orbit's surface colour. If a rebuild ever exposes the window
        // for a frame it shows Orbit's background rather than the default decor or a black frame.
        w.setBackgroundDrawable(new ColorDrawable(UiKit.BG));
        View content = buildContent();
        setContentView(content);
        UiKit.applyActivityInsets(this, content, true);
        navigation = OrbitPredictiveBack.install(this);
        appliedAppearance = UiKit.appearanceSignature(this);
        appliedStructuralAppearance = UiKit.structuralAppearanceSignature(this);
    }

    @Override
    protected void onStart() {
        super.onStart();
        UiKit.registerAppearanceListener(this);
    }

    @Override
    protected void onStop() {
        UiKit.unregisterAppearanceListener(this);
        super.onStop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        UiPresence.enter(this);
        applyAppearanceChange();
        updateAssistantStatus();
        refreshQuickRoutineSelection();
        refreshProviderSection();
        ChatGptBrowserAuth.attach(chatGptBrowserListener);
        updateChatGptStatus();
        if (chatGptStatus != null && !ChatGptAuth.isSignedIn(this) &&
                ChatGptAuth.resumePendingDeviceCode(this, chatGptLoginCallback)) {
            chatGptStatus.setText(OrbitText.get(R.string.ui_finishing_secure_chatgpt_sign_in_7777d7, "Finishing secure ChatGPT sign-in…"));
            chatGptStatus.setTextColor(UiKit.TEXT);
        }
    }

    @Override
    protected void onPause() {
        UiPresence.leave(this);
        // The attempt keeps running while the browser is in front; this screen just stops being
        // the one told about it, and hears the outcome when it comes back.
        ChatGptBrowserAuth.detach(chatGptBrowserListener);
        super.onPause();
    }

    private View buildContent() {
        // Rebuilt with the page, so an in-place appearance rebuild can never leave the search map
        // pointing at views that are no longer on screen.
        searchTargets.clear();
        searchField = null;
        searchResults = null;
        searchClear = null;
        categoryList = null;
        View content = settingsSection.isEmpty() ? buildSettingsHub() : buildDetailContent();
        focusPendingControl();
        return content;
    }

    private View buildSettingsHub() {
        ScrollView scroll = new ScrollView(this);
        settingsScroll = scroll;
        scroll.setFillViewport(true);
        OrbitBackground.applyPage(scroll);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        int p = UiKit.dp(this, 20);
        page.setPadding(p, UiKit.dp(this, 26), p, UiKit.dp(this, 48));
        scroll.addView(page, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout brand = new LinearLayout(this);
        brand.setGravity(Gravity.CENTER_VERTICAL);
        View logo = UiKit.orbitMark(this, 42);
        LinearLayout.LayoutParams logoLp = new LinearLayout.LayoutParams(UiKit.dp(this, 48), UiKit.dp(this, 48));
        logoLp.rightMargin = UiKit.dp(this, 10);
        brand.addView(logo, logoLp);

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.addView(UiKit.text(this, OrbitText.get(R.string.ui_settings_74a883, "Settings"), 28, UiKit.TEXT, true));
        titles.addView(UiKit.text(this, OrbitText.get(R.string.ui_orbit_power_assistant_9a4f13, "Orbit Power Assistant"), 13, UiKit.MUTED, false));
        brand.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        page.addView(brand);

        TextView intro = UiKit.text(this,
                OrbitText.get(R.string.ui_search_for_a_control_or_choose_a_section_o_04807c, "Search for a control, or choose a section. Orbit keeps the everyday controls easy to find while deeper options stay out of the way."),
                14, UiKit.MUTED, false);
        intro.setLineSpacing(0, 1.12f);
        LinearLayout.LayoutParams introLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        introLp.setMargins(UiKit.dp(this, 2), UiKit.dp(this, 18), UiKit.dp(this, 2), UiKit.dp(this, 10));
        page.addView(intro, introLp);

        page.addView(buildSearchField(), searchFieldLp());
        searchResults = new LinearLayout(this);
        searchResults.setOrientation(LinearLayout.VERTICAL);
        searchResults.setVisibility(View.GONE);
        page.addView(searchResults, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // The category cards live in a container of their own so searching can put them aside in
        // one move. Hiding eleven cards individually would work and would also mean the next card
        // added to this screen quietly failing to disappear.
        LinearLayout categories = new LinearLayout(this);
        categories.setOrientation(LinearLayout.VERTICAL);
        categoryList = categories;
        page.addView(categories, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        categories.addView(settingsCategoryCard(SECTION_ASSISTANT, OrbitText.get(R.string.ui_assistant_setup_a047e8, "Assistant setup"),
                OrbitText.get(R.string.ui_default_assistant_side_button_and_quick_se_4f2c7c, "Default assistant, Side button and Quick Settings access")), categoryLp());
        categories.addView(settingsCategoryCard(SECTION_AI, OrbitText.get(R.string.ui_ai_account_6fa54f, "AI & account"),
                OrbitText.get(R.string.ui_ai_providers_chatgpt_sign_in_and_default_a_df2efd, "AI Providers, ChatGPT sign-in and default AI")), categoryLp());
        categories.addView(settingsCategoryCard(SECTION_VOICE, OrbitText.get(R.string.ui_voice_context_permissions_4f431d, "Voice, context & permissions"),
                OrbitText.get(R.string.ui_voice_beta_screen_context_and_capabilities_724fcc, "Voice Beta, screen context and capabilities")), categoryLp());
        categories.addView(settingsCategoryCard(SECTION_DATA, OrbitText.get(R.string.ui_personalization_data_853fcd, "Personalization & data"),
                OrbitText.get(R.string.ui_weather_gallery_reminders_saved_places_mem_2260a1, "Weather, Gallery, reminders, saved places, Memory and backup")), categoryLp());
        categories.addView(settingsCategoryCard(SECTION_ROUTINES, OrbitText.get(R.string.ui_routines_61b7bb, "Routines"),
                OrbitText.get(R.string.ui_create_edit_and_run_saved_action_engine_ch_346aeb, "Create, edit and run saved Action Engine chains")), categoryLp());
        categories.addView(settingsCategoryCard(SECTION_EXTENSIONS, OrbitText.get(R.string.ui_extensions_fe3436, "Extensions"),
                OrbitText.get(R.string.ui_add_integrations_and_new_actions_to_orbit_8ae30a, "Add integrations and new actions to Orbit")), categoryLp());
        categories.addView(settingsCategoryCard(SECTION_DECK, OrbitText.get(R.string.ui_orbit_deck_9ba2c9, "Orbit Deck"),
                OrbitText.get(R.string.ui_customize_your_personal_orbit_shortcuts_f552b4, "Customize your personal Orbit shortcuts")), categoryLp());
        categories.addView(settingsCategoryCard(SECTION_CONVERSATIONS, OrbitText.get(R.string.ui_conversations_1d432f, "Conversations"),
                OrbitText.get(R.string.ui_history_chat_behavior_and_background_notif_992c2f, "History, chat behavior and background notifications")), categoryLp());
        categories.addView(settingsCategoryCard(SECTION_APPEARANCE, OrbitText.get(R.string.ui_look_feel_a4103a, "Look & Feel"),
                OrbitText.get(R.string.ui_theme_studio_font_chat_text_size_and_hapti_bf74c1, "Theme Studio, font, chat text size and haptics")), categoryLp());
        categories.addView(settingsCategoryCard(SECTION_UPDATES, OrbitText.get(R.string.ui_about_updates_1b0c20, "About & updates"),
                OrbitText.get(R.string.ui_current_version_and_verified_official_orbi_0592f5, "Current version and verified official Orbit releases")), categoryLp());
        categories.addView(settingsCategoryCard(SECTION_ADVANCED, OrbitText.get(R.string.ui_advanced_9f088d, "Advanced"),
                OrbitText.get(R.string.ui_diagnostics_and_developer_tools_3cd51c, "Diagnostics and developer tools")), categoryLp());

        TextView footer = UiKit.text(this, "Orbit " + BuildConfig.VERSION_NAME + OrbitText.get(R.string.ui_power_assistant_80cc1c, " • Power Assistant"), 12, UiKit.MUTED, false);
        footer.setGravity(Gravity.CENTER);
        footer.setOnClickListener(v -> handleLeloSecretTap());
        footer.setOnLongClickListener(v -> {
            startActivity(new Intent(this, DiagnosticsActivity.class));
            return true;
        });
        LinearLayout.LayoutParams footerLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        footerLp.setMargins(0, UiKit.dp(this, 24), 0, 0);
        page.addView(footer, footerLp);
        return scroll;
    }

    /**
     * The Settings search field.
     *
     * <p>One compact row rather than another card. Search is a way through the page, not a section
     * of it, and giving it a full card with a heading would make the screen longer for everybody in
     * order to make it shorter for the person who types in it.
     */
    private View buildSearchField() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(UiKit.dp(this, 14), 0, UiKit.dp(this, 8), 0);
        row.setBackground(UiKit.outlined(UiKit.SURFACE_2,
                UiKit.withAlpha(UiKit.accent(this), 62), 16, this));

        ImageButton icon = new ImageButton(this);
        icon.setImageResource(R.drawable.ic_search);
        icon.setBackground(null);
        icon.setColorFilter(UiKit.MUTED);
        icon.setPadding(0, 0, UiKit.dp(this, 10), 0);
        // Decoration beside a labelled field: a screen reader reads the field, not the picture.
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        icon.setClickable(false);
        icon.setFocusable(false);
        row.addView(icon, new LinearLayout.LayoutParams(UiKit.dp(this, 30), UiKit.dp(this, 22)));

        // Orbit's own field, with its surface removed: the row around it is already the outlined
        // box, and a second one inside it would read as a field inside a field.
        EditText field = UiKit.input(this, OrbitText.get(R.string.ui_search_settings_255b07, "Search settings"), false);
        field.setBackground(null);
        field.setPadding(0, 0, 0, 0);
        field.setMinHeight(0);
        field.setSingleLine(true);
        field.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        field.setContentDescription(OrbitText.get(R.string.ui_search_settings_255b07, "Search settings"));
        field.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(android.text.Editable s) {
                showSearchResults(s == null ? "" : s.toString());
            }
        });
        searchField = field;
        row.addView(field, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        ImageButton clear = new ImageButton(this);
        clear.setImageResource(R.drawable.ic_close);
        clear.setBackground(UiKit.ripple(Color.TRANSPARENT, UiKit.accent(this), 18, this));
        clear.setColorFilter(UiKit.MUTED);
        clear.setContentDescription(OrbitText.get(R.string.ui_clear_settings_search_63c62e, "Clear settings search"));
        clear.setVisibility(View.GONE);
        clear.setOnClickListener(v -> {
            field.setText("");
            UiKit.haptic(v, HapticFeedbackConstants.CLOCK_TICK);
        });
        searchClear = clear;
        row.addView(clear, new LinearLayout.LayoutParams(UiKit.dp(this, 36), UiKit.dp(this, 36)));
        return row;
    }

    private LinearLayout.LayoutParams searchFieldLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, UiKit.dp(this, 50));
        lp.setMargins(0, UiKit.dp(this, 2), 0, UiKit.dp(this, 12));
        return lp;
    }

    /**
     * Draws the results for what has been typed, and puts the category cards aside while it does.
     *
     * <p>Results replace the categories rather than sitting above them. Leaving eleven cards below
     * a list of three answers would mean the person who searched still has to scroll past
     * everything they were trying to skip, which is the problem search exists to solve.
     *
     * <p>Entirely local and synchronous. The index is forty entries in memory, so this runs on
     * every keystroke without a debounce, a thread, or a request to anything.
     */
    private void showSearchResults(String query) {
        if (searchResults == null || categoryList == null) return;
        String text = query == null ? "" : query.trim();
        if (searchClear != null) {
            searchClear.setVisibility(text.isEmpty() ? View.GONE : View.VISIBLE);
        }
        searchResults.removeAllViews();
        if (text.isEmpty()) {
            searchResults.setVisibility(View.GONE);
            categoryList.setVisibility(View.VISIBLE);
            return;
        }
        categoryList.setVisibility(View.GONE);
        searchResults.setVisibility(View.VISIBLE);

        List<SettingsSearchIndex.Entry> matches = SettingsSearchIndex.search(text);
        if (matches.isEmpty()) {
            LinearLayout empty = card();
            empty.addView(UiKit.text(this, OrbitText.get(R.string.ui_no_settings_found_3e4d15, "No settings found"), 16, UiKit.TEXT, true));
            TextView hint = UiKit.text(this,
                    OrbitText.get(R.string.ui_try_a_shorter_word_or_the_name_of_the_thin_593ba5, "Try a shorter word, or the name of the thing you want to change."),
                    13, UiKit.MUTED, false);
            hint.setPadding(0, UiKit.dp(this, 6), 0, 0);
            empty.addView(hint);
            searchResults.addView(empty, categoryLp());
            return;
        }
        for (SettingsSearchIndex.Entry entry : matches) {
            searchResults.addView(searchResultRow(entry), categoryLp());
        }
    }

    /** One result: the control's own words, and which part of Settings it lives in. */
    private View searchResultRow(SettingsSearchIndex.Entry entry) {
        LinearLayout row = card();
        row.setPadding(UiKit.dp(this, 16), UiKit.dp(this, 13), UiKit.dp(this, 16), UiKit.dp(this, 13));
        row.setBackground(UiKit.rippleOutlined(UiKit.SURFACE,
                UiKit.withAlpha(UiKit.accent(this), 38), UiKit.accent(this), 20, this));
        row.addView(UiKit.text(this, entry.title, 15, UiKit.TEXT, true));
        TextView where = UiKit.text(this, entry.subtitle(), 12, UiKit.MUTED, false);
        where.setPadding(0, UiKit.dp(this, 4), 0, 0);
        row.addView(where);
        if (!entry.description.isEmpty()) {
            TextView what = UiKit.text(this, entry.description, 12, UiKit.MUTED, false);
            what.setLineSpacing(0, 1.1f);
            what.setPadding(0, UiKit.dp(this, 4), 0, 0);
            row.addView(what);
        }
        row.setContentDescription(entry.title + OrbitText.get(R.string.ui_in_aab38d, ", in ") + entry.subtitle());
        row.setOnClickListener(v -> {
            UiKit.haptic(v, HapticFeedbackConstants.CLOCK_TICK);
            openSearchResult(entry);
        });
        UiKit.pressScale(row);
        return row;
    }

    /**
     * Takes the user to the control they picked.
     *
     * <p>Through the same {@link #openSettingsSection} every category card uses, plus the control
     * key when there is one. A result whose setting lives on a screen of its own - Routines,
     * Extensions, updates - opens that screen, which is exactly where the user was heading; there
     * is no row to pulse because there is no row.
     */
    private void openSearchResult(SettingsSearchIndex.Entry entry) {
        if (entry == null) return;
        if (searchField != null) {
            // Cleared before leaving, so coming back with Back shows the settings page rather than
            // a stale result list for a control the user has already reached.
            searchField.setText("");
            searchField.clearFocus();
        }
        openSettingsSection(entry.section, entry.controlKey);
    }

    private LinearLayout settingsCategoryCard(String section, String title, String subtitle) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(UiKit.dp(this, 17), UiKit.dp(this, 15), UiKit.dp(this, 15), UiKit.dp(this, 15));
        row.setBackground(UiKit.rippleOutlined(UiKit.SURFACE,
                UiKit.withAlpha(UiKit.accent(this), 38), UiKit.accent(this), 20, this));
        row.setElevation(UiKit.dp(this, 2));
        row.setClickable(true);
        row.setFocusable(true);

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        text.addView(UiKit.text(this, title, 16, UiKit.TEXT, true));
        TextView sub = UiKit.text(this, subtitle, 12, UiKit.MUTED, false);
        sub.setPadding(0, UiKit.dp(this, 3), 0, 0);
        text.addView(sub);
        row.addView(text, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView arrow = UiKit.text(this, "›", 28, UiKit.accent(this), false);
        arrow.setGravity(Gravity.CENTER);
        arrow.setPadding(UiKit.dp(this, 12), 0, 0, 0);
        row.addView(arrow, new LinearLayout.LayoutParams(UiKit.dp(this, 34), ViewGroup.LayoutParams.MATCH_PARENT));

        row.setOnClickListener(v -> openSettingsSection(section));
        UiKit.pressScale(row);
        return row;
    }

    private LinearLayout.LayoutParams categoryLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, UiKit.dp(this, 8), 0, 0);
        return lp;
    }

    private void openSettingsSection(String section) {
        openSettingsSection(section, "");
    }

    private void openSettingsSection(String section, String focusKey) {
        if (SECTION_ROUTINES.equals(section)) {
            startActivity(new Intent(this, RoutinesActivity.class));
            return;
        }
        if (SECTION_EXTENSIONS.equals(section)) {
            startActivity(new Intent(this, ExtensionsActivity.class));
            return;
        }
        if (SECTION_UPDATES.equals(section)) {
            startActivity(new Intent(this, UpdateActivity.class));
            return;
        }
        Intent intent = new Intent(this, SettingsActivity.class);
        intent.putExtra(EXTRA_SECTION, section);
        if (focusKey != null && !focusKey.isEmpty()) intent.putExtra(EXTRA_FOCUS, focusKey);
        startActivity(intent);
    }

    /** The shared Back navigation this page installed. For tests. */
    OrbitPredictiveBack navigationForTest() { return navigation; }

    /** Opens a section exactly as tapping its card does. For tests. */
    void openSectionForTest(String section) { openSettingsSection(section); }

    /** For tests: the results currently drawn for a query, without touching the field. */
    java.util.List<String> searchResultTitlesForTest(String query) {
        java.util.List<String> titles = new java.util.ArrayList<>();
        for (SettingsSearchIndex.Entry entry : SettingsSearchIndex.search(query)) {
            titles.add(entry.title);
        }
        return titles;
    }

    /** For tests: type into the hub's search field exactly as a person would. */
    void typeSearchForTest(String query) {
        if (searchField != null) searchField.setText(query == null ? "" : query);
    }

    /** For tests: how many result rows are on screen. */
    int searchResultCountForTest() {
        return searchResults == null || searchResults.getVisibility() != View.VISIBLE
                ? 0 : searchResults.getChildCount();
    }

    /** For tests: the result rows themselves, so one can be chosen the way a person would. */
    java.util.List<View> searchResultsForTest() {
        java.util.List<View> rows = new java.util.ArrayList<>();
        if (searchResults == null) return rows;
        for (int i = 0; i < searchResults.getChildCount(); i++) {
            rows.add(searchResults.getChildAt(i));
        }
        return rows;
    }

    /** For tests: whether the category cards are currently put aside. */
    boolean categoriesHiddenForTest() {
        return categoryList != null && categoryList.getVisibility() != View.VISIBLE;
    }

    /** For tests: whether a detail page found and settled on the control it was opened for. */
    boolean focusedControlForTest(String key) {
        View target = searchTargets.get(key);
        return target != null && target.isAttachedToWindow();
    }

    /** For tests: which control keys this page registered. */
    java.util.Set<String> searchTargetKeysForTest() { return searchTargets.keySet(); }

    private String normalizeSection(String section) {
        if (SECTION_ASSISTANT.equals(section) || SECTION_AI.equals(section) ||
                SECTION_VOICE.equals(section) || SECTION_DATA.equals(section) || SECTION_CONVERSATIONS.equals(section) ||
                SECTION_APPEARANCE.equals(section) || SECTION_ADVANCED.equals(section) ||
                SECTION_DECK.equals(section)) return section;
        return "";
    }

    private String sectionDisplayName(String section) {
        if (SECTION_ASSISTANT.equals(section)) return OrbitText.get(R.string.ui_assistant_setup_a047e8, "Assistant setup");
        if (SECTION_AI.equals(section)) return OrbitText.get(R.string.ui_ai_account_6fa54f, "AI & account");
        if (SECTION_VOICE.equals(section)) return OrbitText.get(R.string.ui_voice_context_permissions_4f431d, "Voice, context & permissions");
        if (SECTION_DATA.equals(section)) return OrbitText.get(R.string.ui_personalization_data_853fcd, "Personalization & data");
        if (SECTION_DECK.equals(section)) return OrbitText.get(R.string.ui_orbit_deck_9ba2c9, "Orbit Deck");
        if (SECTION_CONVERSATIONS.equals(section)) return OrbitText.get(R.string.ui_conversations_1d432f, "Conversations");
        if (SECTION_APPEARANCE.equals(section)) return OrbitText.get(R.string.ui_look_feel_a4103a, "Look & Feel");
        if (SECTION_ADVANCED.equals(section)) return OrbitText.get(R.string.ui_advanced_9f088d, "Advanced");
        return OrbitText.get(R.string.ui_settings_74a883, "Settings");
    }

    private String sectionDescription(String section) {
        if (SECTION_ASSISTANT.equals(section)) return OrbitText.get(R.string.ui_set_orbit_as_your_assistant_and_configure_918c21, "Set Orbit as your assistant and configure Side button and Quick Settings access.");
        if (SECTION_AI.equals(section)) return OrbitText.get(R.string.ui_choose_and_configure_orbit_s_active_ai_pro_c011da, "Choose and configure Orbit's active AI provider, manage your ChatGPT connection, and set default intelligence.");
        if (SECTION_VOICE.equals(section)) return OrbitText.get(R.string.ui_control_voice_beta_screen_awareness_and_de_438006, "Control Voice Beta, screen awareness and device permissions.");
        if (SECTION_DATA.equals(section)) return OrbitText.get(R.string.ui_manage_weather_preferences_and_the_local_i_708753, "Manage weather preferences and the local information Orbit uses to personalize and organize your assistant experience.");
        if (SECTION_DECK.equals(section)) return OrbitText.get(R.string.ui_choose_how_orbit_deck_is_reached_and_wheth_fbcbb0, "Choose how Orbit Deck is reached and whether it may suggest shortcuts.");
        if (SECTION_CONVERSATIONS.equals(section)) return OrbitText.get(R.string.ui_choose_local_chat_storage_and_background_c_5afe1d, "Choose local chat storage and background completion behavior.");
        if (SECTION_APPEARANCE.equals(section)) return OrbitText.get(R.string.ui_design_orbit_s_theme_then_set_typography_a_39c0ef, "Design Orbit's theme, then set typography and tactile feedback.");
        if (SECTION_ADVANCED.equals(section)) return OrbitText.get(R.string.ui_inspect_local_diagnostics_and_developer_tr_3e8e05, "Inspect local diagnostics and developer troubleshooting information.");
        return OrbitText.get(R.string.ui_orbit_settings_fe42a1, "Orbit settings.");
    }

    private String sectionSubtitle(String section) {
        if (SECTION_ASSISTANT.equals(section)) return OrbitText.get(R.string.ui_core_setup_e575a0, "Core setup");
        if (SECTION_AI.equals(section)) return OrbitText.get(R.string.ui_models_access_e33ea5, "Models & access");
        if (SECTION_VOICE.equals(section)) return OrbitText.get(R.string.ui_input_awareness_dd9d99, "Input & awareness");
        if (SECTION_DATA.equals(section)) return OrbitText.get(R.string.ui_local_context_cc1b01, "Local context");
        if (SECTION_DECK.equals(section)) return OrbitText.get(R.string.ui_shortcuts_46e978, "Shortcuts");
        if (SECTION_CONVERSATIONS.equals(section)) return OrbitText.get(R.string.ui_chat_behavior_1419bd, "Chat behavior");
        if (SECTION_APPEARANCE.equals(section)) return OrbitText.get(R.string.ui_style_feedback_25c3ba, "Style & feedback");
        if (SECTION_ADVANCED.equals(section)) return OrbitText.get(R.string.ui_developer_tools_96f0c0, "Developer tools");
        return "Orbit";
    }

    private View buildDetailContent() {
        ScrollView scroll = new ScrollView(this);
        settingsScroll = scroll;
        scroll.setFillViewport(true);
        OrbitBackground.applyPage(scroll);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        int p = UiKit.dp(this, 20);
        page.setPadding(p, UiKit.dp(this, 30), p, UiKit.dp(this, 48));
        scroll.addView(page, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout brand = new LinearLayout(this);
        brand.setGravity(Gravity.CENTER_VERTICAL);
        ImageButton back = new ImageButton(this);
        back.setImageResource(R.drawable.ic_back);
        back.setImageTintList(android.content.res.ColorStateList.valueOf(UiKit.accent(this)));
        back.setBackground(UiKit.ripple(UiKit.SURFACE_2, UiKit.accent(this), 18, this));
        back.setContentDescription(OrbitText.get(R.string.ui_back_to_settings_ea37ec, "Back to Settings"));
        back.setPadding(UiKit.dp(this, 11), UiKit.dp(this, 11), UiKit.dp(this, 11), UiKit.dp(this, 11));
        back.setOnClickListener(v -> navigation.performBack());
        UiKit.pressScale(back);
        LinearLayout.LayoutParams backLp = new LinearLayout.LayoutParams(UiKit.dp(this, 48), UiKit.dp(this, 48));
        backLp.rightMargin = UiKit.dp(this, 12);
        brand.addView(back, backLp);

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        TextView title = UiKit.text(this, sectionDisplayName(settingsSection), 24, UiKit.TEXT, true);
        TextView subtitle = UiKit.text(this, sectionSubtitle(settingsSection), 12, UiKit.MUTED, false);
        titles.addView(title);
        titles.addView(subtitle);
        brand.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        page.addView(brand);

        TextView intro = UiKit.text(this, sectionDescription(settingsSection), 14, UiKit.MUTED, false);
        intro.setLineSpacing(0, 1.14f);
        LinearLayout.LayoutParams introLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        introLp.setMargins(UiKit.dp(this, 2), UiKit.dp(this, 16), UiKit.dp(this, 2), UiKit.dp(this, 8));
        page.addView(intro, introLp);

        page.addView(sectionTitle(OrbitText.get(R.string.ui_setup_7175b0, "SETUP"), "setup"));
        LinearLayout setupCard = card();
        tagSectionCard(setupCard, "setup");
        assistantStatus = UiKit.text(this, OrbitText.get(R.string.ui_checking_assistant_status_4430bc, "Checking assistant status…"), 15, UiKit.TEXT, true);
        setupCard.addView(assistantStatus);
        TextView desc = UiKit.text(this, OrbitText.get(R.string.ui_after_choosing_orbit_as_the_default_assist_b3cab3, "After choosing Orbit as the default assistant, use whichever assistant button or gesture Android exposes on your device."), 13, UiKit.MUTED, false);
        desc.setPadding(0, UiKit.dp(this, 7), 0, UiKit.dp(this, 14));
        setupCard.addView(desc);
        assistantAction = primaryButton(OrbitText.get(R.string.ui_make_orbit_default_assistant_7a5864, "Make Orbit default assistant"));
        assistantAction.setOnClickListener(v -> openAssistantSettings(true));
        setupCard.addView(assistantAction);
        Button rerunSetup = secondaryButton(OrbitText.get(R.string.ui_run_setup_again_bb6f72, "Run setup again"));
        rerunSetup.setOnClickListener(v ->
                startActivity(OnboardingActivity.manualIntent(this)));
        LinearLayout.LayoutParams rerunLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, UiKit.dp(this, 44));
        rerunLp.setMargins(0, UiKit.dp(this, 9), 0, 0);
        setupCard.addView(rerunSetup, rerunLp);
        page.addView(target(SettingsSearchIndex.KEY_DEFAULT_ASSISTANT, setupCard));

        page.addView(sectionTitle(OrbitText.get(R.string.ui_quick_access_3ae0fc, "QUICK ACCESS"), "setup"));
        LinearLayout quickCard = card();
        tagSectionCard(quickCard, "setup");
        quickCard.addView(UiKit.text(this, OrbitText.get(R.string.ui_quick_settings_tiles_4b5739, "Quick Settings tiles"), 16, UiKit.TEXT, true));
        TextView quickNote = UiKit.text(this,
                OrbitText.get(R.string.ui_open_orbit_instantly_or_assign_one_saved_r_df301a, "Open Orbit instantly, or assign one saved Routine to a Quick Settings tile."),
                12, UiKit.MUTED, false);
        quickNote.setLineSpacing(0, 1.12f);
        quickNote.setPadding(0, UiKit.dp(this, 6), 0, UiKit.dp(this, 12));
        quickCard.addView(quickNote);

        LinearLayout tileButtons = new LinearLayout(this);
        tileButtons.setGravity(Gravity.CENTER_VERTICAL);
        Button addOrbit = secondaryButton(OrbitText.get(R.string.ui_add_orbit_tile_b63bc1, "Add Orbit tile"));
        addOrbit.setOnClickListener(v -> QuickSettingsTiles.requestAddAskTile(this));
        tileButtons.addView(addOrbit, new LinearLayout.LayoutParams(0, UiKit.dp(this, 44), 1));
        Button addRoutine = secondaryButton(OrbitText.get(R.string.ui_add_routine_tile_b36fec, "Add Routine tile"));
        addRoutine.setOnClickListener(v -> QuickSettingsTiles.requestAddRoutineTile(this));
        LinearLayout.LayoutParams addRoutineLp = new LinearLayout.LayoutParams(0, UiKit.dp(this, 44), 1);
        addRoutineLp.leftMargin = UiKit.dp(this, 9);
        tileButtons.addView(addRoutine, addRoutineLp);
        quickCard.addView(tileButtons);

        TextView addHint = UiKit.text(this,
                Build.VERSION.SDK_INT >= 33
                        ? OrbitText.get(R.string.ui_android_will_ask_before_adding_either_tile_58235e, "Android will ask before adding either tile.")
                        : OrbitText.get(R.string.ui_add_the_orbit_tiles_manually_from_android_867f4d, "Add the Orbit tiles manually from Android's Quick Settings editor."),
                11, UiKit.MUTED, false);
        addHint.setPadding(0, UiKit.dp(this, 9), 0, UiKit.dp(this, 12));
        quickCard.addView(addHint);

        quickRoutineSelection = UiKit.text(this, OrbitText.get(R.string.ui_quick_settings_routine_none_dee991, "Quick Settings Routine: None"), 13, UiKit.TEXT, true);
        quickRoutineSelection.setPadding(0, 0, 0, UiKit.dp(this, 8));
        quickCard.addView(quickRoutineSelection);
        Button chooseRoutine = secondaryButton(OrbitText.get(R.string.ui_choose_quick_settings_routine_cddbc2, "Choose Quick Settings Routine"));
        chooseRoutine.setOnClickListener(v -> showQuickRoutineChooser());
        quickCard.addView(chooseRoutine, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, UiKit.dp(this, 44)));
        refreshQuickRoutineSelection();
        page.addView(target(SettingsSearchIndex.KEY_QUICK_TILES, quickCard));

        page.addView(sectionTitle(OrbitText.get(R.string.ui_home_screen_widgets_61423f, "HOME-SCREEN WIDGETS"), "setup"));
        LinearLayout widgetCard = card();
        tagSectionCard(widgetCard, "setup");
        widgetCard.addView(UiKit.text(this, OrbitText.get(R.string.ui_orbit_widgets_e89c2c, "Orbit widgets"), 16, UiKit.TEXT, true));
        TextView widgetNote = UiKit.text(this,
                OrbitText.get(R.string.ui_add_orbit_widgets_from_your_launcher_s_wid_b29e45, "Add Orbit widgets from your launcher's widget picker, or ask Android to pin one below."),
                12, UiKit.MUTED, false);
        widgetNote.setLineSpacing(0, 1.12f);
        widgetNote.setPadding(0, UiKit.dp(this, 6), 0, UiKit.dp(this, 12));
        widgetCard.addView(widgetNote);

        TextView widgetEditNote = UiKit.text(this,
                OrbitText.get(R.string.ui_the_buttons_below_add_new_widgets_to_chang_7955d6, "The buttons below add new widgets. To change one later, touch and hold it on your Home screen, then choose Settings, Edit, or Configure. The exact label depends on your launcher."),
                11, UiKit.MUTED, false);
        widgetEditNote.setLineSpacing(0, 1.12f);
        widgetEditNote.setPadding(0, 0, 0, UiKit.dp(this, 12));
        widgetCard.addView(widgetEditNote);

        Button pinAskWidget = secondaryButton(OrbitText.get(R.string.ui_add_ask_orbit_widget_1b6545, "Add Ask Orbit widget"));
        pinAskWidget.setOnClickListener(v -> OrbitWidgets.requestPin(this,
                AskOrbitWidgetProvider.class, OrbitText.get(R.string.ui_ask_orbit_18ea29, "Ask Orbit")));
        widgetCard.addView(pinAskWidget, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, UiKit.dp(this, 44)));

        Button pinRoutineWidget = secondaryButton(OrbitText.get(R.string.ui_add_run_routine_widget_889ddf, "Add Run Routine widget"));
        pinRoutineWidget.setOnClickListener(v -> OrbitWidgets.requestPin(this,
                RunRoutineWidgetProvider.class, OrbitText.get(R.string.ui_run_routine_489eae, "Run Routine")));
        LinearLayout.LayoutParams pinRoutineWidgetLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, UiKit.dp(this, 44));
        pinRoutineWidgetLp.setMargins(0, UiKit.dp(this, 9), 0, 0);
        widgetCard.addView(pinRoutineWidget, pinRoutineWidgetLp);

        Button pinQuickWidget = secondaryButton(OrbitText.get(R.string.ui_add_quick_actions_widget_aec2b0, "Add Quick Actions widget"));
        pinQuickWidget.setOnClickListener(v -> OrbitWidgets.requestPin(this,
                QuickActionsWidgetProvider.class, OrbitText.get(R.string.ui_quick_actions_2cc2b6, "Quick Actions")));
        LinearLayout.LayoutParams pinQuickWidgetLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, UiKit.dp(this, 44));
        pinQuickWidgetLp.setMargins(0, UiKit.dp(this, 9), 0, 0);
        widgetCard.addView(pinQuickWidget, pinQuickWidgetLp);
        page.addView(target(SettingsSearchIndex.KEY_WIDGETS, widgetCard));

        TextView remindersSection = sectionTitle(OrbitText.get(R.string.ui_reminders_63f24c, "REMINDERS"), "data");
        LinearLayout remindersCard = card();
        tagSectionCard(remindersCard, "data");
        TextView remindersHelp = UiKit.text(this,
                OrbitText.get(R.string.ui_orbit_reminders_are_scheduled_locally_on_t_5837cd, "Orbit reminders are scheduled locally on this device and appear as notifications at the time you choose."),
                13, UiKit.MUTED, false);
        remindersHelp.setPadding(0, 0, 0, UiKit.dp(this, 12));
        remindersCard.addView(remindersHelp);
        Button manageReminders = secondaryButton(OrbitText.get(R.string.ui_manage_orbit_reminders_400661, "Manage Orbit reminders"));
        manageReminders.setOnClickListener(v -> startActivity(new Intent(this, RemindersActivity.class)));
        remindersCard.addView(manageReminders, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, UiKit.dp(this, 48)));
        // Added with the other Personalization & data sections below.

        TextView personalizationSection = sectionTitle(OrbitText.get(R.string.ui_personalization_context_f525de, "PERSONALIZATION & CONTEXT"), "data");
        LinearLayout personalDataCard = card();
        tagSectionCard(personalDataCard, "data");
        TextView personalDataHelp = UiKit.text(this,
                OrbitText.get(R.string.ui_open_orbit_s_dedicated_managers_for_saved_31cd17, "Open Orbit's dedicated managers for saved places, remembered information, your Vault, per-app behavior and notification context."),
                13, UiKit.MUTED, false);
        personalDataHelp.setPadding(0, 0, 0, UiKit.dp(this, 12));
        personalDataCard.addView(personalDataHelp);

        Button managePlaces = secondaryButton(OrbitText.get(R.string.ui_manage_saved_places_071418, "Manage saved places"));
        managePlaces.setOnClickListener(v -> startActivity(new Intent(this, SavedPlacesActivity.class)));
        target(SettingsSearchIndex.KEY_PLACES, managePlaces);
        personalDataCard.addView(managePlaces, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, UiKit.dp(this, 48)));

        Button manageMemory = secondaryButton(OrbitText.get(R.string.ui_manage_orbit_memory_a1e811, "Manage Orbit Memory"));
        manageMemory.setOnClickListener(v -> startActivity(new Intent(this, MemoryActivity.class)));
        LinearLayout.LayoutParams manageMemoryLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, UiKit.dp(this, 48));
        manageMemoryLp.setMargins(0, UiKit.dp(this, 9), 0, 0);
        personalDataCard.addView(target(SettingsSearchIndex.KEY_MEMORY, manageMemory), manageMemoryLp);

        Button manageApps = secondaryButton(OrbitText.get(R.string.ui_manage_app_profiles_79153c, "Manage app profiles"));
        manageApps.setOnClickListener(v -> startActivity(new Intent(this, AppsActivity.class)));
        LinearLayout.LayoutParams manageAppsLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, UiKit.dp(this, 48));
        manageAppsLp.setMargins(0, UiKit.dp(this, 9), 0, 0);
        personalDataCard.addView(target(SettingsSearchIndex.KEY_APP_PROFILES, manageApps), manageAppsLp);

        Button manageNotifications = secondaryButton(OrbitText.get(R.string.ui_manage_notification_intelligence_3c2f1c, "Manage notification intelligence"));
        manageNotifications.setOnClickListener(v -> startActivity(new Intent(this, NotificationsActivity.class)));
        LinearLayout.LayoutParams manageNotificationsLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, UiKit.dp(this, 48));
        manageNotificationsLp.setMargins(0, UiKit.dp(this, 9), 0, 0);
        personalDataCard.addView(target(SettingsSearchIndex.KEY_NOTIFICATIONS, manageNotifications), manageNotificationsLp);
        // Added with the other Personalization & data sections below.

        // Orbit Vault gets its own small section rather than a row inside Personalization, because
        // Beta 2 gives it three genuinely different controls: whether the feature exists, a way in,
        // and an erase. Those are the answers to three different questions, and burying the erase
        // among the "Manage ..." rows would put a permanent action next to six reversible ones.
        TextView vaultSection = sectionTitle(OrbitText.get(R.string.ui_orbit_vault_96a29f, "ORBIT VAULT"), "data");
        LinearLayout vaultCard = card();
        tagSectionCard(vaultCard, "data");
        buildVaultCard(vaultCard);
        // Added with the other Personalization & data sections below.

        TextView backupSection = sectionTitle(OrbitText.get(R.string.ui_backup_restore_35a349, "BACKUP & RESTORE"), "data");
        LinearLayout backupCard = card();
        tagSectionCard(backupCard, "data");
        TextView backupHelp = UiKit.text(this,
                OrbitText.get(R.string.ui_orbit_backups_stay_in_the_file_you_choose_778417, "Orbit backups stay in the file you choose. They include local chats, Memory, your Vault, Routines, safe extension manifests, reminders, saved places and personalization. Sensitive account credentials are not included. Android permissions and default-assistant status are not included and may need to be granted again after reinstalling Orbit."),
                13, UiKit.MUTED, false);
        backupHelp.setPadding(0, 0, 0, UiKit.dp(this, 12));
        backupCard.addView(backupHelp);
        TextView backupPrivacy = UiKit.text(this,
                OrbitText.get(R.string.ui_backup_files_contain_personal_data_and_are_3e2904, "Backup files contain personal data and are not encrypted, so keep them somewhere private."),
                12, UiKit.MUTED, false);
        backupPrivacy.setPadding(0, 0, 0, UiKit.dp(this, 12));
        backupCard.addView(backupPrivacy);

        Button exportBackup = secondaryButton(OrbitText.get(R.string.ui_export_orbit_backup_ccc58e, "Export Orbit backup"));
        exportBackup.setOnClickListener(v -> chooseBackupDestination());
        backupCard.addView(exportBackup, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, UiKit.dp(this, 48)));

        Button restoreBackup = secondaryButton(OrbitText.get(R.string.ui_restore_orbit_backup_f0a34e, "Restore Orbit backup"));
        restoreBackup.setOnClickListener(v -> chooseBackupToRestore());
        LinearLayout.LayoutParams restoreBackupLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, UiKit.dp(this, 48));
        restoreBackupLp.setMargins(0, UiKit.dp(this, 9), 0, 0);
        backupCard.addView(restoreBackup, restoreBackupLp);
        // Added last in Personalization & data below.

        page.addView(sectionTitle(OrbitText.get(R.string.ui_connection_provider_130e21, "CONNECTION & PROVIDER"), "account"));
        LinearLayout accountCard = card();
        tagSectionCard(accountCard, "account");
        AiProvider activeProvider = AiProviders.active(this);
        LinearLayout providersRow = new LinearLayout(this);
        providersRow.setGravity(Gravity.CENTER_VERTICAL);
        providersRow.setPadding(UiKit.dp(this, 14), UiKit.dp(this, 12),
                UiKit.dp(this, 12), UiKit.dp(this, 12));
        providersRow.setBackground(UiKit.rippleOutlined(UiKit.SURFACE_2,
                UiKit.withAlpha(UiKit.accent(this), 38), UiKit.accent(this), 16, this));
        providersRow.setClickable(true);
        providersRow.setFocusable(true);
        LinearLayout providersText = new LinearLayout(this);
        providersText.setOrientation(LinearLayout.VERTICAL);
        providersText.addView(UiKit.text(this, OrbitText.get(R.string.ui_ai_providers_5aef60, "AI Providers"), 15, UiKit.TEXT, true));
        providerRowStatus = UiKit.text(this,
                OrbitText.get(R.string.ui_active_de2433, "Active: ") + activeProvider.displayName() + " · " + activeProvider.statusDetail(this),
                12, UiKit.MUTED, false);
        providerRowStatus.setPadding(0, UiKit.dp(this, 3), 0, 0);
        providersText.addView(providerRowStatus);
        providersRow.addView(providersText, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView providersArrow = UiKit.text(this, "›", 24, UiKit.accent(this), false);
        providersArrow.setPadding(UiKit.dp(this, 10), 0, UiKit.dp(this, 4), 0);
        providersRow.addView(providersArrow);
        providersRow.setOnClickListener(v ->
                startActivity(new Intent(this, AiProvidersActivity.class)));
        UiKit.pressScale(providersRow);
        // Deliberately not selectorLp(): that is a fixed 54dp meant for one-line dropdown rows,
        // and this row holds a title plus a status line that may wrap. The row measures itself.
        LinearLayout.LayoutParams providersLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        providersLp.setMargins(0, 0, 0, UiKit.dp(this, 10));
        accountCard.addView(providersRow, providersLp);
        providerDetails = new LinearLayout(this);
        providerDetails.setOrientation(LinearLayout.VERTICAL);
        providerDetails.setPadding(0, UiKit.dp(this, 13), 0, 0);
        populateProviderDetails(providerDetails);
        accountCard.addView(providerDetails);
        page.addView(target(SettingsSearchIndex.KEY_PROVIDER, accountCard));
        // The provider block and the ChatGPT sign-in inside it are two things people look for
        // separately, so both are reachable; the second lands inside the first, which is correct.
        if (providerDetails != null) target(SettingsSearchIndex.KEY_CHATGPT_ACCOUNT, providerDetails);

        page.addView(sectionTitle(OrbitText.get(R.string.ui_voice_context_46ffef, "VOICE & CONTEXT"), "voice"));
        LinearLayout voiceCard = card();
        tagSectionCard(voiceCard, "voice");
        Button micPermission = secondaryButton(OrbitText.get(R.string.ui_grant_microphone_permission_65660d, "Grant microphone permission"));
        micPermission.setOnClickListener(v -> requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_AUDIO));
        voiceCard.addView(micPermission, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UiKit.dp(this, 48)));
        Button contactPermission = secondaryButton(OrbitText.get(R.string.ui_allow_contact_name_lookup_057d65, "Allow contact-name lookup"));
        contactPermission.setOnClickListener(v -> requestPermissions(new String[]{Manifest.permission.READ_CONTACTS}, REQ_CONTACTS));
        LinearLayout.LayoutParams contactLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UiKit.dp(this, 48));
        contactLp.setMargins(0, UiKit.dp(this, 8), 0, 0);
        voiceCard.addView(contactPermission, contactLp);
        Button cameraPermission = secondaryButton(OrbitText.get(R.string.ui_allow_flashlight_control_a34954, "Allow flashlight control"));
        cameraPermission.setOnClickListener(v -> requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_CAMERA));
        LinearLayout.LayoutParams cameraLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UiKit.dp(this, 48));
        cameraLp.setMargins(0, UiKit.dp(this, 8), 0, 0);
        voiceCard.addView(cameraPermission, cameraLp);
        voiceCard.addView(target(SettingsSearchIndex.KEY_SCREEN_TEXT,
                toggle(OrbitText.get(R.string.ui_allow_orbit_to_read_current_screen_text_560f73, "Allow Orbit to read current-screen text"), Prefs.SCREEN_CONTEXT, true)));
        voiceCard.addView(target(SettingsSearchIndex.KEY_SCREENSHOTS,
                toggle(OrbitText.get(R.string.ui_allow_orbit_to_receive_screenshots_857a7a, "Allow Orbit to receive screenshots"),
                OrbitText.get(R.string.ui_visual_context_previews_and_screen_region_d0c0da, "Visual context, previews, and screen-region selection."),
                Prefs.SCREENSHOT, true)));
        voiceCard.addView(target(SettingsSearchIndex.KEY_ATTACH_SCREEN,
                toggle(OrbitText.get(R.string.ui_attach_current_screen_by_default_f37800, "Attach current screen by default"), Prefs.ATTACH_SCREEN_BY_DEFAULT, false)));
        voiceCard.addView(target(SettingsSearchIndex.KEY_CONTEXT_CHIPS,
                toggle(OrbitText.get(R.string.ui_show_contextual_screen_action_chips_when_a_14fb25, "Show contextual screen-action chips when attached"), Prefs.CONTEXT_CHIPS, true)));
        voiceCard.addView(target(SettingsSearchIndex.KEY_SPEAK,
                toggle(OrbitText.get(R.string.ui_speak_replies_to_voice_requests_149b60, "Speak replies to voice requests"), Prefs.SPEAK, true)));
        voiceCard.addView(target(SettingsSearchIndex.KEY_VOICE_PAUSE,
                toggle(OrbitText.get(R.string.ui_allow_longer_pauses_while_speaking_c7b5a7, "Allow longer pauses while speaking"),
                OrbitText.get(R.string.ui_voice_beta_gives_you_more_time_to_pause_an_87c788, "Voice Beta gives you more time to pause and think before Orbit decides you are finished. Tap the mic again if you want to finish sooner."),
                Prefs.VOICE_PAUSE_FRIENDLY, true)));
        voiceCard.addView(target(SettingsSearchIndex.KEY_AUTO_LISTEN,
                toggle(OrbitText.get(R.string.ui_start_listening_when_overlay_opens_f2765c, "Start listening when overlay opens"),
                OrbitText.get(R.string.ui_automatically_activate_the_microphone_when_fc73b4, "Automatically activate the microphone when the assistant overlay appears."),
                Prefs.AUTO_LISTEN_ON_OPEN, false)));
        voiceCard.addView(target(SettingsSearchIndex.KEY_FOLLOW_UPS,
                toggle(OrbitText.get(R.string.ui_hands_free_voice_follow_ups_df5a6a, "Hands-free voice follow-ups"),
                OrbitText.get(R.string.ui_reopen_the_microphone_after_orbit_speaks_ac40d2, "Reopen the microphone after Orbit speaks."),
                Prefs.AUTO_LISTEN, false)));
        voiceCard.addView(target(SettingsSearchIndex.KEY_SMART_FOLLOW_UPS,
                toggle(OrbitText.get(R.string.ui_smart_follow_ups_e8e282, "Smart follow-ups"),
                OrbitText.get(R.string.ui_only_reopen_the_microphone_when_orbit_is_w_66dd85, "Only reopen the microphone when Orbit is waiting for your answer."),
                Prefs.SMART_FOLLOW_UPS, true)));
        voiceCard.addView(target(SettingsSearchIndex.KEY_KEYBOARD_AWARE,
                toggle(OrbitText.get(R.string.ui_keyboard_aware_assistant_invocation_701c19, "Keyboard-aware assistant invocation"), Prefs.KEYBOARD_AWARE_ASSISTANT, true)));

        Button capabilities = secondaryButton(OrbitText.get(R.string.ui_permissions_capabilities_9853eb, "Permissions & capabilities"));
        capabilities.setOnClickListener(v -> startActivity(new Intent(this, CapabilitiesActivity.class)));
        LinearLayout.LayoutParams capabilitiesLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, UiKit.dp(this, 48));
        capabilitiesLp.setMargins(0, UiKit.dp(this, 12), 0, 0);
        voiceCard.addView(target(SettingsSearchIndex.KEY_PERMISSIONS, capabilities), capabilitiesLp);
        page.addView(voiceCard);

        TextView weatherSection = sectionTitle(OrbitText.get(R.string.ui_weather_0a2441, "WEATHER"), "data");
        LinearLayout weatherCard = card();
        tagSectionCard(weatherCard, "data");
        TextView weatherHelp = UiKit.text(this,
                OrbitText.get(R.string.ui_orbit_can_answer_current_weather_and_forec_db729d, "Orbit can answer current weather and forecasts directly in chat using Open-Meteo. You can set a default city or allow approximate device location. Weather questions do not need to open a browser."),
                13, UiKit.MUTED, false);
        weatherHelp.setPadding(0, 0, 0, UiKit.dp(this, 10));
        weatherCard.addView(weatherHelp);
        weatherCard.addView(weatherLocationToggle());
        weatherCard.addView(label(OrbitText.get(R.string.ui_default_weather_location_optional_027770, "Default weather location (optional)")));
        EditText weatherLocation = field("Naples, Florida", Prefs.weatherLocation(this), false);
        weatherCard.addView(weatherLocation);
        weatherCard.addView(label(OrbitText.get(R.string.ui_weather_units_488746, "Weather units")));
        weatherCard.addView(weatherUnitsSelector(), selectorLp());
        Button saveWeather = secondaryButton(OrbitText.get(R.string.ui_save_weather_location_0966b1, "Save weather location"));
        LinearLayout.LayoutParams weatherSaveLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UiKit.dp(this, 46));
        weatherSaveLp.setMargins(0, UiKit.dp(this, 10), 0, 0);
        weatherCard.addView(saveWeather, weatherSaveLp);
        saveWeather.setOnClickListener(v -> {
            Prefs.get(this).edit().putString(Prefs.WEATHER_LOCATION, weatherLocation.getText().toString().trim()).apply();
            Toast.makeText(this, weatherLocation.getText().toString().trim().isEmpty() ? OrbitText.get(R.string.ui_default_weather_location_cleared_c00de2, "Default weather location cleared") : OrbitText.get(R.string.ui_weather_location_saved_e67635, "Weather location saved"), Toast.LENGTH_SHORT).show();
        });
        TextView weatherNote = UiKit.text(this,
                OrbitText.get(R.string.ui_if_device_location_is_off_orbit_uses_the_s_b29858, "If device location is off, Orbit uses the saved city. If neither is available, it will ask for a city and remember the last city you use for weather."),
                12, UiKit.MUTED, false);
        weatherNote.setPadding(0, UiKit.dp(this, 8), 0, 0);
        weatherCard.addView(weatherNote);

        TextView gallerySection = sectionTitle(OrbitText.get(R.string.ui_gallery_316a1d, "GALLERY"), "data");
        LinearLayout galleryCard = card();
        tagSectionCard(galleryCard, "data");
        galleryCard.addView(label(OrbitText.get(R.string.ui_gallery_app_eabbd2, "Gallery app")));
        galleryCard.addView(galleryAppSelector(), selectorLp());
        TextView galleryHelp = UiKit.text(this,
                OrbitText.get(R.string.ui_choose_which_compatible_installed_app_orbi_eb32f5, "Choose which compatible installed app Orbit uses when you attach an image. System picker remains the safe fallback if that app becomes unavailable."),
                12, UiKit.MUTED, false);
        galleryCard.addView(galleryHelp);

        page.addView(weatherSection);
        page.addView(target(SettingsSearchIndex.KEY_WEATHER, weatherCard));
        page.addView(gallerySection);
        page.addView(target(SettingsSearchIndex.KEY_GALLERY, galleryCard));
        page.addView(personalizationSection);
        page.addView(personalDataCard);
        page.addView(vaultSection);
        page.addView(target(SettingsSearchIndex.KEY_VAULT, vaultCard));
        page.addView(remindersSection);
        page.addView(target(SettingsSearchIndex.KEY_REMINDERS, remindersCard));
        page.addView(backupSection);
        page.addView(target(SettingsSearchIndex.KEY_BACKUP, backupCard));

        page.addView(sectionTitle(OrbitText.get(R.string.ui_intelligence_4a3fa3, "INTELLIGENCE"), "intelligence"));
        LinearLayout aiCard = card();
        tagSectionCard(aiCard, "intelligence");
        // One control for the three choices, opening Orbit's shared picker, so Settings can never
        // offer a strength the chosen model refuses. Only the default for new chats lives here;
        // each chat keeps its own selection and is changed from its own header.
        TextView defaultAiLabel = label(OrbitText.get(R.string.ui_default_ai_for_new_chats_30d2da, "Default AI for new chats"));
        LinearLayout defaultAi = defaultAiSelector();
        aiCard.addView(controlGroup(SettingsSearchIndex.KEY_DEFAULT_AI, defaultAiLabel, defaultAi,
                selectorLp()));
        TextView defaultAiHelp = UiKit.text(this,
                OrbitText.get(R.string.ui_provider_model_and_strength_for_new_chats_1e37ac, "Provider, model and strength for new chats and the assistant. Each chat remembers ")
                        + OrbitText.get(R.string.ui_its_own_choice_so_changing_this_never_chan_6058ac, "its own choice, so changing this never changes an existing chat. ")
                        + OrbitText.get(R.string.ui_orbit_always_uses_exactly_the_model_you_ch_46a3a5, "Orbit always uses exactly the model you choose; only Auto chooses for you."),
                12, UiKit.MUTED, false);
        defaultAiHelp.setLineSpacing(0, 1.12f);
        defaultAiHelp.setPadding(0, UiKit.dp(this, 7), 0, UiKit.dp(this, 4));
        aiCard.addView(defaultAiHelp);
        aiCard.addView(autoProvidersRow());
        TextView cost = UiKit.text(this, OrbitText.get(R.string.ui_chatgpt_account_mode_uses_your_account_bac_653d68, "ChatGPT-account mode uses your account-backed allowance. Orbit never silently switches to the separately metered API-relay fallback."), 12, UiKit.MUTED, false);
        cost.setPadding(0, UiKit.dp(this, 8), 0, 0);
        aiCard.addView(cost);
        // Sits with Intelligence because it is about how a request behaves while it runs, and it
        // takes effect on the next request with no restart and no extra setup step.
        // On unless the user says otherwise, and the switch stays so they always can. The default
        // comes from Prefs rather than a literal here, so the two cannot drift apart.
        aiCard.addView(target(SettingsSearchIndex.KEY_THINKING_UPDATES,
                toggle(OrbitText.get(R.string.ui_thinking_updates_1f86f3, "Thinking updates"),
                OrbitText.get(R.string.ui_show_brief_updates_about_what_orbit_is_wor_5b7d2b, "Show brief updates about what Orbit is working on while it prepares an answer. ")
                        + OrbitText.get(R.string.ui_never_shows_private_chain_of_thought_4b1084, "Never shows private chain-of-thought."),
                Prefs.THINKING_UPDATES, Prefs.THINKING_UPDATES_DEFAULT)));
        // Sits with Intelligence because it is about what an answer contains. The copy names the
        // one thing somebody would want to know before leaving it on: which addresses Orbit
        // contacts, and that it is only the pages the answer already used.
        aiCard.addView(target(SettingsSearchIndex.KEY_RICH_ANSWERS,
                toggle(OrbitText.get(R.string.ui_sourced_images_in_answers_3a51b5, "Sourced images in answers"),
                OrbitText.get(R.string.ui_show_a_useful_picture_inside_an_answer_whe_269011, "Show a useful picture inside an answer when one genuinely helps. Orbit only reads ")
                        + OrbitText.get(R.string.ui_the_preview_image_a_page_it_already_cited_163273, "the preview image a page it already cited declares about itself, and ")
                        + OrbitText.get(R.string.ui_never_sends_your_chat_prompt_or_vault_anyw_7aa422, "never sends your chat, prompt or Vault anywhere to do it."),
                Prefs.RICH_ANSWERS, Prefs.RICH_ANSWERS_DEFAULT)));
        page.addView(aiCard);

        page.addView(sectionTitle(OrbitText.get(R.string.ui_diagnostics_c34dc2, "DIAGNOSTICS"), "diagnostics"));
        LinearLayout diagnosticsCard = card();
        tagSectionCard(diagnosticsCard, "diagnostics");
        diagnosticsCard.addView(UiKit.text(this, OrbitText.get(R.string.ui_orbit_diagnostics_c8a737, "Orbit diagnostics"), 16, UiKit.TEXT, true));
        TextView diagnosticsHelp = UiKit.text(this,
                OrbitText.get(R.string.ui_view_local_routing_context_account_capabil_865240, "View local routing, context, account, capability, and troubleshooting information."),
                13, UiKit.MUTED, false);
        diagnosticsHelp.setPadding(0, UiKit.dp(this, 7), 0, UiKit.dp(this, 12));
        diagnosticsCard.addView(diagnosticsHelp);
        Button diagnostics = secondaryButton(OrbitText.get(R.string.ui_open_orbit_diagnostics_029a16, "Open Orbit diagnostics"));
        diagnosticsCard.addView(diagnostics, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, UiKit.dp(this, 48)));
        diagnostics.setOnClickListener(v -> startActivity(new Intent(this, DiagnosticsActivity.class)));
        page.addView(target(SettingsSearchIndex.KEY_DIAGNOSTICS, diagnosticsCard));

        page.addView(sectionTitle(OrbitText.get(R.string.ui_orbit_deck_b9101e, "ORBIT DECK"), "deck"));
        LinearLayout deckCard = card();
        tagSectionCard(deckCard, "deck");
        deckCard.addView(toggle(OrbitText.get(R.string.ui_show_deck_shortcut_on_chats_4b733f, "Show Deck shortcut on Chats"),
                OrbitText.get(R.string.ui_puts_the_deck_control_in_the_chats_header_b409e0, "Puts the Deck control in the Chats header. Deck stays reachable from here either way."),
                Prefs.DECK_SHORTCUT, true));
        deckCard.addView(toggle(OrbitText.get(R.string.ui_smart_suggestions_7cc944, "Smart suggestions"),
                OrbitText.get(R.string.ui_show_relevant_shortcuts_from_recent_orbit_2d66fc, "Show relevant shortcuts from recent Orbit context."),
                Prefs.DECK_SUGGESTIONS, true));
        TextView deckNote = UiKit.text(this,
                OrbitText.get(R.string.ui_deck_arranges_shortcuts_to_things_orbit_al_8d7751, "Deck arranges shortcuts to things Orbit already does. Suggestions are worked out on ")
                        + OrbitText.get(R.string.ui_this_phone_from_context_orbit_already_has_2ba8a2, "this phone from context Orbit already has, and opening Deck never sends ")
                        + OrbitText.get(R.string.ui_anything_to_your_ai_provider_which_tiles_y_c1acb8, "anything to your AI provider. Which tiles you keep, and how they are ")
                        + OrbitText.get(R.string.ui_arranged_is_edited_in_deck_itself_15f305, "arranged, is edited in Deck itself."),
                12, UiKit.MUTED, false);
        deckNote.setPadding(0, UiKit.dp(this, 7), 0, UiKit.dp(this, 12));
        deckCard.addView(deckNote);
        Button openDeck = secondaryButton(OrbitText.get(R.string.ui_open_orbit_deck_cb7553, "Open Orbit Deck"));
        deckCard.addView(openDeck, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, UiKit.dp(this, 48)));
        openDeck.setOnClickListener(v -> startActivity(new Intent(this, DeckActivity.class)));
        page.addView(target(SettingsSearchIndex.KEY_DECK, deckCard));

        page.addView(sectionTitle(OrbitText.get(R.string.ui_conversations_25a898, "CONVERSATIONS"), "conversations"));
        LinearLayout conversationCard = card();
        tagSectionCard(conversationCard, "conversations");
        conversationCard.addView(target(SettingsSearchIndex.KEY_NEW_CHAT,
                toggle(OrbitText.get(R.string.ui_start_a_new_chat_each_time_orbit_opens_ae9f24, "Start a new chat each time Orbit opens"), Prefs.NEW_CHAT_ON_OPEN, true)));
        conversationCard.addView(target(SettingsSearchIndex.KEY_HISTORY,
                toggle(OrbitText.get(R.string.ui_save_recent_chats_on_this_device_92b8c3, "Save recent chats on this device"), Prefs.HISTORY_ENABLED, true)));
        conversationCard.addView(target(SettingsSearchIndex.KEY_THUMBNAILS,
                toggle(OrbitText.get(R.string.ui_save_screen_attachment_thumbnails_in_chat_c2f84e, "Save screen attachment thumbnails in chat history"), Prefs.SAVE_SCREEN_THUMBNAILS, false)));
        conversationCard.addView(target(SettingsSearchIndex.KEY_STOP_BUTTON,
                toggle(OrbitText.get(R.string.ui_show_stop_button_while_replying_56b08e, "Show Stop button while replying"),
                OrbitText.get(R.string.ui_replace_send_with_stop_while_orbit_is_gene_b487e5, "Replace Send with Stop while Orbit is generating a reply."),
                Prefs.SHOW_STOP_BUTTON, true)));
        conversationCard.addView(notificationToggle());
        TextView conversationNote = UiKit.text(this,
                OrbitText.get(R.string.ui_orbit_keeps_up_to_100_recent_chats_locally_83d9a1, "Orbit keeps up to 100 recent chats locally when history is enabled. The full Orbit app can search, rename, reopen, and delete them. Screen thumbnails are stored only in Orbit's private app storage when that option is enabled."),
                12, UiKit.MUTED, false);
        conversationNote.setPadding(0, UiKit.dp(this, 7), 0, UiKit.dp(this, 10));
        conversationCard.addView(conversationNote);
        Button clearHistory = secondaryButton(OrbitText.get(R.string.ui_clear_orbit_conversation_history_23817e, "Clear Orbit conversation history"));
        conversationCard.addView(target(SettingsSearchIndex.KEY_CLEAR_HISTORY, clearHistory),
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UiKit.dp(this, 48)));
        clearHistory.setOnClickListener(v -> {
            AlertDialog dialog = new AlertDialog.Builder(this)
                    .setTitle(OrbitText.get(R.string.ui_clear_orbit_history_a7df4b, "Clear Orbit history?"))
                    .setMessage(OrbitText.get(R.string.ui_this_deletes_orbit_s_locally_saved_recent_c52b96, "This deletes Orbit's locally saved recent chats. It does not affect your ChatGPT account."))
                    .setNegativeButton(OrbitText.get(R.string.ui_cancel_19766e, "Cancel"), null)
                    .setPositiveButton(OrbitText.get(R.string.ui_clear_83b12c, "Clear"), (d, which) -> {
                        ConversationStore.clear(this);
                        Toast.makeText(this, OrbitText.get(R.string.ui_orbit_conversation_history_cleared_af9bc8, "Orbit conversation history cleared"), Toast.LENGTH_SHORT).show();
                    })
                    .create();
            styleOrbitDialog(dialog, true);
            dialog.show();
        });
        page.addView(conversationCard);

        // Look & Feel has one color destination, and one typography-and-feedback card beneath it.
        // Until this release it had both: Theme Studio at the top, and directly under it a second
        // set of controls for accent, AMOLED and the two bubble colors. They wrote the same
        // preferences, so they could not contradict each other, but they could and did leave a
        // person wondering which one was the real place to change a color. There is one answer
        // now, and the controls that used to ask the question are gone rather than duplicated.
        page.addView(sectionTitle(OrbitText.get(R.string.ui_look_feel_fdaac2, "LOOK & FEEL"), "appearance"));
        LinearLayout studioCard = card();
        tagSectionCard(studioCard, "appearance");
        studioCard.addView(themeStudioRow());
        page.addView(target(SettingsSearchIndex.KEY_THEME_STUDIO, studioCard));

        page.addView(sectionTitle(OrbitText.get(R.string.ui_overlay_dffe7e, "OVERLAY"), "appearance"));
        page.addView(target(SettingsSearchIndex.KEY_OVERLAY, overlayCard()));

        // A heading of its own rather than a bare second card. It says what the card below is for,
        // and it carries the same spacing every other section break on this page has, which is what
        // stops the two surfaces from appearing fused into one.
        page.addView(sectionTitle(OrbitText.get(R.string.ui_typography_feedback_0f49aa, "TYPOGRAPHY & FEEDBACK"), "appearance"));
        LinearLayout styleCard = card();
        tagSectionCard(styleCard, "appearance");

        styleCard.addView(controlGroup(SettingsSearchIndex.KEY_FONT,
                label(OrbitText.get(R.string.ui_app_font_cad029, "App font")), fontSelector(), null));
        TextView fontNote = UiKit.text(this,
                OrbitText.get(R.string.ui_orbit_default_is_the_current_app_font_time_15df24, "Orbit Default is the current app font. Times New Roman uses your phone's Times New Roman font when it has one, and otherwise Android's built-in serif font for a similar classic look. Orbit doesn't include a font file."),
                12, UiKit.MUTED, false);
        fontNote.setPadding(0, UiKit.dp(this, 8), 0, UiKit.dp(this, 8));
        styleCard.addView(fontNote);

        TextView chatSizeLabel = label(OrbitText.get(R.string.ui_chat_text_size_dff793, "Chat text size"));
        chatSizeLabel.setPadding(UiKit.dp(this, 2), UiKit.dp(this, 12), 0, UiKit.dp(this, 6));
        styleCard.addView(controlGroup(SettingsSearchIndex.KEY_CHAT_TEXT_SIZE,
                chatSizeLabel, chatTextSizeSelector(), null));
        TextView chatSizeNote = UiKit.text(this,
                OrbitText.get(R.string.ui_changes_conversation_content_only_includin_b3cc92, "Changes conversation content only, including rich Markdown in full chat and the Side-button assistant."),
                12, UiKit.MUTED, false);
        chatSizeNote.setPadding(0, UiKit.dp(this, 8), 0, UiKit.dp(this, 14));
        styleCard.addView(chatSizeNote);

        // Haptics stays here on purpose. It is not a color, it is not saved in a theme, and
        // applying a Theme Studio preset must never silently change how the phone feels.
        styleCard.addView(target(SettingsSearchIndex.KEY_HAPTICS,
                toggle(OrbitText.get(R.string.ui_haptic_feedback_153d8b, "Haptic feedback"), Prefs.HAPTICS, true)));
        TextView hapticNote = UiKit.text(this,
                OrbitText.get(R.string.ui_uses_light_tactile_ticks_for_orbit_control_e89d73, "Uses light tactile ticks for Orbit controls and Settings interactions. Turn this off to disable those haptics."),
                12, UiKit.MUTED, false);
        hapticNote.setPadding(UiKit.dp(this, 4), UiKit.dp(this, 1), 0, UiKit.dp(this, 6));
        styleCard.addView(hapticNote);

        TextView advancedLabel = label(OrbitText.get(R.string.ui_advanced_9f088d, "Advanced"));
        advancedLabel.setPadding(UiKit.dp(this, 2), UiKit.dp(this, 20), 0, UiKit.dp(this, 6));
        styleCard.addView(advancedLabel);
        styleCard.addView(controlGroup(SettingsSearchIndex.KEY_PAGE_TRANSITIONS,
                label(OrbitText.get(R.string.ui_page_transitions_635bbc, "Page transitions")), pageTransitionSelector(), null));
        TextView transitionNote = UiKit.text(this,
                OrbitText.get(R.string.ui_how_full_screen_orbit_pages_move_when_you_9daead, "How full-screen Orbit pages move when you open and leave them. Slide brings a page in from the right and sends it back out on the way back. Fade & settle uses a short fade with a small upward settle. None changes pages immediately. Android's own animation settings still apply."),
                12, UiKit.MUTED, false);
        transitionNote.setPadding(0, UiKit.dp(this, 8), 0, 0);
        styleCard.addView(transitionNote);

        page.addView(styleCard);

        page.addView(sectionTitle(OrbitText.get(R.string.ui_gestures_eb8ed2, "GESTURES"), "appearance"));
        LinearLayout gestureCard = card();
        tagSectionCard(gestureCard, "appearance");
        gestureCard.addView(target(SettingsSearchIndex.KEY_SWIPE_BACK,
                toggle(OrbitText.get(R.string.ui_swipe_to_go_back_cb5e33, "Swipe to go back"),
                OrbitText.get(R.string.ui_use_orbit_s_interactive_edge_gesture_when_770490, "Use Orbit's interactive edge gesture when returning to the previous screen. The ")
                        + OrbitText.get(R.string.ui_page_follows_your_finger_and_the_screen_yo_6faa71, "page follows your finger and the screen you came from appears behind it, ")
                        + OrbitText.get(R.string.ui_across_conversations_settings_and_orbit_s_a0278d, "across conversations, Settings and Orbit's other pages. Turn this off to ")
                        + OrbitText.get(R.string.ui_use_orbit_s_page_transition_instead_back_i_462f21, "use Orbit's page transition instead. Back itself always works either ")
                        + OrbitText.get(R.string.ui_way_from_the_gesture_the_navigation_button_59597c, "way, from the gesture, the navigation buttons, and every Back control."),
                Prefs.ENHANCED_CHAT_BACK, true)));
        gestureCard.addView(target(SettingsSearchIndex.KEY_CHAT_SWIPE,
                toggle(OrbitText.get(R.string.ui_chat_swipe_actions_736fe6, "Chat swipe actions"),
                OrbitText.get(R.string.ui_swipe_a_chat_left_to_delete_it_or_right_to_49945a, "Swipe a chat left to delete it, or right to pin and unpin it. Deleting always ")
                        + OrbitText.get(R.string.ui_offers_undo_with_this_off_chats_scroll_and_f429c3, "offers Undo. With this off, chats scroll and open as normal and both ")
                        + OrbitText.get(R.string.ui_actions_stay_on_the_chat_s_own_menu_56abb0, "actions stay on the chat's own menu."),
                Prefs.CHAT_SWIPE_ACTIONS, true)));
        page.addView(gestureCard);

        TextView footer = UiKit.text(this, "Orbit " + BuildConfig.VERSION_NAME + OrbitText.get(R.string.ui_power_assistant_80cc1c, " • Power Assistant"), 12, UiKit.MUTED, false);
        footer.setGravity(Gravity.CENTER);
        footer.setOnClickListener(v -> handleLeloSecretTap());
        footer.setOnLongClickListener(v -> {
            startActivity(new Intent(this, DiagnosticsActivity.class));
            return true;
        });
        LinearLayout.LayoutParams footerLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        footerLp.setMargins(0, UiKit.dp(this, 28), 0, 0);
        page.addView(footer, footerLp);
        applySettingsSectionFilter(page, settingsSection);
        return scroll;
    }

    /**
     * Sign in with ChatGPT: the browser flow. One tap opens OpenAI's normal sign-in page; the
     * result arrives through {@link #chatGptBrowserListener}. A failure explains itself and offers
     * code sign-in, and Orbit never switches methods without the user choosing to.
     */
    private void startChatGptLogin() {
        ChatGptBrowserAuth.Started started = ChatGptBrowserAuth.start(this, chatGptBrowserListener);
        if (!started.ok()) {
            showBrowserSignInFailure(started.error, true);
            return;
        }
        ChatGptSignInReturnActivity.returnTo = SettingsActivity.class;
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(started.authorizeUrl))
                    .addCategory(Intent.CATEGORY_BROWSABLE));
        } catch (Exception e) {
            ChatGptBrowserAuth.cancel();
            showBrowserSignInFailure(OrbitText.get(R.string.ui_orbit_could_not_open_a_web_browser_on_this_3ed952, "Orbit could not open a web browser on this phone."), true);
            return;
        }
        updateChatGptStatus();
    }

    private void showBrowserSignInFailure(String message, boolean offerCodeSignIn) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(OrbitText.get(R.string.ui_chatgpt_sign_in_could_not_complete_2ffa82, "ChatGPT sign-in could not complete"))
                .setMessage(message);
        if (offerCodeSignIn) {
            builder.setPositiveButton(OrbitText.get(R.string.ui_use_code_sign_in_25c629, "Use code sign-in"), (d, w) -> startCodeSignIn())
                    .setNegativeButton(OrbitText.get(R.string.ui_close_7d9eb7, "Close"), null);
        } else {
            builder.setPositiveButton(OrbitText.get(R.string.ui_ok_565339, "OK"), null);
        }
        AlertDialog dialog = builder.create();
        styleOrbitDialog(dialog, false);
        dialog.show();
    }

    /** The device-code fallback, unchanged: a one-time code entered on OpenAI's page. */
    private void startCodeSignIn() {
        if (chatGptStatus != null) {
            chatGptStatus.setText(OrbitText.get(R.string.ui_starting_secure_chatgpt_sign_in_982836, "Starting secure ChatGPT sign-in…"));
            chatGptStatus.setTextColor(UiKit.TEXT);
        }
        ChatGptAuth.requestDeviceCode(this, new ChatGptAuth.StartCallback() {
            @Override public void onSuccess(ChatGptAuth.DeviceCode code) {
                runOnUiThread(() -> {
                    if (canShowAuthResult()) showDeviceCode(code);
                });
                ChatGptAuth.completeDeviceCode(
                        SettingsActivity.this, code, chatGptLoginCallback);
            }
            @Override public void onError(String message) {
                runOnUiThread(() -> {
                    if (!canShowAuthResult()) return;
                    updateChatGptStatus();
                    showOrbitMessageDialog(OrbitText.get(R.string.ui_could_not_start_chatgpt_sign_in_41b653, "Could not start ChatGPT sign-in"), message);
                });
            }
        });
    }

    private boolean canShowAuthResult() {
        return !isFinishing() && !isDestroyed();
    }

    private void showDeviceCode(ChatGptAuth.DeviceCode code) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard != null) clipboard.setPrimaryClip(ClipData.newPlainText(OrbitText.get(R.string.ui_orbit_chatgpt_code_30d0c7, "Orbit ChatGPT code"), code.userCode));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(OrbitText.get(R.string.ui_sign_in_with_chatgpt_f6de64, "Sign in with ChatGPT"))
                .setMessage(OrbitText.get(R.string.ui_your_one_time_code_is_fe7690, "Your one-time code is:\n\n") + code.userCode +
                        OrbitText.get(R.string.ui_it_has_been_copied_to_your_clipboard_open_f9fc44, "\n\nIt has been copied to your clipboard. Open OpenAI's sign-in page, sign into your normal ChatGPT account, and enter this code. Orbit will finish connecting automatically."))
                .setPositiveButton(OrbitText.get(R.string.ui_open_chatgpt_sign_in_d7f077, "Open ChatGPT sign-in"), (d, which) -> {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(code.verificationUrl))); }
                    catch (Exception e) { Toast.makeText(this, code.verificationUrl, Toast.LENGTH_LONG).show(); }
                })
                .setNeutralButton(OrbitText.get(R.string.ui_copy_code_49a005, "Copy code"), (d, which) -> {
                    ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    if (cm != null) cm.setPrimaryClip(ClipData.newPlainText(OrbitText.get(R.string.ui_orbit_chatgpt_code_30d0c7, "Orbit ChatGPT code"), code.userCode));
                    Toast.makeText(this, OrbitText.get(R.string.ui_code_copied_39868c, "Code copied"), Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(OrbitText.get(R.string.ui_close_7d9eb7, "Close"), null)
                .create();
        styleOrbitDialog(dialog, false);
        dialog.show();
    }

    private void showOrbitMessageDialog(String title, String message) {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(OrbitText.get(R.string.ui_ok_565339, "OK"), null)
                .create();
        styleOrbitDialog(dialog, false);
        dialog.show();
    }

    private void styleOrbitDialog(AlertDialog dialog, boolean destructivePositive) {
        UiKit.styleOrbitDialog(dialog, this, destructivePositive);
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

    /**
     * The connected and signed-out states show different actions, not just different text: an
     * already-connected account never presents a bright "Sign in" as the dominant action, and a
     * signed-out one never offers "Sign out".
     */
    private void updateChatGptStatus() {
        if (chatGptStatus == null) return;
        ChatGptAuth.AccountInfo info = ChatGptAuth.getAccountInfo(this);
        boolean connected = info != null;
        if (chatGptSignIn != null) chatGptSignIn.setVisibility(connected ? View.GONE : View.VISIBLE);
        if (chatGptSignOut != null) chatGptSignOut.setVisibility(connected ? View.VISIBLE : View.GONE);
        if (chatGptCodeSignIn != null) chatGptCodeSignIn.setVisibility(connected ? View.GONE : View.VISIBLE);
        if (chatGptHelp != null) {
            chatGptHelp.setText(connected
                    ? OrbitText.get(R.string.ui_orbit_is_using_this_account_s_chatgpt_code_49e301, "Orbit is using this account's ChatGPT/Codex allowance. No API key is needed.")
                    : OrbitText.get(R.string.ui_sign_in_with_your_normal_chatgpt_account_i_3622df, "Sign in with your normal ChatGPT account in your browser. Orbit uses your ")
                            + OrbitText.get(R.string.ui_account_backed_chatgpt_codex_allowance_and_9695bc, "account-backed ChatGPT/Codex allowance and does not need an OpenAI API key."));
        }
        if (!connected && ChatGptBrowserAuth.inProgress()) {
            chatGptStatus.setText(OrbitText.get(R.string.ui_waiting_for_chatgpt_sign_in_in_your_browse_24e86b, "Waiting for ChatGPT sign-in in your browser…"));
            chatGptStatus.setTextColor(UiKit.TEXT);
            return;
        }
        if (!connected) {
            chatGptStatus.setText(OrbitText.get(R.string.ui_not_signed_in_with_chatgpt_c5d81e, "○ Not signed in with ChatGPT"));
            chatGptStatus.setTextColor(UiKit.TEXT);
            return;
        }
        StringBuilder status = new StringBuilder(OrbitText.get(R.string.ui_connected_to_chatgpt_939681, "✓ Connected to ChatGPT"));
        if (!info.plan.isEmpty()) status.append(" • ").append(info.plan);
        if (!info.email.isEmpty()) status.append("\n").append(info.email);
        chatGptStatus.setText(status.toString());
        chatGptStatus.setTextColor(UiKit.SUCCESS);
    }

    private void populateProviderDetails(LinearLayout container) {
        if (container == null) return;
        chatGptStatus = null;
        chatGptHelp = null;
        chatGptSignIn = null;
        chatGptSignOut = null;
        chatGptCodeSignIn = null;
        // The effective provider, not the raw stored one: if a stored Orbit Local selection lost
        // its model, requests already fall back to ChatGPT, and this block must describe the
        // provider that actually answers.
        providerDetailsFor = AiProviders.active(this).id();
        if (Prefs.PROVIDER_LOCAL.equals(providerDetailsFor)) {
            TextView localHelp = UiKit.text(this,
                    OrbitText.get(R.string.ui_orbit_local_answers_on_this_phone_with_no_493b5c, "Orbit Local answers on this phone with no account and no internet. Your ChatGPT sign-in, if present, stays securely saved but inactive while Orbit Local is selected."),
                    13, UiKit.MUTED, false);
            localHelp.setPadding(0, 0, 0, UiKit.dp(this, 12));
            container.addView(localHelp);
            Button manageLocal = secondaryButton(OrbitText.get(R.string.ui_manage_orbit_local_5a3f67, "Manage Orbit Local"));
            manageLocal.setOnClickListener(v ->
                    startActivity(new Intent(this, LocalAiActivity.class)));
            container.addView(manageLocal, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, UiKit.dp(this, 48)));
            return;
        }
        if (Prefs.PROVIDER_RELAY.equals(providerDetailsFor)) {
            TextView relayHelp = UiKit.text(this,
                    OrbitText.get(R.string.ui_advanced_fallback_orbit_calls_a_private_ht_119c05, "Advanced fallback: Orbit calls a private HTTPS relay you control. Your OpenAI API key stays on that server and is never stored in Orbit. ChatGPT credentials, if present, remain securely saved but inactive while this provider is selected."),
                    13, UiKit.MUTED, false);
            relayHelp.setPadding(0, 0, 0, UiKit.dp(this, 12));
            container.addView(relayHelp);

            container.addView(label(OrbitText.get(R.string.ui_https_relay_url_c7f523, "HTTPS relay URL")));
            EditText url = field("https://your-relay.example.com", Prefs.backendUrl(this), false);
            container.addView(url);
            container.addView(label(OrbitText.get(R.string.ui_relay_access_token_optional_dffc00, "Relay access token (optional)")));
            EditText token = field(OrbitText.get(R.string.ui_relay_access_token_optional_dffc00, "Relay access token (optional)"), Prefs.token(this), true);
            container.addView(token);

            Button save = primaryButton(OrbitText.get(R.string.ui_save_relay_settings_b18e84, "Save relay settings"));
            LinearLayout.LayoutParams saveLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, UiKit.dp(this, 48));
            saveLp.setMargins(0, UiKit.dp(this, 12), 0, 0);
            container.addView(save, saveLp);
            save.setOnClickListener(v -> {
                try {
                    String error = Prefs.saveRelaySettings(this, url.getText().toString(),
                            token.getText().toString());
                    if (!error.isEmpty()) {
                        Toast.makeText(this, error, Toast.LENGTH_LONG).show();
                        return;
                    }
                    Toast.makeText(this, OrbitText.get(R.string.ui_relay_settings_saved_securely_abd926, "Relay settings saved securely"), Toast.LENGTH_SHORT).show();
                } catch (Exception error) {
                    showOrbitMessageDialog(OrbitText.get(R.string.ui_could_not_save_relay_settings_d1d62e, "Could not save relay settings"),
                            OrbitText.get(R.string.ui_orbit_could_not_securely_save_the_optional_3b8093, "Orbit could not securely save the optional relay access token."));
                }
            });

            TextView future = UiKit.text(this,
                    OrbitText.get(R.string.ui_more_provider_options_including_claude_and_7343e5, "More provider options, including Claude and Gemini, are planned for future Orbit versions."),
                    11, UiKit.MUTED, false);
            future.setPadding(0, UiKit.dp(this, 10), 0, 0);
            container.addView(future);
            return;
        }

        chatGptStatus = UiKit.text(this, OrbitText.get(R.string.ui_checking_chatgpt_sign_in_edfa18, "Checking ChatGPT sign-in…"), 15, UiKit.TEXT, true);
        container.addView(chatGptStatus);
        chatGptHelp = UiKit.text(this, "", 13, UiKit.MUTED, false);
        chatGptHelp.setLineSpacing(0, 1.13f);
        chatGptHelp.setPadding(0, UiKit.dp(this, 7), 0, UiKit.dp(this, 13));
        container.addView(chatGptHelp);

        chatGptSignIn = primaryButton(OrbitText.get(R.string.ui_sign_in_with_chatgpt_f6de64, "Sign in with ChatGPT"));
        chatGptSignIn.setOnClickListener(v -> startChatGptLogin());
        container.addView(chatGptSignIn, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, UiKit.dp(this, 48)));

        // The fallback is a quiet text action, not a second primary button: most people never need
        // it, and it must never look like the recommended path.
        chatGptCodeSignIn = new Button(this);
        chatGptCodeSignIn.setText(OrbitText.get(R.string.ui_having_trouble_use_code_sign_in_62f97b, "Having trouble? Use code sign-in"));
        chatGptCodeSignIn.setAllCaps(false);
        chatGptCodeSignIn.setTextSize(13);
        chatGptCodeSignIn.setTextColor(UiKit.accent(this));
        chatGptCodeSignIn.setBackground(UiKit.ripple(Color.TRANSPARENT, UiKit.accent(this), 14, this));
        chatGptCodeSignIn.setStateListAnimator(null);
        chatGptCodeSignIn.setMinHeight(UiKit.dp(this, 44));
        chatGptCodeSignIn.setMinimumHeight(UiKit.dp(this, 44));
        chatGptCodeSignIn.setOnClickListener(v -> startCodeSignIn());
        container.addView(chatGptCodeSignIn, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, UiKit.dp(this, 44)));

        // Signing out is disruptive rather than dangerous, so it stays a restrained outlined
        // action with a confirmation, never a bright primary competing with the connected state.
        chatGptSignOut = dangerOutlineButton(OrbitText.get(R.string.ui_sign_out_of_chatgpt_624386, "Sign out of ChatGPT"));
        LinearLayout.LayoutParams signOutLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, UiKit.dp(this, 48));
        signOutLp.setMargins(0, UiKit.dp(this, 8), 0, 0);
        container.addView(chatGptSignOut, signOutLp);
        chatGptSignOut.setOnClickListener(v -> {
            AlertDialog dialog = new AlertDialog.Builder(this)
                    .setTitle(OrbitText.get(R.string.ui_sign_out_of_chatgpt_4525b7, "Sign out of ChatGPT?"))
                    .setMessage(OrbitText.get(R.string.ui_orbit_forgets_this_chatgpt_connection_on_t_3f1823, "Orbit forgets this ChatGPT connection on this phone. You can sign in again at any time."))
                    .setNegativeButton(OrbitText.get(R.string.ui_cancel_19766e, "Cancel"), null)
                    .setPositiveButton(OrbitText.get(R.string.ui_sign_out_48f0d3, "Sign out"), (d, w) -> {
                        ChatGptAuth.logout(this);
                        updateChatGptStatus();
                        Toast.makeText(this, OrbitText.get(R.string.ui_signed_out_of_chatgpt_in_orbit_1c9636, "Signed out of ChatGPT in Orbit"), Toast.LENGTH_SHORT).show();
                    })
                    .create();
            styleOrbitDialog(dialog, true);
            dialog.show();
        });

        TextView experimental = UiKit.text(this,
                OrbitText.get(R.string.ui_orbit_signs_in_through_openai_s_public_cod_d63068, "Orbit signs in through OpenAI's public Codex OAuth sign-in (browser, or a one-time code). Because OpenAI does not document the Codex backend as a general third-party mobile API, this integration is experimental and the API relay remains available as an explicit fallback."),
                12, UiKit.MUTED, false);
        experimental.setPadding(0, UiKit.dp(this, 8), 0, 0);
        container.addView(experimental);
        updateChatGptStatus();
    }

    /** Reflects a provider switch made in AI Providers when the user returns here. */
    private void refreshProviderSection() {
        if (providerRowStatus != null) {
            AiProvider active = AiProviders.active(this);
            providerRowStatus.setText(
                    OrbitText.get(R.string.ui_active_de2433, "Active: ") + active.displayName() + " · " + active.statusDetail(this));
        }
        if (providerDetails != null && providerDetailsFor != null
                && !providerDetailsFor.equals(AiProviders.active(this).id())) {
            swapProviderDetails();
        }
    }

    private void swapProviderDetails() {
        if (providerDetails == null) return;
        providerDetails.animate().cancel();
        providerDetails.animate()
                .alpha(0f)
                .translationY(-UiKit.dp(this, 3))
                .setDuration(85L)
                .withEndAction(() -> {
                    if (providerDetails == null || isFinishing() || isDestroyed()) return;
                    providerDetails.removeAllViews();
                    populateProviderDetails(providerDetails);
                    UiKit.applyTypography(providerDetails);
                    providerDetails.setTranslationY(UiKit.dp(this, 4));
                    providerDetails.animate()
                            .alpha(1f)
                            .translationY(0f)
                            .setDuration(150L)
                            .start();
                })
                .start();
    }

    private void openAssistantSettings(boolean explain) {
        if (!OrbitSetupHelper.openAssistantSettings(this, REQ_ASSISTANT_SETTINGS, explain))
            showOrbitMessageDialog(OrbitText.get(R.string.ui_assistant_settings_unavailable_7db001, "Assistant settings unavailable"),
                    OrbitText.get(R.string.ui_android_could_not_open_a_supported_default_c372b8, "Android could not open a supported default-assistant settings screen on this device."));
    }

    private void refreshQuickRoutineSelection() {
        if (quickRoutineSelection == null) return;
        RoutineStore.Routine assigned = QuickSettingsTiles.assignedRoutine(this);
        quickRoutineSelection.setText(OrbitText.get(R.string.ui_quick_settings_routine_46d7d0, "Quick Settings Routine: ") +
                (assigned == null ? OrbitText.get(R.string.ui_none_dc937b, "None") : assigned.name));
    }

    private void showQuickRoutineChooser() {
        List<RoutineStore.Routine> routines = RoutineStore.list(this);
        LinearLayout choices = new LinearLayout(this);
        choices.setOrientation(LinearLayout.VERTICAL);
        addQuickRoutineChoice(choices, OrbitText.get(R.string.ui_none_dc937b, "None"), "");
        for (RoutineStore.Routine routine : routines) {
            addQuickRoutineChoice(choices, routine.name, routine.id);
        }
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setPadding(UiKit.dp(this, 12), UiKit.dp(this, 8),
                UiKit.dp(this, 12), UiKit.dp(this, 8));
        scroll.addView(choices, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(OrbitText.get(R.string.ui_quick_settings_routine_39095c, "Quick Settings Routine"))
                .setView(scroll)
                .setNegativeButton(OrbitText.get(R.string.ui_cancel_19766e, "Cancel"), null)
                .create();
        for (int i = 0; i < choices.getChildCount(); i++) {
            choices.getChildAt(i).setTag(dialog);
        }
        styleOrbitDialog(dialog, false);
        dialog.show();
    }

    private void addQuickRoutineChoice(LinearLayout choices, String label, String routineId) {
        String selectedId = Prefs.quickSettingsRoutineId(this);
        boolean selected = selectedId.equals(routineId);
        TextView row = UiKit.text(this, (selected ? "●  " : "○  ") + label,
                14, selected ? UiKit.accent(this) : UiKit.TEXT, selected);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(UiKit.dp(this, 13), 0, UiKit.dp(this, 13), 0);
        row.setBackground(UiKit.ripple(
                selected ? UiKit.blend(UiKit.accent(this), UiKit.SURFACE_2, 0.16f) : UiKit.SURFACE_2,
                UiKit.accent(this), 13, this));
        row.setOnClickListener(v -> {
            if (Prefs.setQuickSettingsRoutineId(this, routineId)) {
                QuickSettingsTiles.refreshRoutineTile(this);
                refreshQuickRoutineSelection();
                Object tag = v.getTag();
                if (tag instanceof AlertDialog) ((AlertDialog) tag).dismiss();
            }
        });
        UiKit.pressScale(row);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, UiKit.dp(this, 46));
        if (choices.getChildCount() > 0) lp.topMargin = UiKit.dp(this, 3);
        choices.addView(row, lp);
    }

    private void chooseBackupDestination() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/octet-stream");
        intent.putExtra(Intent.EXTRA_TITLE, OrbitBackupManager.defaultFileName());
        startActivityForResult(intent, REQ_EXPORT_BACKUP);
    }

    private void chooseBackupToRestore() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, REQ_RESTORE_BACKUP);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_ASSISTANT_SETTINGS) {
            getWindow().getDecorView().postDelayed(this::updateAssistantStatus, 200L);
            return;
        }
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (requestCode == REQ_EXPORT_BACKUP) {
            new Thread(() -> {
                try {
                    OrbitBackupManager.exportTo(getApplicationContext(), uri);
                    runOnUiThread(() -> Toast.makeText(this, OrbitText.get(R.string.ui_orbit_backup_saved_599c72, "Orbit backup saved"), Toast.LENGTH_LONG).show());
                } catch (Exception e) {
                    runOnUiThread(() -> showBackupError(OrbitText.get(R.string.ui_could_not_export_backup_98b279, "Could not export backup"), e));
                }
            }, "orbit-backup-export").start();
        } else if (requestCode == REQ_RESTORE_BACKUP) {
            new Thread(() -> {
                try {
                    OrbitBackupManager.PreparedRestore prepared =
                            OrbitBackupManager.prepareRestore(getApplicationContext(), uri);
                    runOnUiThread(() -> confirmRestore(prepared));
                } catch (Exception e) {
                    runOnUiThread(() -> showBackupError(OrbitText.get(R.string.ui_could_not_open_backup_7902f4, "Could not open backup"), e));
                }
            }, "orbit-backup-validate").start();
        }
    }

    private void confirmRestore(OrbitBackupManager.PreparedRestore prepared) {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(OrbitText.get(R.string.ui_restore_orbit_backup_add4c9, "Restore Orbit backup?"))
                .setMessage(prepared.confirmationMessage())
                .setNegativeButton(OrbitText.get(R.string.ui_cancel_19766e, "Cancel"), null)
                .setPositiveButton(OrbitText.get(R.string.ui_restore_a76e13, "Restore"), (d, which) -> runRestore(prepared))
                .create();
        styleOrbitDialog(dialog, true);
        dialog.show();
    }

    private void runRestore(OrbitBackupManager.PreparedRestore prepared) {
        Toast.makeText(this, OrbitText.get(R.string.ui_restoring_orbit_backup_0f1590, "Restoring Orbit backup…"), Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                OrbitBackupManager.restore(getApplicationContext(), prepared);
                runOnUiThread(() -> {
                    UiKit.syncTheme(this);
                    Toast.makeText(this,
                            OrbitText.get(R.string.ui_orbit_backup_restored_review_android_permi_9932be, "Orbit backup restored. Review Android permissions and default-assistant status."),
                            Toast.LENGTH_LONG).show();
                    recreate();
                });
            } catch (Exception e) {
                runOnUiThread(() -> showBackupError(OrbitText.get(R.string.ui_could_not_restore_backup_eaf9f7, "Could not restore backup"), e));
            }
        }, "orbit-backup-restore").start();
    }

    private void showBackupError(String title, Exception error) {
        String message = error == null || error.getMessage() == null || error.getMessage().trim().isEmpty()
                ? OrbitText.get(R.string.ui_orbit_could_not_complete_this_backup_opera_d02d69, "Orbit could not complete this backup operation.") : error.getMessage();
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(OrbitText.get(R.string.ui_ok_565339, "OK"), null)
                .create();
        styleOrbitDialog(dialog, false);
        dialog.show();
    }

    private void updateAssistantStatus() {
        if (assistantStatus == null) return;
        boolean active = OrbitSetupHelper.isOrbitAssistantActive(this);
        assistantStatus.setText(active ? OrbitText.get(R.string.ui_orbit_is_your_default_assistant_7bea32, "✓ Orbit is your default assistant") : OrbitText.get(R.string.ui_orbit_is_not_the_active_assistant_yet_92ed56, "○ Orbit is not the active assistant yet"));
        assistantStatus.setTextColor(active ? UiKit.SUCCESS : UiKit.TEXT);
        if (assistantAction != null) {
            assistantAction.setText(active ? OrbitText.get(R.string.ui_manage_default_assistant_89d6a0, "Manage default assistant") : OrbitText.get(R.string.ui_make_orbit_default_assistant_7a5864, "Make Orbit default assistant"));
            assistantAction.setEnabled(true);
            assistantAction.setAlpha(1f);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_LOCATION) {
            boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            Prefs.get(this).edit().putBoolean(Prefs.WEATHER_USE_DEVICE_LOCATION, granted).apply();
            Toast.makeText(this, granted ? OrbitText.get(R.string.ui_approximate_location_enabled_for_weather_9c595d, "Approximate location enabled for weather") : OrbitText.get(R.string.ui_location_permission_not_granted_eb2e81, "Location permission not granted"), Toast.LENGTH_SHORT).show();
            View content = buildContent();
            setContentView(content);
            UiKit.applyActivityInsets(this, content, true);
            updateAssistantStatus();
            updateChatGptStatus();
            return;
        }
        if (requestCode == REQ_NOTIFICATIONS) {
            boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            Prefs.get(this).edit().putBoolean(Prefs.BACKGROUND_NOTIFICATIONS, granted).apply();
            if (granted) NotificationHelper.ensureChannel(this);
            Toast.makeText(this, granted ? OrbitText.get(R.string.ui_background_response_notifications_enabled_ddfedf, "Background response notifications enabled") : OrbitText.get(R.string.ui_notification_permission_not_granted_4392f3, "Notification permission not granted"), Toast.LENGTH_SHORT).show();
            // Rebuild so the checkbox reflects the permission result without leaving Settings.
            View content = buildContent();
            setContentView(content);
            UiKit.applyActivityInsets(this, content, true);
            updateAssistantStatus();
            updateChatGptStatus();
        }
    }

    private TextView sectionTitle(String s, String key) {
        TextView t = UiKit.text(this, s, 12, UiKit.MUTED, true);
        t.setLetterSpacing(0.13f);
        t.setTag(TAG_SECTION_PREFIX + key);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(UiKit.dp(this, 4), UiKit.dp(this, 22), 0, UiKit.dp(this, 9));
        t.setLayoutParams(lp);
        return t;
    }

    private void tagSectionCard(View view, String key) {
        if (view != null) view.setTag(TAG_CARD + ":" + key);
    }

    /**
     * Registers one view as the thing a search result for {@code key} should land on.
     *
     * <p>Returns the view so it can be wrapped around an {@code addView} argument at the point the
     * control is created, which is the only place that knows which control it is. Keeping the map
     * beside the tree rather than inside it means a lookup is a hash rather than a walk, and a
     * rebuild starts from an empty map rather than from stale entries.
     */
    private View target(String key, View view) {
        if (view != null && key != null && !key.isEmpty()) searchTargets.put(key, view);
        return view;
    }

    /**
     * Groups a label and its control so a search result lands on both.
     *
     * <p>Several Orbit settings are a caption above a selector, and neither half is the setting on
     * its own: scrolling to "Custom model" alone puts the control it names just below the fold, and
     * scrolling to the selector alone hides the words that say what it is. One container is the
     * whole setting, so that is what a result targets and what pulses.
     */
    private View controlGroup(String key, View labelView, View control,
                              LinearLayout.LayoutParams controlLp) {
        LinearLayout group = new LinearLayout(this);
        group.setOrientation(LinearLayout.VERTICAL);
        if (labelView != null) group.addView(labelView);
        // A null lp means the control already carries the layout it wants, which is true of the
        // selectors that build their own; passing null to addView would throw rather than default.
        if (control != null) {
            if (controlLp == null) group.addView(control);
            else group.addView(control, controlLp);
        }
        target(key, group);
        return group;
    }

    /**
     * Scrolls to the control this page was opened for and pulses it, once.
     *
     * <p>Posted rather than run inline, because the page has just been built and nothing has been
     * laid out yet - a scroll computed against zero heights lands at the top, which is exactly the
     * "took me to the section" outcome this feature exists to replace. The key is consumed on the
     * first successful settle, so a rotation or an appearance rebuild does not re-pulse a control
     * the user has already been shown.
     */
    private void focusPendingControl() {
        if (pendingFocusKey.isEmpty() || settingsScroll == null) return;
        View target = searchTargets.get(pendingFocusKey);
        if (target == null) { pendingFocusKey = ""; return; }
        final ScrollView scroll = settingsScroll;
        scroll.post(() -> {
            int top = 0;
            View walk = target;
            while (walk != null && walk != scroll && walk.getParent() instanceof View) {
                top += walk.getTop();
                walk = (View) walk.getParent();
            }
            // A little above the control rather than exactly at it: a row flush against the top
            // edge of the viewport reads as the page having scrolled past it.
            scroll.smoothScrollTo(0, Math.max(0, top - UiKit.dp(this, 72)));
            UiKit.enterContent(target);
            pulse(target);
        });
        pendingFocusKey = "";
    }

    /**
     * A brief accent wash over one control, so the eye lands where the scroll did.
     *
     * <p>Drawn as the view's foreground and animated by alpha, so nothing is resized, no text
     * reflows and no neighbouring control moves. It respects the system animation scale through
     * {@link UiKit#animationsEnabled()}: a device set to reduced motion gets the scroll and no
     * flashing, which is still an answer to "where is it".
     */
    private void pulse(View target) {
        if (target == null) return;
        if (!UiKit.animationsEnabled()) return;
        final android.graphics.drawable.GradientDrawable wash =
                UiKit.rounded(UiKit.withAlpha(UiKit.accent(this), 90), 18, this);
        target.setForeground(wash);
        wash.setAlpha(0);
        android.animation.ValueAnimator animator =
                android.animation.ValueAnimator.ofInt(0, 255, 0, 200, 0);
        animator.setDuration(900L);
        animator.addUpdateListener(a -> wash.setAlpha((Integer) a.getAnimatedValue()));
        animator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator animation) {
                target.setForeground(null);
            }
        });
        animator.start();
    }

    private void applySettingsSectionFilter(LinearLayout page, String section) {
        if (page == null || section == null || section.isEmpty()) return;
        for (int i = 0; i < page.getChildCount(); i++) {
            View child = page.getChildAt(i);
            Object rawTag = child.getTag();
            if (!(rawTag instanceof String)) continue;
            String tag = (String) rawTag;
            String key = "";
            if (tag.startsWith(TAG_SECTION_PREFIX)) key = tag.substring(TAG_SECTION_PREFIX.length());
            else if (tag.startsWith(TAG_CARD + ":")) key = tag.substring((TAG_CARD + ":").length());
            if (!key.isEmpty()) child.setVisibility(sectionAllows(section, key) ? View.VISIBLE : View.GONE);
        }
    }

    private boolean sectionAllows(String section, String key) {
        if (SECTION_ASSISTANT.equals(section)) return "setup".equals(key);
        if (SECTION_AI.equals(section)) return "account".equals(key) || "intelligence".equals(key);
        if (SECTION_VOICE.equals(section)) return "voice".equals(key);
        if (SECTION_DATA.equals(section)) return "data".equals(key);
        if (SECTION_DECK.equals(section)) return "deck".equals(key);
        if (SECTION_CONVERSATIONS.equals(section)) return "conversations".equals(key);
        if (SECTION_APPEARANCE.equals(section)) return "appearance".equals(key);
        if (SECTION_ADVANCED.equals(section)) return "diagnostics".equals(key);
        return false;
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(UiKit.dp(this, 18), UiKit.dp(this, 18), UiKit.dp(this, 18), UiKit.dp(this, 18));
        c.setBackground(UiKit.outlined(UiKit.SURFACE, UiKit.withAlpha(UiKit.accent(this), 38), 24, this));
        c.setTag(TAG_CARD);
        c.setElevation(UiKit.dp(this, 2));
        return c;
    }

    private TextView label(String s) {
        TextView t = UiKit.text(this, s, 13, UiKit.MUTED, true);
        t.setPadding(UiKit.dp(this, 2), UiKit.dp(this, 4), 0, UiKit.dp(this, 6));
        return t;
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
        b.setTextSize(14);
        b.setAllCaps(false);
        b.setBackground(UiKit.rippleOutlined(UiKit.SURFACE_2, Color.rgb(53,58,72), UiKit.accent(this), 15, this));
        b.setMinHeight(0); b.setMinimumHeight(0); b.setStateListAnimator(null);
        UiKit.pressScale(b);
        return b;
    }

    /** Orbit's restrained destructive treatment: outlined, never a filled bright surface. */
    private Button dangerOutlineButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(UiKit.DANGER);
        b.setTextSize(14);
        b.setAllCaps(false);
        b.setBackground(UiKit.rippleOutlined(UiKit.SURFACE_2,
                UiKit.withAlpha(UiKit.DANGER, 110), UiKit.DANGER, 15, this));
        b.setMinHeight(0); b.setMinimumHeight(0); b.setStateListAnimator(null);
        UiKit.pressScale(b);
        return b;
    }

    /** What the Vault's switch and its erase control say, in the words the screen shows. */
    static final String VAULT_TOGGLE_LABEL = "Use Orbit Vault";
    static final String VAULT_TOGGLE_HELP =
            "Save text, links, images and useful Orbit answers for later. Everything stays on this "
                    + "device, and Orbit uses a saved item only when you choose it yourself.";
    static final String VAULT_OPEN_LABEL = "Open Orbit Vault";
    static final String SMART_VAULT_OPEN_LABEL = "Smart Vault";
    static final String VAULT_DELETE_LABEL = "Delete Vault data";
    static final String VAULT_DELETE_TITLE = "Delete all Vault data?";
    static final String VAULT_DELETE_MESSAGE =
            "All items saved in Orbit Vault and their local media will be removed from this "
                    + "device. This cannot be undone.\n\nYour chats, Orbit Memory, your providers "
                    + "and the rest of your settings are not affected.";

    /**
     * The Vault's three controls, and the difference between two of them.
     *
     * <p>Switching the Vault off and erasing what is in it are separate on purpose, because they
     * are separate intentions with very different costs. Off is a preference: the Vault leaves the
     * app, nothing is deleted, and one tap brings all of it back. Delete is permanent, so it is its
     * own control with Orbit's destructive treatment and its own confirmation - and it stays
     * available while the Vault is off, because somebody who has stopped using the feature should
     * not have to switch it back on in order to be rid of the contents.
     */
    private void buildVaultCard(LinearLayout card) {
        TextView help = UiKit.text(this, localizedVaultToggleHelp(), 13, UiKit.MUTED, false);
        help.setPadding(0, 0, 0, UiKit.dp(this, 12));
        card.addView(help);

        Button openVault = secondaryButton(OrbitText.get(R.string.ui_open_orbit_vault_146873, VAULT_OPEN_LABEL));
        openVault.setOnClickListener(v -> startActivity(new Intent(this, OrbitVaultActivity.class)));
        LinearLayout.LayoutParams openVaultLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, UiKit.dp(this, 48));
        openVaultLp.setMargins(0, UiKit.dp(this, 4), 0, 0);

        OrbitSwitch control = new OrbitSwitch(this);
        control.setChecked(Prefs.vaultEnabled(this), false);
        control.setOnCheckedChangeListener((button, checked) -> {
            Prefs.get(this).edit().putBoolean(Prefs.VAULT_ENABLED, checked).apply();
            // The way in disappears with the feature rather than staying as a control that opens a
            // page saying it is switched off. Corrected here and now, because a switch whose
            // consequences only appear after leaving Settings does not read as connected to it.
            openVault.setVisibility(checked ? View.VISIBLE : View.GONE);
            if (smartVaultButton != null) smartVaultButton.setVisibility(checked ? View.VISIBLE : View.GONE);
        });
        card.addView(UiKit.switchRow(this, OrbitText.get(R.string.ui_use_orbit_vault_3e080f, VAULT_TOGGLE_LABEL), null, control));

        openVault.setVisibility(Prefs.vaultEnabled(this) ? View.VISIBLE : View.GONE);
        card.addView(openVault, openVaultLp);

        // Smart Vault (v0.8.1.0-beta.1) has its own screen, because each of its privacy choices
        // needs a sentence saying what leaves the phone, and five of those would bury this card.
        Button smartVault = secondaryButton(Prefs.smartVaultEnabled(this)
                ? OrbitText.get(R.string.ui_smart_vault_475097, SMART_VAULT_OPEN_LABEL) + OrbitText.get(R.string.ui_on_be4a8e, " · On") : OrbitText.get(R.string.ui_smart_vault_475097, SMART_VAULT_OPEN_LABEL));
        smartVault.setOnClickListener(v -> startActivity(new Intent(this, SmartVaultActivity.class)));
        LinearLayout.LayoutParams smartLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, UiKit.dp(this, 48));
        smartLp.setMargins(0, UiKit.dp(this, 9), 0, 0);
        smartVault.setVisibility(Prefs.vaultEnabled(this) ? View.VISIBLE : View.GONE);
        card.addView(smartVault, smartLp);
        smartVaultButton = smartVault;

        Button deleteVault = dangerOutlineButton(OrbitText.get(R.string.ui_delete_vault_data_5ae787, VAULT_DELETE_LABEL));
        deleteVault.setOnClickListener(v -> confirmDeleteVaultData());
        LinearLayout.LayoutParams deleteVaultLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, UiKit.dp(this, 48));
        deleteVaultLp.setMargins(0, UiKit.dp(this, 9), 0, 0);
        card.addView(deleteVault, deleteVaultLp);
    }

    /**
     * Asks before erasing the Vault, and says exactly what is and is not included.
     *
     * <p>Naming what survives is as much a part of an irreversible confirmation as naming what
     * goes. "Delete Vault data" beside a Memory control and a Backup control is precisely the kind
     * of button somebody presses while wondering how much of their Orbit it reaches.
     */
    private void confirmDeleteVaultData() {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(OrbitText.get(R.string.ui_delete_all_vault_data_58d0ee, VAULT_DELETE_TITLE))
                .setMessage(localizedVaultDeleteMessage())
                .setNegativeButton(OrbitText.get(R.string.ui_cancel_19766e, "Cancel"), null)
                .setPositiveButton(OrbitText.get(R.string.ui_delete_all_978111, "Delete all"), (d, w) -> {
                    int removed = OrbitVaultStore.deleteAllData(this);
                    Toast.makeText(this, removed == 0
                                    ? OrbitText.get(R.string.ui_your_vault_was_already_empty_d6d1fb, "Your Vault was already empty")
                                    : OrbitText.get(R.string.ui_deleted_7960c0, "Deleted ") + removed
                                            + (removed == 1 ? OrbitText.get(R.string.ui_saved_item_90fa72, " saved item") : OrbitText.get(R.string.ui_saved_items_5fef97, " saved items")),
                            Toast.LENGTH_SHORT).show();
                })
                .create();
        UiKit.styleOrbitDialog(dialog, this, true);
        dialog.show();
    }

    private View toggle(String label, String key, boolean def) {
        return toggle(label, null, key, def);
    }

    private View toggle(String label, String description, String key, boolean def) {
        OrbitSwitch control = new OrbitSwitch(this);
        control.setChecked(Prefs.get(this).getBoolean(key, def), false);
        // The confirmation tick lives in OrbitSwitch now, so every switch in the app feels the
        // same and one tap can only produce one tick.
        control.setOnCheckedChangeListener((button, checked) ->
                Prefs.get(this).edit().putBoolean(key, checked).apply());
        return UiKit.switchRow(this, label, description, control);
    }

    private View weatherLocationToggle() {
        OrbitSwitch control = new OrbitSwitch(this);
        boolean granted = checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        control.setChecked(Prefs.weatherUseDeviceLocation(this) && granted, false);
        control.setOnCheckedChangeListener((button, checked) -> {
            if (checked && !granted) {
                // setChecked never re-enters this listener, so the rollback is a plain correction.
                button.setChecked(false);
                requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOCATION);
                return;
            }
            Prefs.get(this).edit().putBoolean(Prefs.WEATHER_USE_DEVICE_LOCATION, checked).apply();
        });
        return UiKit.switchRow(this, OrbitText.get(R.string.ui_use_approximate_device_location_for_local_033b63, "Use approximate device location for local weather"), null, control);
    }

    /**
     * The way into Theme Studio, and the only place in Settings that shows Orbit's colors.
     *
     * <p>It used to sit above a second set of color controls that wrote the same preferences. That
     * was defensible while Theme Studio was new and unproven, and stopped being defensible the
     * moment it worked: two editors for one set of values is a question the app asks the user and
     * cannot answer. So the row now states what the theme currently is, shows it, and is the way to
     * change it.
     *
     * <p>The summary is read from {@link OrbitThemeTokens}, the same resolver the app itself draws
     * from, so the strip here is the theme rather than an illustration of it.
     */
    private View themeStudioRow() {
        OrbitTheme active = OrbitThemeStore.active(this);
        OrbitThemeTokens tokens = OrbitThemeTokens.resolve(this, active);

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(UiKit.dp(this, 14), UiKit.dp(this, 12),
                UiKit.dp(this, 12), UiKit.dp(this, 12));
        row.setBackground(UiKit.rippleOutlined(UiKit.SURFACE_2,
                UiKit.withAlpha(UiKit.accent(this), 38), UiKit.accent(this), 16, this));
        row.setClickable(true);
        row.setFocusable(true);
        UiKit.pressScale(row);

        row.addView(themeSummarySwatch(tokens), new LinearLayout.LayoutParams(
                UiKit.dp(this, 44), UiKit.dp(this, 44)));

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        text.addView(UiKit.text(this, OrbitText.get(R.string.ui_theme_studio_d64bfb, "Theme Studio"), 15, UiKit.TEXT, true));
        TextView status = UiKit.text(this, themeSummaryLine(active), 12, UiKit.MUTED, false);
        status.setPadding(0, UiKit.dp(this, 3), 0, 0);
        text.addView(status);
        LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        textLp.leftMargin = UiKit.dp(this, 13);
        row.addView(text, textLp);

        TextView arrow = UiKit.text(this, "›", 24, UiKit.accent(this), false);
        arrow.setPadding(UiKit.dp(this, 10), 0, UiKit.dp(this, 4), 0);
        row.addView(arrow);

        row.setContentDescription(OrbitText.get(R.string.ui_theme_studio_20f4c4, "Theme Studio. ") + themeSummaryLine(active)
                + OrbitText.get(R.string.ui_opens_orbit_s_color_designer_4e1be8, ". Opens Orbit's color designer."));
        row.setOnClickListener(v -> startActivity(new Intent(this, ThemeStudioActivity.class)));

        LinearLayout holder = new LinearLayout(this);
        holder.setOrientation(LinearLayout.VERTICAL);
        holder.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView note = UiKit.text(this,
                OrbitText.get(R.string.ui_design_orbit_s_colors_with_live_previews_p_400add, "Design Orbit's colors with live previews, presets and your own saved themes."),
                12, UiKit.MUTED, false);
        note.setPadding(0, UiKit.dp(this, 10), 0, 0);
        holder.addView(note);
        return holder;
    }

    /** "Nova AMOLED, Nova, AMOLED" - the theme's name, its accent, and whether it is true black. */
    private String themeSummaryLine(OrbitTheme active) {
        String accent = OrbitPalette.labelFor(active.accent);
        String line = active.name;
        if (!accent.equalsIgnoreCase(active.name)) line += " · " + accent;
        return active.amoled ? line + OrbitText.get(R.string.ui_amoled_a153f4, " · AMOLED") : line;
    }

    /**
     * Three bands of the active theme on its own page colour: accent, the raised card step,
     * and the card itself.
     *
     * <p>Every one of these is part of the structural appearance signature, so the strip is refreshed
     * by the same rebuild that refreshes the rest of the page. Showing a bubble colour here would
     * look better and be wrong: bubble colours are deliberately outside that signature, and this
     * swatch would then be the one stale thing on an otherwise current screen.
     */
    private View themeSummarySwatch(OrbitThemeTokens tokens) {
        LinearLayout strip = new LinearLayout(this);
        strip.setOrientation(LinearLayout.VERTICAL);
        strip.setBackground(UiKit.outlined(tokens.background,
                UiKit.withAlpha(UiKit.TEXT, 60), 12, this));
        strip.setPadding(UiKit.dp(this, 4), UiKit.dp(this, 4), UiKit.dp(this, 4), UiKit.dp(this, 4));
        strip.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        int[] bands = {tokens.accent, tokens.surface2, tokens.surface};
        for (int i = 0; i < bands.length; i++) {
            View band = new View(this);
            band.setBackground(UiKit.rounded(bands[i], 3, this));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
            if (i > 0) lp.topMargin = UiKit.dp(this, 3);
            strip.addView(band, lp);
        }
        return strip;
    }

    private View notificationToggle() {
        OrbitSwitch control = new OrbitSwitch(this);
        control.setChecked(Prefs.backgroundNotifications(this), false);
        control.setOnCheckedChangeListener((button, checked) -> {
            if (checked && Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                Prefs.get(this).edit().putBoolean(Prefs.BACKGROUND_NOTIFICATIONS, false).apply();
                button.setChecked(false);
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATIONS);
                return;
            }
            Prefs.get(this).edit().putBoolean(Prefs.BACKGROUND_NOTIFICATIONS, checked).apply();
            if (checked) NotificationHelper.ensureChannel(this);
        });
        return UiKit.switchRow(this, OrbitText.get(R.string.ui_notify_me_when_a_background_response_finis_0e484f, "Notify me when a background response finishes"), null, control);
    }

    private void performSettingsHaptic(View view) {
        if (view == null || !Prefs.haptics(this)) return;
        try { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); }
        catch (Exception ignored) {}
    }

    private View fontSelector() {
        String[] keys = new String[]{"orbit_default", "times_new_roman", "light", "condensed", "monospace", "casual"};
        String[] labels = new String[]{OrbitText.get(R.string.ui_orbit_default_85a4dc, "Orbit Default"), "Times New Roman", OrbitText.get(R.string.ui_light_dbcd5e, "Light"), OrbitText.get(R.string.ui_condensed_ec5ac7, "Condensed"), OrbitText.get(R.string.ui_monospace_64c223, "Monospace"), OrbitText.get(R.string.ui_casual_f32445, "Casual")};
        String selected = Prefs.appFont(this);
        int selectedIndex = indexOf(keys, selected);
        LinearLayout selector = fontMenuSelector(keys, labels, selectedIndex, (position, label) -> {
            String key = keys[Math.max(0, Math.min(keys.length - 1, position))];
            if (!key.equals(Prefs.appFont(this))) {
                Prefs.get(this).edit().putString(Prefs.APP_FONT, key).apply();
                UiKit.notifyAppearanceChanged(this);
            }
        });
        selector.setLayoutParams(selectorLp());
        return selector;
    }

    private View pageTransitionSelector() {
        String[] keys = new String[]{Prefs.PAGE_TRANSITION_SLIDE, Prefs.PAGE_TRANSITION_FADE,
                Prefs.PAGE_TRANSITION_NONE};
        String[] labels = new String[]{OrbitText.get(R.string.ui_slide_6bb4c1, "Slide"), OrbitText.get(R.string.ui_fade_settle_e6a246, "Fade & settle"), OrbitText.get(R.string.ui_none_dc937b, "None")};
        int selected = indexOf(keys, Prefs.pageTransition(this));
        LinearLayout selector = menuSelector(labels, selected, (position, label) -> {
            String key = keys[Math.max(0, Math.min(keys.length - 1, position))];
            Prefs.get(this).edit().putString(Prefs.PAGE_TRANSITION, key).apply();
            // Retarget this window straight away so leaving Settings already uses the new style.
            // Nothing on the page is rebuilt, so there is no flash and no scroll movement.
            UiKit.applyPageTransition(this);
        });
        selector.setLayoutParams(selectorLp());
        return selector;
    }

    private View chatTextSizeSelector() {
        String[] keys = new String[]{
                Prefs.CHAT_TEXT_SMALL, Prefs.CHAT_TEXT_DEFAULT,
                Prefs.CHAT_TEXT_LARGE, Prefs.CHAT_TEXT_EXTRA_LARGE};
        String[] labels = new String[]{OrbitText.get(R.string.ui_small_526329, "Small"), OrbitText.get(R.string.ui_default_21b111, "Default"), OrbitText.get(R.string.ui_large_ab8054, "Large"), OrbitText.get(R.string.ui_extra_large_cc1691, "Extra large")};
        int selected = indexOf(keys, Prefs.chatTextSize(this));
        LinearLayout selector = menuSelector(labels, selected, (position, label) -> {
            String key = keys[Math.max(0, Math.min(keys.length - 1, position))];
            Prefs.get(this).edit().putString(Prefs.CHAT_TEXT_SIZE, key).apply();
        });
        selector.setLayoutParams(selectorLp());
        return selector;
    }

    /**
     * Look &amp; Feel's Overlay card: a live preview, the style, and which quick controls stay on the
     * sheet. Every row writes the same preferences the overlay reads on its next opening, and the
     * preview redraws from them at once. The styles offered come from {@link OverlayStyle#choices},
     * so Lelo mode's page rebuild is all it takes for the list and the preview to follow it.
     */
    private View overlayCard() {
        LinearLayout card = card();
        tagSectionCard(card, "appearance");
        TextView intro = UiKit.text(this, OrbitText.get(R.string.ui_customize_how_orbit_appears_over_other_app_b45a09, "Customize how Orbit appears over other apps."),
                13, UiKit.MUTED, false);
        intro.setPadding(UiKit.dp(this, 2), 0, 0, UiKit.dp(this, 12));
        card.addView(intro);

        overlayPreview = new OverlayPreviewView(this);
        LinearLayout.LayoutParams previewLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        previewLp.setMargins(0, 0, 0, UiKit.dp(this, 16));
        card.addView(target(SettingsSearchIndex.KEY_OVERLAY_PREVIEW, overlayPreview), previewLp);

        TextView styleLabel = label(OrbitText.get(R.string.ui_overlay_style_5b130b, "Overlay style"));
        styleLabel.setPadding(UiKit.dp(this, 2), 0, 0, UiKit.dp(this, 6));
        card.addView(controlGroup(SettingsSearchIndex.KEY_OVERLAY_STYLE,
                styleLabel, overlayStyleSelector(), null));
        TextView styleNote = UiKit.text(this,
                OrbitText.get(R.string.ui_modern_is_orbit_s_full_floating_assistant_69afb8, "Modern is Orbit's full floating assistant. Float is compact and grows only when a conversation needs the room; on wider screens it rests in the bottom-right corner, and tapping its handle folds it into a small Peek that keeps your chat going. Classic is the original overlay. Every style works the same way, and changes apply the next time you open the assistant."),
                12, UiKit.MUTED, false);
        styleNote.setPadding(0, UiKit.dp(this, 2), 0, UiKit.dp(this, 6));
        card.addView(styleNote);

        LinearLayout quick = new LinearLayout(this);
        quick.setOrientation(LinearLayout.VERTICAL);
        TextView quickLabel = label(OrbitText.get(R.string.ui_quick_controls_4b79a9, "Quick controls"));
        quickLabel.setPadding(UiKit.dp(this, 2), UiKit.dp(this, 14), 0, UiKit.dp(this, 2));
        quick.addView(quickLabel);
        TextView quickNote = UiKit.text(this,
                OrbitText.get(R.string.ui_choose_which_controls_stay_on_the_overlay_62b0c7, "Choose which controls stay on the overlay. Hidden ones are still one tap away in its More menu, where Float always keeps Recent chats and New chat."),
                12, UiKit.MUTED, false);
        quickNote.setPadding(UiKit.dp(this, 2), 0, 0, UiKit.dp(this, 4));
        quick.addView(quickNote);
        quick.addView(overlayToggle(OrbitText.get(R.string.ui_ai_model_4d2e74, "AI model"), null, Prefs.OVERLAY_SHOW_MODEL));
        quick.addView(overlayToggle(OrbitText.get(R.string.ui_recent_chats_2ccfec, "Recent chats"), null, Prefs.OVERLAY_SHOW_HISTORY));
        quick.addView(overlayToggle(OrbitText.get(R.string.ui_new_chat_db1838, "New chat"), null, Prefs.OVERLAY_SHOW_NEW_CHAT));
        quick.addView(overlayToggle(OrbitText.get(R.string.ui_screen_buttons_e922b1, "Screen buttons"),
                OrbitText.get(R.string.ui_use_screen_and_select_area_also_in_attach_96d44f, "Use screen and Select area. Also in Attach, under Screen."), Prefs.OVERLAY_SHOW_SCREEN));
        quick.addView(overlayToggle(OrbitText.get(R.string.ui_attach_d406ad, "Attach"), null, Prefs.OVERLAY_SHOW_ATTACH));
        quick.addView(overlayToggle(OrbitText.get(R.string.ui_voice_87bf2b, "Voice"),
                OrbitText.get(R.string.ui_auto_listen_still_works_and_the_mic_appear_7065f1, "Auto-listen still works, and the mic appears while Orbit is listening."),
                Prefs.OVERLAY_SHOW_VOICE));
        card.addView(target(SettingsSearchIndex.KEY_OVERLAY_QUICK_CONTROLS, quick));

        refreshOverlayPreview();
        return card;
    }

    private View overlayToggle(String label, String description, String key) {
        OrbitSwitch control = new OrbitSwitch(this);
        control.setChecked(Prefs.get(this).getBoolean(key, true), false);
        control.setOnCheckedChangeListener((button, checked) -> {
            Prefs.get(this).edit().putBoolean(key, checked).apply();
            refreshOverlayPreview();
        });
        return UiKit.switchRow(this, label, description, control);
    }

    /** Redraws the preview from the stored overlay settings. */
    private void refreshOverlayPreview() {
        if (overlayPreview == null) return;
        OverlayStyle style = OverlayStyle.current(this);
        overlayPreview.show(style, OverlayControls.of(this, style));
    }

    private View overlayStyleSelector() {
        List<OverlayStyle> choices = OverlayStyle.choices(this);
        String[] keys = new String[choices.size()];
        String[] labels = new String[choices.size()];
        for (int i = 0; i < keys.length; i++) {
            keys[i] = choices.get(i).id;
            labels[i] = choices.get(i).displayLabel();
        }
        int selected = indexOf(keys, Prefs.overlayStyle(this));
        LinearLayout selector = menuSelector(labels, selected, (position, label) -> {
            String key = keys[Math.max(0, Math.min(keys.length - 1, position))];
            Prefs.setOverlayStyle(this, key);
            refreshOverlayPreview();
        });
        selector.setLayoutParams(selectorLp());
        return selector;
    }

    private View weatherUnitsSelector() {
        String[] keys = new String[]{Prefs.WEATHER_UNITS_SYSTEM,
                Prefs.WEATHER_UNITS_FAHRENHEIT, Prefs.WEATHER_UNITS_CELSIUS};
        String[] labels = new String[]{OrbitText.get(R.string.ui_system_default_b04592, "System default"), OrbitText.get(R.string.ui_fahrenheit_f_9c9497, "Fahrenheit (°F)"), OrbitText.get(R.string.ui_celsius_c_400b94, "Celsius (°C)")};
        int selected = indexOf(keys, Prefs.weatherUnits(this));
        LinearLayout selector = menuSelector(labels, selected, (position, label) -> {
            String key = keys[Math.max(0, Math.min(keys.length - 1, position))];
            Prefs.get(this).edit().putString(Prefs.WEATHER_UNITS, key).apply();
        });
        selector.setLayoutParams(selectorLp());
        return selector;
    }

    private View galleryAppSelector() {
        List<GalleryAppPreference.Option> options = GalleryAppPreference.options(this);
        String preferred = GalleryAppPreference.storedPackage(this);
        String[] labels = new String[options.size()];
        int selected = 0;
        for (int i = 0; i < options.size(); i++) {
            GalleryAppPreference.Option option = options.get(i);
            labels[i] = option.label;
            if (option.packageName.equals(preferred)) selected = i;
        }
        LinearLayout selector = menuSelector(labels, selected, (position, label) -> {
            int safe = Math.max(0, Math.min(options.size() - 1, position));
            // Saves the exact Activity and action discovered here, so every surface can launch it
            // later without resolving anything again.
            GalleryAppPreference.setPreferredOption(this, options.get(safe));
        });
        selector.setLayoutParams(selectorLp());
        return selector;
    }

    private void handleLeloSecretTap() {
        long now = System.currentTimeMillis();
        if (now - lastLeloTapMs > 2200L) leloTapCount = 0;
        lastLeloTapMs = now;
        leloTapCount++;
        if (leloTapCount >= 7) {
            leloTapCount = 0;
            boolean enabled = !Prefs.leloMode(this);
            Prefs.get(this).edit().putBoolean(Prefs.LELO_MODE, enabled).apply();
            Toast.makeText(this, enabled ? "Lelo mode unlocked ✨" : "Lelo mode hidden again", Toast.LENGTH_SHORT).show();
            // Lelo mode decides which overlay styles exist, so the page rebuilds in place (same
            // scroll position) the way an accent change does, with the selector re-read.
            appliedStructuralAppearance = "";
            refreshAppearanceIfNeeded();
        }
    }

    private void applyFontInPlace() {
        View rootView = getWindow().getDecorView().findViewById(android.R.id.content);
        if (rootView != null) UiKit.applyTypography(rootView);
    }

    @Override
    public void onOrbitAppearanceChanged() {
        applyAppearanceChange();
    }

    /**
     * Applies an appearance change to the screen already on display.
     *
     * <p>Only accent and AMOLED are baked into built views, so only those rebuild. App font is
     * re-applied to the existing hierarchy, and conversation bubble colours change nothing that
     * Settings draws — their selectors already update their own labels. Replacing the content view
     * for those was what briefly emptied the content frame and flashed the window through.
     */
    private void applyAppearanceChange() {
        if (rebuildingAppearance) return;
        if (refreshAppearanceIfNeeded()) return;
        String desired = UiKit.appearanceSignature(this);
        if (desired.equals(appliedAppearance)) return;
        // Font or bubble colours changed: update in place, no rebuild and no flash.
        UiKit.syncTheme(this);
        applyFontInPlace();
        appliedAppearance = desired;
    }

    /** Rebuilds only for appearance the built views cannot pick up any other way. */
    private boolean refreshAppearanceIfNeeded() {
        String desiredStructural = UiKit.structuralAppearanceSignature(this);
        if (rebuildingAppearance || desiredStructural.equals(appliedStructuralAppearance)) return false;
        rebuildingAppearance = true;
        final int oldScrollY = settingsScroll == null ? 0 : settingsScroll.getScrollY();
        try {
            UiKit.syncTheme(this);
            Window window = getWindow();
            window.setStatusBarColor(UiKit.BG);
            window.setNavigationBarColor(UiKit.BG);
            window.setBackgroundDrawable(new ColorDrawable(UiKit.BG));
            View content = buildContent();
            setContentView(content);
            UiKit.applyActivityInsets(this, content, true);
            appliedAppearance = UiKit.appearanceSignature(this);
            appliedStructuralAppearance = desiredStructural;
            updateAssistantStatus();
            updateChatGptStatus();
            restoreScrollBeforeFirstDraw(oldScrollY);
            return true;
        } finally {
            rebuildingAppearance = false;
        }
    }

    /**
     * Puts the rebuilt page back at its previous offset before it is ever drawn, so the new
     * hierarchy never appears at the top and then jumps.
     */
    private void restoreScrollBeforeFirstDraw(int scrollY) {
        final ScrollView target = settingsScroll;
        if (target == null || scrollY <= 0) return;
        target.getViewTreeObserver().addOnPreDrawListener(
                new ViewTreeObserver.OnPreDrawListener() {
                    @Override public boolean onPreDraw() {
                        ViewTreeObserver observer = target.getViewTreeObserver();
                        if (observer.isAlive()) observer.removeOnPreDrawListener(this);
                        int maximum = Math.max(0, (target.getChildCount() == 0 ? 0
                                : target.getChildAt(0).getHeight()) - target.getHeight());
                        target.scrollTo(0, Math.min(scrollY, maximum));
                        return true;
                    }
                });
    }

    private EditText field(String hint, String value, boolean secret) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setHintTextColor(Color.rgb(115,120,135));
        e.setText(value);
        e.setTextColor(UiKit.TEXT);
        e.setTextSize(14);
        e.setSingleLine(true);
        e.setPadding(UiKit.dp(this, 15), 0, UiKit.dp(this, 15), 0);
        e.setBackground(UiKit.outlined(UiKit.SURFACE_2,
                UiKit.withAlpha(UiKit.accent(this), 72), 15, this));
        if (secret) e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        else e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        e.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UiKit.dp(this, 52)));
        return e;
    }

    private interface MenuSelectionCallback {
        void selected(int position, String label);
    }

    private LinearLayout fontMenuSelector(String[] keys, String[] labels, int selectedIndex,
                                          MenuSelectionCallback callback) {
        LinearLayout field = new LinearLayout(this);
        field.setOrientation(LinearLayout.HORIZONTAL);
        field.setGravity(Gravity.CENTER_VERTICAL);
        field.setPadding(UiKit.dp(this, 16), 0, UiKit.dp(this, 14), 0);
        field.setBackground(UiKit.rippleOutlined(UiKit.SURFACE_2,
                UiKit.withAlpha(UiKit.accent(this), 72), UiKit.accent(this), 16, this));

        int safeIndex = Math.max(0, Math.min(labels.length - 1, selectedIndex));
        final int[] current = {safeIndex};

        TextView value = UiKit.text(this, labels[safeIndex], 15, UiKit.TEXT, false);
        value.setMaxLines(2);
        UiKit.applyFontPreview(value, keys[safeIndex], Typeface.NORMAL);
        field.addView(value, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView arrow = UiKit.text(this, "▾", 18, UiKit.MUTED, true);
        arrow.setPadding(UiKit.dp(this, 12), 0, 0, 0);
        field.addView(arrow);

        field.setOnClickListener(v -> UiKit.showOrbitFontMenu(this, field, keys, labels,
                current[0], (index, label) -> {
                    current[0] = index;
                    value.setText(label);
                    UiKit.applyFontPreview(value, keys[index], Typeface.NORMAL);
                    if (callback != null) callback.selected(index, label);
                }));
        UiKit.pressScale(field);
        return field;
    }

    /**
     * The default-AI field: shows the current default and opens the shared picker. Built once;
     * a choice updates its text in place.
     */
    private LinearLayout defaultAiSelector() {
        LinearLayout field = new LinearLayout(this);
        field.setOrientation(LinearLayout.HORIZONTAL);
        field.setGravity(Gravity.CENTER_VERTICAL);
        field.setPadding(UiKit.dp(this, 16), 0, UiKit.dp(this, 14), 0);
        field.setBackground(UiKit.rippleOutlined(UiKit.SURFACE_2,
                UiKit.withAlpha(UiKit.accent(this), 72), UiKit.accent(this), 16, this));
        TextView value = UiKit.text(this, defaultAiText(), 15, UiKit.TEXT, false);
        value.setMaxLines(2);
        field.addView(value, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView arrow = UiKit.text(this, "▾", 18, UiKit.MUTED, true);
        arrow.setPadding(UiKit.dp(this, 12), 0, 0, 0);
        field.addView(arrow);
        field.setContentDescription(OrbitText.get(R.string.ui_default_ai_for_new_chats_34971b, "Default AI for new chats: ") + defaultAiText());
        field.setOnClickListener(v -> AiSelectorDialog.show(this, OrbitText.get(R.string.ui_default_for_new_chats_899941, "Default for new chats"),
                AiSelections.newChatSelection(this), s -> OrbitText.get(R.string.ui_use_bb33db, "Use ") + s.label(), chosen -> {
                    AiSelections.setGlobalDefault(this, chosen);
                    value.setText(defaultAiText());
                    field.setContentDescription(OrbitText.get(R.string.ui_default_ai_for_new_chats_34971b, "Default AI for new chats: ") + defaultAiText());
                }));
        UiKit.pressScale(field);
        return field;
    }

    private String defaultAiText() {
        if (AiSelections.newChatsUseAuto(this)) return AiSelection.AUTO_LABEL;
        AiSelection s = AiSelections.globalDefault(this);
        return s.providerName() + " · " + s.label();
    }

    /**
     * "Auto can use": one row opening Auto's provider switches. A summary of what is on, so the
     * row says what Auto may spend through without opening it.
     */
    private LinearLayout autoProvidersRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(UiKit.dp(this, 2), UiKit.dp(this, 8), UiKit.dp(this, 2), UiKit.dp(this, 8));
        row.setMinimumHeight(UiKit.dp(this, 52));
        row.setBackground(UiKit.ripple(Color.TRANSPARENT, UiKit.accent(this), 14, this));
        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        words.addView(UiKit.text(this, OrbitText.get(R.string.ui_auto_can_use_a728a9, "Auto can use"), 15, UiKit.TEXT, true));
        TextView summary = UiKit.text(this, AutoPermissions.summary(this), 12, UiKit.MUTED, false);
        summary.setPadding(0, UiKit.dp(this, 3), 0, 0);
        words.addView(summary);
        row.addView(words, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView arrow = UiKit.text(this, "›", 24, UiKit.accent(this), false);
        arrow.setPadding(UiKit.dp(this, 10), 0, UiKit.dp(this, 4), 0);
        row.addView(arrow);
        row.setContentDescription(OrbitText.get(R.string.ui_auto_can_use_f73958, "Auto can use: ") + AutoPermissions.summary(this));
        row.setOnClickListener(v -> AutoSheets.showSettings(this, () -> {
            summary.setText(AutoPermissions.summary(this));
            row.setContentDescription(OrbitText.get(R.string.ui_auto_can_use_f73958, "Auto can use: ") + AutoPermissions.summary(this));
        }));
        UiKit.pressScale(row);
        return row;
    }

    private LinearLayout menuSelector(String[] labels, int selectedIndex,
                                      MenuSelectionCallback callback) {
        LinearLayout field = new LinearLayout(this);
        field.setOrientation(LinearLayout.HORIZONTAL);
        field.setGravity(Gravity.CENTER_VERTICAL);
        field.setPadding(UiKit.dp(this, 16), 0, UiKit.dp(this, 14), 0);
        field.setBackground(UiKit.rippleOutlined(UiKit.SURFACE_2,
                UiKit.withAlpha(UiKit.accent(this), 72), UiKit.accent(this), 16, this));

        int safeIndex = Math.max(0, Math.min(labels.length - 1, selectedIndex));
        final int[] current = {safeIndex};

        TextView value = UiKit.text(this, labels[safeIndex], 15, UiKit.TEXT, false);
        value.setMaxLines(2);
        field.addView(value, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView arrow = UiKit.text(this, "▾", 18, UiKit.MUTED, true);
        arrow.setPadding(UiKit.dp(this, 12), 0, 0, 0);
        field.addView(arrow);

        field.setOnClickListener(v -> UiKit.showOrbitMenu(this, field, labels,
                current[0], (index, label) -> {
                    current[0] = index;
                    value.setText(label);
                    if (callback != null) callback.selected(index, label);
                }));
        UiKit.pressScale(field);
        return field;
    }

    private int indexOf(String[] values, String target) {
        if (values == null || values.length == 0) return 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(target)) return i;
        }
        return 0;
    }

    private LinearLayout.LayoutParams selectorLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, UiKit.dp(this, 54));
        lp.setMargins(0, 0, 0, UiKit.dp(this, 10));
        return lp;
    }

    private static String localizedVaultToggleHelp() {
        return OrbitText.get(R.string.ui_save_text_links_images_and_useful_orbit_an_4b8928, "Save text, links, images and useful Orbit answers for later. Everything stays on this ")
                    + OrbitText.get(R.string.ui_device_and_orbit_uses_a_saved_item_only_wh_f110ed, "device, and Orbit uses a saved item only when you choose it yourself.");
    }

    private static String localizedVaultDeleteMessage() {
        return OrbitText.get(R.string.ui_all_items_saved_in_orbit_vault_and_their_l_271cac, "All items saved in Orbit Vault and their local media will be removed from this ")
                    + OrbitText.get(R.string.ui_device_this_cannot_be_undone_your_chats_or_c22585, "device. This cannot be undone.\n\nYour chats, Orbit Memory, your providers ")
                    + OrbitText.get(R.string.ui_and_the_rest_of_your_settings_are_not_affe_fa5d7e, "and the rest of your settings are not affected.");
    }
}
