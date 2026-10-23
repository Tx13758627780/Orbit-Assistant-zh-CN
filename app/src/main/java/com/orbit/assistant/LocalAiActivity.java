package com.orbit.assistant;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
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
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.List;

/**
 * Orbit Local: the on-device AI management screen, and the only one there is.
 *
 * <p>The optional component is a separate package, but it deliberately has no UI of its own —
 * no launcher icon, no settings, no onboarding. Everything about Orbit Local is managed from
 * here, so from a user's point of view there is still just Orbit with a feature that can be
 * installed and removed.
 *
 * <p>Four cards, in the order the user needs them: what this device can do, the component that
 * runs the AI, the model it runs, and what all of it is costing in storage. Nothing here touches
 * the main thread with file, network, or cross-process work.
 */
public final class LocalAiActivity extends Activity {
    /**
     * Remembers which removal is waiting on Android's answer, if either is.
     *
     * <p>Persisted rather than held in a field because Android's uninstall confirmation can take
     * this Activity through a full destroy and recreate. A field would come back empty and the
     * Orbit-side cleanup would simply never happen.
     *
     * <p>Holds {@link #SCOPE_EVERYTHING}, {@link #SCOPE_COMPONENT}, or nothing at all. The two
     * scopes are genuinely different requests and are not allowed to borrow each other's cleanup:
     * uninstalling the component must not quietly delete a model an older Orbit still owns.
     */
    private static final String KEY_REMOVAL_PENDING = "orbit_local_removal_pending";
    /** Everything Orbit Local uses: component, its model, legacy model data, installer cache. */
    private static final String SCOPE_EVERYTHING = "everything";
    /** Only the component and the model Android takes with it. Legacy Orbit data is left alone. */
    private static final String SCOPE_COMPONENT = "component";

    /**
     * What the Google Play edition says where it would otherwise offer to install the component.
     *
     * <p>The component is a separate APK that Orbit from GitHub installs from its own release. An
     * app distributed through Google Play may not install executable code from anywhere else, so
     * until Orbit Local has a Play-delivered form the Play edition does not offer it. The Play
     * edition is also signed with a different key from the GitHub edition, so even a genuine
     * component that is already installed cannot serve it; it can only be removed.
     */
    static final String PLAY_UNAVAILABLE =
            "Orbit Local isn't available in the Google Play edition of Orbit yet.";

    private final Handler main = new Handler(Looper.getMainLooper());
    private LinearLayout cards;
    private String appearanceSignature;

    /** Last status read from the component, or null when it cannot be reached. */
    private OrbitLocalStatus status;
    private boolean statusChecked;
    private boolean installing;
    private int installPercent = -1;
    private String installMessage = "";
    private File readyComponentApk;
    private boolean waitingForInstallPermission;
    /** What the last removal attempt came to, shown once and never invented. */
    private String removalMessage = "";

