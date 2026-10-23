package com.orbit.assistant;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
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
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * AI Providers: choose and manage the backends Orbit can think with.
 *
 * <p>Each card is built for scanning, top to bottom: name, a status dot that always agrees with
 * the actions below it, one short sentence, capability chips, and only the actions the current
 * state genuinely supports. A provider that cannot answer right now never shows an enabled
 * "Use this provider"; its single action is the step that would make it usable. Selection is
 * explicit; Orbit never switches providers by itself.
 */
public final class AiProvidersActivity extends Activity {
    static final String ACTION_USE = "Use this provider";
    static final String ACTION_MANAGE = "Manage";
    static final String ACTION_SET_UP = "Set up";
    static final String ACTION_DETAILS = "Details";
    /** OpenRouter's primary connection: the browser sign-in (0.8.3.0-beta.6+). */
    static final String ACTION_OPENROUTER_SIGN_IN = "Sign in with OpenRouter";
    /** OpenRouter's advanced fallback: a key the user creates on OpenRouter and pastes here. */
    static final String ACTION_USE_API_KEY = "Use API key instead";

    /** Hears the OpenRouter sign-in while this screen is visible. */
    private final OpenRouterAuth.Listener openRouterListener = new OpenRouterAuth.Listener() {
        @Override public void onConnected() {
            Toast.makeText(AiProvidersActivity.this, OrbitText.get(R.string.ui_openrouter_connected_2d8899, "OpenRouter connected"), Toast.LENGTH_SHORT).show();
            refreshOpenRouterCatalog(false);
            refreshCards();
        }

        @Override public void onFailed(String message) {
            showOpenRouterSignInFailure(message);
            refreshCards();
        }

        @Override public void onCancelled() { refreshCards(); }
    };

    private LinearLayout cardsContainer;
    private String appearanceSignature;

