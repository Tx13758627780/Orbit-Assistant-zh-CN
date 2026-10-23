package com.orbit.assistant;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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

import java.util.List;
import java.util.Locale;

/**
 * Smart Vault's setup and settings: one screen, with every privacy boundary its own switch.
 *
 * <p>Before Smart Vault is on, the switches are the choices the user is about to make, and nothing
 * happens until they press "Turn on Smart Vault". Afterwards each switch acts immediately. Each
 * says in one line what leaves the phone and where it goes - nothing, Google Play services, a model
 * server once, a website, or the user's AI provider - and the longer technical detail sits under
 * Privacy details.
 *
 * <p>Since v0.8.1.0-beta.2 the page is grouped into Search, Capture, AI suggestions, Saved items
 * and Manage, and is built once. A switch, the model download and the index counts all update the
 * views already on screen. Beta 1 rebuilt the whole page on every tap, which destroyed the switch
 * mid-animation and swallowed its haptic tick.
 */
public final class SmartVaultActivity extends Activity {

    static final String TITLE = "Smart Vault";
    static final String TURN_ON = "Turn on Smart Vault";
    static final String TURN_OFF = "Turn off Smart Vault";

    static final String SECTION_SEARCH = "SEARCH";
    static final String SECTION_CAPTURE = "CAPTURE";
    static final String SECTION_AI = "AI SUGGESTIONS";
    static final String SECTION_ITEMS = "SAVED ITEMS";
    static final String SECTION_MANAGE = "MANAGE";

    static final String OCR_LABEL = "Read text in pictures";
    static final String OCR_HELP = "Reads the words in screenshots and photos on this phone, with "
            + "Google ML Kit through Google Play services. Pictures never leave the phone.";
    static final String MEANING_LABEL = "Search by meaning";
    static final String MEANING_HELP = "Finds items by what they mean. Downloads a "
            + SmartVaultModel.sizeLabel() + " model once from Hugging Face; after that, search "
            + "runs on this phone.";
    static final String LINKS_LABEL = "Read saved links";
    static final String LINKS_HELP = "Opens each saved link once to read its title and text. "
            + "This contacts that website.";
    static final String AI_LABEL = "Suggest details for new items";
    static final String AI_HELP = "Sends new items to your AI provider for a suggested title, "
            + "summary and topics. This uses your AI allowance.";

    static final String DETAILS_LABEL = "Privacy details";
    static final String DETAILS_BODY = "Search model: potion-base-8M by Minish Lab, MIT licence, "
            + SmartVaultModel.sizeLabel() + ". Downloaded once from Hugging Face and checked "
            + "against a fixed checksum. Works best in English.\n\n"
            + "Text in pictures: read on this phone. Google may collect anonymous statistics about "
            + "how ML Kit is used.\n\n"
            + "AI suggestions: only items saved after you turn this on are sent. Items you saved "
            + "before are only sent if you ask, under Saved items.\n\n"
            + "Your saved items, notes and titles are never changed without you. Indexing runs in "
            + "the background and pauses while the battery is low.";

    /** Model states, as the row under Search by meaning words them. */
    static final String MODEL_READY = "Search model ready on this phone";
    static final String MODEL_PREPARING = "Preparing download…";
    static final String MODEL_DOWNLOADING = "Downloading search model";
    static final String MODEL_WAITING = "Waiting to continue";
    static final String MODEL_FAILED = "Download didn't finish";
    static final String MODEL_MISSING = "Search model not downloaded";
    static final String ACTION_RETRY = "Retry";
    static final String ACTION_DOWNLOAD = "Download";
    static final String ACTION_REMOVE = "Remove";

    private LinearLayout page;
    private boolean choiceOcr = true;
    private boolean choiceMeaning = true;
    private boolean choiceLinks = false;
    private boolean choiceAi = false;
    private String appearanceSignature = "";
    private final Handler ticker = new Handler(Looper.getMainLooper());
    private final Runnable tick = this::tick;
    private OrbitPredictiveBack navigation;
    private ScrollView scroll;

    /** Views that update in place, present only while Smart Vault is on. */
    private OrbitSwitch meaningSwitch;
    private ModelRow modelRow;
    private LinearLayout itemsBody;
    private String itemsSignature = "";
    private boolean detailsOpen;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        UiKit.syncTheme(this);
        appearanceSignature = UiKit.appearanceSignature(this);
        Window window = getWindow();
        window.setStatusBarColor(UiKit.BG);
        window.setNavigationBarColor(UiKit.BG);
        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        OrbitBackground.applyPage(scroll);
        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        int side = UiKit.dp(this, 18);
        page.setPadding(side, UiKit.dp(this, 10), side, UiKit.dp(this, 40));
        scroll.addView(page, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(scroll);
        UiKit.applyActivityInsets(this, scroll, true);
        navigation = OrbitPredictiveBack.install(this);
        rebuild();
    }

