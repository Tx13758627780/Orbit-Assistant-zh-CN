package com.orbit.assistant;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.method.LinkMovementMethod;
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
import java.util.Locale;

/** Orbit-styled stable-release update UI. Installation always remains user-confirmed Android UI. */
public final class UpdateActivity extends Activity {
    private TextView status;
    private TextView channelValue;
    private Button action;
    private LinearLayout downloadProgress;
    private ProgressBar downloadProgressBar;
    private TextView downloadPercent;
    private OrbitUpdater.Release availableRelease;
    private OrbitUpdater.Release readyRelease;
    private File readyApk;
    private OrbitUpdater.DownloadHandle download;
    private boolean waitingForInstallPermission;

    /** Interactive Back for this page. Its classification lives in OrbitNavigation. */
    private OrbitPredictiveBack navigation;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        UiKit.syncTheme(this);
        Window window = getWindow();
        window.setStatusBarColor(UiKit.BG);
        window.setNavigationBarColor(UiKit.BG);
        View content = buildContent();
        setContentView(content);
        UiKit.applyActivityInsets(this, content, true);
        navigation = OrbitPredictiveBack.install(this);
        OrbitUpdateWorker.schedule(this);
        OrbitUpdater.reconcilePendingInstall(this);

        if (OrbitDistribution.isPlay()) {
            showPlayState();
            return;
        }
        availableRelease = OrbitUpdater.loadCachedAvailable(this);
        if (availableRelease != null) showAvailableState(availableRelease, false);
        else showIdleState();
    }

    @Override protected void onResume() {
        super.onResume();
        UiPresence.enter(this);
        if (!OrbitDistribution.isPlay() && waitingForInstallPermission && readyApk != null
                && readyRelease != null &&
                OrbitUpdater.canRequestPackageInstalls(this)) {
            waitingForInstallPermission = false;
            verifyAndInstall();
        }
    }

    @Override protected void onPause() {
        UiPresence.leave(this);
        super.onPause();
    }

    @Override protected void onDestroy() {
        if (download != null) download.cancel();
        super.onDestroy();
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
        back.setColorFilter(UiKit.TEXT);
        back.setBackground(UiKit.ripple(UiKit.SURFACE_2, UiKit.TEXT, 18, this));
        back.setPadding(UiKit.dp(this, 10), UiKit.dp(this, 10), UiKit.dp(this, 10), UiKit.dp(this, 10));
        back.setOnClickListener(v -> navigation.performBack());
        header.addView(back, new LinearLayout.LayoutParams(UiKit.dp(this, 44), UiKit.dp(this, 44)));

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setPadding(UiKit.dp(this, 13), 0, 0, 0);
        titles.addView(UiKit.text(this, OrbitText.get(R.string.ui_about_updates_1b0c20, "About & updates"), 24, UiKit.TEXT, true));
        titles.addView(UiKit.text(this, OrbitDistribution.isPlay()
                ? OrbitText.get(R.string.ui_orbit_from_google_play_b63b08, "Orbit from Google Play") : OrbitText.get(R.string.ui_official_orbit_releases_cf4b18, "Official Orbit releases"), 13, UiKit.MUTED, false));
        header.addView(titles, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        page.addView(header);

        TextView section = UiKit.text(this, OrbitText.get(R.string.ui_orbit_assistant_7a6a45, "ORBIT ASSISTANT"), 12, UiKit.MUTED, true);
        section.setLetterSpacing(0.13f);
        LinearLayout.LayoutParams sectionLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sectionLp.setMargins(UiKit.dp(this, 4), UiKit.dp(this, 28), 0, UiKit.dp(this, 9));
        page.addView(section, sectionLp);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(UiKit.dp(this, 18), UiKit.dp(this, 18),
                UiKit.dp(this, 18), UiKit.dp(this, 18));
        card.setBackground(UiKit.outlined(
                UiKit.SURFACE, UiKit.withAlpha(UiKit.accent(this), 38), 24, this));
        card.setElevation(UiKit.dp(this, 2));

        // The name line carries a restrained Beta pill when the running APK is itself a Beta
        // build. That is a fact about the installed binary, not about the update channel, and the
        // two are deliberately shown separately.
        LinearLayout nameRow = new LinearLayout(this);
        nameRow.setOrientation(LinearLayout.HORIZONTAL);
        nameRow.setGravity(Gravity.CENTER_VERTICAL);
        nameRow.addView(UiKit.text(this, OrbitText.get(R.string.ui_orbit_assistant_81abac, "Orbit Assistant"), 18, UiKit.TEXT, true));
        if (OrbitVersion.installedIsBeta()) {
            TextView pill = betaPill(OrbitText.get(R.string.ui_beta_build_c82890, "BETA BUILD"));
            LinearLayout.LayoutParams pillLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            pillLp.setMargins(UiKit.dp(this, 9), UiKit.dp(this, 2), 0, 0);
            nameRow.addView(pill, pillLp);
        }
        card.addView(nameRow);

        TextView current = UiKit.text(this,
                OrbitText.get(R.string.ui_current_version_70ebe0, "Current version: ") + OrbitVersion.installedDisplayName()
                        + " (" + BuildConfig.VERSION_CODE + ")",
                13, UiKit.MUTED, false);
        current.setPadding(0, UiKit.dp(this, 5), 0, UiKit.dp(this, 11));
        card.addView(current);

        status = UiKit.text(this, idleMessage(), 14, UiKit.TEXT, false);
        status.setLineSpacing(0, 1.12f);
        status.setPadding(0, 0, 0, UiKit.dp(this, 14));
        card.addView(status);

        downloadProgress = new LinearLayout(this);
        downloadProgress.setOrientation(LinearLayout.VERTICAL);
        downloadProgress.setVisibility(View.GONE);
        downloadProgressBar = UiKit.horizontalProgress(this);
        downloadProgress.addView(downloadProgressBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, UiKit.dp(this, 8)));
        downloadPercent = UiKit.text(this, "0%", 11, UiKit.MUTED, false);
        downloadPercent.setGravity(Gravity.END);
        downloadPercent.setPadding(0, UiKit.dp(this, 5), UiKit.dp(this, 1), 0);
        downloadProgress.addView(downloadPercent);
        LinearLayout.LayoutParams progressLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        progressLp.setMargins(0, 0, 0, UiKit.dp(this, 14));
        card.addView(downloadProgress, progressLp);

        action = primaryButton(OrbitText.get(R.string.ui_check_for_updates_f26f32, "Check for updates"));
        card.addView(action, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, UiKit.dp(this, 48)));

        Button whatsNew = secondaryButton(OrbitText.get(R.string.ui_what_s_new_561b17, "What's New"));
        whatsNew.setOnClickListener(v -> startActivity(new Intent(this, WhatsNewActivity.class)));
        LinearLayout.LayoutParams whatsNewLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, UiKit.dp(this, 44));
        whatsNewLp.setMargins(0, UiKit.dp(this, 10), 0, 0);
        card.addView(whatsNew, whatsNewLp);

        // The update channel and Orbit's own update notifications belong to the GitHub updater. In
        // the Play edition Google Play owns both: its testing tracks are the Beta channel, and it
        // notifies about updates itself. Showing either here would be a control that does nothing.
        if (!OrbitDistribution.isPlay()) addGitHubUpdateControls(card);
        page.addView(card);

        TextView roadmapSection = UiKit.text(this, OrbitText.get(R.string.ui_roadmap_020784, "ROADMAP"), 12, UiKit.MUTED, true);
        roadmapSection.setLetterSpacing(0.13f);
        LinearLayout.LayoutParams roadmapSectionLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        roadmapSectionLp.setMargins(UiKit.dp(this, 4), UiKit.dp(this, 22), 0, UiKit.dp(this, 9));
        page.addView(roadmapSection, roadmapSectionLp);

        LinearLayout roadmap = new LinearLayout(this);
        roadmap.setOrientation(LinearLayout.HORIZONTAL);
        roadmap.setGravity(Gravity.CENTER_VERTICAL);
        roadmap.setPadding(UiKit.dp(this, 18), UiKit.dp(this, 16),
                UiKit.dp(this, 16), UiKit.dp(this, 16));
        roadmap.setBackground(UiKit.rippleOutlined(UiKit.SURFACE,
                UiKit.withAlpha(UiKit.accent(this), 38), UiKit.accent(this), 22, this));
        roadmap.setElevation(UiKit.dp(this, 2));
        roadmap.setClickable(true);
        roadmap.setFocusable(true);
        LinearLayout roadmapCopy = new LinearLayout(this);
        roadmapCopy.setOrientation(LinearLayout.VERTICAL);
        roadmapCopy.addView(UiKit.text(this, OrbitText.get(R.string.ui_roadmap_92375f, "Roadmap"), 17, UiKit.TEXT, true));
        TextView roadmapDescription = UiKit.text(this,
                OrbitText.get(R.string.ui_see_what_s_next_for_orbit_65fb38, "See what's next for Orbit"), 12, UiKit.MUTED, false);
        roadmapDescription.setPadding(0, UiKit.dp(this, 3), 0, 0);
        roadmapCopy.addView(roadmapDescription);
        roadmap.addView(roadmapCopy, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        roadmap.addView(UiKit.text(this, "›", 28, UiKit.accent(this), false));
        roadmap.setOnClickListener(v -> startActivity(new Intent(this, RoadmapActivity.class)));
        UiKit.pressScale(roadmap);
        page.addView(roadmap);

        page.addView(linksSection(), sectionLp(22));
        page.addView(linksCard());

        TextView privacy = UiKit.text(this, OrbitDistribution.isPlay()
                ? OrbitText.get(R.string.ui_this_copy_of_orbit_was_installed_from_goog_07669b, "This copy of Orbit was installed from Google Play, which delivers and verifies its updates. Orbit itself never downloads or installs app updates.")
                : OrbitText.get(R.string.ui_orbit_checks_only_the_public_lpnovi_orbit_a4b24a, "Orbit checks only the public lpnovi/Orbit-Assistant releases — stable releases, plus official Beta prereleases when you have joined the Beta channel. It sends no account credentials, never downloads without your approval, verifies the APK checksum, package, version and permanent signing certificate, then uses Android's normal installer. Every build is verified the same way, whichever channel it came from."),
                12, UiKit.MUTED, false);
        privacy.setLineSpacing(0, 1.12f);
        LinearLayout.LayoutParams privacyLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        privacyLp.setMargins(UiKit.dp(this, 3), UiKit.dp(this, 15), UiKit.dp(this, 3), 0);
        page.addView(privacy, privacyLp);
        UiKit.applyTypography(page);
        return scroll;
    }

    /** One external page the About screen links to. */
    static final class AboutLink {
        final String title;
        final String subtitle;
        final String uri;

        AboutLink(String title, String subtitle, String uri) {
            this.title = title;
            this.subtitle = subtitle;
            this.uri = uri;
        }
    }

    /** Everything on the Links card, in order. A new link is one more entry here. */
    static final AboutLink[] LINKS = {
            new AboutLink("GitHub", "Source code, releases and issues",
                    "https://github.com/lpnovi/Orbit-Assistant"),
    };

    private TextView linksSection() {
        TextView label = UiKit.text(this, OrbitText.get(R.string.ui_links_55da65, "LINKS"), 12, UiKit.MUTED, true);
        label.setLetterSpacing(0.13f);
        return label;
    }

    private LinearLayout.LayoutParams sectionLp(int topDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(UiKit.dp(this, 4), UiKit.dp(this, topDp), 0, UiKit.dp(this, 9));
        return lp;
    }

    private LinearLayout linksCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(UiKit.dp(this, 6), UiKit.dp(this, 6), UiKit.dp(this, 6), UiKit.dp(this, 6));
        card.setBackground(UiKit.outlined(
                UiKit.SURFACE, UiKit.withAlpha(UiKit.accent(this), 38), 22, this));
        card.setElevation(UiKit.dp(this, 2));
        for (AboutLink link : LINKS) card.addView(linkRow(link));
        return card;
    }

    /** A full-width tappable row: title, subtitle and an external-link mark, opened in a browser. */
    private View linkRow(AboutLink link) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(UiKit.dp(this, 12), UiKit.dp(this, 12), UiKit.dp(this, 12), UiKit.dp(this, 12));
        row.setMinimumHeight(UiKit.dp(this, 56));
        row.setBackground(UiKit.ripple(UiKit.SURFACE, UiKit.accent(this), 16, this));
        row.setClickable(true);
        row.setFocusable(true);
        row.setContentDescription(link.title + ", " + link.subtitle + OrbitText.get(R.string.ui_opens_in_your_browser_b23909, ". Opens in your browser."));
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(UiKit.text(this, link.title, 16, UiKit.TEXT, true));
        TextView subtitle = UiKit.text(this, link.subtitle, 12, UiKit.MUTED, false);
        subtitle.setPadding(0, UiKit.dp(this, 3), 0, 0);
        copy.addView(subtitle);
        row.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView mark = UiKit.text(this, "↗", 18, UiKit.accent(this), true);
        mark.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        row.addView(mark);
        row.setTag(link.uri);
        row.setOnClickListener(v -> openExternal(this, link.uri));
        UiKit.pressScale(row);
        return row;
    }

    /**
     * Hands a web address to the user's browser. Never a WebView. A phone with nothing that can
     * open it gets a short message instead of a crash.
     */
    static boolean openExternal(android.content.Context c, String uri) {
        try {
            c.startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(uri))
                    .addCategory(Intent.CATEGORY_BROWSABLE));
            return true;
        } catch (android.content.ActivityNotFoundException | SecurityException e) {
            Toast.makeText(c, OrbitText.get(R.string.ui_no_app_on_this_phone_can_open_that_link_edd61d, "No app on this phone can open that link"), Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    /** The GitHub edition's update channel and update-notification controls. */
    private void addGitHubUpdateControls(LinearLayout card) {
        card.addView(channelRow());

        OrbitSwitch updateNotifications = new OrbitSwitch(this);
        updateNotifications.setChecked(Prefs.updateNotifications(this), false);
        updateNotifications.setOnCheckedChangeListener((button, checked) -> {
            Prefs.get(this).edit().putBoolean(Prefs.UPDATE_NOTIFICATIONS, checked).apply();
            if (!checked) OrbitUpdateNotifier.cancel(this);
        });
        card.addView(UiKit.switchRow(this, OrbitText.get(R.string.ui_update_notifications_175ba3, "Update notifications"),
                OrbitText.get(R.string.ui_notify_me_when_a_new_orbit_version_is_avai_25d9a2, "Notify me when a new Orbit version is available"), updateNotifications));
    }

    /**
     * The Play edition's whole update story: Google Play does it.
     *
     * <p>The one action opens Orbit's Play listing, which is where Play offers an update that has
     * not installed yet. Nothing here reaches GitHub.
     */
    private void showPlayState() {
        hideDownloadProgress();
        status.setText(OrbitDistribution.PLAY_UPDATES_MESSAGE);
        status.setTextColor(UiKit.TEXT);
        setAction(OrbitText.get(R.string.ui_open_in_google_play_0a61ad, "Open in Google Play"), () -> {
            if (!OrbitDistribution.openPlayListing(this)) {
                status.setText(OrbitText.get(R.string.ui_google_play_could_not_be_opened_on_this_de_ba1931, "Google Play could not be opened on this device."));
                status.setTextColor(UiKit.DANGER);
            }
        }, true);
    }

    private void showIdleState() {
        hideDownloadProgress();
        status.setText(idleMessage());
        status.setTextColor(UiKit.TEXT);
        setAction(OrbitText.get(R.string.ui_check_for_updates_f26f32, "Check for updates"), this::checkForUpdates, true);
    }

    private String idleMessage() {
        return Prefs.betaChannel(this)
                ? OrbitText.get(R.string.ui_ready_to_check_for_the_newest_orbit_releas_5e9274, "Ready to check for the newest Orbit release, including Betas.")
                : OrbitText.get(R.string.ui_ready_to_check_for_a_stable_orbit_release_867b0e, "Ready to check for a stable Orbit release.");
    }

    /**
     * What Orbit says when there is nothing newer to install.
     *
     * <p>Three genuinely different situations. On Beta, Orbit has looked at prereleases too. On
     * Stable, it is simply current. And a Stable-channel user who is still running a Beta build is
     * neither of those: there is a newer build installed than the newest Stable release, and Orbit
     * will not downgrade it, so it says exactly that instead of pretending to be up to date.
     */
    private String upToDateMessage() {
        if (Prefs.betaChannel(this)) return OrbitText.get(R.string.ui_you_re_on_the_newest_available_orbit_build_d4ec60, "You're on the newest available Orbit build.");
        if (OrbitVersion.installedIsBeta()) {
            return OrbitText.get(R.string.ui_you_re_running_a_newer_beta_build_orbit_wi_1049b0, "You're running a newer Beta build. Orbit will return to Stable updates when a ")
                    + OrbitText.get(R.string.ui_newer_stable_release_becomes_available_d83856, "newer Stable release becomes available.");
        }
        return OrbitText.get(R.string.ui_orbit_is_up_to_date_9820fe, "Orbit is up to date.");
    }

    // ---- update channel -------------------------------------------------------------------------

    private LinearLayout channelRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(UiKit.dp(this, 14), UiKit.dp(this, 12),
                UiKit.dp(this, 12), UiKit.dp(this, 12));
        row.setBackground(UiKit.rippleOutlined(UiKit.SURFACE_2,
                UiKit.withAlpha(UiKit.accent(this), 38), UiKit.accent(this), 16, this));
        row.setClickable(true);
        row.setFocusable(true);

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(UiKit.text(this, OrbitText.get(R.string.ui_update_channel_d4fc42, "Update channel"), 15, UiKit.TEXT, true));
        channelValue = UiKit.text(this, channelLabel(), 12, UiKit.accent(this), false);
        channelValue.setPadding(0, UiKit.dp(this, 3), 0, 0);
        copy.addView(channelValue);
        row.addView(copy, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        row.addView(UiKit.text(this, "›", 26, UiKit.accent(this), false));

        row.setOnClickListener(v -> showChannelSelector());
        UiKit.pressScale(row);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowLp.setMargins(0, UiKit.dp(this, 12), 0, UiKit.dp(this, 2));
        row.setLayoutParams(rowLp);
        return row;
    }

    private String channelLabel() {
        return Prefs.betaChannel(this) ? OrbitText.get(R.string.ui_beta_703390, "Beta") : OrbitText.get(R.string.ui_stable_90ee30, "Stable");
    }

    /**
     * An Orbit-styled channel picker.
     *
     * <p>A dialog rather than the compact popup menu, because each option needs a second line
     * explaining what it commits the user to, and the popup's rows are a single fixed height.
     */
    private void showChannelSelector() {
        boolean beta = Prefs.betaChannel(this);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(UiKit.dp(this, 22), UiKit.dp(this, 20),
                UiKit.dp(this, 22), UiKit.dp(this, 6));
        TextView title = UiKit.text(this, OrbitText.get(R.string.ui_update_channel_d4fc42, "Update channel"), 20, UiKit.TEXT, true);
        title.setAccessibilityHeading(true);
        content.addView(title);

        AlertDialog[] holder = new AlertDialog[1];
        content.addView(channelOption(OrbitText.get(R.string.ui_stable_90ee30, "Stable"), OrbitText.get(R.string.ui_recommended_tested_releases_a2145e, "Recommended · tested releases"), !beta,
                () -> {
                    if (holder[0] != null) holder[0].dismiss();
                    applyChannel(Prefs.CHANNEL_STABLE);
                }), optionLp(14));
        content.addView(channelOption(OrbitText.get(R.string.ui_beta_703390, "Beta"), OrbitText.get(R.string.ui_early_access_may_contain_bugs_ee71ae, "Early access · may contain bugs"), beta,
                () -> {
                    if (holder[0] != null) holder[0].dismiss();
                    // Already enrolled: choosing Beta again changes nothing and must not
                    // re-ask a question the user has already answered.
                    if (Prefs.betaChannel(this)) return;
                    confirmJoinBeta();
                }), optionLp(9));
        UiKit.applyTypography(content);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setForceDarkAllowed(false);
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(scroll)
                .setNegativeButton(OrbitText.get(R.string.ui_cancel_19766e, "Cancel"), null)
                .create();
        holder[0] = dialog;
        styleOrbitDialog(dialog);
        dialog.show();
    }

    private LinearLayout.LayoutParams optionLp(int topDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, UiKit.dp(this, topDp), 0, 0);
        return lp;
    }

    private LinearLayout channelOption(String name, String description, boolean selected,
                                       Runnable onChosen) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(UiKit.dp(this, 15), UiKit.dp(this, 13),
                UiKit.dp(this, 13), UiKit.dp(this, 13));
        row.setBackground(UiKit.rippleOutlined(
                selected ? UiKit.blend(UiKit.accent(this), UiKit.SURFACE_2, 0.16f) : UiKit.SURFACE_2,
                UiKit.withAlpha(UiKit.accent(this), selected ? 110 : 38),
                UiKit.accent(this), 16, this));
        row.setClickable(true);
        row.setFocusable(true);

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(UiKit.text(this, name, 16, UiKit.TEXT, selected));
        TextView detail = UiKit.text(this, description, 12, UiKit.MUTED, false);
        detail.setLineSpacing(0, 1.1f);
        detail.setPadding(0, UiKit.dp(this, 3), 0, 0);
        copy.addView(detail);
        row.addView(copy, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        row.addView(UiKit.text(this, selected ? "●" : "", 15, UiKit.accent(this), true));

        row.setOnClickListener(v -> onChosen.run());
        UiKit.pressScale(row);
        return row;
    }

    /**
     * The one confirmation Orbit shows before enrolling a device in Beta.
     *
     * <p>Honest about the risk and honest about what is not at risk: a Beta build is less tested,
     * not less trusted, and it is signed and verified exactly like every Stable release. Shown
     * again if someone leaves Beta and later comes back, because that is a fresh decision to
     * accept the risk.
     */
    private void confirmJoinBeta() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(UiKit.dp(this, 24), UiKit.dp(this, 20),
                UiKit.dp(this, 24), UiKit.dp(this, 6));
        TextView title = UiKit.text(this, OrbitText.get(R.string.ui_join_the_beta_channel_a583dc, "Join the Beta channel?"), 20, UiKit.TEXT, true);
        title.setAccessibilityHeading(true);
        content.addView(title);
        TextView body = UiKit.text(this,
                OrbitText.get(R.string.ui_beta_builds_give_you_new_orbit_features_ea_3aabec, "Beta builds give you new Orbit features earlier, but they may contain bugs, ")
                        + OrbitText.get(R.string.ui_crashes_regressions_increased_battery_use_199fc6, "crashes, regressions, increased battery use, or unfinished behavior.\n\n")
                        + OrbitText.get(R.string.ui_beta_updates_are_still_officially_signed_a_1a68df, "Beta updates are still officially signed and verified by Orbit.\n\n")
                        + OrbitText.get(R.string.ui_you_can_return_to_stable_at_any_time_orbit_4f5bb1, "You can return to Stable at any time. Orbit will never automatically ")
                        + OrbitText.get(R.string.ui_downgrade_your_installed_version_079617, "downgrade your installed version."),
                14, UiKit.TEXT, false);
        body.setLineSpacing(0, 1.14f);
        body.setPadding(0, UiKit.dp(this, 12), 0, 0);
        content.addView(body);
        UiKit.applyTypography(content);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setForceDarkAllowed(false);
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(scroll)
                .setNegativeButton(OrbitText.get(R.string.ui_cancel_19766e, "Cancel"), null)
                .setPositiveButton(OrbitText.get(R.string.ui_join_beta_9bf033, "Join Beta"), (d, which) -> applyChannel(Prefs.CHANNEL_BETA))
                .create();
        styleOrbitDialog(dialog);
        dialog.show();
    }

    /**
     * Commits a channel change and puts the screen back into a clean, uncached state.
     *
     * <p>{@link Prefs#setUpdateChannel} drops the discovery state the old channel owned, so
     * anything still on screen from the previous channel is stale by definition and is cleared
     * here rather than left to be tapped.
     */
    private void applyChannel(String channel) {
        if (Prefs.normalizeChannel(channel).equals(Prefs.updateChannel(this))) return;
        Prefs.setUpdateChannel(this, channel);
        availableRelease = null;
        readyRelease = null;
        readyApk = null;
        if (channelValue != null) {
            channelValue.setText(channelLabel());
            channelValue.setTextColor(UiKit.accent(this));
        }
        showIdleState();
    }

    private TextView betaPill(String label) {
        TextView pill = UiKit.text(this, label, 10, UiKit.onAccent(this), true);
        pill.setLetterSpacing(0.09f);
        pill.setPadding(UiKit.dp(this, 8), UiKit.dp(this, 3), UiKit.dp(this, 8), UiKit.dp(this, 4));
        pill.setBackground(UiKit.rounded(UiKit.accent(this), 9, this));
        return pill;
    }

    private void checkForUpdates() {
        hideDownloadProgress();
        status.setText(OrbitText.get(R.string.ui_checking_ec963f, "Checking…"));
        status.setTextColor(UiKit.TEXT);
        setAction(OrbitText.get(R.string.ui_checking_ec963f, "Checking…"), null, false);
        OrbitUpdater.checkAsync(this, new OrbitUpdater.CheckCallback() {
            @Override public void onResult(OrbitUpdater.CheckResult result) {
                runOnUiThread(() -> {
                    if (!alive()) return;
                    if (result.updateAvailable && result.release != null) {
                        availableRelease = result.release;
                        showAvailableState(result.release, true);
                    } else {
                        availableRelease = null;
                        status.setText(upToDateMessage());
                        status.setTextColor(UiKit.SUCCESS);
                        setAction(OrbitText.get(R.string.ui_check_again_fb7099, "Check again"), UpdateActivity.this::checkForUpdates, true);
                    }
                });
            }

            @Override public void onError(String message) {
                runOnUiThread(() -> {
                    if (!alive()) return;
                    status.setText(OrbitText.get(R.string.ui_network_update_service_error_383fe8, "Network/update service error\n") + message);
                    status.setTextColor(Color.rgb(239, 105, 105));
                    setAction(OrbitText.get(R.string.ui_try_again_d8b839, "Try again"), UpdateActivity.this::checkForUpdates, true);
                });
            }
        });
    }

    private void showAvailableState(OrbitUpdater.Release release, boolean showDialog) {
        hideDownloadProgress();
        status.setText(OrbitText.get(R.string.ui_update_available_orbit_assistant_v_ebeb97, "Update available: Orbit Assistant v") + release.displayName());
        status.setTextColor(UiKit.accent(this));
        setAction(OrbitText.get(R.string.ui_view_update_97850a, "View update"), () -> showUpdateDialog(release), true);
        if (showDialog) showUpdateDialog(release);
    }

    private void showUpdateDialog(OrbitUpdater.Release release) {
        String notes = release.releaseNotes.isEmpty()
                ? OrbitText.get(R.string.ui_see_the_official_github_release_for_detail_ec98b4, "See the official GitHub Release for details.")
                : release.releaseNotes;

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(UiKit.dp(this, 24), UiKit.dp(this, 20),
                UiKit.dp(this, 24), UiKit.dp(this, 6));
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = UiKit.text(this,
                OrbitText.get(R.string.ui_orbit_assistant_v_8b1325, "Orbit Assistant v") + release.displayName(), 20, UiKit.TEXT, true);
        title.setAccessibilityHeading(true);
        titleRow.addView(title, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        if (release.isBeta()) {
            LinearLayout.LayoutParams pillLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            pillLp.setMargins(UiKit.dp(this, 10), UiKit.dp(this, 2), 0, 0);
            titleRow.addView(betaPill(OrbitText.get(R.string.ui_beta_639181, "BETA")), pillLp);
        }
        content.addView(titleRow);
        if (release.isBeta()) {
            // One short line, once, at the moment of installing. The user opted into Beta
            // already; the full warning is not repeated for every update.
            TextView betaNote = UiKit.text(this,
                    OrbitText.get(R.string.ui_beta_build_may_be_less_stable_than_a_norma_7101ad, "Beta build · may be less stable than a normal Orbit release."),
                    12, UiKit.MUTED, false);
            betaNote.setLineSpacing(0, 1.1f);
            betaNote.setPadding(0, UiKit.dp(this, 7), 0, 0);
            content.addView(betaNote);
        }
        TextView body = UiKit.text(this, "", 14, UiKit.TEXT, false);
        body.setText(OrbitMarkdown.render(this, notes));
        body.setLinkTextColor(UiKit.linkColorOn(this, UiKit.SURFACE));
        body.setMovementMethod(LinkMovementMethod.getInstance());
        body.setLinksClickable(true);
        body.setLineSpacing(0, 1.12f);
        body.setPadding(0, UiKit.dp(this, 12), 0, 0);
        content.addView(body);
        TextView size = UiKit.text(this,
                OrbitText.get(R.string.ui_download_size_fcfd9e, "Download size: ") + formatSize(release.apkSize), 12, UiKit.MUTED, false);
        size.setPadding(0, UiKit.dp(this, 13), 0, 0);
        content.addView(size);
        UiKit.applyTypography(content);
        ScrollView dialogScroll = new ScrollView(this);
        dialogScroll.setFillViewport(true);
        dialogScroll.setForceDarkAllowed(false);
        dialogScroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogScroll)
                .setNegativeButton(OrbitText.get(R.string.ui_later_73b6e4, "Later"), null)
                .setPositiveButton(OrbitText.get(R.string.ui_download_install_4c9a6b, "Download & install"), (d, which) -> startDownload(release))
                .create();
        styleOrbitDialog(dialog);
        dialog.show();
    }

    private void startDownload(OrbitUpdater.Release release) {
        availableRelease = release;
        status.setText(OrbitText.get(R.string.ui_downloading_update_014368, "Downloading update"));
        status.setTextColor(UiKit.TEXT);
        showDownloadProgress(-1);
        setAction(OrbitText.get(R.string.ui_cancel_download_de5b23, "Cancel download"), () -> {
            if (download != null) download.cancel();
        }, true);
        download = OrbitUpdater.downloadAsync(this, release, new OrbitUpdater.DownloadCallback() {
            @Override public void onProgress(int percent) {
                runOnUiThread(() -> {
                    if (!alive()) return;
                    showDownloadProgress(percent);
                });
            }

            @Override public void onVerifying() {
                runOnUiThread(() -> {
                    if (!alive()) return;
                    hideDownloadProgress();
                    status.setText(OrbitText.get(R.string.ui_verifying_63bbd0, "Verifying…"));
                    setAction(OrbitText.get(R.string.ui_verifying_63bbd0, "Verifying…"), null, false);
                });
            }

            @Override public void onReady(File apk) {
                runOnUiThread(() -> {
                    if (!alive()) return;
                    hideDownloadProgress();
                    download = null;
                    readyApk = apk;
                    readyRelease = release;
                    status.setText(OrbitText.get(R.string.ui_ready_to_install_bda18b, "Ready to install"));
                    status.setTextColor(UiKit.SUCCESS);
                    setAction(OrbitText.get(R.string.ui_install_update_cada42, "Install update"), UpdateActivity.this::attemptInstall, true);
                    attemptInstall();
                });
            }

            @Override public void onError(String message, boolean verificationFailure) {
                runOnUiThread(() -> {
                    if (!alive()) return;
                    hideDownloadProgress();
                    download = null;
                    status.setText((verificationFailure ? OrbitText.get(R.string.ui_verification_failed_b1f933, "Verification failed\n") :
                            OrbitText.get(R.string.ui_network_update_service_error_383fe8, "Network/update service error\n")) + message);
                    status.setTextColor(Color.rgb(239, 105, 105));
                    setAction(OrbitText.get(R.string.ui_try_again_d8b839, "Try again"), () -> startDownload(release), true);
                });
            }

            @Override public void onCancelled() {
                runOnUiThread(() -> {
                    if (!alive()) return;
                    hideDownloadProgress();
                    download = null;
                    status.setText(OrbitText.get(R.string.ui_download_cancelled_79310f, "Download cancelled."));
                    status.setTextColor(UiKit.TEXT);
                    setAction(OrbitText.get(R.string.ui_download_install_4c9a6b, "Download & install"), () -> startDownload(release), true);
                });
            }
        });
    }

    private void attemptInstall() {
        hideDownloadProgress();
        if (readyApk == null || readyRelease == null) return;
        if (!OrbitUpdater.canRequestPackageInstalls(this)) {
            waitingForInstallPermission = true;
            status.setText(OrbitText.get(R.string.ui_android_needs_permission_to_install_update_f18a92, "Android needs permission to install updates from Orbit. Allow this source, then return to Orbit."));
            status.setTextColor(UiKit.TEXT);
            setAction(OrbitText.get(R.string.ui_allow_installation_c0605f, "Allow installation"), () -> OrbitUpdater.openUnknownSourcesSettings(this), true);
            OrbitUpdater.openUnknownSourcesSettings(this);
            return;
        }
        verifyAndInstall();
    }

    private void verifyAndInstall() {
        if (readyApk == null || readyRelease == null) return;
        hideDownloadProgress();
        status.setText(OrbitText.get(R.string.ui_verifying_63bbd0, "Verifying…"));
        setAction(OrbitText.get(R.string.ui_verifying_63bbd0, "Verifying…"), null, false);
        File apk = readyApk;
        OrbitUpdater.Release release = readyRelease;
        OrbitUpdater.verifyAsync(this, apk, release, new OrbitUpdater.VerifyCallback() {
            @Override public void onVerified() {
                runOnUiThread(() -> {
                    if (!alive()) return;
                    try {
                        status.setText(OrbitText.get(R.string.ui_ready_to_install_bda18b, "Ready to install"));
                        status.setTextColor(UiKit.SUCCESS);
                        setAction(OrbitText.get(R.string.ui_install_update_cada42, "Install update"), UpdateActivity.this::attemptInstall, true);
                        OrbitUpdater.launchPackageInstaller(UpdateActivity.this, apk, release);
                    } catch (Exception e) {
                        status.setText(OrbitText.get(R.string.ui_android_could_not_open_the_package_install_2f977b, "Android could not open the package installer."));
                        status.setTextColor(Color.rgb(239, 105, 105));
                    }
                });
            }

            @Override public void onError(String message) {
                runOnUiThread(() -> {
                    if (!alive()) return;
                    readyApk = null;
                    readyRelease = null;
                    status.setText(OrbitText.get(R.string.ui_verification_failed_b1f933, "Verification failed\n") + message);
                    status.setTextColor(Color.rgb(239, 105, 105));
                    setAction(OrbitText.get(R.string.ui_check_again_fb7099, "Check again"), UpdateActivity.this::checkForUpdates, true);
                });
            }
        });
    }

    private Button primaryButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(UiKit.onAccent(this));
        button.setTextSize(14);
        button.setAllCaps(false);
        button.setBackground(UiKit.ripple(
                UiKit.accent(this), UiKit.onAccent(this), 15, this));
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setStateListAnimator(null);
        UiKit.pressScale(button);
        return button;
    }

    private Button secondaryButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(UiKit.TEXT);
        button.setTextSize(14);
        button.setAllCaps(false);
        button.setBackground(UiKit.rippleOutlined(UiKit.SURFACE_2,
                Color.rgb(53, 58, 72), UiKit.accent(this), 15, this));
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setStateListAnimator(null);
        UiKit.pressScale(button);
        return button;
    }

    private void setAction(String text, Runnable runnable, boolean enabled) {
        action.setText(text);
        action.setEnabled(enabled);
        action.setAlpha(enabled ? 1f : 0.62f);
        action.setOnClickListener(runnable == null ? null : v -> runnable.run());
    }

    private void styleOrbitDialog(AlertDialog dialog) {
        UiKit.styleOrbitDialog(dialog, this, false);
    }

    private void showDownloadProgress(int percent) {
        if (downloadProgress == null || downloadProgressBar == null || downloadPercent == null) return;
        downloadProgress.setVisibility(View.VISIBLE);
        if (percent >= 0) {
            int safePercent = Math.max(0, Math.min(100, percent));
            downloadProgressBar.setProgress(safePercent, true);
            downloadPercent.setText(safePercent + "%");
        } else {
            downloadProgressBar.setProgress(0, false);
            downloadPercent.setText(OrbitText.get(R.string.ui_preparing_download_f40b15, "Preparing download…"));
        }
    }

    private void hideDownloadProgress() {
        if (downloadProgress != null) downloadProgress.setVisibility(View.GONE);
    }

    private boolean alive() {
        return !isFinishing() && !isDestroyed();
    }

    private static String formatSize(long bytes) {
        if (bytes <= 0L) return OrbitText.get(R.string.ui_unknown_b764cd, "Unknown");
        return String.format(Locale.US, "%.1f MB", bytes / (1024d * 1024d));
    }
}
