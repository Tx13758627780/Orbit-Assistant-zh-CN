package com.orbit.assistant;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Gravity;
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

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Orbit-native manager and SAF installer for declarative .orbitext packages. */
public final class ExtensionsActivity extends Activity {
    private static final String LOG_TAG = "OrbitExtensions";
    private static final int REQ_IMPORT_EXTENSION = 7010;
    private static final String FIRST_PARTY_ASSET_DIR = "orbit-extensions/";
    private static final String[] FIRST_PARTY_ASSETS = {
            "orbit-web-tools.orbitext",
            "developer-tools.orbitext",
            "quick-links.orbitext",
            "discord-webhook.orbitext",
            "ntfy-notifications.orbitext"
    };
    private ScrollView activeScroll;
    private int bundledLoadFailures;

    /** Interactive Back for this page. Its classification lives in OrbitNavigation. */
    private OrbitPredictiveBack navigation;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        UiKit.syncTheme(this);
        navigation = OrbitPredictiveBack.install(this);
        Window window = getWindow();
        window.setStatusBarColor(UiKit.BG);
        window.setNavigationBarColor(UiKit.BG);
        // Re-establish the v0.7.0.2 manager shell before any v2 parser or
        // secure-configuration code can participate in Activity startup.
        try {
            renderContent(false, -1);
            window.getDecorView().post(() -> renderFullContent(-1));
        } catch (RuntimeException | LinkageError error) {
            showRecovery(error);
        }
    }

    @Override protected void onResume() {
        super.onResume();
        UiPresence.enter(this);
    }

    @Override protected void onPause() {
        UiPresence.leave(this);
        super.onPause();
    }

    private void rebuild() {
        renderFullContent(-1);
    }

    private void rebuildPreservingPosition() {
        renderFullContent(activeScroll == null ? 0 : activeScroll.getScrollY());
    }

    private void renderFullContent(int requestedScrollY) {
        try {
            renderContent(true, requestedScrollY);
        } catch (RuntimeException | LinkageError error) {
            logFailure("screen_render", error);
            showRecovery(error);
        }
    }

    private void renderContent(boolean includeV2, int requestedScrollY) {
        View content = buildContent(includeV2);
        setContentView(content);
        UiKit.applyActivityInsets(this, content, true);
        if (requestedScrollY >= 0 && activeScroll != null) {
            ScrollView target = activeScroll;
            ViewTreeObserver.OnGlobalLayoutListener listener = new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override public void onGlobalLayout() {
                    ViewTreeObserver observer = target.getViewTreeObserver();
                    if (observer.isAlive()) observer.removeOnGlobalLayoutListener(this);
                    int childHeight = target.getChildCount() == 0 ? 0 : target.getChildAt(0).getHeight();
                    int maximum = Math.max(0, childHeight - target.getHeight());
                    target.scrollTo(0, Math.min(requestedScrollY, maximum));
                }
            };
            target.getViewTreeObserver().addOnGlobalLayoutListener(listener);
        }
    }

    private View buildContent(boolean includeV2) {
        ScrollView scroll = new ScrollView(this);
        activeScroll = scroll;
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
        back.setContentDescription(OrbitText.get(R.string.ui_back_to_settings_ea37ec, "Back to Settings"));
        back.setPadding(UiKit.dp(this, 10), UiKit.dp(this, 10),
                UiKit.dp(this, 10), UiKit.dp(this, 10));
        back.setOnClickListener(v -> navigation.performBack());
        UiKit.pressScale(back);
        header.addView(back, new LinearLayout.LayoutParams(UiKit.dp(this, 44), UiKit.dp(this, 44)));

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setPadding(UiKit.dp(this, 13), 0, 0, 0);
        titles.addView(UiKit.text(this, OrbitText.get(R.string.ui_extensions_fe3436, "Extensions"), 25, UiKit.TEXT, true));
        titles.addView(UiKit.text(this, OrbitText.get(R.string.ui_add_integrations_and_new_actions_to_orbit_8ae30a, "Add integrations and new actions to Orbit"), 12, UiKit.MUTED, false));
        header.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        page.addView(header);

        TextView intro = UiKit.text(this,
                OrbitText.get(R.string.ui_orbit_extensions_are_reviewed_declarative_7968fc, "Orbit Extensions are reviewed, declarative action packages. They cannot run code or access chats, Memory, screen context, files, credentials, or Android permissions."),
                13, UiKit.MUTED, false);
        intro.setLineSpacing(0, 1.13f);
        LinearLayout.LayoutParams introLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        introLp.setMargins(UiKit.dp(this, 2), UiKit.dp(this, 17), UiKit.dp(this, 2), UiKit.dp(this, 14));
        page.addView(intro, introLp);

        OrbitExtensionStore.ManagerSnapshot snapshot =
                OrbitExtensionStore.managerSnapshot(this, includeV2);

        page.addView(sectionHeading(OrbitText.get(R.string.ui_first_party_extensions_d46bf6, "FIRST-PARTY EXTENSIONS")));
        List<OrbitExtension> firstParty = loadFirstPartyExtensions(includeV2);
        for (int i = 0; i < firstParty.size(); i++) {
            LinearLayout.LayoutParams lp = cardSpacing(i > 0);
            try {
                OrbitExtension extension = firstParty.get(i);
                page.addView(firstPartyCard(extension,
                        findInstalled(snapshot, extension.id)), lp);
            } catch (RuntimeException | LinkageError e) {
                bundledLoadFailures++;
                logFailure("first_party_card", e);
            }
        }
        if (bundledLoadFailures > 0) {
            LinearLayout unavailable = card();
            unavailable.addView(UiKit.text(this,
                    bundledLoadFailures == 1
                            ? OrbitText.get(R.string.ui_one_official_extension_is_unavailable_in_t_a3e5ec, "One official extension is unavailable in this build.")
                            : bundledLoadFailures + OrbitText.get(R.string.ui_official_extensions_are_unavailable_in_thi_02b11a, " official extensions are unavailable in this build."),
                    13, UiKit.TEXT, true));
            page.addView(unavailable, cardSpacing(!firstParty.isEmpty()));
        }

        page.addView(sectionHeading(OrbitText.get(R.string.ui_import_40bc49, "IMPORT")));
        LinearLayout importCard = card();
        importCard.addView(UiKit.text(this, OrbitText.get(R.string.ui_import_extension_from_file_3711aa, "Import extension from file"), 16, UiKit.TEXT, true));
        TextView importNote = UiKit.text(this,
                OrbitText.get(R.string.ui_choose_a_user_created_community_or_custom_552729, "Choose a user-created, community, or custom .orbitext file. Orbit validates it and shows the same complete review before installation."),
                12, UiKit.MUTED, false);
        importNote.setLineSpacing(0, 1.12f);
        importNote.setPadding(0, UiKit.dp(this, 5), 0, UiKit.dp(this, 11));
        importCard.addView(importNote);
        Button install = secondaryButton(OrbitText.get(R.string.ui_import_extension_from_file_3711aa, "Import extension from file"));
        install.setContentDescription(OrbitText.get(R.string.ui_import_extension_from_file_using_android_f_ab9407, "Import extension from file using Android file picker"));
        install.setOnClickListener(v -> chooseExtension());
        importCard.addView(install, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, UiKit.dp(this, 44)));
        page.addView(importCard);

        page.addView(sectionHeading(OrbitText.get(R.string.ui_installed_extensions_e86b1d, "INSTALLED EXTENSIONS")));

        if (snapshot.entries.isEmpty() && snapshot.unreadableStoreToken == null) {
            LinearLayout empty = card();
            empty.addView(UiKit.text(this, OrbitText.get(R.string.ui_no_extensions_installed_c20077, "No extensions installed"), 16, UiKit.TEXT, true));
            TextView note = UiKit.text(this,
                    OrbitText.get(R.string.ui_choose_a_orbitext_file_to_review_its_actio_fc5713, "Choose a .orbitext file to review its actions and network destinations before installing it."),
                    12, UiKit.MUTED, false);
            note.setPadding(0, UiKit.dp(this, 5), 0, 0);
            empty.addView(note);
            page.addView(empty);
        } else {
            int rendered = 0;
            for (OrbitExtensionStore.ManagerEntry entry : snapshot.entries) {
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                if (rendered++ > 0) lp.topMargin = UiKit.dp(this, 10);
                if (entry.isUnavailable()) {
                    page.addView(unavailableExtensionCard(
                            entry.removalToken, false, entry.displayId), lp);
                    continue;
                }
                try {
                    page.addView(extensionCard(entry.installed), lp);
                } catch (RuntimeException | LinkageError e) {
                    logFailure("installed_card", e);
                    page.addView(unavailableExtensionCard(
                            entry.installed.extension.id, true,
                            entry.installed.extension.id), lp);
                }
            }
            if (snapshot.unreadableStoreToken != null) {
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                if (rendered > 0) lp.topMargin = UiKit.dp(this, 10);
                page.addView(unavailableExtensionCard(
                        snapshot.unreadableStoreToken, false, ""), lp);
            }
        }

        TextView footer = UiKit.text(this,
                OrbitText.get(R.string.ui_extensions_v1_remains_compatible_extension_d388f7, "Extensions v1 remains compatible. Extensions v2 adds Orbit-rendered setup and Routine parameters while keeping actions declarative, reviewed and non-executable."),
                11, UiKit.MUTED, false);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(UiKit.dp(this, 6), UiKit.dp(this, 17), UiKit.dp(this, 6), 0);
        page.addView(footer);
        UiKit.applyTypography(page);
        return scroll;
    }

    private LinearLayout extensionCard(OrbitExtensionStore.Installed installed) {
        OrbitExtension extension = installed.extension;
        boolean requiresSetup = !extension.setupFields.isEmpty();
        LinearLayout card = card();

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(UiKit.text(this, extension.name, 17, UiKit.TEXT, true));
        copy.addView(UiKit.text(this,
                "v" + extension.version + " · " + extension.author, 12, UiKit.MUTED, false));
        top.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        String stateText = !installed.enabled ? OrbitText.get(R.string.ui_disabled_0c7ef3, "DISABLED") :
                requiresSetup ? OrbitText.get(R.string.ui_needs_setup_7a7446, "NEEDS SETUP") : OrbitText.get(R.string.ui_enabled_616f73, "ENABLED");
        TextView state = UiKit.text(this, stateText, 11,
                installed.enabled ? UiKit.accent(this) : UiKit.MUTED, true);
        state.setLetterSpacing(0.08f);
        top.addView(state);
        card.addView(top);

        TextView description = UiKit.text(this, extension.description, 13, UiKit.MUTED, false);
        description.setLineSpacing(0, 1.12f);
        description.setPadding(0, UiKit.dp(this, 9), 0, UiKit.dp(this, 7));
        card.addView(description);
        for (OrbitExtension.Action action : extension.actions) {
            TextView actionView = UiKit.text(this,
                    "• " + action.name + " · " + action.capabilityLabel(),
                    12, UiKit.TEXT, false);
            actionView.setPadding(0, UiKit.dp(this, 3), 0, 0);
            card.addView(actionView);
        }

        if (requiresSetup) {
            Button configure = secondaryButton(OrbitText.get(R.string.ui_set_up_configure_e48c10, "Set up / Configure"));
            configure.setContentDescription(OrbitText.get(R.string.ui_set_up_or_configure_875737, "Set up or configure ") + extension.name);
            configure.setOnClickListener(v -> showConfiguration(installed));
            LinearLayout.LayoutParams configureLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, UiKit.dp(this, 44));
            configureLp.topMargin = UiKit.dp(this, 14);
            card.addView(configure, configureLp);
        }

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setPadding(0, extension.setupFields.isEmpty() ? UiKit.dp(this, 14) :
                UiKit.dp(this, 9), 0, 0);
        Button toggle = installed.enabled ? warningButton(OrbitText.get(R.string.ui_disable_b7e3e4, "Disable")) : primaryButton(OrbitText.get(R.string.ui_enable_5342e0, "Enable"));
        toggle.setContentDescription((installed.enabled ? OrbitText.get(R.string.ui_disable_91c787, "Disable ") : OrbitText.get(R.string.ui_enable_3820d5, "Enable ")) + extension.name);
        toggle.setOnClickListener(v -> {
            if (OrbitExtensionStore.setEnabled(this, extension.id, !installed.enabled))
                rebuildPreservingPosition();
        });
        buttons.addView(toggle, new LinearLayout.LayoutParams(0, UiKit.dp(this, 44), 1));
        Button remove = destructiveButton(OrbitText.get(R.string.ui_remove_c3812f, "Remove"));
        remove.setContentDescription(OrbitText.get(R.string.ui_remove_594895, "Remove ") + extension.name);
        LinearLayout.LayoutParams removeLp = new LinearLayout.LayoutParams(0, UiKit.dp(this, 44), 1);
        removeLp.leftMargin = UiKit.dp(this, 9);
        buttons.addView(remove, removeLp);
        remove.setOnClickListener(v -> confirmRemove(extension));
        card.addView(buttons);
        return card;
    }

    private LinearLayout firstPartyCard(OrbitExtension extension,
                                        OrbitExtensionStore.Installed installed) {
        boolean requiresSetup = !extension.setupFields.isEmpty();
        LinearLayout card = card();
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(UiKit.text(this, extension.name, 16, UiKit.TEXT, true));
        copy.addView(UiKit.text(this,
                "v" + extension.version + " · " + extension.author, 12, UiKit.MUTED, false));
        top.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        String stateText = installed == null ? OrbitText.get(R.string.ui_not_installed_f00623, "NOT INSTALLED") :
                !installed.enabled ? OrbitText.get(R.string.ui_disabled_0c7ef3, "DISABLED") : requiresSetup ? OrbitText.get(R.string.ui_needs_setup_7a7446, "NEEDS SETUP") : OrbitText.get(R.string.ui_installed_aa7201, "INSTALLED");
        TextView state = UiKit.text(this, stateText, 10,
                installed == null ? UiKit.MUTED : UiKit.accent(this), true);
        state.setLetterSpacing(0.07f);
        top.addView(state);
        card.addView(top);

        TextView description = UiKit.text(this, extension.description, 12, UiKit.MUTED, false);
        description.setLineSpacing(0, 1.12f);
        description.setPadding(0, UiKit.dp(this, 7), 0, UiKit.dp(this, 12));
        card.addView(description);

        Button action;
        if (installed == null) {
            action = primaryButton(OrbitText.get(R.string.ui_install_569ca4, "Install"));
            action.setContentDescription(OrbitText.get(R.string.ui_review_and_install_378f59, "Review and install ") + extension.name);
            action.setOnClickListener(v -> showInstallReview(extension));
        } else {
            action = installedButton(!installed.enabled ? OrbitText.get(R.string.ui_installed_disabled_882e17, "Installed · Disabled") :
                    requiresSetup ? OrbitText.get(R.string.ui_installed_needs_setup_717b96, "Installed · Needs setup") : OrbitText.get(R.string.ui_installed_enabled_ee4c8e, "Installed · Enabled"));
            action.setContentDescription(extension.name + OrbitText.get(R.string.ui_is_installed_and_dc5079, " is installed and ") +
                    (!installed.enabled ? "disabled" : requiresSetup ? OrbitText.get(R.string.ui_needs_setup_dd7065, "needs setup") : "enabled"));
        }
        card.addView(action, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, UiKit.dp(this, 44)));
        return card;
    }

    private void chooseExtension() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("application/json");
        intent.putExtra(Intent.EXTRA_MIME_TYPES,
                new String[]{"application/json", "application/octet-stream", "text/plain"});
        startActivityForResult(intent, REQ_IMPORT_EXTENSION);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_IMPORT_EXTENSION || resultCode != RESULT_OK || data == null) return;
        Uri uri = data.getData();
        if (uri == null) return;
        try {
            String displayName = displayName(uri);
            if (!displayName.toLowerCase(java.util.Locale.US).endsWith(".orbitext"))
                throw new IllegalArgumentException(OrbitText.get(R.string.ui_choose_a_file_ending_in_orbitext_e6d904, "Choose a file ending in .orbitext."));
            OrbitExtension extension = OrbitExtension.parse(readManifest(uri));
            if (OrbitExtensionStore.find(this, extension.id) != null)
                throw new IllegalArgumentException(OrbitText.get(R.string.ui_an_extension_with_this_id_is_already_insta_f7e902, "An extension with this ID is already installed."));
            showInstallReview(extension);
        } catch (Exception e) {
            showError(e.getMessage() == null ? OrbitText.get(R.string.ui_orbit_could_not_read_this_extension_212e8b, "Orbit could not read this extension.") : e.getMessage());
        }
    }

    private void showInstallReview(OrbitExtension extension) {
        LinearLayout review = new LinearLayout(this);
        review.setOrientation(LinearLayout.VERTICAL);
        review.setPadding(UiKit.dp(this, 22), UiKit.dp(this, 8),
                UiKit.dp(this, 22), UiKit.dp(this, 4));
        review.addView(UiKit.text(this, extension.name + " · v" + extension.version,
                17, UiKit.TEXT, true));
        review.addView(spacedText(OrbitText.get(R.string.ui_by_7dd370, "By ") + extension.author, UiKit.MUTED));
        review.addView(spacedText(extension.description, UiKit.TEXT));

        if (!extension.setupFields.isEmpty()) {
            TextView setupTitle = spacedText(OrbitText.get(R.string.ui_requires_setup_b3e5bf, "REQUIRES SETUP"), UiKit.MUTED);
            setupTitle.setLetterSpacing(0.1f);
            review.addView(setupTitle);
            for (OrbitExtension.SetupField field : extension.setupFields) {
                String privacy = field.isSecret() ? OrbitText.get(R.string.ui_secret_fd6782, "Secret ") : "";
                String kind = OrbitExtension.SETUP_SECRET_URL.equals(field.type) ||
                        OrbitExtension.SETUP_URL.equals(field.type) ? "URL" : OrbitText.get(R.string.ui_text_71988c, "Text");
                review.addView(spacedText("• " + field.label + " — " + privacy + kind +
                        (field.required ? OrbitText.get(R.string.ui_required_1fbeb5, " · Required") : OrbitText.get(R.string.ui_optional_1504ff, " · Optional")), UiKit.TEXT));
            }
            review.addView(spacedText(
                    OrbitText.get(R.string.ui_install_first_then_configure_explicitly_or_56eb34, "Install first, then configure explicitly. Orbit encrypts secret values and does not expose them to chats, Memory, backups, Routines or other extensions."),
                    UiKit.TEXT));
        }

        TextView actionsTitle = spacedText(OrbitText.get(R.string.ui_actions_being_added_35e4bc, "ACTIONS BEING ADDED"), UiKit.MUTED);
        actionsTitle.setLetterSpacing(0.1f);
        review.addView(actionsTitle);
        for (OrbitExtension.Action action : extension.actions) {
            String fixedBody = OrbitExtension.TYPE_HTTPS_REQUEST.equals(action.type) &&
                    "POST".equals(action.method)
                    ? "\n  " + (action.parameters.isEmpty() ? OrbitText.get(R.string.ui_json_body_fbcb93, "JSON body: ") :
                    OrbitText.get(R.string.ui_templated_json_body_a33f4f, "Templated JSON body: ")) +
                    (action.body == null ? "{}" : action.body.toString()) : "";
            String parameters = action.parameters.isEmpty() ? "" :
                    OrbitText.get(R.string.ui_routine_parameters_afa8a6, "\n  Routine parameters: ") + parameterLabels(action);
            String headers = action.headers.isEmpty() ? "" :
                    OrbitText.get(R.string.ui_declared_headers_c8ab2e, "\n  Declared headers: ") + headerNames(action);
            review.addView(spacedText("• " + action.name + " — " + action.capabilityLabel() +
                    "\n  " + action.url + parameters + headers + fixedBody, UiKit.TEXT));
        }
        List<String> hosts = extension.contactedHosts();
        String destinations = hosts.isEmpty() ? OrbitText.get(R.string.ui_none_fixed_in_the_manifest_a4fd28, "None fixed in the manifest") :
                String.join("\n", hosts);
        if (extension.hasDynamicDestination())
            destinations += OrbitText.get(R.string.ui_user_supplied_during_setup_validated_publi_6280be, "\nUser-supplied during setup (validated public HTTPS only)");
        review.addView(spacedText(OrbitText.get(R.string.ui_network_destinations_262b8a, "NETWORK DESTINATIONS\n") + destinations, UiKit.MUTED));
        TextView isolation = spacedText(
                OrbitText.get(R.string.ui_this_extension_cannot_receive_orbit_chats_f77639, "This extension cannot receive Orbit chats, Memory, screen context, files, other credentials, or Android permission state. Only declared configuration and Routine parameters can enter its reviewed request templates."),
                UiKit.TEXT);
        isolation.setBackground(UiKit.rounded(UiKit.SURFACE_2, 14, this));
        isolation.setPadding(UiKit.dp(this, 13), UiKit.dp(this, 11),
                UiKit.dp(this, 13), UiKit.dp(this, 11));
        review.addView(isolation);
        UiKit.applyTypography(review);

        ScrollView wrapper = new ScrollView(this);
        wrapper.addView(review);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(OrbitText.get(R.string.ui_review_extension_b3bad2, "Review extension"))
                .setView(wrapper)
                .setNegativeButton(OrbitText.get(R.string.ui_cancel_19766e, "Cancel"), null)
                .setPositiveButton(OrbitText.get(R.string.ui_install_569ca4, "Install"), (d, w) -> {
                    if (OrbitExtensionStore.install(this, extension)) {
                        Toast.makeText(this, extension.name + OrbitText.get(R.string.ui_installed_cf2f63, " installed"), Toast.LENGTH_SHORT).show();
                        rebuildPreservingPosition();
                    } else {
                        showError(OrbitText.get(R.string.ui_orbit_could_not_install_this_extension_it_e3541f, "Orbit could not install this extension. It may already exist or the extension limit was reached."));
                    }
                }).create();
        UiKit.styleOrbitDialog(dialog, this, false);
        dialog.show();
    }

    private void showConfiguration(OrbitExtensionStore.Installed installed) {
        OrbitExtension extension = installed.extension;
        boolean initiallyConfigured = isConfiguredSafely(installed);
        JSONObject saved = OrbitExtensionStore.configuration(this, extension.id);
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(UiKit.dp(this, 22), UiKit.dp(this, 5),
                UiKit.dp(this, 22), UiKit.dp(this, 5));

        TextView note = UiKit.text(this,
                OrbitText.get(R.string.ui_secret_values_are_encrypted_by_android_key_af8cdf, "Secret values are encrypted by Android Keystore and are never shown again, backed up, or stored in Routines."),
                12, UiKit.MUTED, false);
        note.setLineSpacing(0, 1.12f);
        note.setPadding(0, 0, 0, UiKit.dp(this, 8));
        form.addView(note);

        Map<String, EditText> inputs = new LinkedHashMap<>();
        Map<String, Boolean> hadSecret = new HashMap<>();
        Set<String> clearRequested = new HashSet<>();
        for (OrbitExtension.SetupField field : extension.setupFields) {
            TextView label = UiKit.text(this, field.label +
                    (field.required ? OrbitText.get(R.string.ui_required_1fbeb5, " · Required") : OrbitText.get(R.string.ui_optional_1504ff, " · Optional")),
                    12, UiKit.MUTED, true);
            label.setPadding(UiKit.dp(this, 2), UiKit.dp(this, 10), 0, UiKit.dp(this, 5));
            form.addView(label);
            if (!field.description.isEmpty()) {
                TextView description = UiKit.text(this, field.description,
                        11, UiKit.MUTED, false);
                description.setPadding(UiKit.dp(this, 2), 0, 0, UiKit.dp(this, 6));
                form.addView(description);
            }

            boolean secret = field.isSecret();
            String storedSecret = secret ? OrbitExtensionSecretStore.load(
                    this, extension.id, field.id) : null;
            boolean hasSecret = storedSecret != null;
            hadSecret.put(field.id, hasSecret);
            EditText input = configurationInput(field, secret ? "" :
                    saved.optString(field.id, field.defaultValue));
            if (secret) input.setHint(hasSecret ? safeSecretStatus(field, storedSecret) :
                    OrbitText.get(R.string.ui_enter_securely_693475, "Enter securely"));
            inputs.put(field.id, input);
            form.addView(input, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, UiKit.dp(this, 52)));

            if (secret && hasSecret) {
                Button clear = secondaryButton(OrbitText.get(R.string.ui_clear_saved_value_46b3b8, "Clear saved value"));
                LinearLayout.LayoutParams clearLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, UiKit.dp(this, 40));
                clearLp.topMargin = UiKit.dp(this, 6);
                form.addView(clear, clearLp);
                clear.setOnClickListener(v -> {
                    if (clearRequested.remove(field.id)) {
                        clear.setText(OrbitText.get(R.string.ui_clear_saved_value_46b3b8, "Clear saved value"));
                        input.setHint(safeSecretStatus(field,
                                OrbitExtensionSecretStore.load(this, extension.id, field.id)));
                    } else {
                        clearRequested.add(field.id);
                        input.setText("");
                        input.setHint(OrbitText.get(R.string.ui_will_be_cleared_f16afa, "Will be cleared"));
                        clear.setText(OrbitText.get(R.string.ui_keep_saved_value_884e11, "Keep saved value"));
                    }
                });
                input.addTextChangedListener(new TextWatcher() {
                    @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                    @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                        if (s != null && s.length() > 0 && clearRequested.remove(field.id)) {
                            clear.setText(OrbitText.get(R.string.ui_clear_saved_value_46b3b8, "Clear saved value"));
                        }
                    }
                    @Override public void afterTextChanged(Editable s) {}
                });
            }
        }
        UiKit.applyTypography(form);
        ScrollView wrapper = new ScrollView(this);
        wrapper.addView(form);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle((initiallyConfigured ? OrbitText.get(R.string.ui_configure_23c778, "Configure ") : OrbitText.get(R.string.ui_set_up_f0ed49, "Set up ")) + extension.name)
                .setView(wrapper)
                .setNegativeButton(OrbitText.get(R.string.ui_cancel_19766e, "Cancel"), null)
                .setPositiveButton(OrbitText.get(R.string.ui_save_1509f5, "Save"), null)
                .create();
        UiKit.styleOrbitDialog(dialog, this, false, () -> {
            Button saveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            if (saveButton != null) saveButton.setOnClickListener(v -> {
                JSONObject nonSecret = new JSONObject();
                Map<String, String> secretReplacements = new LinkedHashMap<>();
                try {
                    for (OrbitExtension.SetupField field : extension.setupFields) {
                        EditText input = inputs.get(field.id);
                        String entered = input == null ? "" : input.getText().toString();
                        if (field.isSecret()) {
                            String safe = entered.trim().isEmpty() ? "" :
                                    OrbitExtensionV2.validateSetupValue(field, entered);
                            if (!safe.isEmpty()) secretReplacements.put(field.id, safe);
                            boolean willExist = !safe.isEmpty() ||
                                    (Boolean.TRUE.equals(hadSecret.get(field.id)) &&
                                            !clearRequested.contains(field.id));
                            if (field.required && !willExist &&
                                    !(initiallyConfigured && clearRequested.contains(field.id)))
                                throw new IllegalArgumentException(OrbitText.get(R.string.ui_enter_1d1748, "Enter ") + field.label + ".");
                        } else {
                            String safe = OrbitExtensionV2.validateSetupValue(field, entered);
                            if (field.required && safe.isEmpty())
                                throw new IllegalArgumentException(OrbitText.get(R.string.ui_enter_1d1748, "Enter ") + field.label + ".");
                            if (!safe.isEmpty()) nonSecret.put(field.id, safe);
                        }
                    }
                } catch (IllegalArgumentException e) {
                    Toast.makeText(this, e.getMessage() == null ?
                            OrbitText.get(R.string.ui_check_the_setup_values_bc6bec, "Check the setup values.") : e.getMessage(), Toast.LENGTH_SHORT).show();
                    return;
                } catch (Exception e) {
                    Toast.makeText(this, OrbitText.get(R.string.ui_check_the_setup_values_bc6bec, "Check the setup values."), Toast.LENGTH_SHORT).show();
                    return;
                }

                JSONObject previous = OrbitExtensionStore.configuration(this, extension.id);
                if (!OrbitExtensionStore.setConfiguration(this, extension.id, nonSecret)) {
                    configurationSaveFailed();
                    return;
                }
                boolean secretsSaved = secretReplacements.isEmpty() && clearRequested.isEmpty() ||
                        OrbitExtensionSecretStore.save(this, extension.id,
                                secretReplacements, clearRequested);
                if (!secretsSaved) {
                    OrbitExtensionStore.setConfiguration(this, extension.id, previous);
                    configurationSaveFailed();
                    return;
                }
                dialog.dismiss();
                Toast.makeText(this, extension.name + OrbitText.get(R.string.ui_configuration_saved_e13c9d, " configuration saved"),
                        Toast.LENGTH_SHORT).show();
                rebuildPreservingPosition();
            });
        });
        dialog.show();
    }

    private EditText configurationInput(OrbitExtension.SetupField field, String value) {
        EditText input = new EditText(this);
        input.setText(value == null ? "" : value);
        input.setTextColor(UiKit.TEXT);
        input.setHintTextColor(UiKit.MUTED);
        input.setTextSize(14);
        input.setSingleLine(true);
        input.setPadding(UiKit.dp(this, 15), 0, UiKit.dp(this, 15), 0);
        input.setBackground(UiKit.outlined(UiKit.SURFACE_2,
                Color.rgb(53, 58, 72), 15, this));
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(field.maxLength)});
        if (field.isSecret()) {
            input.setInputType(InputType.TYPE_CLASS_TEXT |
                    InputType.TYPE_TEXT_VARIATION_PASSWORD |
                    InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        } else if (OrbitExtension.SETUP_URL.equals(field.type)) {
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        } else {
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        }
        return input;
    }

    private String safeSecretStatus(OrbitExtension.SetupField field, String value) {
        if (value == null || value.isEmpty()) return OrbitText.get(R.string.ui_enter_securely_693475, "Enter securely");
        if (OrbitExtension.SETUP_SECRET_URL.equals(field.type)) {
            try {
                String host = new java.net.URI(value).getHost();
                if (host != null && !host.trim().isEmpty()) return OrbitText.get(R.string.ui_configured_40c090, "Configured · ") + host;
            } catch (Exception ignored) {}
        }
        return OrbitText.get(R.string.ui_saved_b5c120, "Saved");
    }

    private String parameterLabels(OrbitExtension.Action action) {
        List<String> labels = new ArrayList<>();
        for (OrbitExtension.ActionParameter parameter : action.parameters)
            labels.add(parameter.label + (parameter.required ? OrbitText.get(R.string.ui_required_e68d8d, " (required)") : OrbitText.get(R.string.ui_optional_edbfc3, " (optional)")));
        return String.join(", ", labels);
    }

    private String headerNames(OrbitExtension.Action action) {
        List<String> names = new ArrayList<>();
        for (OrbitExtension.RequestHeader header : action.headers) names.add(header.name);
        return String.join(", ", names);
    }

    private void configurationSaveFailed() {
        AlertDialog error = new AlertDialog.Builder(this)
                .setTitle(OrbitText.get(R.string.ui_configuration_not_saved_111ede, "Configuration not saved"))
                .setMessage(OrbitText.get(R.string.ui_orbit_could_not_securely_save_this_extensi_ac4d02, "Orbit could not securely save this extension configuration. No credential was stored in plaintext."))
                .setPositiveButton(OrbitText.get(R.string.ui_ok_565339, "OK"), null)
                .create();
        UiKit.styleOrbitDialog(error, this, false);
        error.show();
    }

    private void confirmRemove(OrbitExtension extension) {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(OrbitText.get(R.string.ui_remove_594895, "Remove ") + extension.name + "?")
                .setMessage(OrbitText.get(R.string.ui_routines_that_reference_its_actions_will_r_f55a8c, "Routines that reference its actions will remain saved and report “Extension action unavailable” until a matching extension is installed again."))
                .setNegativeButton(OrbitText.get(R.string.ui_cancel_19766e, "Cancel"), null)
                .setPositiveButton(OrbitText.get(R.string.ui_remove_c3812f, "Remove"), (d, w) -> {
                    if (OrbitExtensionStore.remove(this, extension.id))
                        rebuildPreservingPosition();
                    else Toast.makeText(this, OrbitText.get(R.string.ui_orbit_could_not_safely_remove_this_extensi_dbe8b1, "Orbit could not safely remove this extension."),
                            Toast.LENGTH_SHORT).show();
                }).create();
        UiKit.styleOrbitDialog(dialog, this, true);
        dialog.show();
    }

    private LinearLayout unavailableExtensionCard(String removalKey, boolean validExtension,
                                                   String displayId) {
        LinearLayout card = card();
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = UiKit.text(this, OrbitText.get(R.string.ui_extension_unavailable_cd78b0, "Extension unavailable"), 16, UiKit.TEXT, true);
        top.addView(title, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView state = UiKit.text(this, OrbitText.get(R.string.ui_unavailable_eb0be6, "UNAVAILABLE"), 10, UiKit.MUTED, true);
        state.setLetterSpacing(0.07f);
        top.addView(state);
        card.addView(top);

        TextView guidance = UiKit.text(this,
                OrbitText.get(R.string.ui_orbit_could_not_safely_read_this_installed_dc55ca, "Orbit could not safely read this installed extension. It remains inactive and its saved data has not been changed."),
                12, UiKit.MUTED, false);
        guidance.setLineSpacing(0, 1.12f);
        guidance.setPadding(0, UiKit.dp(this, 8), 0, UiKit.dp(this, 12));
        card.addView(guidance);
        if (displayId != null && !displayId.isEmpty()) {
            TextView id = UiKit.text(this, OrbitText.get(R.string.ui_stored_id_88b96b, "Stored ID: ") + displayId,
                    11, UiKit.MUTED, false);
            id.setPadding(0, 0, 0, UiKit.dp(this, 10));
            card.addView(id);
        }
        Button remove = destructiveButton(OrbitText.get(R.string.ui_remove_c3812f, "Remove"));
        remove.setContentDescription(OrbitText.get(R.string.ui_remove_unavailable_extension_e7c663, "Remove unavailable extension"));
        remove.setOnClickListener(v -> confirmRemoveUnavailable(removalKey, validExtension));
        card.addView(remove, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, UiKit.dp(this, 44)));
        return card;
    }

    private void confirmRemoveUnavailable(String removalKey, boolean validExtension) {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(OrbitText.get(R.string.ui_remove_unavailable_extension_95fbbb, "Remove unavailable extension?"))
                .setMessage(OrbitText.get(R.string.ui_only_this_unavailable_installed_entry_will_5690be, "Only this unavailable installed entry will be removed. Existing Routines remain saved."))
                .setNegativeButton(OrbitText.get(R.string.ui_cancel_19766e, "Cancel"), null)
                .setPositiveButton(OrbitText.get(R.string.ui_remove_c3812f, "Remove"), (d, w) -> {
                    boolean removed = validExtension
                            ? OrbitExtensionStore.remove(this, removalKey)
                            : OrbitExtensionStore.removeUnavailable(this, removalKey);
                    if (removed) rebuildPreservingPosition();
                    else Toast.makeText(this, OrbitText.get(R.string.ui_orbit_could_not_safely_remove_this_extensi_dbe8b1, "Orbit could not safely remove this extension."),
                            Toast.LENGTH_SHORT).show();
                }).create();
        UiKit.styleOrbitDialog(dialog, this, true);
        dialog.show();
    }

    private boolean isConfiguredSafely(OrbitExtensionStore.Installed installed) {
        try { return OrbitExtensionStore.isConfigured(this, installed); }
        catch (RuntimeException | LinkageError e) {
            logFailure("configuration_state", e);
            return false;
        }
    }

    private void logFailure(String stage, Throwable error) {
        Log.w(LOG_TAG, "stage=" + stage + " error=" +
                (error == null ? "unknown" : error.getClass().getSimpleName()));
    }

    private OrbitExtensionStore.Installed findInstalled(
            OrbitExtensionStore.ManagerSnapshot snapshot, String extensionId) {
        if (snapshot == null || extensionId == null) return null;
        for (OrbitExtensionStore.ManagerEntry entry : snapshot.entries) {
            if (entry.installed != null &&
                    extensionId.equals(entry.installed.extension.id)) return entry.installed;
        }
        return null;
    }

    private void showRecovery(Throwable error) {
        final String diagnostic = safeDiagnostic(error);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setGravity(Gravity.CENTER_VERTICAL);
        OrbitBackground.applyPage(page);
        page.setPadding(UiKit.dp(this, 28), UiKit.dp(this, 40),
                UiKit.dp(this, 28), UiKit.dp(this, 40));

        page.addView(UiKit.text(this, OrbitText.get(R.string.ui_extensions_couldn_t_load_f84e53, "Extensions couldn't load"), 24, UiKit.TEXT, true));
        TextView message = UiKit.text(this,
                OrbitText.get(R.string.ui_orbit_encountered_a_problem_loading_extens_717b04, "Orbit encountered a problem loading Extensions. Your extensions and Routines have not been deleted."),
                14, UiKit.MUTED, false);
        message.setLineSpacing(0, 1.15f);
        message.setPadding(0, UiKit.dp(this, 12), 0, UiKit.dp(this, 22));
        page.addView(message);

        Button retry = primaryButton(OrbitText.get(R.string.ui_retry_942087, "Retry"));
        retry.setOnClickListener(v -> {
            try {
                renderContent(false, -1);
                getWindow().getDecorView().post(() -> renderFullContent(-1));
            } catch (RuntimeException | LinkageError retryError) {
                logFailure("recovery_retry", retryError);
                showRecovery(retryError);
            }
        });
        page.addView(retry, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, UiKit.dp(this, 46)));

        Button back = secondaryButton(OrbitText.get(R.string.ui_back_76900f, "Back"));
        back.setOnClickListener(v -> navigation.performBack());
        LinearLayout.LayoutParams backLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, UiKit.dp(this, 46));
        backLp.topMargin = UiKit.dp(this, 9);
        page.addView(back, backLp);

        Button copy = secondaryButton(OrbitText.get(R.string.ui_copy_diagnostic_details_c48905, "Copy diagnostic details"));
        copy.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager)
                    getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) clipboard.setPrimaryClip(
                    ClipData.newPlainText(OrbitText.get(R.string.ui_orbit_extensions_diagnostic_cba678, "Orbit Extensions diagnostic"), diagnostic));
            Toast.makeText(this, OrbitText.get(R.string.ui_diagnostic_details_copied_365730, "Diagnostic details copied"), Toast.LENGTH_SHORT).show();
        });
        LinearLayout.LayoutParams copyLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, UiKit.dp(this, 46));
        copyLp.topMargin = UiKit.dp(this, 9);
        page.addView(copy, copyLp);

        UiKit.applyTypography(page);
        setContentView(page);
        UiKit.applyActivityInsets(this, page, true);
    }

    private String safeDiagnostic(Throwable error) {
        String type = error == null ? OrbitText.get(R.string.ui_unknown_b764cd, "Unknown") : error.getClass().getName();
        String location = "unavailable";
        if (error != null) {
            for (StackTraceElement frame : error.getStackTrace()) {
                if (frame.getClassName().startsWith("com.orbit.assistant")) {
                    location = frame.getClassName() + "." + frame.getMethodName() +
                            ":" + frame.getLineNumber();
                    break;
                }
            }
        }
        return "Orbit " + BuildConfig.VERSION_NAME + "\n" +
                "Exception: " + type + "\n" +
                OrbitText.get(R.string.ui_location_bb9b32, "Location: ") + location;
    }

    private void showError(String message) {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(OrbitText.get(R.string.ui_extension_not_installed_142c32, "Extension not installed"))
                .setMessage(message)
                .setPositiveButton(OrbitText.get(R.string.ui_ok_565339, "OK"), null).create();
        UiKit.styleOrbitDialog(dialog, this, false);
        dialog.show();
    }

    private String readManifest(Uri uri) throws Exception {
        try (InputStream input = getContentResolver().openInputStream(uri)) {
            if (input == null) throw new IllegalArgumentException(OrbitText.get(R.string.ui_android_could_not_open_the_selected_file_9b48c4, "Android could not open the selected file."));
            return readManifest(input);
        }
    }

    private List<OrbitExtension> loadFirstPartyExtensions(boolean includeV2) {
        List<OrbitExtension> out = new ArrayList<>();
        bundledLoadFailures = 0;
        int count = includeV2 ? FIRST_PARTY_ASSETS.length : 3;
        for (int i = 0; i < count; i++) {
            String assetName = FIRST_PARTY_ASSETS[i];
            try (InputStream input = getAssets().open(FIRST_PARTY_ASSET_DIR + assetName)) {
                // First-party packages use the exact same size limit and untrusted
                // OrbitExtension parser as files selected through Android SAF.
                out.add(OrbitExtension.parse(readManifest(input)));
            } catch (Exception | LinkageError error) {
                // A damaged bundled manifest must remain unavailable, never privileged.
                bundledLoadFailures++;
                Log.w(LOG_TAG, "stage=first_party_manifest asset=" + assetName +
                        " error=" + error.getClass().getSimpleName());
            }
        }
        return out;
    }

    private String readManifest(InputStream input) throws Exception {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > OrbitExtension.MAX_MANIFEST_BYTES)
                    throw new IllegalArgumentException(OrbitText.get(R.string.ui_the_extension_manifest_is_too_large_618735, "The extension manifest is too large."));
                output.write(buffer, 0, read);
            }
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }

    private String displayName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                String name = cursor.getString(0);
                if (name != null) return name;
            }
        } catch (Exception ignored) {}
        return uri.getLastPathSegment() == null ? "" : uri.getLastPathSegment();
    }

    private TextView spacedText(String text, int color) {
        TextView view = UiKit.text(this, text, 12, color, false);
        view.setLineSpacing(0, 1.12f);
        view.setPadding(0, UiKit.dp(this, 8), 0, UiKit.dp(this, 2));
        return view;
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(UiKit.dp(this, 17), UiKit.dp(this, 16),
                UiKit.dp(this, 17), UiKit.dp(this, 16));
        card.setBackground(UiKit.outlined(UiKit.SURFACE,
                UiKit.withAlpha(UiKit.accent(this), 38), 22, this));
        card.setElevation(UiKit.dp(this, 2));
        return card;
    }

    private TextView sectionHeading(String text) {
        TextView heading = UiKit.text(this, text, 12, UiKit.MUTED, true);
        heading.setLetterSpacing(0.13f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(UiKit.dp(this, 4), UiKit.dp(this, 24), 0, UiKit.dp(this, 9));
        heading.setLayoutParams(lp);
        return heading;
    }

    private LinearLayout.LayoutParams cardSpacing(boolean separated) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (separated) lp.topMargin = UiKit.dp(this, 10);
        return lp;
    }

    private Button primaryButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(UiKit.onAccent(this));
        button.setTextSize(14);
        button.setAllCaps(false);
        button.setBackground(UiKit.ripple(UiKit.accent(this), UiKit.onAccent(this), 15, this));
        button.setMinHeight(0); button.setMinimumHeight(0); button.setStateListAnimator(null);
        UiKit.pressScale(button);
        return button;
    }

    private Button secondaryButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(UiKit.TEXT);
        button.setTextSize(13);
        button.setAllCaps(false);
        button.setBackground(UiKit.rippleOutlined(UiKit.SURFACE_2,
                Color.rgb(53, 58, 72), UiKit.accent(this), 14, this));
        button.setMinHeight(0); button.setMinimumHeight(0); button.setStateListAnimator(null);
        UiKit.pressScale(button);
        return button;
    }

    private Button warningButton(String text) {
        int warning = Color.rgb(224, 157, 78);
        Button button = outlinedActionButton(text, warning,
                UiKit.blend(warning, UiKit.SURFACE_2, 0.10f));
        return button;
    }

    private Button destructiveButton(String text) {
        int destructive = Color.rgb(226, 100, 108);
        return outlinedActionButton(text, destructive,
                UiKit.blend(destructive, UiKit.SURFACE_2, 0.08f));
    }

    private Button installedButton(String text) {
        Button button = secondaryButton(text);
        button.setEnabled(false);
        button.setAlpha(0.72f);
        return button;
    }

    private Button outlinedActionButton(String text, int color, int fill) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(color);
        button.setTextSize(13);
        button.setAllCaps(false);
        button.setBackground(UiKit.rippleOutlined(fill,
                UiKit.withAlpha(color, 190), color, 14, this));
        button.setMinHeight(0); button.setMinimumHeight(0); button.setStateListAnimator(null);
        UiKit.pressScale(button);
        return button;
    }
}