    @Override protected void onResume() {
        super.onResume();
        UiPresence.enter(this);
        if (!UiKit.appearanceSignature(this).equals(appearanceSignature)) {
            recreate();
            return;
        }
        if (Prefs.smartVaultEnabled(this) && Prefs.smartVaultMeaning(this)) {
            SmartVaultModel.ensureScheduled(this);
        }
        // Rebuilt at the same scroll position: coming back from the Vault or Settings may have
        // changed what the page shows, but it should not move under the user.
        int y = scroll == null ? 0 : scroll.getScrollY();
        rebuild();
        if (scroll != null && y > 0) scroll.post(() -> scroll.scrollTo(0, y));
        ticker.removeCallbacks(tick);
        ticker.postDelayed(tick, 1000);
    }

    @Override protected void onPause() {
        ticker.removeCallbacks(tick);
        UiPresence.leave(this);
        super.onPause();
    }

    /**
     * Keeps the model row and the index counts current while the screen is visible. Only those two
     * change, in place, so nothing else on the page moves or is rebuilt.
     */
    private void tick() {
        if (isFinishing() || isDestroyed()) return;
        refreshLive();
        SmartVaultModel.Phase phase = SmartVaultModel.phase(this);
        boolean busy = phase == SmartVaultModel.Phase.DOWNLOADING
                || phase == SmartVaultModel.Phase.PREPARING;
        ticker.postDelayed(tick, busy ? 700 : 3000);
    }

    /** Brings the model row and the Saved items card up to date without rebuilding either card. */
    void refreshLive() {
        if (!Prefs.smartVaultEnabled(this)) return;
        if (modelRow != null) modelRow.bind();
        if (itemsBody != null && !itemsSignature().equals(itemsSignature)) fillItems();
    }

    private String itemsSignature() {
        StringBuilder s = new StringBuilder();
        s.append(OrbitVaultStore.count(this)).append('|').append(SmartVaultModel.isReady(this))
                .append('|').append(Prefs.smartVaultMeaning(this))
                .append(Prefs.smartVaultOcr(this)).append(Prefs.smartVaultReadLinks(this));
        if (!SmartVault.databaseExists(this)) return s.toString();
        try {
            SmartVaultDb db = SmartVaultDb.get(this);
            s.append('|').append(db.vectorCount()).append('|').append(db.queuedCount())
                    .append('|').append(db.derivedCount(SmartVaultDb.KIND_OCR))
                    .append('|').append(db.derivedCount(SmartVaultDb.KIND_PAGE))
                    .append('|').append(SmartVault.itemsWithoutSuggestions(this).size());
        } catch (Exception ignored) {
        }
        return s.toString();
    }