    /** Interactive Back for this page. Its classification lives in OrbitNavigation. */
    private OrbitPredictiveBack navigation;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        UiKit.syncTheme(this);
        appearanceSignature = UiKit.appearanceSignature(this);
        Window window = getWindow();
        window.setStatusBarColor(UiKit.BG);
        window.setNavigationBarColor(UiKit.BG);
        View content = buildContent();
        setContentView(content);
        UiKit.applyActivityInsets(this, content, true);
        navigation = OrbitPredictiveBack.install(this);
    }

    @Override protected void onResume() {
        super.onResume();
        UiPresence.enter(this);
        ProviderCatalogRepository.loadCached(this);
        OpenRouterAuth.attach(openRouterListener);
        for (String provider : new String[]{Prefs.PROVIDER_ANTHROPIC, Prefs.PROVIDER_XAI,
                Prefs.PROVIDER_OPENROUTER}) {
            ProviderCatalogRepository.refreshIfStale(this, provider, (changed, error) -> {
                if (changed) runOnUiThread(this::refreshCards);
            });
        }
        if (!UiKit.appearanceSignature(this).equals(appearanceSignature)) {
            recreate();
            return;
        }
        refreshCards();
    }

    @Override protected void onPause() {
        // The sign-in keeps waiting while the browser is in front; its outcome is held for onResume.
        OpenRouterAuth.detach(openRouterListener);
        UiPresence.leave(this);
        super.onPause();
    }

    private View buildContent() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        OrbitBackground.applyPage(scroll);
        scroll.setForceDarkAllowed(false);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        int padding = UiKit.dp(this, 20);
        page.setPadding(padding, UiKit.dp(this, 30), padding, UiKit.dp(this, 48));
        scroll.addView(page, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        ImageButton back = new ImageButton(this);
        back.setImageResource(R.drawable.ic_back);
        back.setImageTintList(ColorStateList.valueOf(UiKit.accent(this)));
        back.setBackground(UiKit.ripple(UiKit.SURFACE_2, UiKit.accent(this), 18, this));
        back.setContentDescription(OrbitText.get(R.string.ui_back_76900f, "Back"));
        back.setPadding(UiKit.dp(this, 10), UiKit.dp(this, 10),
                UiKit.dp(this, 10), UiKit.dp(this, 10));
        back.setOnClickListener(v -> navigation.performBack());
        UiKit.pressScale(back);
        header.addView(back, new LinearLayout.LayoutParams(UiKit.dp(this, 44), UiKit.dp(this, 44)));

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setPadding(UiKit.dp(this, 13), 0, 0, 0);
        titles.addView(UiKit.text(this, OrbitText.get(R.string.ui_ai_providers_5aef60, "AI Providers"), 24, UiKit.TEXT, true));
        titles.addView(UiKit.text(this, OrbitText.get(R.string.ui_choose_what_orbit_thinks_with_0f34df, "Choose what Orbit thinks with"), 13, UiKit.MUTED, false));
        header.addView(titles, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        page.addView(header);

        TextView intro = UiKit.text(this,
                OrbitText.get(R.string.ui_choose_how_orbit_thinks_features_and_capab_808740, "Choose how Orbit thinks. Features and capabilities vary by provider."),
                13, UiKit.MUTED, false);
        intro.setLineSpacing(0, 1.14f);
        LinearLayout.LayoutParams introLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        introLp.setMargins(UiKit.dp(this, 3), UiKit.dp(this, 18), UiKit.dp(this, 3), UiKit.dp(this, 2));
        page.addView(intro, introLp);

        cardsContainer = new LinearLayout(this);
        cardsContainer.setOrientation(LinearLayout.VERTICAL);
        page.addView(cardsContainer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        refreshCards();

        UiKit.applyTypography(page);
        return scroll;
    }

    private void refreshCards() {
        if (cardsContainer == null) return;
        cardsContainer.removeAllViews();
        String activeId = AiProviders.active(this).id();
        for (AiProvider provider : AiProviders.all()) {
            cardsContainer.addView(providerCard(provider, provider.id().equals(activeId)),
                    cardLp());
        }
        UiKit.applyTypography(cardsContainer);
    }

    private LinearLayout.LayoutParams cardLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, UiKit.dp(this, 11), 0, 0);
        return lp;
    }

    private View providerCard(AiProvider provider, boolean active) {
        AiProvider.Status status = provider.status(this);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(UiKit.dp(this, 17), UiKit.dp(this, 15), UiKit.dp(this, 17), UiKit.dp(this, 15));
        // The active provider reads at a glance: accent edge, a faint accent-warmed surface, and
        // the Active pill. Everything else keeps the quiet standard outline.
        int fill = active ? UiKit.blend(UiKit.SURFACE, UiKit.accent(this), 0.93f) : UiKit.SURFACE;
        int stroke = active ? UiKit.accent(this) : UiKit.withAlpha(UiKit.accent(this), 38);
        card.setBackground(UiKit.outlined(fill, stroke, 22, this));
        card.setElevation(UiKit.dp(this, 2));

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        titleRow.addView(UiKit.text(this, provider.displayName(), 17, UiKit.TEXT, true),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        if (active) {
            titleRow.addView(pill(OrbitText.get(R.string.ui_active_923406, "Active"), UiKit.onAccent(this),
                    UiKit.rounded(UiKit.accent(this), 99, this)));
        } else if (status == AiProvider.Status.COMING_SOON) {
            titleRow.addView(pill(OrbitText.get(R.string.ui_experimental_3dc9f5, "Experimental"), UiKit.MUTED,
                    UiKit.outlined(UiKit.SURFACE_2, Color.rgb(53, 58, 72), 99, this)));
        }
        card.addView(titleRow);

        LinearLayout statusRow = new LinearLayout(this);
        statusRow.setGravity(Gravity.CENTER_VERTICAL);
        statusRow.setPadding(0, UiKit.dp(this, 7), 0, 0);
        View dot = new View(this);
        dot.setBackground(UiKit.rounded(statusColor(status), 99, this));
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(
                UiKit.dp(this, 8), UiKit.dp(this, 8));
        dotLp.rightMargin = UiKit.dp(this, 7);
        statusRow.addView(dot, dotLp);
        statusRow.addView(UiKit.text(this, provider.statusDetail(this), 13, UiKit.TEXT, false),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        card.addView(statusRow);

        TextView description = UiKit.text(this, provider.description(), 12.5f, UiKit.MUTED, false);
        description.setLineSpacing(0, 1.15f);
        description.setPadding(0, UiKit.dp(this, 8), 0, 0);
        card.addView(description);

        ChipFlow chips = new ChipFlow(this, UiKit.dp(this, 6), UiKit.dp(this, 6));
        for (String label : capabilityChips(provider.capabilities())) {
            chips.addView(chip(label));
        }
        LinearLayout.LayoutParams chipsLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        chipsLp.topMargin = UiKit.dp(this, 10);
        card.addView(chips, chipsLp);

        String limitation = capabilityLimitation(provider.capabilities());
        if (!limitation.isEmpty()) {
            TextView limits = UiKit.text(this, limitation, 11.5f,
                    UiKit.withAlpha(UiKit.MUTED, 210), false);
            limits.setPadding(0, UiKit.dp(this, 7), 0, 0);
            card.addView(limits);
        }

        addActions(card, provider, status, active);
        return card;
    }

    /**
     * Which actions a card offers, purely from state. READY earns "Use this provider" (plus
     * Manage); anything not ready offers only the step that would make it usable, so status and
     * actions can never contradict each other.
     */
    static List<String> actionLabels(AiProvider.Status status, boolean active) {
        List<String> out = new ArrayList<>();
        switch (status) {
            case READY:
                if (!active) out.add(OrbitText.get(R.string.ui_use_this_provider_67bf11, ACTION_USE));
                out.add(OrbitText.get(R.string.ui_manage_5a2344, ACTION_MANAGE));
                break;
            case NEEDS_SETUP:
            case NOT_INSTALLED:
            case COMING_SOON:
                out.add(OrbitText.get(R.string.ui_set_up_4da10f, ACTION_SET_UP));
                break;
            default: // UNSUPPORTED: nothing to enable, but the explanation stays reachable.
                out.add(OrbitText.get(R.string.ui_details_45989d, ACTION_DETAILS));
        }
        return out;
    }

    /**
     * The same, for one provider. OpenRouter replaces the generic Set up with its own two ways in:
     * the browser sign-in as the primary action, and a typed key as the quiet fallback.
     */
    static List<String> actionLabels(String providerId, AiProvider.Status status, boolean active) {
        if (Prefs.PROVIDER_OPENROUTER.equals(providerId) && status == AiProvider.Status.NEEDS_SETUP) {
            List<String> out = new ArrayList<>();
            out.add(OrbitText.get(R.string.ui_sign_in_with_openrouter_ec2200, ACTION_OPENROUTER_SIGN_IN));
            out.add(OrbitText.get(R.string.ui_use_api_key_instead_91c16a, ACTION_USE_API_KEY));
            return out;
        }
        return actionLabels(status, active);
    }

    private void addActions(LinearLayout card, AiProvider provider, AiProvider.Status status,
                            boolean active) {
        List<String> labels = actionLabels(provider.id(), status, active);
        boolean first = true;
        for (String label : labels) {
            boolean primary = OrbitText.get(R.string.ui_use_this_provider_67bf11, ACTION_USE).equals(label) || OrbitText.get(R.string.ui_sign_in_with_openrouter_ec2200, ACTION_OPENROUTER_SIGN_IN).equals(label)
                    || (OrbitText.get(R.string.ui_set_up_4da10f, ACTION_SET_UP).equals(label) && status != AiProvider.Status.COMING_SOON);
            View action;
            LinearLayout.LayoutParams lp;
            if (primary) {
                // The one state-progressing action is full width, in Orbit's stacked settings
                // button language; nothing is ever squeezed beside it.
                Button b = primaryButton(label);
                b.setOnClickListener(v -> runAction(provider, label));
                action = b;
                lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, UiKit.dp(this, 46));
            } else {
                // Secondary actions stay compact and right-aligned so cards without a primary
                // action do not gain a wall of full-width buttons.
                Button b = secondaryButton(label);
                b.setOnClickListener(v -> runAction(provider, label));
                LinearLayout row = new LinearLayout(this);
                row.setGravity(Gravity.END);
                row.addView(b, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, UiKit.dp(this, 40)));
                action = row;
                lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            }
            lp.topMargin = UiKit.dp(this, first ? 13 : 8);
            first = false;
            card.addView(action, lp);
        }
    }

    private void runAction(AiProvider provider, String label) {
        if (OrbitText.get(R.string.ui_use_this_provider_67bf11, ACTION_USE).equals(label)) {
            if (AiProviders.select(this, provider.id())) {
                Toast.makeText(this, provider.displayName() + OrbitText.get(R.string.ui_is_now_orbit_s_active_provider_2612e2, " is now Orbit's active provider"),
                        Toast.LENGTH_SHORT).show();
                refreshCards();
            }
            return;
        }
        if (OrbitText.get(R.string.ui_sign_in_with_openrouter_ec2200, ACTION_OPENROUTER_SIGN_IN).equals(label)) {
            startOpenRouterSignIn();
            return;
        }
        if (OrbitText.get(R.string.ui_use_api_key_instead_91c16a, ACTION_USE_API_KEY).equals(label)) {
            showOpenRouterKeyEntry();
            return;
        }
        manage(provider);
    }

    private TextView pill(String text, int textColor, android.graphics.drawable.Drawable background) {
        TextView pill = UiKit.text(this, text, 11, textColor, true);
        pill.setBackground(background);
        pill.setPadding(UiKit.dp(this, 10), UiKit.dp(this, 4), UiKit.dp(this, 10), UiKit.dp(this, 4));
        return pill;
    }

    private TextView chip(String label) {
        TextView chip = UiKit.text(this, label, 11, UiKit.withAlpha(UiKit.TEXT, 205), false);
        chip.setBackground(UiKit.outlined(UiKit.SURFACE_2,
                UiKit.withAlpha(UiKit.accent(this), 46), 99, this));
        chip.setPadding(UiKit.dp(this, 9), UiKit.dp(this, 4), UiKit.dp(this, 9), UiKit.dp(this, 4));
        chip.setSingleLine(true);
        return chip;
    }

    private int statusColor(AiProvider.Status status) {
        switch (status) {
            case READY: return UiKit.SUCCESS;
            case COMING_SOON: return UiKit.MUTED;
            case UNSUPPORTED: return UiKit.DANGER;
            default: return Color.rgb(240, 193, 100);
        }
    }

    /** Compact positive capabilities, in scanning order. */
    static List<String> capabilityChips(AiCapabilities caps) {
        List<String> chips = new ArrayList<>();
        if (caps.streaming) chips.add(OrbitText.get(R.string.ui_live_replies_b313ab, "Live replies"));
        if (caps.deviceActions) chips.add(OrbitText.get(R.string.ui_device_actions_4b0730, "Device actions"));
        if (caps.images) chips.add(OrbitText.get(R.string.ui_screens_images_09ba03, "Screens & images"));
        if (caps.hostedWebSearch) chips.add(OrbitText.get(R.string.ui_web_search_7711fa, "Web search"));
        if (caps.richWebMedia) chips.add(OrbitText.get(R.string.ui_sourced_images_a5de69, "Sourced images"));
        if (caps.offline) chips.add(OrbitText.get(R.string.ui_works_offline_099187, "Works offline"));
        if (!caps.needsCredentials) chips.add(OrbitText.get(R.string.ui_no_account_79eb5c, "No account"));
        return chips;
    }

    /** One honest sentence about what the provider cannot do yet, or empty. */
    static String capabilityLimitation(AiCapabilities caps) {
        if (caps.deviceActions) return "";
        return caps.images
                ? OrbitText.get(R.string.ui_can_t_run_device_actions_yet_7fdac2, "Can't run device actions yet")
                : OrbitText.get(R.string.ui_can_t_run_device_actions_or_read_screens_a_665d94, "Can't run device actions or read screens and images yet");
    }

    private void manage(AiProvider provider) {
        String id = provider.id();
        if (Prefs.PROVIDER_LOCAL.equals(id)) {
            startActivity(new Intent(this, LocalAiActivity.class));
            return;
        }
        if (Prefs.PROVIDER_OPENROUTER.equals(id)) {
            if (SecureStore.hasOpenRouterKey(this)) showOpenRouterManage();
            else startOpenRouterSignIn();
            return;
        }
        if (Prefs.PROVIDER_ANTHROPIC.equals(id) || Prefs.PROVIDER_XAI.equals(id)) {
            showApiKeySetup(id);
            return;
        }
        // ChatGPT sign-in and relay configuration keep living in Settings > AI & account, which
        // already owns those flows.
        Intent intent = new Intent(this, SettingsActivity.class);
        intent.putExtra(SettingsActivity.EXTRA_SECTION, SettingsActivity.SECTION_AI);
        startActivity(intent);
    }

    // ---- OpenRouter (0.8.3.0-beta.6+) ------------------------------------------------------------

    /**
     * Sign in with OpenRouter: opens OpenRouter's own page in the browser. The result arrives
     * through {@link #openRouterListener}; a failure explains itself and offers the key fallback,
     * and Orbit never switches methods without the user choosing to.
     */
    private void startOpenRouterSignIn() {
        OpenRouterAuth.Started started = OpenRouterAuth.start(this, openRouterListener);
        if (!started.ok()) {
            showOpenRouterSignInFailure(started.error);
            return;
        }
        ChatGptSignInReturnActivity.returnTo = AiProvidersActivity.class;
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(started.authorizeUrl))
                    .addCategory(Intent.CATEGORY_BROWSABLE));
        } catch (Exception e) {
            OpenRouterAuth.cancel();
            showOpenRouterSignInFailure(OrbitText.get(R.string.ui_orbit_could_not_open_a_web_browser_on_this_3ed952, "Orbit could not open a web browser on this phone."));
        }
    }

    private void showOpenRouterSignInFailure(String message) {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(OrbitText.get(R.string.ui_openrouter_was_not_connected_56d044, "OpenRouter was not connected"))
                .setMessage(message)
                .setPositiveButton(OrbitText.get(R.string.ui_use_api_key_instead_91c16a, ACTION_USE_API_KEY), (d, w) -> showOpenRouterKeyEntry())
                .setNegativeButton(OrbitText.get(R.string.ui_close_7d9eb7, "Close"), null)
                .create();
        UiKit.styleOrbitDialog(dialog, this, false);
        dialog.show();
    }

    /** Refreshes OpenRouter's model list; a rejected credential is reported, never kept quietly. */
    private void refreshOpenRouterCatalog(boolean announce) {
        ProviderCatalogRepository.refreshAsync(this, Prefs.PROVIDER_OPENROUTER, (changed, error) ->
                runOnUiThread(() -> {
                    if (announce || error.contains("rejected")) {
                        Toast.makeText(this, error.isEmpty() ? OrbitText.get(R.string.ui_openrouter_models_are_up_to_date_ffec21, "OpenRouter models are up to date")
                                : error, error.isEmpty() ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG).show();
                    }
                    refreshCards();
                }));
    }

    /**
     * The connected OpenRouter card's Manage: how it is connected, and the actions that make sense
     * now. The saved key is never shown, in whole or in part.
     */
    private void showOpenRouterManage() {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        int pad = UiKit.dp(this, 22);
        wrap.setPadding(pad, UiKit.dp(this, 8), pad, 0);
        String status = AiProviders.byId(Prefs.PROVIDER_OPENROUTER).statusDetail(this);
        TextView note = UiKit.text(this, status + OrbitText.get(R.string.ui_usage_is_billed_to_your_openrouter_account_880b2f, ". Usage is billed to your OpenRouter account. ")
                + OrbitText.get(R.string.ui_auto_uses_openrouter_only_if_you_switch_it_d40dbc, "Auto uses OpenRouter only if you switch it on in Settings > Intelligence > Auto."),
                13, UiKit.MUTED, false);
        note.setLineSpacing(0, 1.14f);
        wrap.addView(note);
        AlertDialog[] holder = new AlertDialog[1];
        addSheetButton(wrap, OrbitText.get(R.string.ui_check_connection_be5dff, "Check connection"), false, () -> {
            Toast.makeText(this, OrbitText.get(R.string.ui_checking_openrouter_connection_76a9f3, "Checking OpenRouter connection…"), Toast.LENGTH_SHORT).show();
            ProviderCatalogRepository.checkOpenRouterAsync(this, (changed, error) ->
                    runOnUiThread(() -> {
                        Toast.makeText(this, error.isEmpty() ? OrbitText.get(R.string.ui_openrouter_is_connected_85f922, "OpenRouter is connected") : error,
                                error.isEmpty() ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG).show();
                        if (error.isEmpty()) refreshOpenRouterCatalog(false);
                    }));
        }, holder);
        addSheetButton(wrap, OrbitText.get(R.string.ui_refresh_models_049030, "Refresh models"), false, () -> refreshOpenRouterCatalog(true), holder);
        addSheetButton(wrap, OrbitText.get(R.string.ui_reconnect_with_openrouter_6e9bfb, "Reconnect with OpenRouter"), false, this::startOpenRouterSignIn, holder);
        addSheetButton(wrap, SecureStore.OPENROUTER_SOURCE_MANUAL.equals(
                SecureStore.openRouterKeySource(this)) ? OrbitText.get(R.string.ui_replace_api_key_f97925, "Replace API key") : OrbitText.get(R.string.ui_use_api_key_instead_91c16a, ACTION_USE_API_KEY),
                false, this::showOpenRouterKeyEntry, holder);
        addSheetButton(wrap, OrbitText.get(R.string.ui_disconnect_acfc5b, "Disconnect"), true, this::confirmOpenRouterDisconnect, holder);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("OpenRouter")
                .setView(wrap)
                .setNegativeButton(OrbitText.get(R.string.ui_close_7d9eb7, "Close"), null)
                .create();
        holder[0] = dialog;
        UiKit.styleOrbitDialog(dialog, this, false);
        dialog.show();
    }

    private void addSheetButton(LinearLayout wrap, String label, boolean destructive,
                                Runnable action, AlertDialog[] holder) {
        Button b = secondaryButton(label);
        if (destructive) b.setTextColor(UiKit.DANGER);
        b.setOnClickListener(v -> {
            if (holder[0] != null) holder[0].dismiss();
            action.run();
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, UiKit.dp(this, 44));
        lp.topMargin = UiKit.dp(this, 8);
        wrap.addView(b, lp);
    }

    /** Disconnect removes the key and Auto's permission; chats and their history stay. */
    private void confirmOpenRouterDisconnect() {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(OrbitText.get(R.string.ui_disconnect_openrouter_068f16, "Disconnect OpenRouter?"))
                .setMessage(OrbitText.get(R.string.ui_orbit_removes_its_openrouter_key_from_this_487e65, "Orbit removes its OpenRouter key from this phone and stops using ")
                        + OrbitText.get(R.string.ui_openrouter_including_in_auto_your_chats_an_09847f, "OpenRouter, including in Auto. Your chats and their history stay. To ")
                        + OrbitText.get(R.string.ui_revoke_the_key_itself_delete_it_in_your_op_22426a, "revoke the key itself, delete it in your OpenRouter account settings."))
                .setNegativeButton(OrbitText.get(R.string.ui_cancel_19766e, "Cancel"), null)
                .setPositiveButton(OrbitText.get(R.string.ui_disconnect_acfc5b, "Disconnect"), (d, w) -> {
                    OpenRouterAuth.cancel();
                    SecureStore.clearOpenRouterKey(this);
                    Toast.makeText(this, OrbitText.get(R.string.ui_openrouter_disconnected_619cb1, "OpenRouter disconnected"), Toast.LENGTH_SHORT).show();
                    refreshCards();
                })
                .create();
        UiKit.styleOrbitDialog(dialog, this, true);
        dialog.show();
    }

    /**
     * Use API key instead: the advanced fallback, and how keys saved before this release arrived.
     * A key OpenRouter explicitly rejects is not kept as a misleading connection; a network outage
     * keeps it so the user can retry without entering it again.
     */
    private void showOpenRouterKeyEntry() {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        int pad = UiKit.dp(this, 22);
        wrap.setPadding(pad, UiKit.dp(this, 8), pad, 0);
        TextView note = UiKit.text(this,
                OrbitText.get(R.string.ui_paste_an_api_key_created_in_your_openroute_fc7230, "Paste an API key created in your OpenRouter account. Orbit encrypts it with Android Keystore, never backs it up, and never shows it again. Signing in with OpenRouter does this for you."),
                13, UiKit.MUTED, false);
        note.setLineSpacing(0, 1.14f);
        wrap.addView(note);

        EditText input = new EditText(this);
        input.setHint(SecureStore.hasOpenRouterKey(this) ? OrbitText.get(R.string.ui_key_saved_enter_a_new_key_to_replace_it_1b9dd2, "Key saved · enter a new key to replace it") : "sk-or-…");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setTextColor(UiKit.TEXT);
        input.setHintTextColor(UiKit.MUTED);
        input.setBackgroundTintList(ColorStateList.valueOf(UiKit.accent(this)));
        LinearLayout.LayoutParams inputLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        inputLp.topMargin = UiKit.dp(this, 10);
        wrap.addView(input, inputLp);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(OrbitText.get(R.string.ui_openrouter_api_key_89616b, "OpenRouter API key"))
                .setView(wrap)
                .setNegativeButton(OrbitText.get(R.string.ui_cancel_19766e, "Cancel"), null)
                .setPositiveButton(OrbitText.get(R.string.ui_save_and_check_0a9315, "Save and check"), (d, w) -> {
                    String value = input.getText().toString().trim();
                    if (value.isEmpty()) return;
                    if (!SecureStore.saveOpenRouterKey(this, value,
                            SecureStore.OPENROUTER_SOURCE_MANUAL)) {
                        Toast.makeText(this, OrbitText.get(R.string.ui_could_not_store_the_key_securely_so_it_was_3eb68b, "Could not store the key securely, so it was not saved"),
                                Toast.LENGTH_LONG).show();
                        return;
                    }
                    Toast.makeText(this, OrbitText.get(R.string.ui_checking_openrouter_connection_76a9f3, "Checking OpenRouter connection…"), Toast.LENGTH_SHORT).show();
                    ProviderCatalogRepository.checkOpenRouterAsync(this, (changed, error) ->
                            runOnUiThread(() -> {
                                if (ProviderCatalogRepository.OPENROUTER_REJECTED.equals(error)) {
                                    SecureStore.clearOpenRouterKey(this);
                                    Toast.makeText(this, OrbitText.get(R.string.ui_openrouter_rejected_that_key_so_it_was_not_88ef8f, "OpenRouter rejected that key, so it was not saved."),
                                            Toast.LENGTH_LONG).show();
                                } else {
                                    Toast.makeText(this, error.isEmpty() ? OrbitText.get(R.string.ui_openrouter_connected_2d8899, "OpenRouter connected") : error,
                                            error.isEmpty() ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG).show();
                                    refreshOpenRouterCatalog(false);
                                }
                                refreshCards();
                            }));
                })
                .create();
        UiKit.styleOrbitDialog(dialog, this, false);
        dialog.show();
    }

    private void showApiKeySetup(String provider) {
        boolean anthropic = Prefs.PROVIDER_ANTHROPIC.equals(provider);
        String name = anthropic ? "Anthropic" : "xAI";
        boolean saved = anthropic ? SecureStore.hasAnthropicKey(this) : SecureStore.hasXaiKey(this);
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        int pad = UiKit.dp(this, 22);
        wrap.setPadding(pad, UiKit.dp(this, 8), pad, 0);
        TextView note = UiKit.text(this,
                OrbitText.get(R.string.ui_enter_a_developer_api_key_from_74bc51, "Enter a developer API key from ") + name + OrbitText.get(R.string.ui_orbit_encrypts_it_with_android_keystore_ne_f4a47b, ". Orbit encrypts it with Android Keystore, never backs it up, and never shows the saved key again."),
                13, UiKit.MUTED, false);
        note.setLineSpacing(0, 1.14f);
        wrap.addView(note);

        EditText input = new EditText(this);
        input.setHint(saved ? OrbitText.get(R.string.ui_key_saved_enter_a_new_key_to_replace_it_1b9dd2, "Key saved · enter a new key to replace it") : OrbitText.get(R.string.ui_api_key_16f0ee, "API key"));
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setTextColor(UiKit.TEXT);
        input.setHintTextColor(UiKit.MUTED);
        input.setBackgroundTintList(ColorStateList.valueOf(UiKit.accent(this)));
        LinearLayout.LayoutParams inputLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        inputLp.topMargin = UiKit.dp(this, 10);
        wrap.addView(input, inputLp);

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(name + OrbitText.get(R.string.ui_connection_a0ac96, " connection"))
                .setView(wrap)
                .setNegativeButton(OrbitText.get(R.string.ui_cancel_19766e, "Cancel"), null)
                .setPositiveButton(OrbitText.get(R.string.ui_save_and_check_0a9315, "Save and check"), (d, w) -> {
                    String value = input.getText().toString().trim();
                    if (value.isEmpty()) return;
                    boolean stored = anthropic ? SecureStore.saveAnthropicKey(this, value)
                            : SecureStore.saveXaiKey(this, value);
                    if (!stored) {
                        Toast.makeText(this, OrbitText.get(R.string.ui_could_not_store_the_key_securely_so_it_was_3eb68b, "Could not store the key securely, so it was not saved"),
                                Toast.LENGTH_LONG).show();
                        return;
                    }
                    Toast.makeText(this, OrbitText.get(R.string.ui_checking_cd1263, "Checking ") + name + OrbitText.get(R.string.ui_connection_9d8942, " connection…"), Toast.LENGTH_SHORT).show();
                    ProviderCatalogRepository.refreshAsync(this, provider, (changed, error) ->
                            runOnUiThread(() -> {
                                // A credential the provider explicitly rejected is not kept as a
                                // misleading "ready" connection. A network/provider outage is
                                // different: keep the encrypted key and cached catalog so the user
                                // can retry without re-entering a secret.
                                if (error.contains("saved API key was rejected")) {
                                    if (anthropic) SecureStore.clearAnthropicKey(this);
                                    else SecureStore.clearXaiKey(this);
                                }
                                Toast.makeText(this, error.isEmpty()
                                                ? name + OrbitText.get(R.string.ui_connected_6ab2ba, " connected") : error,
                                        error.isEmpty() ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG).show();
                                refreshCards();
                            }));
                });
        if (saved) builder.setNeutralButton(OrbitText.get(R.string.ui_remove_key_81c45f, "Remove key"), (d, w) -> {
            if (anthropic) SecureStore.clearAnthropicKey(this); else SecureStore.clearXaiKey(this);
            Toast.makeText(this, name + OrbitText.get(R.string.ui_key_removed_f762b3, " key removed"), Toast.LENGTH_SHORT).show();
            refreshCards();
        });
        AlertDialog dialog = builder.create();
        UiKit.styleOrbitDialog(dialog, this, false);
        dialog.show();
    }

    private Button primaryButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(UiKit.onAccent(this));
        b.setTextSize(14);
        b.setAllCaps(false);
        b.setSingleLine(true);
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
        b.setSingleLine(true);
        b.setPadding(UiKit.dp(this, 18), 0, UiKit.dp(this, 18), 0);
        b.setBackground(UiKit.rippleOutlined(UiKit.SURFACE_2, Color.rgb(53, 58, 72), UiKit.accent(this), 15, this));
        b.setMinHeight(0); b.setMinimumHeight(0); b.setStateListAnimator(null);
        UiKit.pressScale(b);
        return b;
    }

    /**
     * A minimal wrapping row for capability chips: children flow left to right and wrap to new
     * lines when the card runs out of width, so no chip is ever clipped or squeezed at any
     * screen width or font scale.
     */
    private static final class ChipFlow extends ViewGroup {
        private final int hGap;
        private final int vGap;

        ChipFlow(Context context, int hGapPx, int vGapPx) {
            super(context);
            this.hGap = hGapPx;
            this.vGap = vGapPx;
        }

        @Override protected void onMeasure(int widthSpec, int heightSpec) {
            int width = MeasureSpec.getSize(widthSpec);
            int x = 0, y = 0, rowHeight = 0;
            boolean any = false;
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                if (child.getVisibility() == GONE) continue;
                any = true;
                child.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST),
                        MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
                int cw = child.getMeasuredWidth();
                int ch = child.getMeasuredHeight();
                if (x > 0 && x + cw > width) {
                    x = 0;
                    y += rowHeight + vGap;
                    rowHeight = 0;
                }
                x += cw + hGap;
                rowHeight = Math.max(rowHeight, ch);
            }
            setMeasuredDimension(width, any ? y + rowHeight : 0);
        }

        @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
            int width = r - l;
            int x = 0, y = 0, rowHeight = 0;
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                if (child.getVisibility() == GONE) continue;
                int cw = child.getMeasuredWidth();
                int ch = child.getMeasuredHeight();
                if (x > 0 && x + cw > width) {
                    x = 0;
                    y += rowHeight + vGap;
                    rowHeight = 0;
                }
                child.layout(x, y, x + cw, y + ch);
                x += cw + hGap;
                rowHeight = Math.max(rowHeight, ch);
            }
        }
    }
}