    /**
     * The live view of the component while this screen is visible.
     *
     * <p>Started in onResume and stopped in onPause, so it never runs behind another screen and
     * never survives this one. Each reading is kept, and the cards are redrawn whenever anything
     * they show changed - either model's state, bytes, errors, pause flag or storage - or while
     * Orbit is downloading the component APK itself, whose percentage is Orbit's own to draw.
     */
    /** Package-visible so tests can observe the loop. */
    final OrbitLocalStatusPoller poller = new OrbitLocalStatusPoller(main,
            answer -> {
                if (!OrbitLocalComponent.isUsable(this)) {
                    // Nothing to ask across a process boundary: the component is absent.
                    answer.accept(null);
                    return;
                }
                OrbitLocalClient.statusAsync(this, answer::accept);
            },
            (fresh, changed) -> {
                if (isFinishing() || isDestroyed()) return;
                boolean firstCheck = !statusChecked;
                statusChecked = true;
                status = fresh;
                if (firstCheck || changed || installing) rebuild();
            },
            () -> installing);

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
        if (!UiKit.appearanceSignature(this).equals(appearanceSignature)) {
            recreate();
            return;
        }
        // Coming back from Android's installer or uninstaller: what actually happened is
        // whatever the package manager now reports, never what Orbit asked for.
        reconcileComponentRemoval();
        reconcileComponentInstall();
        reconcileProviderSelection();
        if (waitingForInstallPermission && readyComponentApk != null
                && OrbitUpdater.canRequestPackageInstalls(this)) {
            waitingForInstallPermission = false;
            launchComponentInstaller(readyComponentApk);
        }
        rebuild();
        // An immediate read, then live updates for as long as the page stays visible.
        poller.start();
    }

    /**
     * Settles a "Remove Orbit Local" that was waiting on Android's own uninstall confirmation.
     *
     * <p>The ordering v0.7.7.5 got wrong. It deleted the model, the legacy copy, and the installer
     * cache <em>before</em> asking Android to remove the package — so cancelling Android's
     * confirmation left a component installed with its 1.6 GB model already thrown away, and Orbit
     * had no way to tell that from success.
     *
     * <p>Now nothing is deleted until the package manager says the component is genuinely gone.
     * The observed package is the whole decision; the status broadcast only supplies the sentence
     * shown to the user. A cancelled removal leaves the component, the model, the legacy data, and
     * the provider selection exactly as they were.
     */
    /** What resuming after an uninstall request must do. Nothing else decides this. */
    enum RemovalOutcome {
        /** No removal was in flight. Nothing to settle. */
        NOTHING_PENDING,
        /** The package is still there, so nothing of the user's may be touched. */
        CANCELLED,
        /** The component went. Orbit's own legacy model data was not part of the request. */
        REMOVED_COMPONENT,
        /** The component went, and everything Orbit still holds for Orbit Local goes with it. */
        REMOVED_EVERYTHING
    }

    /**
     * The removal decision, on plain values.
     *
     * <p>Separated out because it is the exact judgement v0.7.7.5 never made: it deleted first and
     * asked Android second, so a cancelled uninstall was indistinguishable from a successful one
     * and the model was already gone either way. A package that is still installed can only ever
     * produce {@link RemovalOutcome#CANCELLED}, and that outcome deletes nothing.
     */
    static RemovalOutcome removalOutcome(String pendingScope, boolean componentStillInstalled) {
        if (pendingScope == null || pendingScope.isEmpty()) return RemovalOutcome.NOTHING_PENDING;
        if (componentStillInstalled) return RemovalOutcome.CANCELLED;
        return SCOPE_COMPONENT.equals(pendingScope)
                ? RemovalOutcome.REMOVED_COMPONENT : RemovalOutcome.REMOVED_EVERYTHING;
    }

    private void reconcileComponentRemoval() {
        String scope = pendingRemovalScope(this);
        RemovalOutcome outcome = removalOutcome(scope, OrbitLocalComponent.isInstalled(this));
        if (outcome == RemovalOutcome.NOTHING_PENDING) return;
        clearRemovalPending();
        if (outcome == RemovalOutcome.CANCELLED) {
            // Still here. Either the user backed out, or Android refused — and in both cases the
            // honest thing is to change nothing and say so.
            String reported = OrbitLocalUninstaller.message(lastRemovalStage(this));
            removalMessage = reported.isEmpty()
                    ? OrbitText.get(R.string.ui_orbit_local_was_not_removed_nothing_was_de_dea747, "Orbit Local was not removed. Nothing was deleted.") : reported;
            return;
        }
        // Confirmed absent. Android took the component and, with it, the model that lived in the
        // component's own private storage — so what is left is only what belongs to Orbit.
        finishOrbitSideRemoval(scope);
        removalMessage = outcome == RemovalOutcome.REMOVED_COMPONENT
                ? OrbitText.get(R.string.ui_the_orbit_local_component_was_uninstalled_d76707, "The Orbit Local component was uninstalled.")
                : OrbitText.get(R.string.ui_orbit_local_was_removed_063343, "Orbit Local was removed.");
        // A complete removal takes the card that message would have been shown on with it, so the
        // confirmation is said out loud instead of being written somewhere that no longer exists.
        if (!hasAnythingToRemove()) {
            Toast.makeText(this, removalMessage, Toast.LENGTH_LONG).show();
        }
    }

    /**
     * The Orbit-side remainder, run only after the component is confirmed gone.
     *
     * <p>Deliberately small, because Android took the large part with it. What is left is the
     * cached component installer, a provider selection that can no longer answer, and — for the
     * full removal only — a model left behind by an Orbit from before Orbit Local was modular.
     * An "Uninstall component" must not reach that far: nobody asked it to.
     */
    private void finishOrbitSideRemoval(String scope) {
        if (SCOPE_EVERYTHING.equals(scope)) LocalModelStore.deleteLegacy(this);
        OrbitLocalInstaller.cleanup(this);
        OrbitLocalClient.disconnect(this);
        OrbitLocalProvider.invalidateStatus();
        status = null;
        if (Prefs.PROVIDER_LOCAL.equals(Prefs.provider(this))) {
            AiProviders.select(this, Prefs.PROVIDER_CHATGPT);
        }
    }

    /** Which removal, if any, is waiting on Android. Package-visible so tests can drive it. */
    static String pendingRemovalScope(android.content.Context context) {
        return Prefs.get(context).getString(KEY_REMOVAL_PENDING, "");
    }

    static OrbitLocalUninstaller.Stage lastRemovalStage(android.content.Context context) {
        try {
            return OrbitLocalUninstaller.Stage.valueOf(
                    DiagnosticStore.lastComponentUninstallStage(context));
        } catch (Exception e) {
            return OrbitLocalUninstaller.Stage.CANCELLED;
        }
    }

    /**
     * Written synchronously, because Android's uninstaller is opened in the very next breath.
     *
     * <p>An asynchronous write that lost its race with the process going away would leave a
     * component removed and Orbit's own side of the cleanup never run.
     */
    private void setRemovalPending(String scope) {
        Prefs.get(this).edit().putString(KEY_REMOVAL_PENDING, scope).commit();
    }

    private void clearRemovalPending() {
        Prefs.get(this).edit().putString(KEY_REMOVAL_PENDING, "").commit();
    }

    /**
     * Re-reads the component from the package manager, and settles everything that follows from it.
     *
     * <p>This is the fix for the v0.7.7.5-beta.2 device report where Android said "Install success"
     * and Orbit still said "Not installed". The manifest change is what lets the query see the
     * package at all; this is what makes Orbit ask again at the one moment it matters, instead of
     * carrying forward whatever it believed before the installer opened.
     *
     * <p>Deliberately keyed on the observed package, not on a remembered "install requested" flag.
     * A cancelled install and a successful one are indistinguishable from Orbit's side except by
     * asking Android, so Orbit asks: the cancelled case simply reports the component still missing
     * and keeps the verified download for Retry, and the successful case is the only thing that
     * ever clears the installer cache.
     */
    private void reconcileComponentInstall() {
        OrbitLocalProvider.invalidateStatus();
        if (installing) return; // A download owns the cache directory until it finishes.

        OrbitLocalComponent.State state = OrbitLocalComponent.state(this);
        if (state == OrbitLocalComponent.State.INSTALLED) {
            // Confirmed by the package manager, so the downloaded APK is now a duplicate of a
            // package Android is storing itself, and the pending install is genuinely over.
            waitingForInstallPermission = false;
            readyComponentApk = null;
            installMessage = "";
            OrbitLocalInstaller.cleanupAfterInstall(this);
            return;
        }
        // Still not usable: drop any stale binding to a component that changed underneath Orbit,
        // and clear out installers that can no longer be handed to Android — old Beta APKs and
        // interrupted downloads — while keeping this version's verified one for Retry.
        OrbitLocalClient.disconnect(this);
        OrbitLocalInstaller.prune(this, readyComponentApk);
    }

    /**
     * Leaving this screen stops watching the download. It never stops the download.
     *
     * <p>The work belongs to the component's own WorkManager job in its own process, so locking
     * the phone, opening another app, or destroying this Activity has no bearing on it. All that
     * ends here is Orbit looking.
     */
    @Override protected void onPause() {
        poller.stop();
        UiPresence.leave(this);
        super.onPause();
    }

    /**
     * Orbit Local must never sit there claiming to be active when it cannot answer.
     *
     * <p>The component can be uninstalled from Android Settings at any moment, entirely outside
     * Orbit, so its absence is reconciled whenever this screen is shown rather than assumed.
     */
    private void reconcileProviderSelection() {
        if (!Prefs.PROVIDER_LOCAL.equals(Prefs.provider(this))) return;
        if (AiProviders.byId(Prefs.PROVIDER_LOCAL).selectable(this)) return;
        AiProviders.select(this, Prefs.PROVIDER_CHATGPT);
    }

    // ---- layout -----------------------------------------------------------------------------------

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
        back.setContentDescription(OrbitText.get(R.string.ui_back_to_ai_providers_d858df, "Back to AI Providers"));
        back.setPadding(UiKit.dp(this, 10), UiKit.dp(this, 10),
                UiKit.dp(this, 10), UiKit.dp(this, 10));
        back.setOnClickListener(v -> navigation.performBack());
        UiKit.pressScale(back);
        header.addView(back, new LinearLayout.LayoutParams(UiKit.dp(this, 44), UiKit.dp(this, 44)));

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setPadding(UiKit.dp(this, 13), 0, 0, 0);
        titles.addView(UiKit.text(this, OrbitText.get(R.string.ui_orbit_local_bafd6d, "Orbit Local"), 24, UiKit.TEXT, true));
        titles.addView(UiKit.text(this, OrbitText.get(R.string.ui_private_ai_on_your_phone_3a61c9, "Private AI on your phone"), 13, UiKit.MUTED, false));
        header.addView(titles, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        page.addView(header);

        TextView intro = UiKit.text(this,
                OrbitText.get(R.string.ui_orbit_local_answers_on_the_phone_itself_co_79630e, "Orbit Local answers on the phone itself. Conversations never leave the device, and chat keeps working with no internet at all. It is optional: nothing is installed until you ask for it, and everything can be removed again."),
                13, UiKit.MUTED, false);
        intro.setLineSpacing(0, 1.14f);
        LinearLayout.LayoutParams introLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        introLp.setMargins(UiKit.dp(this, 3), UiKit.dp(this, 18), UiKit.dp(this, 3), UiKit.dp(this, 4));
        page.addView(intro, introLp);

        cards = new LinearLayout(this);
        cards.setOrientation(LinearLayout.VERTICAL);
        page.addView(cards, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        rebuild();

        UiKit.applyTypography(page);
        return scroll;
    }

    private void rebuild() {
        if (cards == null) return;
        cards.removeAllViews();
        cards.addView(deviceCard(), cardLp());
        cards.addView(componentCard(), cardLp());
        // Only once there is a trusted component to describe; before that every line would be
        // "not yet", which the component card already says in one sentence.
        if (OrbitLocalComponent.isUsable(this)) cards.addView(capabilityCard(), cardLp());
        cards.addView(modelCard(), cardLp());
        cards.addView(actionModelCard(), cardLp());
        cards.addView(storageCard(), cardLp());
        if (hasAnythingToRemove()) cards.addView(removeCard(), cardLp());
        UiKit.applyTypography(cards);
    }

    private LinearLayout.LayoutParams cardLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, UiKit.dp(this, 10), 0, 0);
        return lp;
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(UiKit.dp(this, 17), UiKit.dp(this, 15), UiKit.dp(this, 17), UiKit.dp(this, 15));
        card.setBackground(UiKit.outlined(UiKit.SURFACE,
                UiKit.withAlpha(UiKit.accent(this), 38), 22, this));
        card.setElevation(UiKit.dp(this, 2));
        return card;
    }

    private TextView pill(String label, boolean accent) {
        TextView pill = UiKit.text(this, label, 11,
                accent ? UiKit.onAccent(this) : UiKit.TEXT, true);
        pill.setBackground(accent
                ? UiKit.rounded(UiKit.accent(this), 99, this)
                : UiKit.outlined(UiKit.SURFACE_2, Color.rgb(53, 58, 72), 99, this));
        pill.setPadding(UiKit.dp(this, 10), UiKit.dp(this, 4), UiKit.dp(this, 10), UiKit.dp(this, 4));
        return pill;
    }

    private LinearLayout titleRow(String title, String pillLabel, boolean accentPill) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, UiKit.dp(this, 7), 0, 0);
        row.addView(UiKit.text(this, title, 17, UiKit.TEXT, true),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        row.addView(pill(pillLabel, accentPill));
        return row;
    }

    // ---- this device ------------------------------------------------------------------------------

    private View deviceCard() {
        DeviceCapabilityCheck.Assessment assessment = DeviceCapabilityCheck.assess(this);
        LinearLayout card = card();
        card.addView(UiKit.text(this, OrbitText.get(R.string.ui_this_device_d05257, "This device"), 12, UiKit.MUTED, true));

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, UiKit.dp(this, 7), 0, 0);
        View dot = new View(this);
        dot.setBackground(UiKit.rounded(tierColor(assessment.tier), 99, this));
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(
                UiKit.dp(this, 9), UiKit.dp(this, 9));
        dotLp.rightMargin = UiKit.dp(this, 8);
        row.addView(dot, dotLp);
        row.addView(UiKit.text(this, assessment.tierLabel(), 16, UiKit.TEXT, true));
        card.addView(row);

        TextView summary = UiKit.text(this, assessment.summary, 13, UiKit.MUTED, false);
        summary.setLineSpacing(0, 1.13f);
        summary.setPadding(0, UiKit.dp(this, 6), 0, 0);
        card.addView(summary);

        long free = LocalModelStore.freeStorageBytes(this);
        if (free >= 0) {
            TextView storage = UiKit.text(this,
                    OrbitText.get(R.string.ui_free_storage_61acab, "Free storage: ") + LocalModelStore.formatBytes(free), 12, UiKit.MUTED, false);
            storage.setPadding(0, UiKit.dp(this, 5), 0, 0);
            card.addView(storage);
        }
        return card;
    }

    private int tierColor(DeviceCapabilityCheck.Tier tier) {
        switch (tier) {
            case EXCELLENT:
            case SUPPORTED: return UiKit.SUCCESS;
            case LIMITED: return Color.rgb(240, 193, 100);
            default: return UiKit.DANGER;
        }
    }

    // ---- what works here ---------------------------------------------------------------------------

    /**
     * What Orbit Local does on this phone right now, and what still needs a cloud provider.
     *
     * <p>Every line comes from {@link OrbitLocalCapabilities}, which reads real readiness - installed
     * models, switches, permissions - so nothing is listed as working because the code for it exists.
     */
    private View capabilityCard() {
        List<OrbitLocalCapabilities.Capability> all = OrbitLocalCapabilities.evaluate(this);
        LinearLayout card = card();
        card.addView(UiKit.text(this, OrbitText.get(R.string.ui_what_works_on_this_phone_3b82ac, "What works on this phone"), 12, UiKit.MUTED, true));
        LinearLayout local = new LinearLayout(this);
        local.setOrientation(LinearLayout.VERTICAL);
        local.setPadding(0, UiKit.dp(this, 6), 0, 0);
        LinearLayout cloud = new LinearLayout(this);
        cloud.setOrientation(LinearLayout.VERTICAL);
        for (OrbitLocalCapabilities.Capability capability : all) {
            if (capability.availability == OrbitLocalCapabilities.Availability.CLOUD_ONLY) {
                cloud.addView(capabilityRow(capability));
            } else {
                local.addView(capabilityRow(capability));
            }
        }
        card.addView(local);
        TextView cloudTitle = UiKit.text(this, OrbitText.get(R.string.ui_needs_a_cloud_provider_6314c5, "Needs a cloud provider"), 12, UiKit.MUTED, true);
        cloudTitle.setPadding(0, UiKit.dp(this, 12), 0, UiKit.dp(this, 2));
        card.addView(cloudTitle);
        card.addView(cloud);
        TextView promise = UiKit.text(this,
                OrbitText.get(R.string.ui_orbit_local_never_passes_a_request_to_the_3d917e, "Orbit Local never passes a request to the cloud. When something needs one, ")
                        + OrbitText.get(R.string.ui_orbit_says_so_and_you_choose_d71c4f, "Orbit says so and you choose."), 12, UiKit.MUTED, false);
        promise.setLineSpacing(0, 1.12f);
        promise.setPadding(0, UiKit.dp(this, 10), 0, 0);
        card.addView(promise);
        return card;
    }

    private View capabilityRow(OrbitLocalCapabilities.Capability capability) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, UiKit.dp(this, 5), 0, UiKit.dp(this, 1));
        View dot = new View(this);
        int color;
        switch (capability.availability) {
            case READY: color = UiKit.SUCCESS; break;
            case NEEDS_SETUP: color = Color.rgb(240, 193, 100); break;
            default: color = UiKit.withAlpha(UiKit.MUTED, 150); break;
        }
        dot.setBackground(UiKit.rounded(color, 99, this));
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(
                UiKit.dp(this, 7), UiKit.dp(this, 7));
        dotLp.rightMargin = UiKit.dp(this, 10);
        row.addView(dot, dotLp);
        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        boolean cloudOnly = capability.availability == OrbitLocalCapabilities.Availability.CLOUD_ONLY;
        words.addView(UiKit.text(this, capability.name, 14,
                cloudOnly ? UiKit.MUTED : UiKit.TEXT, !cloudOnly));
        if (!capability.detail.isEmpty()) {
            words.addView(UiKit.text(this, capability.detail, 12, UiKit.MUTED, false));
        }
        row.addView(words, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        String state = capability.availability == OrbitLocalCapabilities.Availability.READY
                ? OrbitText.get(R.string.ui_works_on_this_phone_0d7097, "works on this phone") : cloudOnly ? OrbitText.get(R.string.ui_needs_a_cloud_provider_2a0b25, "needs a cloud provider") : OrbitText.get(R.string.ui_not_ready_yet_34a545, "not ready yet");
        row.setContentDescription(capability.name + ", " + state
                + (capability.detail.isEmpty() ? "" : ". " + capability.detail));
        return row;
    }

    // ---- the component ----------------------------------------------------------------------------

    private View componentCard() {
        OrbitLocalComponent.State state = OrbitLocalComponent.state(this);
        LinearLayout card = card();
        card.addView(UiKit.text(this, OrbitText.get(R.string.ui_orbit_local_component_d1dfe4, "Orbit Local component"), 12, UiKit.MUTED, true));
        card.addView(titleRow(OrbitText.get(R.string.ui_on_device_ai_component_1077df, "On-device AI component"),
                OrbitLocalComponent.stateLabel(state), state == OrbitLocalComponent.State.INSTALLED));

        TextView details = UiKit.text(this, componentDetails(state), 13, UiKit.MUTED, false);
        details.setLineSpacing(0, 1.13f);
        details.setPadding(0, UiKit.dp(this, 6), 0, 0);
        card.addView(details);

        if (installing) {
            ProgressBar bar = UiKit.horizontalProgress(this);
            bar.setMax(100);
            bar.setProgress(Math.max(0, installPercent));
            LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, UiKit.dp(this, 8));
            barLp.topMargin = UiKit.dp(this, 11);
            card.addView(bar, barLp);
            TextView progressText = UiKit.text(this,
                    installPercent < 0 ? OrbitText.get(R.string.ui_preparing_download_f40b15, "Preparing download…") : installPercent + "%",
                    12, UiKit.MUTED, false);
            progressText.setPadding(0, UiKit.dp(this, 6), 0, 0);
            card.addView(progressText);
        }

        if (!installMessage.isEmpty()) {
            TextView message = UiKit.text(this, installMessage, 13, UiKit.DANGER, false);
            message.setLineSpacing(0, 1.13f);
            message.setPadding(0, UiKit.dp(this, 8), 0, 0);
            card.addView(message);
        }

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.VERTICAL);
        actions.setPadding(0, UiKit.dp(this, 12), 0, 0);
        buildComponentActions(actions, state);
        if (actions.getChildCount() > 0) {
            card.addView(actions, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        return card;
    }

    private String componentDetails(OrbitLocalComponent.State state) {
        if (!OrbitDistribution.supportsOrbitLocal()) return playComponentDetails(state);
        String size = componentSizeText();
        switch (state) {
            case INSTALLED:
                return OrbitText.get(R.string.ui_runs_ai_privately_on_this_device_457b44, "Runs AI privately on this device · ") + size
                        + OrbitText.get(R.string.ui_version_80ad7e, "\nVersion ") + OrbitVersion.displayName(
                                OrbitLocalComponent.installedVersionName(this));
            case UPDATE_REQUIRED:
                return OrbitText.get(R.string.ui_the_installed_component_was_built_for_a_di_b5b292, "The installed component was built for a different version of Orbit. Update it to use Orbit Local again.");
            case UNTRUSTED:
                return OrbitText.get(R.string.ui_a_package_named_orbit_local_is_installed_b_53e19e, "A package named Orbit Local is installed but was not published by Orbit, so it will not be used. Remove it, then install Orbit Local from here.");
            default:
                return OrbitText.get(R.string.ui_required_to_run_ai_privately_on_this_devic_cc83d2, "Required to run AI privately on this device · ") + size;
        }
    }

    /**
     * The Google Play edition's whole view of the component.
     *
     * <p>Orbit from Google Play is signed with a different key from Orbit on GitHub, and the
     * component only serves an Orbit carrying the GitHub certificate, so no installed component
     * can ever work with it. What is left to offer is removing one that is present.
     */
    static String playComponentDetails(OrbitLocalComponent.State state) {
        switch (state) {
            case NOT_INSTALLED:
                return OrbitText.get(R.string.ui_orbit_local_isn_t_available_in_the_google_8ba143, PLAY_UNAVAILABLE);
            case UNTRUSTED:
                return OrbitText.get(R.string.ui_a_package_named_orbit_local_is_installed_b_83e74c, "A package named Orbit Local is installed but was not published by Orbit, ")
                        + OrbitText.get(R.string.ui_so_it_will_not_be_used_you_can_remove_it_h_006461, "so it will not be used. You can remove it here.");
            default:
                return OrbitText.get(R.string.ui_an_orbit_local_component_from_the_github_e_9c00ab, "An Orbit Local component from the GitHub edition of Orbit is installed. ")
                        + OrbitText.get(R.string.ui_the_google_play_edition_can_t_use_it_you_c_719f35, "The Google Play edition can't use it. You can uninstall it here.");
        }
    }

    private String componentSizeText() {
        long bytes = OrbitLocalInstaller.knownComponentBytes(this);
        return bytes > 0L ? LocalModelStore.formatBytes(bytes) : OrbitText.get(R.string.ui_a_small_download_c38cb9, "a small download");
    }

    private void buildComponentActions(LinearLayout actions, OrbitLocalComponent.State state) {
        boolean allowed = DeviceCapabilityCheck.allowsLocalAi(DeviceCapabilityCheck.assess(this));
        if (installing) {
            Button working = secondaryButton(OrbitText.get(R.string.ui_downloading_a778d3, "Downloading…"));
            working.setEnabled(false);
            working.setAlpha(0.62f);
            addPrimaryAction(actions, working);
            return;
        }
        if (!OrbitDistribution.supportsOrbitLocal()) {
            // Nothing to set up or update in the Play edition; see playComponentDetails. Only a
            // component that is actually present can be removed, through Android's own flow.
            if (state == OrbitLocalComponent.State.UNTRUSTED) {
                Button remove = dangerButton(OrbitText.get(R.string.ui_remove_untrusted_component_9ae95d, "Remove untrusted component"));
                remove.setOnClickListener(v -> removeOrbitLocal(SCOPE_COMPONENT));
                addPrimaryAction(actions, remove);
            } else if (state != OrbitLocalComponent.State.NOT_INSTALLED) {
                Button uninstall = dangerButton(OrbitText.get(R.string.ui_uninstall_component_cef50f, "Uninstall component"));
                uninstall.setOnClickListener(v -> confirmUninstallComponent());
                addDestructiveAction(actions, uninstall);
            }
            return;
        }
        switch (state) {
            case INSTALLED: {
                // The component is a real installed package, so removing just it is a real thing
                // to want — separate from Remove Orbit Local, which also clears what Orbit holds.
                Button uninstall = dangerButton(OrbitText.get(R.string.ui_uninstall_component_cef50f, "Uninstall component"));
                uninstall.setOnClickListener(v -> confirmUninstallComponent());
                addDestructiveAction(actions, uninstall);
                return;
            }
            case UPDATE_REQUIRED: {
                Button update = primaryButton(OrbitText.get(R.string.ui_update_component_f76f52, "Update component"));
                update.setOnClickListener(v -> startComponentDownload());
                addPrimaryAction(actions, update);
                return;
            }
            case UNTRUSTED: {
                // A package pretending to be Orbit Local. Removing it goes through exactly the
                // same Android flow, and its outcome is read back from the package manager too.
                Button remove = dangerButton(OrbitText.get(R.string.ui_remove_untrusted_component_9ae95d, "Remove untrusted component"));
                remove.setOnClickListener(v -> removeOrbitLocal(SCOPE_COMPONENT));
                addPrimaryAction(actions, remove);
                return;
            }
            default: {
                // Nothing installed at all: present one Orbit Local setup, not two technical
                // downloads. The component always comes first, because a model without it cannot
                // run and downloading 1.6 GB into that situation would be indefensible.
                boolean firstTime = !LocalModelStore.hasLegacyModel(this);
                Button install = primaryButton(firstTime ? OrbitText.get(R.string.ui_set_up_orbit_local_666080, "Set up Orbit Local") : OrbitText.get(R.string.ui_install_component_f0ee39, "Install component"));
                install.setEnabled(allowed);
                if (!allowed) install.setAlpha(0.5f);
                install.setOnClickListener(v -> {
                    if (firstTime) showSetupSheet();
                    else startComponentDownload();
                });
                addPrimaryAction(actions, install);
            }
        }
    }

    /**
     * The one place a new user is told what Orbit Local actually costs.
     *
     * <p>Both downloads, both sizes, stated up front and in that order, with the plain fact that
     * they can be removed again. Nothing starts until Continue.
     */
    private void showSetupSheet() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(UiKit.dp(this, 24), UiKit.dp(this, 20),
                UiKit.dp(this, 24), UiKit.dp(this, 6));
        TextView title = UiKit.text(this, OrbitText.get(R.string.ui_set_up_orbit_local_666080, "Set up Orbit Local"), 20, UiKit.TEXT, true);
        title.setAccessibilityHeading(true);
        content.addView(title);

        TextView lead = UiKit.text(this, OrbitText.get(R.string.ui_orbit_local_needs_two_optional_downloads_ac94f4, "Orbit Local needs two optional downloads:"),
                14, UiKit.TEXT, false);
        lead.setPadding(0, UiKit.dp(this, 12), 0, 0);
        content.addView(lead);

        content.addView(setupItem(OrbitText.get(R.string.ui_orbit_local_component_8da49c, "Orbit Local component · ") + componentSizeText(),
                OrbitText.get(R.string.ui_lets_orbit_run_ai_privately_on_this_device_b550ad, "Lets Orbit run AI privately on this device.")));
        content.addView(setupItem(OrbitText.get(R.string.ui_ai_model_75767b, "AI model · ")
                        + LocalModelStore.formatBytes(LocalModelStore.MODEL_SIZE_BYTES),
                OrbitText.get(R.string.ui_contains_the_local_ai_model_itself_3573c0, "Contains the local AI model itself.")));

        TextView footer = UiKit.text(this,
                OrbitText.get(R.string.ui_both_can_be_removed_later_a_separate_optio_be97dd, "Both can be removed later. A separate, optional action model can be added ")
                        + OrbitText.get(R.string.ui_afterwards_if_you_want_orbit_local_to_hand_e1aea9, "afterwards if you want Orbit Local to handle phone commands too."),
                13, UiKit.MUTED, false);
        footer.setLineSpacing(0, 1.13f);
        footer.setPadding(0, UiKit.dp(this, 12), 0, 0);
        content.addView(footer);
        UiKit.applyTypography(content);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setForceDarkAllowed(false);
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(scroll)
                .setNegativeButton(OrbitText.get(R.string.ui_cancel_19766e, "Cancel"), null)
                .setPositiveButton(OrbitText.get(R.string.ui_continue_31fbef, "Continue"), (d, w) -> startComponentDownload())
                .create();
        UiKit.styleOrbitDialog(dialog, this, false);
        dialog.show();
    }

    private View setupItem(String title, String description) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setPadding(UiKit.dp(this, 14), UiKit.dp(this, 11),
                UiKit.dp(this, 14), UiKit.dp(this, 11));
        item.setBackground(UiKit.outlined(UiKit.SURFACE_2,
                UiKit.withAlpha(UiKit.accent(this), 38), 16, this));
        item.addView(UiKit.text(this, title, 14, UiKit.TEXT, true));
        TextView detail = UiKit.text(this, description, 12, UiKit.MUTED, false);
        detail.setPadding(0, UiKit.dp(this, 3), 0, 0);
        detail.setLineSpacing(0, 1.1f);
        item.addView(detail);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, UiKit.dp(this, 11), 0, 0);
        item.setLayoutParams(lp);
        return item;
    }

    private void startComponentDownload() {
        installing = true;
        installPercent = -1;
        installMessage = "";
        rebuild();
        poller.nudge();
        OrbitLocalInstaller.downloadAsync(this, new OrbitLocalInstaller.Callback() {
            @Override public void onProgress(int percent) {
                main.post(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    installPercent = percent;
                });
            }

            @Override public void onVerifying() {
                main.post(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    installPercent = 100;
                    rebuild();
                });
            }

            @Override public void onReady(File apk) {
                main.post(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    installing = false;
                    readyComponentApk = apk;
                    rebuild();
                    launchComponentInstaller(apk);
                });
            }

            @Override public void onError(String message) {
                main.post(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    installing = false;
                    installMessage = message;
                    rebuild();
                });
            }
        });
    }

    /** Android confirms every install. Orbit only ever hands it a fully verified file. */
    private void launchComponentInstaller(File apk) {
        if (!OrbitUpdater.canRequestPackageInstalls(this)) {
            waitingForInstallPermission = true;
            installMessage = OrbitText.get(R.string.ui_android_needs_permission_to_install_the_co_e604d0, "Android needs permission to install the component from Orbit. Allow this source, then return to Orbit.");
            rebuild();
            OrbitUpdater.openUnknownSourcesSettings(this);
            return;
        }
        try {
            OrbitLocalInstaller.launchInstaller(this, apk);
        } catch (Exception e) {
            // The installer now reports which step of the handoff failed, so show that rather than
            // one message that was only ever accurate for the last of them.
            String message = e.getMessage();
            installMessage = message == null || message.trim().isEmpty()
                    ? OrbitText.get(R.string.ui_android_could_not_open_the_package_install_2f977b, "Android could not open the package installer.") : message;
            rebuild();
        }
    }

    // ---- the model --------------------------------------------------------------------------------

    private View modelCard() {
        boolean componentReady = OrbitLocalComponent.isUsable(this);
        boolean active = Prefs.PROVIDER_LOCAL.equals(AiProviders.active(this).id());
        LinearLayout card = card();
        if (active) {
            card.setBackground(UiKit.outlined(
                    UiKit.blend(UiKit.SURFACE, UiKit.accent(this), 0.93f),
                    UiKit.accent(this), 22, this));
        }
        card.addView(UiKit.text(this, OrbitText.get(R.string.ui_local_model_ea8157, "Local model"), 12, UiKit.MUTED, true));

        String pillLabel;
        boolean accentPill;
        if (!componentReady) {
            pillLabel = OrbitText.get(R.string.ui_requires_orbit_local_component_e0a085, "Requires Orbit Local component");
            accentPill = false;
        } else if (status == null) {
            pillLabel = statusChecked ? getString(R.string.local_ai_check_failed)
                    : OrbitText.get(R.string.ui_checking_ec963f, "Checking…");
            accentPill = false;
        } else if (active) {
            pillLabel = OrbitText.get(R.string.ui_active_923406, "Active");
            accentPill = true;
        } else {
            pillLabel = status.stateLabel();
            accentPill = status.modelReady();
        }
        card.addView(titleRow(LocalModelStore.MODEL_DISPLAY_NAME, pillLabel, accentPill));

        TextView details = UiKit.text(this, modelDetails(componentReady), 13, UiKit.MUTED, false);
        details.setLineSpacing(0, 1.13f);
        details.setPadding(0, UiKit.dp(this, 6), 0, 0);
        card.addView(details);

        if (componentReady && status != null && status.showsProgress()) {
            ProgressBar bar = UiKit.horizontalProgress(this);
            bar.setMax(1000);
            // Straight from the component's own byte count. Orbit keeps no second counter that
            // could drift away from the file actually on disk.
            bar.setProgress(OrbitLocalStatus.VALIDATING.equals(status.modelState)
                    ? 1000 : status.progressPerMille());
            LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, UiKit.dp(this, 8));
            barLp.topMargin = UiKit.dp(this, 11);
            card.addView(bar, barLp);

            TextView progress = UiKit.text(this, progressText(), 12, UiKit.MUTED, false);
            progress.setPadding(0, UiKit.dp(this, 6), 0, 0);
            card.addView(progress);
        }

        if (componentReady && status != null && !status.modelError.isEmpty()) {
            TextView error = UiKit.text(this, status.modelError, 13, UiKit.DANGER, false);
            error.setLineSpacing(0, 1.13f);
            error.setPadding(0, UiKit.dp(this, 8), 0, 0);
            card.addView(error);
        }

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.VERTICAL);
        actions.setPadding(0, UiKit.dp(this, 12), 0, 0);
        buildModelActions(actions, componentReady);
        if (actions.getChildCount() > 0) {
            card.addView(actions, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        return card;
    }

    /**
     * The line under the progress bar: how much has arrived, out of how much.
     *
     * <p>Both figures come from the component's real on-disk state, so during a download this
     * reads like "682 MB of 1.60 GB · 42%" and advances with the file rather than with a timer.
     */
    private String progressText() {
        String have = LocalModelStore.formatBytes(status.modelBytes);
        String total = LocalModelStore.formatBytes(status.modelSizeBytes);
        switch (status.modelState) {
            case OrbitLocalStatus.VALIDATING:
                return OrbitText.get(R.string.ui_verifying_the_model_f6ab7a, "Verifying the model…");
            case OrbitLocalStatus.IMPORTING:
                return OrbitText.get(R.string.ui_moving_your_existing_model_8fdb6d, "Moving your existing model · ") + have + OrbitText.get(R.string.ui_of_a4282e, " of ") + total;
            case OrbitLocalStatus.QUEUED:
                return have + OrbitText.get(R.string.ui_of_a4282e, " of ") + total + OrbitText.get(R.string.ui_starting_0de54d, " · starting…");
            case OrbitLocalStatus.WAITING_FOR_NETWORK:
                return have + OrbitText.get(R.string.ui_of_a4282e, " of ") + total + OrbitText.get(R.string.ui_waiting_for_a_connection_20e31e, " · waiting for a connection");
            default:
                return have + OrbitText.get(R.string.ui_of_a4282e, " of ") + total + " · " + (status.progressPerMille() / 10) + "%";
        }
    }

    private String modelDetails(boolean componentReady) {
        String size = LocalModelStore.formatBytes(LocalModelStore.MODEL_SIZE_BYTES);
        if (!componentReady) {
            String base = OrbitText.get(R.string.ui_local_ai_model_about_024907, "Local AI model · about ") + size;
            if (!OrbitDistribution.installsOrbitLocalComponent()) return base + "\n" + OrbitText.get(R.string.ui_orbit_local_isn_t_available_in_the_google_8ba143, PLAY_UNAVAILABLE);
            if (LocalModelStore.hasLegacyModel(this)) {
                return base + OrbitText.get(R.string.ui_you_already_have_this_model_on_the_phone_i_a0a807, "\nYou already have this model on the phone. Install the Orbit Local ")
                        + OrbitText.get(R.string.ui_component_and_orbit_can_move_it_across_ins_fafe64, "component and Orbit can move it across instead of downloading it again.");
            }
            return base + OrbitText.get(R.string.ui_install_the_orbit_local_component_first_46145f, "\nInstall the Orbit Local component first.");
        }
        if (status == null) return statusChecked ? getString(R.string.local_ai_connection_failed)
                : OrbitText.get(R.string.ui_checking_the_orbit_local_component_fbd066, "Checking the Orbit Local component…");
        switch (status.modelState) {
            case OrbitLocalStatus.READY:
                // The action model is a separate install and does not change what *this* model can
                // do, so the sentence changes only to stop claiming device commands are impossible
                // on a phone where they demonstrably are not.
                return size + OrbitText.get(R.string.ui_installed_works_completely_offline_848332, " installed · Works completely offline\n")
                        + OrbitText.get(R.string.ui_good_for_private_chat_drafting_and_quick_a_bc2a0d, "Good for private chat, drafting, and quick answers. ")
                        + (status.actionModelReady()
                                ? OrbitText.get(R.string.ui_phone_commands_are_handled_by_the_action_m_db1d03, "Phone commands are handled by the action model below; screen reading stays with cloud providers.")
                                : OrbitText.get(R.string.ui_device_actions_and_screen_reading_stay_wit_ee2ac0, "Device actions and screen reading stay with cloud providers for now."));
            case OrbitLocalStatus.DOWNLOADING:
                return OrbitText.get(R.string.ui_downloading_in_the_background_you_can_leav_ab48e8, "Downloading in the background. You can leave this screen, lock the phone, or use other apps; the download keeps going and picks up where it left off after any interruption.");
            case OrbitLocalStatus.QUEUED:
                return OrbitText.get(R.string.ui_the_download_is_starting_it_runs_in_the_ba_989164, "The download is starting. It runs in the background and does not need this screen to stay open.");
            case OrbitLocalStatus.WAITING_FOR_NETWORK:
                return OrbitText.get(R.string.ui_waiting_for_a_connection_the_download_cont_1afc82, "Waiting for a connection. The download continues on its own once this phone is back online; nothing already downloaded is lost.");
            case OrbitLocalStatus.VALIDATING:
                return OrbitText.get(R.string.ui_making_sure_every_byte_arrived_intact_befo_2ea2e8, "Making sure every byte arrived intact before the model is enabled.");
            case OrbitLocalStatus.IMPORTING:
                return OrbitText.get(R.string.ui_moving_the_model_you_already_downloaded_in_1d6091, "Moving the model you already downloaded into Orbit Local. Your existing copy is kept until this finishes and verifies.");
            case OrbitLocalStatus.PAUSED:
                return OrbitText.get(R.string.ui_you_paused_this_download_resume_any_time_a_3fb63a, "You paused this download. Resume any time; already-downloaded data is kept.");
            case OrbitLocalStatus.INTERRUPTED:
                return OrbitText.get(R.string.ui_the_download_stopped_before_it_finished_ta_230d57, "The download stopped before it finished. Tap Resume to continue from where it got to; already-downloaded data is kept.");
            case OrbitLocalStatus.ERROR:
                return OrbitText.get(R.string.ui_one_time_download_cef67e, "One-time download · ") + size + OrbitText.get(R.string.ui_stored_only_on_this_device_f06bca, " · stored only on this device.");
            default:
                if (LocalModelStore.hasLegacyModel(this)) {
                    return OrbitText.get(R.string.ui_you_already_have_this_model_on_the_phone_f_0b216d, "You already have this model on the phone from an earlier version of Orbit. ")
                            + OrbitText.get(R.string.ui_orbit_can_move_it_into_orbit_local_instead_c00d72, "Orbit can move it into Orbit Local instead of downloading ") + size + OrbitText.get(R.string.ui_again_3e14c2, " again.");
                }
                return OrbitText.get(R.string.ui_one_time_download_about_17b6d5, "One-time download · about ") + size + OrbitText.get(R.string.ui_stored_only_on_this_device_after_that_orbi_825829, " · stored only on this device. After that, Orbit Local needs no internet and no account.");
        }
    }

    private void addStatusRetry(LinearLayout actions) {
        if (!statusChecked) return;
        Button retry = secondaryButton(getString(R.string.local_ai_retry_connection));
        retry.setOnClickListener(v -> {
            statusChecked = false;
            OrbitLocalClient.disconnect(this);
            rebuild();
            poller.nudge();
        });
        addPrimaryAction(actions, retry);
    }

    private void buildModelActions(LinearLayout actions, boolean componentReady) {
        if (!componentReady) return;
        if (status == null) {
            addStatusRetry(actions);
            return;
        }
        boolean allowed = DeviceCapabilityCheck.allowsLocalAi(DeviceCapabilityCheck.assess(this));
        switch (status.modelState) {
            case OrbitLocalStatus.NOT_INSTALLED:
            case OrbitLocalStatus.ERROR: {
                if (LocalModelStore.hasLegacyModel(this)) {
                    Button move = primaryButton(OrbitText.get(R.string.ui_move_existing_model_b3e9c3, "Move existing model"));
                    move.setOnClickListener(v -> confirmMigration());
                    addPrimaryAction(actions, move);
                }
                Button download = primaryButton(
                        OrbitLocalStatus.ERROR.equals(status.modelState) ? OrbitText.get(R.string.ui_try_again_d8b839, "Try again") : OrbitText.get(R.string.ui_download_model_9979be, "Download model"));
                download.setEnabled(allowed);
                if (!allowed) download.setAlpha(0.5f);
                download.setOnClickListener(v -> {
                    OrbitLocalClient.startModelDownload(this);
                    bumpRefresh();
                });
                addPrimaryAction(actions, download);
                break;
            }
            case OrbitLocalStatus.PAUSED:
            case OrbitLocalStatus.INTERRUPTED: {
                // Whoever stopped it, the same two things are worth offering: continue from the
                // bytes already here, or give them up deliberately.
                Button resume = primaryButton(OrbitText.get(R.string.ui_resume_download_808463, "Resume download"));
                resume.setOnClickListener(v -> {
                    OrbitLocalClient.startModelDownload(this);
                    bumpRefresh();
                });
                addPrimaryAction(actions, resume);
                Button discard = dangerButton(OrbitText.get(R.string.ui_remove_downloaded_data_da5c28, "Remove downloaded data"));
                discard.setOnClickListener(v -> confirmDeleteModel(true));
                addDestructiveAction(actions, discard);
                break;
            }
            case OrbitLocalStatus.DOWNLOADING:
            case OrbitLocalStatus.QUEUED:
            case OrbitLocalStatus.WAITING_FOR_NETWORK: {
                Button pause = secondaryButton(OrbitText.get(R.string.ui_pause_858e4b, "Pause"));
                pause.setOnClickListener(v -> {
                    OrbitLocalClient.pauseModelDownload(this);
                    bumpRefresh();
                });
                addCompactAction(actions, pause);
                break;
            }
            case OrbitLocalStatus.IMPORTING: {
                Button stop = secondaryButton(OrbitText.get(R.string.ui_stop_moving_dde06f, "Stop moving"));
                stop.setOnClickListener(v -> {
                    OrbitLocalClient.abortModelImport(this);
                    bumpRefresh();
                });
                addCompactAction(actions, stop);
                break;
            }
            case OrbitLocalStatus.VALIDATING:
                break;
            case OrbitLocalStatus.READY: {
                if (!Prefs.PROVIDER_LOCAL.equals(AiProviders.active(this).id())) {
                    Button use = primaryButton(OrbitText.get(R.string.ui_use_orbit_local_144ca5, "Use Orbit Local"));
                    use.setOnClickListener(v -> {
                        if (AiProviders.select(this, Prefs.PROVIDER_LOCAL)) {
                            Toast.makeText(this, OrbitText.get(R.string.ui_orbit_local_is_now_the_active_provider_9fcfde, "Orbit Local is now the active provider"),
                                    Toast.LENGTH_SHORT).show();
                            rebuild();
                        }
                    });
                    addPrimaryAction(actions, use);
                }
                if (LocalModelStore.hasLegacyModel(this)) {
                    // The component has its own verified copy now, so Orbit's old one is pure
                    // duplication. Offered, never done silently.
                    Button reclaim = secondaryButton(OrbitText.get(R.string.ui_free_f876b7, "Free ")
                            + LocalModelStore.formatBytes(LocalModelStore.legacyBytes(this))
                            + OrbitText.get(R.string.ui_of_old_model_data_6caf8a, " of old model data"));
                    reclaim.setOnClickListener(v -> confirmReclaimLegacy());
                    addPrimaryAction(actions, reclaim);
                }
                Button delete = dangerButton(OrbitText.get(R.string.ui_delete_local_model_86e835, "Delete local model"));
                delete.setOnClickListener(v -> confirmDeleteModel(false));
                addDestructiveAction(actions, delete);
                break;
            }
            default:
                break;
        }
    }

    private void bumpRefresh() {
        poller.nudge();
    }

    // ---- migration --------------------------------------------------------------------------------

    /**
     * Moving the existing model, or — when there is no room to hold two copies — replacing it.
     *
     * <p>Copying needs space for a second complete model until the original goes away, so on a
     * full device Orbit says so plainly and offers the honest alternative instead of starting a
     * transfer that cannot finish.
     */
    private void confirmMigration() {
        if (!LocalModelStore.enoughStorageToMigrate(this)) {
            confirmLowStorageReplace();
            return;
        }
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(OrbitText.get(R.string.ui_existing_orbit_local_model_found_5ecadf, "Existing Orbit Local model found"))
                .setMessage(OrbitText.get(R.string.ui_orbit_can_move_your_existing_local_model_i_b5961a, "Orbit can move your existing local model into the new Orbit Local ")
                        + OrbitText.get(R.string.ui_component_instead_of_downloading_it_again_200c8e, "component instead of downloading it again.\n\nYour current copy is kept ")
                        + OrbitText.get(R.string.ui_until_the_moved_copy_has_been_verified_so_dbe31a, "until the moved copy has been verified, so nothing is lost if this is ")
                        + "interrupted.")
                .setNegativeButton(OrbitText.get(R.string.ui_cancel_19766e, "Cancel"), null)
                .setPositiveButton(OrbitText.get(R.string.ui_move_existing_model_b3e9c3, "Move existing model"), (d, w) -> startMigration())
                .create();
        UiKit.styleOrbitDialog(dialog, this, false);
        dialog.show();
    }

    private void confirmLowStorageReplace() {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(OrbitText.get(R.string.ui_not_enough_space_to_move_the_model_c06c1d, "Not enough space to move the model"))
                .setMessage(OrbitText.get(R.string.ui_moving_the_model_needs_room_for_a_second_c_79fe6b, "Moving the model needs room for a second copy until the first is ")
                        + OrbitText.get(R.string.ui_removed_and_this_device_does_not_have_it_o_79c591, "removed, and this device does not have it.\n\nOrbit can instead delete ")
                        + OrbitText.get(R.string.ui_the_old_model_and_download_a_fresh_copy_in_d57776, "the old model and download a fresh copy into Orbit Local. Local AI will ")
                        + OrbitText.get(R.string.ui_be_unavailable_until_that_download_finishe_06fe5a, "be unavailable until that download finishes."))
                .setNegativeButton(OrbitText.get(R.string.ui_cancel_19766e, "Cancel"), null)
                .setPositiveButton(OrbitText.get(R.string.ui_replace_with_new_copy_d093dd, "Replace with new copy"), (d, w) -> {
                    LocalModelStore.deleteLegacy(this);
                    OrbitLocalClient.startModelDownload(this);
                    Toast.makeText(this, OrbitText.get(R.string.ui_downloading_the_model_into_orbit_local_5793f8, "Downloading the model into Orbit Local"),
                            Toast.LENGTH_SHORT).show();
                    bumpRefresh();
                })
                .create();
        UiKit.styleOrbitDialog(dialog, this, true);
        dialog.show();
    }

    private void startMigration() {
        File legacy = LocalModelStore.legacyModelFile(this);
        new Thread(() -> {
            boolean started = OrbitLocalClient.startModelImport(
                    this, legacy, LocalModelStore.MODEL_SIZE_BYTES);
            main.post(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (!started) {
                    Toast.makeText(this, OrbitText.get(R.string.ui_orbit_local_could_not_start_moving_the_mod_d01102, "Orbit Local could not start moving the model"),
                            Toast.LENGTH_LONG).show();
                }
                bumpRefresh();
            });
        }, "orbit-local-migrate").start();
    }

    /** Removing Orbit's now-duplicate copy, only after the component verified its own. */
    private void confirmReclaimLegacy() {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(OrbitText.get(R.string.ui_free_up_the_old_copy_ce358c, "Free up the old copy?"))
                .setMessage(OrbitText.get(R.string.ui_orbit_local_now_has_its_own_verified_copy_01b739, "Orbit Local now has its own verified copy of the model. The older copy ")
                        + OrbitText.get(R.string.ui_stored_by_orbit_is_no_longer_used_and_remo_145f5e, "stored by Orbit is no longer used, and removing it frees about ")
                        + LocalModelStore.formatBytes(LocalModelStore.legacyBytes(this))
                        + OrbitText.get(R.string.ui_orbit_local_keeps_working_exactly_as_it_do_1917dc, ".\n\nOrbit Local keeps working exactly as it does now."))
                .setNegativeButton(OrbitText.get(R.string.ui_keep_it_fdce5d, "Keep it"), null)
                .setPositiveButton(OrbitText.get(R.string.ui_free_up_space_5912ec, "Free up space"), (d, w) -> {
                    LocalModelStore.deleteLegacy(this);
                    Toast.makeText(this, OrbitText.get(R.string.ui_old_model_data_removed_26f003, "Old model data removed"), Toast.LENGTH_SHORT).show();
                    rebuild();
                })
                .create();
        UiKit.styleOrbitDialog(dialog, this, true);
        dialog.show();
    }

    // ---- storage ----------------------------------------------------------------------------------

    // ---- the device-action model --------------------------------------------------------------------

    /**
     * The second, much smaller model: the one that turns a spoken instruction into an Orbit action.
     *
     * <p>Deliberately its own card, with its own state, its own storage figure and its own removal.
     * It is optional in exactly the way the chat model is optional, and Orbit Local's chat works
     * with or without it — as does every cloud provider, which never touches it at all.
     */
    private View actionModelCard() {
        boolean componentReady = OrbitLocalComponent.isUsable(this);
        LinearLayout card = card();
        card.addView(UiKit.text(this, OrbitText.get(R.string.ui_device_actions_4b0730, "Device actions"), 12, UiKit.MUTED, true));

        String pillLabel;
        boolean accentPill = false;
        if (!componentReady) {
            pillLabel = OrbitText.get(R.string.ui_requires_orbit_local_component_e0a085, "Requires Orbit Local component");
        } else if (status == null) {
            pillLabel = statusChecked ? getString(R.string.local_ai_check_failed)
                    : OrbitText.get(R.string.ui_checking_ec963f, "Checking…");
        } else if (status.actionModelReady()) {
            boolean enabled = Prefs.localDeviceActions(this);
            pillLabel = enabled ? OrbitText.get(R.string.ui_on_130011, "On") : OrbitText.get(R.string.ui_off_ca7981, "Off");
            accentPill = enabled;
        } else {
            pillLabel = status.actionStateLabel();
        }
        card.addView(titleRow(
                status == null || status.actionModelDisplayName.isEmpty()
                        ? OrbitText.get(R.string.ui_local_action_model_caf6d6, "Local action model") : status.actionModelDisplayName,
                pillLabel, accentPill));

        TextView details = UiKit.text(this, actionModelDetails(componentReady), 13, UiKit.MUTED, false);
        details.setLineSpacing(0, 1.13f);
        details.setPadding(0, UiKit.dp(this, 6), 0, 0);
        card.addView(details);

        if (componentReady && status != null && status.actionShowsProgress()) {
            ProgressBar bar = UiKit.horizontalProgress(this);
            bar.setMax(1000);
            bar.setProgress(OrbitLocalStatus.VALIDATING.equals(status.actionModelState)
                    ? 1000 : status.actionProgressPerMille());
            LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, UiKit.dp(this, 8));
            barLp.topMargin = UiKit.dp(this, 11);
            card.addView(bar, barLp);
            card.addView(actionProgressLine());
        }

        if (componentReady && status != null && !status.actionModelError.isEmpty()) {
            TextView error = UiKit.text(this, status.actionModelError, 13, UiKit.DANGER, false);
            error.setLineSpacing(0, 1.13f);
            error.setPadding(0, UiKit.dp(this, 8), 0, 0);
            card.addView(error);
        }

        if (componentReady && status != null && status.actionModelReady()) {
            card.addView(actionsEnabledRow());
            TextView covers = UiKit.text(this,
                    OrbitText.get(R.string.ui_understands_flashlight_brightness_media_vo_9efd1d, "Understands: flashlight, brightness, media volume, Do Not Disturb, ringer mode, ")
                            + OrbitText.get(R.string.ui_play_pause_skip_timers_alarms_opening_an_a_184da5, "play/pause/skip, timers, alarms, opening an app, and opening Settings. ")
                            + OrbitText.get(R.string.ui_nothing_else_and_nothing_it_suggests_runs_faca24, "Nothing else, and nothing it suggests runs without Orbit checking it first."),
                    12, UiKit.MUTED, false);
            covers.setLineSpacing(0, 1.13f);
            covers.setPadding(0, UiKit.dp(this, 10), 0, 0);
            card.addView(covers);
        }

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.VERTICAL);
        actions.setPadding(0, UiKit.dp(this, 12), 0, 0);
        buildActionModelActions(actions, componentReady);
        if (actions.getChildCount() > 0) {
            card.addView(actions, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        return card;
    }

    /** The on/off control for consulting the model, separate from having it installed. */
    private View actionsEnabledRow() {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, UiKit.dp(this, 12), 0, 0);
        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.addView(UiKit.text(this, OrbitText.get(R.string.ui_use_for_device_commands_1a99cf, "Use for device commands"), 15, UiKit.TEXT, false));
        labels.addView(UiKit.text(this,
                OrbitText.get(R.string.ui_orbit_s_own_command_recognition_always_run_01c0f7, "Orbit's own command recognition always runs first. This only handles what it cannot."),
                12, UiKit.MUTED, false));
        row.addView(labels, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        OrbitSwitch toggle = new OrbitSwitch(this);
        toggle.setChecked(Prefs.localDeviceActions(this), false);
        toggle.setOnCheckedChangeListener((view, checked) -> {
            Prefs.get(this).edit().putBoolean(Prefs.LOCAL_DEVICE_ACTIONS, checked).apply();
            rebuild();
        });
        row.addView(toggle);
        return row;
    }

    private TextView actionProgressLine() {
        String have = LocalModelStore.formatBytes(status.actionModelBytes);
        String total = LocalModelStore.formatBytes(status.actionModelSizeBytes);
        String text;
        switch (status.actionModelState) {
            case OrbitLocalStatus.VALIDATING: text = OrbitText.get(R.string.ui_verifying_the_model_f6ab7a, "Verifying the model…"); break;
            case OrbitLocalStatus.QUEUED: text = have + OrbitText.get(R.string.ui_of_a4282e, " of ") + total + OrbitText.get(R.string.ui_starting_0de54d, " · starting…"); break;
            case OrbitLocalStatus.WAITING_FOR_NETWORK:
                text = have + OrbitText.get(R.string.ui_of_a4282e, " of ") + total + OrbitText.get(R.string.ui_waiting_for_a_connection_20e31e, " · waiting for a connection"); break;
            default:
                text = have + OrbitText.get(R.string.ui_of_a4282e, " of ") + total + " · " + (status.actionProgressPerMille() / 10) + "%";
        }
        TextView line = UiKit.text(this, text, 12, UiKit.MUTED, false);
        line.setPadding(0, UiKit.dp(this, 6), 0, 0);
        return line;
    }

    private String actionModelDetails(boolean componentReady) {
        String size = status == null || status.actionModelSizeBytes <= 0L
                ? OrbitText.get(R.string.ui_about_520_mb_9c6530, "about 520 MB")
                : LocalModelStore.formatBytes(status.actionModelSizeBytes);
        if (!componentReady) {
            if (!OrbitDistribution.installsOrbitLocalComponent()) {
                return OrbitText.get(R.string.ui_lets_orbit_local_understand_phone_commands_bbe4cd, "Lets Orbit Local understand phone commands it has no exact wording for, entirely ")
                        + OrbitText.get(R.string.ui_on_the_device_e9c8f1, "on the device. ") + OrbitText.get(R.string.ui_orbit_local_isn_t_available_in_the_google_8ba143, PLAY_UNAVAILABLE);
            }
            return OrbitText.get(R.string.ui_lets_orbit_local_understand_phone_commands_bbe4cd, "Lets Orbit Local understand phone commands it has no exact wording for, entirely ")
                    + OrbitText.get(R.string.ui_on_the_device_install_the_orbit_local_comp_91edbf, "on the device. Install the Orbit Local component first.");
        }
        if (status == null) return statusChecked ? getString(R.string.local_ai_connection_failed)
                : OrbitText.get(R.string.ui_checking_the_orbit_local_component_fbd066, "Checking the Orbit Local component…");
        switch (status.actionModelState) {
            case OrbitLocalStatus.READY:
                return size + OrbitText.get(R.string.ui_installed_works_completely_offline_848332, " installed · Works completely offline\n")
                        + OrbitText.get(R.string.ui_turns_everyday_phrasing_like_kill_the_torc_ec8c20, "Turns everyday phrasing like \"kill the torch\" or \"a bit quieter\" into an ")
                        + OrbitText.get(R.string.ui_orbit_action_on_the_phone_it_never_chats_a_e96925, "Orbit action, on the phone. It never chats and never controls Android itself.");
            case OrbitLocalStatus.DOWNLOADING:
                return OrbitText.get(R.string.ui_downloading_in_the_background_you_can_leav_9fec34, "Downloading in the background. You can leave this screen or use other apps; ")
                        + OrbitText.get(R.string.ui_the_download_continues_and_resumes_after_a_f0d7b2, "the download continues and resumes after any interruption.");
            case OrbitLocalStatus.QUEUED:
                return OrbitText.get(R.string.ui_the_download_is_starting_it_runs_in_the_ba_4b6052, "The download is starting. It runs in the background and does not need this screen open.");
            case OrbitLocalStatus.WAITING_FOR_NETWORK:
                return OrbitText.get(R.string.ui_waiting_for_a_connection_the_download_cont_b32abe, "Waiting for a connection. The download continues on its own once this phone is back online.");
            case OrbitLocalStatus.VALIDATING:
                return OrbitText.get(R.string.ui_making_sure_every_byte_arrived_intact_befo_2ea2e8, "Making sure every byte arrived intact before the model is enabled.");
            case OrbitLocalStatus.PAUSED:
                return OrbitText.get(R.string.ui_you_paused_this_download_resume_any_time_a_3fb63a, "You paused this download. Resume any time; already-downloaded data is kept.");
            case OrbitLocalStatus.INTERRUPTED:
                return OrbitText.get(R.string.ui_the_download_stopped_before_it_finished_re_7d8129, "The download stopped before it finished. Resume to continue from where it got to.");
            case OrbitLocalStatus.ERROR:
                return OrbitText.get(R.string.ui_one_time_download_cef67e, "One-time download · ") + size + OrbitText.get(R.string.ui_stored_only_on_this_device_f06bca, " · stored only on this device.");
            default:
                return OrbitText.get(R.string.ui_optional_one_time_download_of_0554a3, "Optional · one-time download of ") + size + OrbitText.get(R.string.ui_stored_only_on_this_device_7dd58c, " · stored only on this device.\n")
                        + OrbitText.get(R.string.ui_lets_orbit_local_act_on_phone_commands_tha_e5d640, "Lets Orbit Local act on phone commands that do not match Orbit's exact wording, ")
                        + OrbitText.get(R.string.ui_with_no_internet_and_no_account_chat_works_989ede, "with no internet and no account. Chat works without it.");
        }
    }

    private void buildActionModelActions(LinearLayout actions, boolean componentReady) {
        if (!componentReady) return;
        if (status == null) {
            addStatusRetry(actions);
            return;
        }
        boolean allowed = DeviceCapabilityCheck.allowsLocalAi(DeviceCapabilityCheck.assess(this));
        switch (status.actionModelState) {
            case OrbitLocalStatus.NOT_INSTALLED:
            case OrbitLocalStatus.ERROR: {
                Button download = primaryButton(
                        OrbitLocalStatus.ERROR.equals(status.actionModelState)
                                ? OrbitText.get(R.string.ui_try_again_d8b839, "Try again") : OrbitText.get(R.string.ui_download_action_model_3dc2a1, "Download action model"));
                download.setEnabled(allowed);
                if (!allowed) download.setAlpha(0.5f);
                download.setOnClickListener(v -> {
                    OrbitLocalClient.startActionModelDownload(this);
                    bumpRefresh();
                });
                addPrimaryAction(actions, download);
                break;
            }
            case OrbitLocalStatus.PAUSED:
            case OrbitLocalStatus.INTERRUPTED: {
                Button resume = primaryButton(OrbitText.get(R.string.ui_resume_download_808463, "Resume download"));
                resume.setOnClickListener(v -> {
                    OrbitLocalClient.startActionModelDownload(this);
                    bumpRefresh();
                });
                addPrimaryAction(actions, resume);
                Button discard = dangerButton(OrbitText.get(R.string.ui_remove_downloaded_data_da5c28, "Remove downloaded data"));
                discard.setOnClickListener(v -> confirmDeleteActionModel(true));
                addDestructiveAction(actions, discard);
                break;
            }
            case OrbitLocalStatus.DOWNLOADING:
            case OrbitLocalStatus.QUEUED:
            case OrbitLocalStatus.WAITING_FOR_NETWORK: {
                Button pause = secondaryButton(OrbitText.get(R.string.ui_pause_858e4b, "Pause"));
                pause.setOnClickListener(v -> {
                    OrbitLocalClient.pauseActionModelDownload(this);
                    bumpRefresh();
                });
                addCompactAction(actions, pause);
                break;
            }
            case OrbitLocalStatus.VALIDATING:
                break;
            case OrbitLocalStatus.READY: {
                Button delete = dangerButton(OrbitText.get(R.string.ui_delete_action_model_c461fa, "Delete action model"));
                delete.setOnClickListener(v -> confirmDeleteActionModel(false));
                addDestructiveAction(actions, delete);
                break;
            }
            default:
                break;
        }
    }

    private void confirmDeleteActionModel(boolean partialOnly) {
        long bytes = status == null ? 0L : status.actionModelTotalBytes;
        String size = LocalModelStore.formatBytes(bytes);
        String title = partialOnly ? OrbitText.get(R.string.ui_remove_downloaded_data_5973e7, "Remove downloaded data?") : OrbitText.get(R.string.ui_remove_the_action_model_54b094, "Remove the action model?");
        String message = partialOnly
                ? OrbitText.get(R.string.ui_about_c381a5, "About ") + size + OrbitText.get(R.string.ui_of_partly_downloaded_model_data_will_be_re_59895b, " of partly downloaded model data will be removed, freeing that ")
                        + OrbitText.get(R.string.ui_storage_you_can_start_the_download_again_a_790cdc, "storage. You can start the download again at any time.")
                : OrbitText.get(R.string.ui_about_c381a5, "About ") + size + OrbitText.get(R.string.ui_of_downloaded_model_data_will_be_removed_f_c474cb, " of downloaded model data will be removed, freeing that storage. ")
                        + OrbitText.get(R.string.ui_orbit_local_chat_and_its_own_model_are_not_64326d, "Orbit Local chat and its own model are not affected, and Orbit's built-in ")
                        + OrbitText.get(R.string.ui_command_recognition_keeps_working_exactly_63af7a, "command recognition keeps working exactly as it does now.");
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setNegativeButton(OrbitText.get(R.string.ui_cancel_19766e, "Cancel"), null)
                .setPositiveButton(partialOnly ? OrbitText.get(R.string.ui_remove_c3812f, "Remove") : OrbitText.get(R.string.ui_remove_model_1ab8f6, "Remove model"), (d, w) -> {
                    OrbitLocalClient.deleteActionModel(this);
                    OrbitLocalProvider.invalidateStatus();
                    if (!partialOnly) {
                        Toast.makeText(this, OrbitText.get(R.string.ui_action_model_removed_b61d78, "Action model removed"), Toast.LENGTH_SHORT).show();
                    }
                    bumpRefresh();
                })
                .create();
        UiKit.styleOrbitDialog(dialog, this, true);
        dialog.show();
    }

    private View storageCard() {
        LinearLayout card = card();
        card.addView(UiKit.text(this, OrbitText.get(R.string.ui_orbit_local_storage_6d0d28, "Orbit Local storage"), 12, UiKit.MUTED, true));

        long componentBytes = OrbitLocalComponent.installedApkBytes(this);
        long modelBytes = status == null ? 0L : status.modelTotalBytes;
        long actionBytes = status == null ? 0L : status.actionModelTotalBytes;
        long legacyBytes = LocalModelStore.legacyBytes(this);
        long total = componentBytes + modelBytes + actionBytes + legacyBytes;

        LinearLayout totalRow = new LinearLayout(this);
        totalRow.setGravity(Gravity.CENTER_VERTICAL);
        totalRow.setPadding(0, UiKit.dp(this, 7), 0, 0);
        totalRow.addView(UiKit.text(this, total > 0L ? LocalModelStore.formatBytes(total) : OrbitText.get(R.string.ui_nothing_installed_ad631a, "Nothing installed"),
                17, UiKit.TEXT, true), new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        card.addView(totalRow);

        if (total <= 0L) {
            TextView none = UiKit.text(this,
                    OrbitText.get(R.string.ui_orbit_local_uses_no_storage_on_this_device_6f2d1d, "Orbit Local uses no storage on this device yet. The normal Orbit app is not counted here."),
                    13, UiKit.MUTED, false);
            none.setLineSpacing(0, 1.13f);
            none.setPadding(0, UiKit.dp(this, 6), 0, 0);
            card.addView(none);
            return card;
        }

        if (componentBytes > 0L) {
            card.addView(storageLine(OrbitText.get(R.string.ui_component_ce54f0, "Component"), LocalModelStore.formatBytes(componentBytes)));
        }
        if (modelBytes > 0L) {
            String label = status != null && status.modelReady()
                    ? LocalModelStore.MODEL_DISPLAY_NAME
                    : LocalModelStore.MODEL_DISPLAY_NAME + OrbitText.get(R.string.ui_partial_27f7a4, " (partial)");
            card.addView(storageLine(label, LocalModelStore.formatBytes(modelBytes)));
        }
        if (actionBytes > 0L) {
            String label = status != null && status.actionModelReady()
                    ? OrbitText.get(R.string.ui_action_model_2610de, "Action model") : OrbitText.get(R.string.ui_action_model_partial_c1774c, "Action model (partial)");
            card.addView(storageLine(label, LocalModelStore.formatBytes(actionBytes)));
        }
        if (legacyBytes > 0L) {
            card.addView(storageLine(
                    LocalModelStore.hasLegacyModel(this) ? OrbitText.get(R.string.ui_earlier_orbit_copy_c42c92, "Earlier Orbit copy") : OrbitText.get(R.string.ui_earlier_partial_download_055d38, "Earlier partial download"),
                    LocalModelStore.formatBytes(legacyBytes)));
        }
        return card;
    }

    private View storageLine(String label, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, UiKit.dp(this, 7), 0, 0);
        row.addView(UiKit.text(this, "· " + label, 13, UiKit.MUTED, false),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        row.addView(UiKit.text(this, value, 13, UiKit.TEXT, false));
        return row;
    }

    // ---- removal ----------------------------------------------------------------------------------

    /** True when there is anything left of Orbit Local on this device to remove. */
    private boolean hasAnythingToRemove() {
        return OrbitLocalComponent.isInstalled(this) || LocalModelStore.legacyBytes(this) > 0L;
    }

    private View removeCard() {
        boolean componentInstalled = OrbitLocalComponent.isInstalled(this);
        LinearLayout card = card();
        card.addView(UiKit.text(this, OrbitText.get(R.string.ui_remove_orbit_local_52ed6b, "Remove Orbit Local"), 12, UiKit.MUTED, true));
        TextView details = UiKit.text(this,
                componentInstalled
                        ? OrbitText.get(R.string.ui_removes_everything_orbit_local_uses_on_thi_a8203d, "Removes everything Orbit Local uses on this device: the component, the ")
                                + OrbitText.get(R.string.ui_ai_model_it_stores_any_model_data_left_by_6b3743, "AI model it stores, any model data left by an earlier version of ")
                                + OrbitText.get(R.string.ui_orbit_and_the_downloaded_component_install_be9e92, "Orbit, and the downloaded component installer.\n\nOrbit itself, ")
                                + OrbitText.get(R.string.ui_your_conversations_memories_routines_setti_81f060, "your conversations, memories, Routines, settings, and cloud ")
                                + OrbitText.get(R.string.ui_providers_are_all_untouched_081d22, "providers are all untouched.")
                        : OrbitText.get(R.string.ui_the_orbit_local_component_is_not_installed_d82ee8, "The Orbit Local component is not installed, but model data from an ")
                                + OrbitText.get(R.string.ui_earlier_version_of_orbit_is_still_stored_o_f7b1ac, "earlier version of Orbit is still stored on this device. This ")
                                + OrbitText.get(R.string.ui_removes_it_orbit_itself_your_conversations_da7c29, "removes it.\n\nOrbit itself, your conversations, memories, ")
                                + OrbitText.get(R.string.ui_routines_settings_and_cloud_providers_are_0f3469, "Routines, settings, and cloud providers are all untouched."),
                13, UiKit.MUTED, false);
        details.setLineSpacing(0, 1.13f);
        details.setPadding(0, UiKit.dp(this, 7), 0, 0);
        card.addView(details);

        if (!removalMessage.isEmpty()) {
            TextView outcome = UiKit.text(this, removalMessage, 13, UiKit.MUTED, false);
            outcome.setLineSpacing(0, 1.13f);
            outcome.setPadding(0, UiKit.dp(this, 9), 0, 0);
            card.addView(outcome);
        }

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.VERTICAL);
        actions.setPadding(0, UiKit.dp(this, 12), 0, 0);
        Button remove = dangerButton(componentInstalled
                ? OrbitText.get(R.string.ui_remove_orbit_local_52ed6b, "Remove Orbit Local") : OrbitText.get(R.string.ui_remove_old_local_model_data_518bf7, "Remove old local model data"));
        remove.setOnClickListener(v -> {
            if (componentInstalled) confirmRemoveOrbitLocal();
            else confirmRemoveLegacyOnly();
        });
        addPrimaryAction(actions, remove);
        card.addView(actions, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return card;
    }

    void confirmRemoveOrbitLocal() {
        long freed = OrbitLocalComponent.installedApkBytes(this)
                + (status == null ? 0L : status.modelTotalBytes)
                + LocalModelStore.legacyBytes(this);
        boolean active = Prefs.PROVIDER_LOCAL.equals(AiProviders.active(this).id());
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(OrbitText.get(R.string.ui_remove_orbit_local_ddef44, "Remove Orbit Local?"))
                .setMessage(OrbitText.get(R.string.ui_this_removes_everything_orbit_local_uses_o_93aab3, "This removes everything Orbit Local uses on this device — the ")
                        + OrbitText.get(R.string.ui_component_the_ai_model_any_model_data_left_445368, "component, the AI model, any model data left by an earlier version of ")
                        + OrbitText.get(R.string.ui_orbit_and_the_downloaded_installer_freeing_b385ac, "Orbit, and the downloaded installer — freeing about ")
                        + LocalModelStore.formatBytes(freed)
                        + OrbitText.get(R.string.ui_orbit_itself_stays_installed_your_conversa_9704e5, ".\n\nOrbit itself stays installed. Your conversations, memories, ")
                        + OrbitText.get(R.string.ui_routines_and_settings_are_not_affected_and_066904, "Routines, and settings are not affected, and your cloud providers keep ")
                        + "working."
                        + (active ? OrbitText.get(R.string.ui_chatgpt_becomes_the_active_provider_53008e, " ChatGPT becomes the active provider.") : "")
                        + OrbitText.get(R.string.ui_android_asks_you_to_confirm_next_nothing_i_2f91c0, "\n\nAndroid asks you to confirm next. Nothing is deleted unless you ")
                        + OrbitText.get(R.string.ui_confirm_it_there_0c9f0d, "confirm it there."))
                .setNegativeButton(OrbitText.get(R.string.ui_cancel_19766e, "Cancel"), null)
                .setPositiveButton(OrbitText.get(R.string.ui_remove_orbit_local_52ed6b, "Remove Orbit Local"), null)
                .create();
        UiKit.styleOrbitDialog(dialog, this, true, () -> {
            Button remove = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            if (remove != null) {
                remove.setOnClickListener(v -> UiKit.handoffOrbitDialogToSystemSurface(
                        dialog, () -> removeOrbitLocal(SCOPE_EVERYTHING)));
            }
        });
        dialog.show();
    }

    /**
     * Android's uninstall first. Nothing of the user's is deleted before it answers.
     *
     * <p>Orbit never claims the component was removed. Whether it actually went is read back from
     * the package manager when this screen resumes, and only a confirmed absence lets any cleanup
     * run at all.
     */
    private void removeOrbitLocal(String scope) {
        removalMessage = "";
        setRemovalPending(scope);
        OrbitLocalUninstaller.Launch launch = OrbitLocalUninstaller.request(this);
        if (launch == OrbitLocalUninstaller.Launch.LAUNCHED) return;

        clearRemovalPending();
        if (launch == OrbitLocalUninstaller.Launch.NOT_INSTALLED) {
            // Removed from Android Settings while this screen was open. Settle the rest.
            finishOrbitSideRemoval(scope);
            removalMessage = OrbitText.get(R.string.ui_orbit_local_was_already_removed_47caf8, "Orbit Local was already removed.");
        } else {
            // Never silent again. The button did nothing, and the user is told so along with the
            // one thing they can do about it.
            removalMessage = OrbitLocalUninstaller.message(
                    OrbitLocalUninstaller.Stage.NOT_LAUNCHED);
        }
        rebuild();
    }

    /**
     * The case where a model outlived the component: nothing to uninstall, only data to delete.
     *
     * <p>Happens on a phone that downloaded the 1.6 GB model before Orbit Local was modular and
     * never installed the component, or one where the component was removed from Android Settings
     * with the migration never run. Android has no part to play, so this deletes directly — after
     * the same explicit confirmation.
     */
    private void confirmRemoveLegacyOnly() {
        long bytes = LocalModelStore.legacyBytes(this);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(OrbitText.get(R.string.ui_remove_old_local_model_data_6fdafa, "Remove old local model data?"))
                .setMessage(OrbitText.get(R.string.ui_about_c381a5, "About ") + LocalModelStore.formatBytes(bytes)
                        + OrbitText.get(R.string.ui_of_model_data_stored_by_an_earlier_version_678e23, " of model data stored by an earlier version of Orbit will be deleted, ")
                        + OrbitText.get(R.string.ui_freeing_that_storage_orbit_itself_your_con_01c4f6, "freeing that storage.\n\nOrbit itself, your conversations, memories, ")
                        + OrbitText.get(R.string.ui_routines_settings_and_cloud_providers_are_fc5589, "Routines, settings, and cloud providers are not affected. You can set ")
                        + OrbitText.get(R.string.ui_orbit_local_up_again_at_any_time_563aa2, "Orbit Local up again at any time."))
                .setNegativeButton(OrbitText.get(R.string.ui_cancel_19766e, "Cancel"), null)
                .setPositiveButton(OrbitText.get(R.string.ui_remove_c3812f, "Remove"), (d, w) -> {
                    LocalModelStore.deleteLegacy(this);
                    OrbitLocalInstaller.cleanup(this);
                    OrbitLocalProvider.invalidateStatus();
                    if (Prefs.PROVIDER_LOCAL.equals(Prefs.provider(this))) {
                        AiProviders.select(this, Prefs.PROVIDER_CHATGPT);
                    }
                    removalMessage = OrbitText.get(R.string.ui_old_local_model_data_removed_fa3391, "Old local model data removed.");
                    rebuild();
                })
                .create();
        UiKit.styleOrbitDialog(dialog, this, true);
        dialog.show();
    }

    // ---- removing just the component ---------------------------------------------------------------

    /**
     * Removing the component while leaving the rest of Orbit alone.
     *
     * <p>Separate from the global removal because they are genuinely different requests, and the
     * dialog says the one thing a person could not otherwise know: the model lives in the
     * component's own private app storage, so Android takes it along with the package. Pretending
     * the model could be kept would be a promise the platform's storage model cannot honour.
     */
    void confirmUninstallComponent() {
        long modelBytes = status == null ? 0L : status.modelTotalBytes;
        String modelWarning = modelBytes > 0L
                ? OrbitText.get(R.string.ui_the_local_ai_model_is_stored_inside_the_co_98debe, "\n\nThe local AI model is stored inside the component, so Android removes the ")
                        + LocalModelStore.formatBytes(modelBytes)
                        + OrbitText.get(R.string.ui_of_model_data_with_it_it_cannot_be_kept_se_d60bb5, " of model data with it. It cannot be kept separately.")
                : "";
        boolean active = Prefs.PROVIDER_LOCAL.equals(AiProviders.active(this).id());
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(OrbitText.get(R.string.ui_uninstall_the_component_56d013, "Uninstall the component?"))
                .setMessage(OrbitText.get(R.string.ui_orbit_stops_being_able_to_run_ai_on_this_d_fb9943, "Orbit stops being able to run AI on this device until the component ")
                        + OrbitText.get(R.string.ui_is_installed_again_30a853, "is installed again.") + modelWarning
                        + (active ? OrbitText.get(R.string.ui_chatgpt_becomes_the_active_provider_50414c, "\n\nChatGPT becomes the active provider.") : "")
                        + OrbitText.get(R.string.ui_android_asks_you_to_confirm_next_nothing_i_2f91c0, "\n\nAndroid asks you to confirm next. Nothing is deleted unless you ")
                        + OrbitText.get(R.string.ui_confirm_it_there_0c9f0d, "confirm it there."))
                .setNegativeButton(OrbitText.get(R.string.ui_cancel_19766e, "Cancel"), null)
                .setPositiveButton(OrbitText.get(R.string.ui_uninstall_component_cef50f, "Uninstall component"), null)
                .create();
        UiKit.styleOrbitDialog(dialog, this, true, () -> {
            Button uninstall = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            if (uninstall != null) {
                uninstall.setOnClickListener(v -> UiKit.handoffOrbitDialogToSystemSurface(
                        dialog, () -> removeOrbitLocal(SCOPE_COMPONENT)));
            }
        });
        dialog.show();
    }

    private void confirmDeleteModel(boolean partialOnly) {
        boolean active = Prefs.PROVIDER_LOCAL.equals(AiProviders.active(this).id());
        long bytes = status == null ? LocalModelStore.MODEL_SIZE_BYTES : status.modelTotalBytes;
        String title;
        String message;
        if (partialOnly) {
            title = OrbitText.get(R.string.ui_remove_downloaded_data_5973e7, "Remove downloaded data?");
            message = OrbitText.get(R.string.ui_about_c381a5, "About ") + LocalModelStore.formatBytes(bytes)
                    + OrbitText.get(R.string.ui_of_partly_downloaded_model_data_will_be_re_b850b3, " of partly downloaded model data will be removed, freeing that storage. ")
                    + OrbitText.get(R.string.ui_you_can_start_the_download_again_at_any_ti_0a98df, "You can start the download again at any time.");
        } else {
            title = OrbitText.get(R.string.ui_remove_594895, "Remove ") + LocalModelStore.MODEL_DISPLAY_NAME + "?";
            message = OrbitText.get(R.string.ui_about_c381a5, "About ") + LocalModelStore.formatBytes(bytes)
                    + OrbitText.get(R.string.ui_of_downloaded_model_data_will_be_removed_f_c474cb, " of downloaded model data will be removed, freeing that storage. ")
                    + OrbitText.get(R.string.ui_the_orbit_local_component_stays_installed_dd5780, "The Orbit Local component stays installed, so another model can be ")
                    + OrbitText.get(R.string.ui_downloaded_without_setting_it_up_again_b4990d, "downloaded without setting it up again.")
                    + (active ? OrbitText.get(R.string.ui_chatgpt_becomes_the_active_provider_53008e, " ChatGPT becomes the active provider.") : "")
                    + OrbitText.get(R.string.ui_your_conversations_settings_and_other_prov_6f60d0, " Your conversations, settings, and other providers are not affected.");
        }
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setNegativeButton(OrbitText.get(R.string.ui_cancel_19766e, "Cancel"), null)
                .setPositiveButton(partialOnly ? OrbitText.get(R.string.ui_remove_c3812f, "Remove") : OrbitText.get(R.string.ui_remove_model_1ab8f6, "Remove model"), (d, w) -> {
                    boolean wasActive = Prefs.PROVIDER_LOCAL.equals(AiProviders.active(this).id());
                    OrbitLocalClient.deleteModel(this);
                    OrbitLocalProvider.invalidateStatus();
                    if (wasActive) {
                        AiProviders.select(this, Prefs.PROVIDER_CHATGPT);
                        Toast.makeText(this, OrbitText.get(R.string.ui_model_removed_chatgpt_is_the_active_provid_f4a135, "Model removed. ChatGPT is the active provider again."),
                                Toast.LENGTH_LONG).show();
                    } else if (!partialOnly) {
                        Toast.makeText(this, OrbitText.get(R.string.ui_local_model_removed_e56781, "Local model removed"), Toast.LENGTH_SHORT).show();
                    }
                    bumpRefresh();
                })
                .create();
        UiKit.styleOrbitDialog(dialog, this, true);
        dialog.show();
    }

    // ---- shared button layout ---------------------------------------------------------------------

    /** Full-width primary, in Orbit's stacked settings button language. */
    private void addPrimaryAction(LinearLayout actions, Button button) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, UiKit.dp(this, 46));
        if (actions.getChildCount() > 0) lp.topMargin = UiKit.dp(this, 8);
        actions.addView(button, lp);
    }

    /** Compact right-aligned secondary action; never squeezed beside a primary. */
    private void addCompactAction(LinearLayout actions, Button button) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.END);
        row.addView(button, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, UiKit.dp(this, 40)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (actions.getChildCount() > 0) lp.topMargin = UiKit.dp(this, 8);
        actions.addView(row, lp);
    }

    /** Restrained destructive row, separated from the primary action by deliberate space. */
    private void addDestructiveAction(LinearLayout actions, Button button) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, UiKit.dp(this, 44));
        if (actions.getChildCount() > 0) lp.topMargin = UiKit.dp(this, 14);
        actions.addView(button, lp);
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
        b.setBackground(UiKit.rippleOutlined(UiKit.SURFACE_2, Color.rgb(53, 58, 72), UiKit.accent(this), 15, this));
        b.setMinHeight(0); b.setMinimumHeight(0); b.setStateListAnimator(null);
        UiKit.pressScale(b);
        return b;
    }

    /** Restrained destructive treatment, matching the Extensions manager's Remove control. */
    private Button dangerButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(UiKit.DANGER);
        b.setTextSize(14);
        b.setAllCaps(false);
        b.setBackground(UiKit.rippleOutlined(
                UiKit.blend(UiKit.DANGER, UiKit.SURFACE_2, 0.08f),
                UiKit.withAlpha(UiKit.DANGER, 110), UiKit.DANGER, 15, this));
        b.setMinHeight(0); b.setMinimumHeight(0); b.setStateListAnimator(null);
        UiKit.pressScale(b);
        return b;
    }
}