    private void rebuild() {
        if (page == null) return;
        page.removeAllViews();
        meaningSwitch = null;
        modelRow = null;
        itemsBody = null;
        boolean on = Prefs.smartVaultEnabled(this);
        page.addView(header(on));

        if (!Prefs.vaultEnabled(this)) {
            LinearLayout off = card();
            off.addView(UiKit.text(this, OrbitText.get(R.string.ui_orbit_vault_is_turned_off_cd728c, "Orbit Vault is turned off"), 15, UiKit.TEXT, true));
            off.addView(muted(OrbitText.get(R.string.ui_turn_orbit_vault_on_in_settings_to_use_sma_6be168, "Turn Orbit Vault on in Settings to use Smart Vault.")));
            page.addView(off, cardLp());
            return;
        }

        if (!on) {
            LinearLayout intro = card();
            intro.addView(UiKit.text(this, OrbitText.get(R.string.ui_find_what_you_saved_faster_68c3c2, "Find what you saved, faster"), 15, UiKit.TEXT, true));
            intro.addView(muted(OrbitText.get(R.string.ui_smart_vault_indexes_your_vault_on_this_pho_707956, "Smart Vault indexes your Vault on this phone: words in ")
                    + OrbitText.get(R.string.ui_screenshots_similar_meanings_and_topics_ch_1c874c, "screenshots, similar meanings and topics. Choose what it may do below. ")
                    + OrbitText.get(R.string.ui_nothing_you_saved_is_changed_without_you_aeea83, "Nothing you saved is changed without you.")));
            page.addView(intro, cardLp());
        }

        page.addView(sectionLabel(OrbitText.get(R.string.ui_search_56f216, SECTION_SEARCH), !on));
        LinearLayout search = card();
        meaningSwitch = new OrbitSwitch(this);
        search.addView(option(meaningSwitch, OrbitText.get(R.string.ui_search_by_meaning_aefe82, MEANING_LABEL), localizedMeaningHelp(),
                on ? Prefs.smartVaultMeaning(this) : choiceMeaning, Prefs.SMART_VAULT_MEANING,
                v -> choiceMeaning = v));
        if (on) {
            modelRow = new ModelRow();
            search.addView(modelRow.root);
            modelRow.bind();
        }
        page.addView(search, cardLp());

        page.addView(sectionLabel(OrbitText.get(R.string.ui_capture_095cab, SECTION_CAPTURE), true));
        LinearLayout capture = card();
        capture.addView(option(new OrbitSwitch(this), OrbitText.get(R.string.ui_read_text_in_pictures_981a97, OCR_LABEL), localizedOcrHelp(),
                on ? Prefs.smartVaultOcr(this) : choiceOcr, Prefs.SMART_VAULT_OCR,
                v -> choiceOcr = v));
        capture.addView(option(new OrbitSwitch(this), OrbitText.get(R.string.ui_read_saved_links_f96be1, LINKS_LABEL), localizedLinksHelp(),
                on ? Prefs.smartVaultReadLinks(this) : choiceLinks, Prefs.SMART_VAULT_READ_LINKS,
                v -> choiceLinks = v));
        page.addView(capture, cardLp());

        page.addView(sectionLabel(OrbitText.get(R.string.ui_ai_suggestions_b0a319, SECTION_AI), true));
        LinearLayout ai = card();
        ai.addView(option(new OrbitSwitch(this), OrbitText.get(R.string.ui_suggest_details_for_new_items_a9ec7a, AI_LABEL), localizedAiHelp(),
                on ? Prefs.smartVaultAiForNewItems(this) : choiceAi, Prefs.SMART_VAULT_AI_NEW,
                v -> choiceAi = v));
        ai.addView(providerLine());
        page.addView(ai, cardLp());

        if (!on) {
            page.addView(details());
            Button turnOn = UiKit.button(this, OrbitText.get(R.string.ui_turn_on_smart_vault_0b4d3f, TURN_ON), true);
            turnOn.setOnClickListener(v -> {
                SmartVault.enable(this, choiceOcr, choiceMeaning, choiceLinks, choiceAi);
                Toast.makeText(this, OrbitText.get(R.string.ui_smart_vault_is_on_indexing_happens_in_the_d08f0a, "Smart Vault is on. Indexing happens in the background."),
                        Toast.LENGTH_LONG).show();
                rebuild();
                scroll.post(() -> scroll.scrollTo(0, 0));
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, UiKit.dp(this, 50));
            lp.setMargins(0, UiKit.dp(this, 16), 0, 0);
            page.addView(turnOn, lp);
            return;
        }

        page.addView(sectionLabel(OrbitText.get(R.string.ui_saved_items_756447, SECTION_ITEMS), true));
        LinearLayout items = card();
        itemsBody = new LinearLayout(this);
        itemsBody.setOrientation(LinearLayout.VERTICAL);
        items.addView(itemsBody);
        fillItems();
        page.addView(items, cardLp());

        page.addView(sectionLabel(OrbitText.get(R.string.ui_manage_7f54be, SECTION_MANAGE), true));
        LinearLayout manage = card();
        Button off = UiKit.button(this, OrbitText.get(R.string.ui_turn_off_smart_vault_9ee58e, TURN_OFF), false);
        off.setOnClickListener(v -> {
            SmartVault.disable(this);
            Toast.makeText(this, OrbitText.get(R.string.ui_smart_vault_is_off_your_vault_is_unchanged_4b7a3b, "Smart Vault is off. Your Vault is unchanged."),
                    Toast.LENGTH_SHORT).show();
            rebuild();
        });
        manage.addView(off, buttonLp(0));
        Button delete = UiKit.button(this, OrbitText.get(R.string.ui_delete_smart_vault_data_3536c3, "Delete Smart Vault data"), false);
        delete.setTextColor(UiKit.DANGER);
        delete.setBackground(UiKit.rippleOutlined(UiKit.SURFACE_2,
                UiKit.withAlpha(UiKit.DANGER, 90), UiKit.DANGER, 15, this));
        delete.setOnClickListener(v -> confirmDeleteData());
        manage.addView(delete, buttonLp(9));
        manage.addView(muted(OrbitText.get(R.string.ui_deleting_removes_recognised_text_page_text_6b1fee, "Deleting removes recognised text, page text, the index and ")
                + OrbitText.get(R.string.ui_orbit_s_suggestions_your_items_notes_own_t_c33e20, "Orbit's suggestions. Your items, notes, own titles and kept topics stay.")));
        page.addView(manage, cardLp());

        page.addView(details());
    }

    private View providerLine() {
        String blocker = SmartVault.suggestionBlocker(this);
        String text = blocker.isEmpty()
                ? OrbitText.get(R.string.ui_provider_e0f3fd, "Provider: ") + SmartVault.providerName(this)
                : blocker + (blocker.endsWith(".") ? "" : ".");
        TextView line = UiKit.text(this, text, 12, UiKit.MUTED, false);
        line.setPadding(UiKit.dp(this, 2), UiKit.dp(this, 2), UiKit.dp(this, 2), UiKit.dp(this, 4));
        return line;
    }

    interface Choice {
        void set(boolean value);
    }

    /**
     * One Smart Vault switch, as an ordinary Orbit switch row.
     *
     * <p>The listener records the choice and updates only what depends on it. It never rebuilds
     * the page, so the control that was tapped stays on screen: its thumb slides with Orbit's
     * standard motion (or jumps, with animations off), and {@link OrbitSwitch#toggle()} gives the
     * one light tick every Settings switch gives, from a view that is still attached.
     */
    private View option(OrbitSwitch control, String label, String help, boolean checked,
                        String key, Choice choice) {
        control.setChecked(checked, false);
        control.setOnCheckedChangeListener((button, value) -> {
            if (!Prefs.smartVaultEnabled(this)) {
                choice.set(value);
                return;
            }
            SmartVault.setOption(this, key, value);
            if (Prefs.SMART_VAULT_MEANING.equals(key) && modelRow != null) modelRow.bind();
            refreshLive();
        });
        return UiKit.switchRow(this, label, help, control);
    }

    /**
     * The search model's state under Search by meaning: a small accent ring with its percentage
     * while downloading, and a single restrained line once ready.
     */
    final class ModelRow {
        final LinearLayout root = new LinearLayout(SmartVaultActivity.this);
        final OrbitProgressRing ring = new OrbitProgressRing(SmartVaultActivity.this);
        final TextView title;
        final TextView detail;
        final TextView trailing;
        SmartVaultModel.Phase shown;

        ModelRow() {
            root.setOrientation(LinearLayout.HORIZONTAL);
            root.setGravity(Gravity.CENTER_VERTICAL);
            root.setPadding(UiKit.dp(SmartVaultActivity.this, 2), UiKit.dp(SmartVaultActivity.this, 4),
                    UiKit.dp(SmartVaultActivity.this, 2), UiKit.dp(SmartVaultActivity.this, 8));
            ring.setContentDescription(OrbitText.get(R.string.ui_search_model_download_d1f40e, "Search model download"));
            LinearLayout.LayoutParams ringLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            ringLp.setMargins(0, 0, UiKit.dp(SmartVaultActivity.this, 12), 0);
            root.addView(ring, ringLp);

            LinearLayout texts = new LinearLayout(SmartVaultActivity.this);
            texts.setOrientation(LinearLayout.VERTICAL);
            title = UiKit.text(SmartVaultActivity.this, "", 13, UiKit.TEXT, false);
            // Announced when the state changes, not on every percent.
            title.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
            detail = UiKit.text(SmartVaultActivity.this, "", 12, UiKit.MUTED, false);
            detail.setPadding(0, UiKit.dp(SmartVaultActivity.this, 1), 0, 0);
            texts.addView(title);
            texts.addView(detail);
            root.addView(texts, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

            trailing = UiKit.text(SmartVaultActivity.this, "", 13, UiKit.accent(SmartVaultActivity.this), true);
            int pad = UiKit.dp(SmartVaultActivity.this, 10);
            trailing.setPadding(pad, pad, UiKit.dp(SmartVaultActivity.this, 4), pad);
            trailing.setMinWidth(UiKit.dp(SmartVaultActivity.this, 44));
            trailing.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
            root.addView(trailing, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        void bind() {
            boolean meaning = Prefs.smartVaultMeaning(SmartVaultActivity.this);
            root.setVisibility(meaning ? View.VISIBLE : View.GONE);
            if (!meaning) return;
            SmartVaultModel.Phase phase = SmartVaultModel.phase(SmartVaultActivity.this);
            int percent = SmartVaultModel.percent(SmartVaultActivity.this);
            String size = mb(SmartVaultModel.doneBytes(SmartVaultActivity.this)) + OrbitText.get(R.string.ui_of_a4282e, " of ")
                    + SmartVaultModel.sizeLabel();
            int accent = UiKit.accent(SmartVaultActivity.this);
            String titleText;
            String detailText;
            String action = "";
            View.OnClickListener onAction = null;
            boolean showRing = true;
            int titleColor = UiKit.TEXT;
            int trailingColor = accent;
            switch (phase) {
                case PREPARING:
                    titleText = OrbitText.get(R.string.ui_preparing_download_f40b15, MODEL_PREPARING);
                    detailText = SmartVaultModel.sizeLabel() + OrbitText.get(R.string.ui_once_runs_in_the_background_702762, ", once. Runs in the background.");
                    ring.setIndeterminate();
                    break;
                case DOWNLOADING:
                    titleText = OrbitText.get(R.string.ui_downloading_search_model_e30c1f, MODEL_DOWNLOADING);
                    detailText = size + OrbitText.get(R.string.ui_runs_in_the_background_e444ca, " · runs in the background");
                    ring.setProgress(percent);
                    action = percent + "%";
                    break;
                case WAITING:
                    titleText = OrbitText.get(R.string.ui_waiting_to_continue_ed1958, MODEL_WAITING);
                    detailText = OrbitText.get(R.string.ui_continues_by_itself_when_the_phone_is_onli_96d197, "Continues by itself when the phone is online")
                            + (percent > 0 ? " · " + size : "");
                    ring.setProgress(percent);
                    action = percent > 0 ? percent + "%" : "";
                    trailingColor = UiKit.MUTED;
                    break;
                case FAILED:
                    titleText = OrbitText.get(R.string.ui_download_didn_t_finish_bb8f30, MODEL_FAILED);
                    detailText = SmartVaultModel.error(SmartVaultActivity.this);
                    showRing = false;
                    action = OrbitText.get(R.string.ui_retry_942087, ACTION_RETRY);
                    onAction = v -> {
                        SmartVaultModel.requestDownload(SmartVaultActivity.this);
                        bind();
                    };
                    break;
                case READY:
                    titleText = "✓  " + OrbitText.get(R.string.ui_search_model_ready_on_this_phone_6dcd7e, MODEL_READY);
                    detailText = "";
                    titleColor = accent;
                    showRing = false;
                    action = OrbitText.get(R.string.ui_remove_c3812f, ACTION_REMOVE);
                    onAction = v -> {
                        SmartVaultModel.delete(SmartVaultActivity.this);
                        SmartVault.setOption(SmartVaultActivity.this, Prefs.SMART_VAULT_MEANING, false);
                        if (meaningSwitch != null) meaningSwitch.setChecked(false);
                        bind();
                        refreshLive();
                    };
                    break;
                case MISSING:
                default:
                    titleText = OrbitText.get(R.string.ui_search_model_not_downloaded_745597, MODEL_MISSING);
                    detailText = SmartVaultModel.sizeLabel() + OrbitText.get(R.string.ui_once_from_hugging_face_7488fe, ", once, from Hugging Face");
                    showRing = false;
                    action = OrbitText.get(R.string.ui_download_d6eafe, ACTION_DOWNLOAD);
                    onAction = v -> {
                        SmartVaultModel.requestDownload(SmartVaultActivity.this);
                        bind();
                    };
                    break;
            }
            ring.setVisibility(showRing ? View.VISIBLE : View.GONE);
            title.setText(titleText);
            title.setTextColor(titleColor);
            detail.setText(detailText);
            detail.setVisibility(detailText.isEmpty() ? View.GONE : View.VISIBLE);
            trailing.setText(action);
            trailing.setTextColor(trailingColor);
            trailing.setVisibility(action.isEmpty() ? View.GONE : View.VISIBLE);
            trailing.setOnClickListener(onAction);
            trailing.setClickable(onAction != null);
            trailing.setBackground(onAction == null ? null
                    : UiKit.ripple(android.graphics.Color.TRANSPARENT, accent, 12, SmartVaultActivity.this));
            trailing.setContentDescription(onAction == null ? null
                    : action + (phase == SmartVaultModel.Phase.READY ? OrbitText.get(R.string.ui_search_model_52b917, " search model")
                            : OrbitText.get(R.string.ui_search_model_download_3a7051, " search model download")));
            shown = phase;
        }
    }

    static String mb(long bytes) {
        return String.format(Locale.US, "%.0f MB", bytes / 1_000_000.0);
    }

    /** Index counts, then suggestions for the items saved before Smart Vault. */
    private void fillItems() {
        if (itemsBody == null) return;
        itemsSignature = itemsSignature();
        itemsBody.removeAllViews();
        int total = OrbitVaultStore.count(this);
        itemsBody.addView(stat(OrbitText.get(R.string.ui_saved_items_76f485, "Saved items"), String.valueOf(total)));
        int queued = 0;
        try {
            SmartVaultDb db = SmartVaultDb.get(this);
            if (Prefs.smartVaultMeaning(this) && SmartVaultModel.isReady(this)) {
                itemsBody.addView(stat(OrbitText.get(R.string.ui_searchable_by_meaning_d408a3, "Searchable by meaning"),
                        Math.min(total, db.vectorCount()) + OrbitText.get(R.string.ui_of_a4282e, " of ") + total));
            }
            if (Prefs.smartVaultOcr(this)) {
                itemsBody.addView(stat(OrbitText.get(R.string.ui_pictures_with_text_found_7e85b0, "Pictures with text found"),
                        String.valueOf(db.derivedCount(SmartVaultDb.KIND_OCR))));
            }
            if (Prefs.smartVaultReadLinks(this)) {
                itemsBody.addView(stat(OrbitText.get(R.string.ui_links_read_91ea65, "Links read"),
                        String.valueOf(db.derivedCount(SmartVaultDb.KIND_PAGE))));
            }
            queued = db.queuedCount();
            if (queued > 0) {
                itemsBody.addView(stat(OrbitText.get(R.string.ui_waiting_for_suggestions_0d6410, "Waiting for suggestions"), queued + OrbitText.get(R.string.ui_needs_a_connection_7ada12, " · needs a connection")));
            }
        } catch (Exception ignored) {
        }

        // Suggestions for items saved before, only when the user asks, with the exact number of
        // items that would be sent and to which provider.
        List<String> without = SmartVault.itemsWithoutSuggestions(this);
        if (without.isEmpty()) {
            itemsBody.addView(muted(OrbitText.get(R.string.ui_every_item_already_has_suggestions_e09b9e, "Every item already has suggestions.")));
        } else {
            itemsBody.addView(muted(without.size() + (without.size() == 1 ? OrbitText.get(R.string.ui_item_has_87f501, " item has")
                    : OrbitText.get(R.string.ui_items_have_876954, " items have")) + OrbitText.get(R.string.ui_no_suggestions_orbit_only_sends_them_to_yo_2868db, " no suggestions. Orbit only sends them to your AI ")
                    + OrbitText.get(R.string.ui_provider_if_you_ask_13f824, "provider if you ask.")));
            Button suggest = UiKit.button(this, OrbitText.get(R.string.ui_suggest_details_for_these_items_c405cb, "Suggest details for these items"), false);
            suggest.setOnClickListener(v -> confirmBatch(without));
            itemsBody.addView(suggest, buttonLp(10));
        }
        if (queued > 0) {
            Button stop = UiKit.button(this, OrbitText.get(R.string.ui_stop_366e0f, "Stop ") + queued + OrbitText.get(R.string.ui_waiting_93a420, " waiting ")
                    + (queued == 1 ? "suggestion" : "suggestions"), false);
            stop.setOnClickListener(v -> {
                try { SmartVaultDb.get(this).cancelQueued(); } catch (Exception ignored) { }
                fillItems();
            });
            itemsBody.addView(stop, buttonLp(9));
        }
    }

    private View stat(String label, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, UiKit.dp(this, 3), 0, UiKit.dp(this, 3));
        row.addView(UiKit.text(this, label, 13, UiKit.MUTED, false), new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        row.addView(UiKit.text(this, value, 13, UiKit.TEXT, true));
        return row;
    }

    /**
     * The longer technical detail, one tap away instead of under every switch. Each switch still
     * says in its own line what leaves the phone; this holds the model's name and licence, ML Kit's
     * statistics, and what happens to items saved before.
     */
    private View details() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        TextView toggle = UiKit.text(this, "", 13, UiKit.accent(this), true);
        int pad = UiKit.dp(this, 12);
        toggle.setPadding(UiKit.dp(this, 4), pad, UiKit.dp(this, 4), pad);
        toggle.setBackground(UiKit.ripple(android.graphics.Color.TRANSPARENT, UiKit.accent(this), 12, this));
        TextView body = muted(localizedDetailsBody());
        body.setPadding(UiKit.dp(this, 4), 0, UiKit.dp(this, 4), UiKit.dp(this, 6));
        Runnable apply = () -> {
            toggle.setText((detailsOpen ? "▾  " : "▸  ") + OrbitText.get(R.string.ui_privacy_details_445de1, DETAILS_LABEL));
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                toggle.setStateDescription(detailsOpen ? OrbitText.get(R.string.ui_expanded_e72d5d, "Expanded") : OrbitText.get(R.string.ui_collapsed_b322b6, "Collapsed"));
            }
            body.setVisibility(detailsOpen ? View.VISIBLE : View.GONE);
        };
        toggle.setOnClickListener(v -> {
            detailsOpen = !detailsOpen;
            apply.run();
        });
        apply.run();
        box.addView(toggle);
        box.addView(body);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, UiKit.dp(this, 8), 0, 0);
        box.setLayoutParams(lp);
        return box;
    }

    private void confirmBatch(List<String> ids) {
        String blocker = SmartVault.suggestionBlocker(this);
        if (!blocker.isEmpty()) {
            Toast.makeText(this, blocker, Toast.LENGTH_LONG).show();
            return;
        }
        String providerName = SmartVault.providerName(this);
        List<String> batch = ids.size() > SmartVault.MAX_BATCH
                ? ids.subList(0, SmartVault.MAX_BATCH) : ids;
        String message = OrbitText.get(R.string.ui_orbit_will_send_d0b88a, "Orbit will send ") + batch.size()
                + (batch.size() == 1 ? OrbitText.get(R.string.ui_saved_item_90fa72, " saved item") : OrbitText.get(R.string.ui_saved_items_5fef97, " saved items"))
                + OrbitText.get(R.string.ui_to_877216, " to ") + providerName + OrbitText.get(R.string.ui_one_at_a_time_for_a_suggested_title_c2bd15, ", one at a time, for a suggested title, ")
                + OrbitText.get(R.string.ui_summary_and_topics_this_uses_your_ai_allow_f6bf0e, "summary and topics. This uses your AI allowance: about one small request per ")
                + OrbitText.get(R.string.ui_item_you_can_stop_at_any_time_82fb21, "item. You can stop at any time.")
                + (ids.size() > batch.size() ? OrbitText.get(R.string.ui_the_newest_ea3fac, "\n\nThe newest ") + SmartVault.MAX_BATCH
                        + OrbitText.get(R.string.ui_items_are_sent_now_the_rest_can_be_sent_af_1b1112, " items are sent now; the rest can be sent afterwards.") : "");
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(OrbitText.get(R.string.ui_suggest_details_for_eb0744, "Suggest details for ") + batch.size()
                        + (batch.size() == 1 ? OrbitText.get(R.string.ui_item_ee5282, " item?") : OrbitText.get(R.string.ui_items_a794f0, " items?")))
                .setMessage(message)
                .setNegativeButton(OrbitText.get(R.string.ui_cancel_19766e, "Cancel"), null)
                .setPositiveButton(OrbitText.get(R.string.ui_send_e8fbd3, "Send ") + batch.size(), (d, w) -> {
                    int queued = SmartVault.queueSuggestions(this, batch);
                    Toast.makeText(this, queued + OrbitText.get(R.string.ui_queued_suggestions_arrive_in_the_backgroun_399943, " queued. Suggestions arrive in the background."),
                            Toast.LENGTH_LONG).show();
                    fillItems();
                })
                .create();
        UiKit.styleOrbitDialog(dialog, this, false);
        dialog.show();
    }

    private void confirmDeleteData() {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(OrbitText.get(R.string.ui_delete_smart_vault_data_335e86, "Delete Smart Vault data?"))
                .setMessage(OrbitText.get(R.string.ui_removes_recognised_text_page_text_the_sear_140e45, "Removes recognised text, page text, the search index and every ")
                        + OrbitText.get(R.string.ui_suggestion_orbit_wrote_your_saved_items_no_554691, "suggestion Orbit wrote. Your saved items, notes, your own titles and ")
                        + OrbitText.get(R.string.ui_kept_topics_are_not_touched_the_search_mod_9ee59e, "kept topics are not touched. The search model stays until you remove it."))
                .setNegativeButton(OrbitText.get(R.string.ui_cancel_19766e, "Cancel"), null)
                .setPositiveButton(OrbitText.get(R.string.ui_delete_e2d0a5, "Delete"), (d, w) -> {
                    int cleared = SmartVault.deleteData(this);
                    Toast.makeText(this, OrbitText.get(R.string.ui_smart_vault_data_deleted_8a8382, "Smart Vault data deleted")
                            + (cleared > 0 ? " (" + cleared + OrbitText.get(R.string.ui_items_had_suggestions_21ada5, " items had suggestions)") : ""),
                            Toast.LENGTH_SHORT).show();
                    fillItems();
                })
                .create();
        UiKit.styleOrbitDialog(dialog, this, true);
        dialog.show();
    }

    // ---- small helpers ----------------------------------------------------------------------------

    private View header(boolean on) {
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        ImageButton back = new ImageButton(this);
        back.setImageResource(R.drawable.ic_back);
        back.setImageTintList(ColorStateList.valueOf(UiKit.accent(this)));
        back.setBackground(UiKit.ripple(UiKit.SURFACE, UiKit.accent(this), 18, this));
        back.setContentDescription(OrbitText.get(R.string.ui_back_76900f, "Back"));
        back.setPadding(UiKit.dp(this, 11), UiKit.dp(this, 11), UiKit.dp(this, 11), UiKit.dp(this, 11));
        back.setOnClickListener(v -> navigation.performBack());
        UiKit.pressScale(back);
        top.addView(back, new LinearLayout.LayoutParams(UiKit.dp(this, 48), UiKit.dp(this, 48)));
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.addView(UiKit.text(this, OrbitText.get(R.string.ui_smart_vault_475097, TITLE), 24, UiKit.TEXT, true));
        titles.addView(UiKit.text(this, on ? OrbitText.get(R.string.ui_on_indexes_your_vault_on_this_phone_a0918d, "On · indexes your Vault on this phone")
                : OrbitText.get(R.string.ui_off_free_optional_private_by_default_30f9b2, "Off · free, optional, private by default"), 12, UiKit.MUTED, false));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        lp.setMargins(UiKit.dp(this, 14), 0, 0, UiKit.dp(this, 6));
        top.addView(titles, lp);
        return top;
    }

    /** The same small spaced capitals the Vault uses to head its groups. */
    private TextView sectionLabel(String name, boolean spacedAbove) {
        TextView label = UiKit.text(this, name, 12, UiKit.MUTED, true);
        label.setLetterSpacing(0.18f);
        label.setPadding(UiKit.dp(this, 4), UiKit.dp(this, spacedAbove ? 16 : 8), 0, 0);
        label.setAccessibilityHeading(true);
        return label;
    }

    private TextView muted(String text) {
        TextView view = UiKit.text(this, text, 12, UiKit.MUTED, false);
        view.setPadding(0, UiKit.dp(this, 6), 0, 0);
        view.setLineSpacing(0, 1.12f);
        return view;
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(UiKit.dp(this, 14), UiKit.dp(this, 8), UiKit.dp(this, 14), UiKit.dp(this, 10));
        c.setBackground(UiKit.outlined(UiKit.SURFACE,
                UiKit.withAlpha(UiKit.accent(this), 34), 20, this));
        return c;
    }

    private LinearLayout.LayoutParams cardLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, UiKit.dp(this, 8), 0, 0);
        return lp;
    }

    private LinearLayout.LayoutParams buttonLp(int topDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, UiKit.dp(this, 44));
        lp.setMargins(0, UiKit.dp(this, topDp), 0, 0);
        return lp;
    }

    private static String localizedOcrHelp() {
        return OrbitText.get(R.string.ui_reads_the_words_in_screenshots_and_photos_ff3fc2, "Reads the words in screenshots and photos on this phone, with ")
            + OrbitText.get(R.string.ui_google_ml_kit_through_google_play_services_8efa8b, "Google ML Kit through Google Play services. Pictures never leave the phone.");
    }

    private static String localizedMeaningHelp() {
        return OrbitText.get(R.string.ui_finds_items_by_what_they_mean_downloads_a_e1f657, "Finds items by what they mean. Downloads a ")
            + SmartVaultModel.sizeLabel() + OrbitText.get(R.string.ui_model_once_from_hugging_face_after_that_se_87b477, " model once from Hugging Face; after that, search ")
            + OrbitText.get(R.string.ui_runs_on_this_phone_ee9c10, "runs on this phone.");
    }

    private static String localizedLinksHelp() {
        return OrbitText.get(R.string.ui_opens_each_saved_link_once_to_read_its_tit_10ab44, "Opens each saved link once to read its title and text. ")
            + OrbitText.get(R.string.ui_this_contacts_that_website_28a249, "This contacts that website.");
    }

    private static String localizedAiHelp() {
        return OrbitText.get(R.string.ui_sends_new_items_to_your_ai_provider_for_a_cc7f69, "Sends new items to your AI provider for a suggested title, ")
            + OrbitText.get(R.string.ui_summary_and_topics_this_uses_your_ai_allow_915b05, "summary and topics. This uses your AI allowance.");
    }

    private static String localizedDetailsBody() {
        return OrbitText.get(R.string.ui_search_model_potion_base_8m_by_minish_lab_0a97cf, "Search model: potion-base-8M by Minish Lab, MIT licence, ")
            + SmartVaultModel.sizeLabel() + OrbitText.get(R.string.ui_downloaded_once_from_hugging_face_and_chec_3a4d0b, ". Downloaded once from Hugging Face and checked ")
            + OrbitText.get(R.string.ui_against_a_fixed_checksum_works_best_in_eng_308a89, "against a fixed checksum. Works best in English.\n\n")
            + OrbitText.get(R.string.ui_text_in_pictures_read_on_this_phone_google_db703f, "Text in pictures: read on this phone. Google may collect anonymous statistics about ")
            + OrbitText.get(R.string.ui_how_ml_kit_is_used_93c3eb, "how ML Kit is used.\n\n")
            + OrbitText.get(R.string.ui_ai_suggestions_only_items_saved_after_you_6ceb33, "AI suggestions: only items saved after you turn this on are sent. Items you saved ")
            + OrbitText.get(R.string.ui_before_are_only_sent_if_you_ask_under_save_ed3131, "before are only sent if you ask, under Saved items.\n\n")
            + OrbitText.get(R.string.ui_your_saved_items_notes_and_titles_are_neve_a671e3, "Your saved items, notes and titles are never changed without you. Indexing runs in ")
            + OrbitText.get(R.string.ui_the_background_and_pauses_while_the_batter_1c52d4, "the background and pauses while the battery is low.");
    }
}
