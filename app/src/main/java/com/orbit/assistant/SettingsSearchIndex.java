package com.orbit.assistant;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Every Orbit setting somebody might go looking for, written down once.
 *
 * <p>Orbit Settings crossed the line where browsing stops working. Ten categories, forty-odd
 * controls, and finding AMOLED means remembering that it lives inside Theme Studio inside Look &amp;
 * Feel. At that size a settings page is not a page any more, it is a search problem, and the answer
 * to a search problem is an index.
 *
 * <p><b>Written by hand, and deliberately not scraped.</b> The tempting implementation is to walk
 * the built view tree collecting every {@code TextView} - it needs no maintenance and it is wrong.
 * A scraped index searches whatever a screen happens to have drawn, which includes help paragraphs,
 * privacy notes and button labels; it cannot know a control's section once the view is detached; it
 * cannot carry an alias, so "dark mode" finds nothing because nothing on screen says it; and it
 * silently changes behaviour every time somebody rewords a caption. Structured entries cost a line
 * per setting and are the only version that can answer the question people actually ask.
 *
 * <p>Entirely local. No network, no provider, no AI, no database: forty entries in memory, matched
 * with string comparisons. Typing in this field must never be a reason for Orbit to contact
 * anything.
 */
public final class SettingsSearchIndex {

    /** How many results are worth showing before a list stops being a shortcut. */
    public static final int MAX_RESULTS = 8;

    /** One thing the user can go and change. */
    public static final class Entry {
        /** The control's own words, as they appear on the settings row. */
        public final String title;
        /** One short line of what it does, or empty. Matched, and shown when it helps. */
        public final String description;
        /** The Settings section holding it, in Orbit's own display words. */
        public final String sectionName;
        /** The section id this entry's screen opens with. */
        public final String section;
        /**
         * The control key this entry scrolls to, or empty for a section-level result.
         *
         * <p>The difference between "took me to Look &amp; Feel" and "took me to AMOLED". A result
         * with a key scrolls to and pulses that exact row; one without opens the section, which is
         * the honest outcome when a setting lives behind another screen entirely.
         */
        public final String controlKey;
        /**
         * Words somebody would type that the row does not contain.
         *
         * <p>Where most of this index's value is. "dark mode" is not written anywhere in Orbit
         * because Orbit's control is called AMOLED; "saved stuff" is not written anywhere because
         * the feature is called Vault. Without aliases a search for either finds nothing at all,
         * which reads as the setting not existing.
         */
        public final List<String> aliases;

        Entry(String title, String description, String sectionName, String section,
              String controlKey, String... aliases) {
            this.title = title == null ? "" : title;
            this.description = description == null ? "" : description;
            this.sectionName = sectionName == null ? "" : sectionName;
            this.section = section == null ? "" : section;
            this.controlKey = controlKey == null ? "" : controlKey;
            List<String> words = new ArrayList<>();
            if (aliases != null) {
                for (String alias : aliases) {
                    if (alias != null && !alias.trim().isEmpty()) {
                        words.add(alias.trim().toLowerCase(Locale.US));
                    }
                }
            }
            Collections.addAll(words, chineseAliases(controlKey));
            this.aliases = Collections.unmodifiableList(words);
        }

        /** Whether choosing this result can land on an exact control rather than a section. */
        public boolean hasControl() { return !controlKey.isEmpty(); }

        /** The second line of a result row: which part of Settings this lives in. */
        public String subtitle() { return sectionName; }
    }

    // ---- control keys (navigation identity: never rename) ---------------------------------------

    public static final String KEY_DEFAULT_ASSISTANT = "default_assistant";
    public static final String KEY_QUICK_TILES = "quick_tiles";
    public static final String KEY_WIDGETS = "widgets";
    public static final String KEY_PROVIDER = "provider";
    public static final String KEY_CHATGPT_ACCOUNT = "chatgpt_account";
    public static final String KEY_DEFAULT_AI = "default_ai";
    public static final String KEY_THINKING_UPDATES = "thinking_updates";
    public static final String KEY_RICH_ANSWERS = "rich_answers";
    public static final String KEY_SCREEN_TEXT = "screen_text";
    public static final String KEY_SCREENSHOTS = "screenshots";
    public static final String KEY_ATTACH_SCREEN = "attach_screen";
    public static final String KEY_CONTEXT_CHIPS = "context_chips";
    public static final String KEY_SPEAK = "speak_replies";
    public static final String KEY_VOICE_PAUSE = "voice_pause";
    public static final String KEY_AUTO_LISTEN = "auto_listen";
    public static final String KEY_FOLLOW_UPS = "follow_ups";
    public static final String KEY_SMART_FOLLOW_UPS = "smart_follow_ups";
    public static final String KEY_KEYBOARD_AWARE = "keyboard_aware";
    public static final String KEY_PERMISSIONS = "permissions";
    public static final String KEY_MEMORY = "memory";
    public static final String KEY_REMINDERS = "reminders";
    public static final String KEY_PLACES = "saved_places";
    public static final String KEY_APP_PROFILES = "app_profiles";
    public static final String KEY_NOTIFICATIONS = "notifications";
    public static final String KEY_VAULT = "vault";
    public static final String KEY_WEATHER = "weather";
    public static final String KEY_GALLERY = "gallery";
    public static final String KEY_BACKUP = "backup";
    public static final String KEY_DECK = "deck";
    public static final String KEY_NEW_CHAT = "new_chat_on_open";
    public static final String KEY_HISTORY = "chat_history";
    public static final String KEY_THUMBNAILS = "chat_thumbnails";
    public static final String KEY_STOP_BUTTON = "stop_button";
    public static final String KEY_CLEAR_HISTORY = "clear_history";
    public static final String KEY_THEME_STUDIO = "theme_studio";
    public static final String KEY_FONT = "app_font";
    public static final String KEY_CHAT_TEXT_SIZE = "chat_text_size";
    public static final String KEY_OVERLAY = "overlay";
    public static final String KEY_OVERLAY_PREVIEW = "overlay_preview";
    public static final String KEY_OVERLAY_STYLE = "overlay_style";
    public static final String KEY_OVERLAY_QUICK_CONTROLS = "overlay_quick_controls";
    public static final String KEY_HAPTICS = "haptics";
    public static final String KEY_PAGE_TRANSITIONS = "page_transitions";
    public static final String KEY_SWIPE_BACK = "swipe_back";
    public static final String KEY_CHAT_SWIPE = "chat_swipe";
    public static final String KEY_DIAGNOSTICS = "diagnostics";
    public static final String KEY_ROUTINES = "routines";
    public static final String KEY_EXTENSIONS = "extensions";
    public static final String KEY_UPDATES = "updates";

    private static final String S_ASSISTANT = "Assistant setup";
    private static final String S_AI = "AI & account";
    private static final String S_VOICE = "Voice, context & permissions";
    private static final String S_DATA = "Personalization & data";
    private static final String S_DECK = "Orbit Deck";
    private static final String S_CHATS = "Conversations";
    private static final String S_LOOK = "Look & Feel";
    private static final String S_ADVANCED = "Advanced";
    private static final String S_ROUTINES = "Routines";
    private static final String S_EXTENSIONS = "Extensions";
    private static final String S_UPDATES = "About & updates";


    /** Search aliases are input vocabulary, not stored navigation identities. */
    private static String[] chineseAliases(String key) {
        if (key == null) return new String[0];
        switch (key) {
            case KEY_DEFAULT_ASSISTANT: return new String[]{"默认助手", "数字助理", "长按", "电源键", "侧键"};
            case KEY_QUICK_TILES: return new String[]{"下拉", "快捷设置", "磁贴"};
            case KEY_WIDGETS: return new String[]{"桌面", "小组件"};
            case KEY_PROVIDER: return new String[]{"服务商", "提供商", "模型服务"};
            case KEY_CHATGPT_ACCOUNT: return new String[]{"登录", "退出", "账号", "账户"};
            case KEY_DEFAULT_AI: return new String[]{"模型", "推理", "强度", "自动路由"};
            case KEY_THEME_STUDIO: return new String[]{"深色", "暗色", "黑色", "配色", "颜色", "主题"};
            case KEY_FONT: return new String[]{"字体", "字形"};
            case KEY_CHAT_TEXT_SIZE: return new String[]{"字号", "字体大小", "大字"};
            case KEY_HAPTICS: return new String[]{"震动", "振动", "触感"};
            case KEY_OVERLAY: return new String[]{"浮窗", "悬浮窗"};
            case KEY_MEMORY: return new String[]{"记住", "记忆", "偏好"};
            case KEY_VAULT: return new String[]{"收藏", "书签", "保存内容"};
            case KEY_NOTIFICATIONS: return new String[]{"通知", "未读消息"};
            case KEY_REMINDERS: return new String[]{"提醒", "闹钟"};
            case KEY_BACKUP: return new String[]{"备份", "恢复", "导出", "导入", "迁移"};
            case KEY_SCREEN_TEXT: return new String[]{"读屏", "屏幕文字"};
            case KEY_SCREENSHOTS: return new String[]{"截图", "屏幕截图"};
            case KEY_SWIPE_BACK: return new String[]{"返回", "手势"};
            case KEY_CHAT_SWIPE: return new String[]{"左滑", "右滑", "置顶", "滑动删除"};
            default: return new String[0];
        }
    }

    private SettingsSearchIndex() {}

    /** Every indexed setting. Stable order, so equal-scoring results never shuffle. */
    public static List<Entry> all() { return buildEntries(); }

    /**
     * The settings matching what somebody has typed, best first.
     *
     * <p>Substring matching over four fields, weighted by which one matched and how completely. It
     * is not clever and it does not need to be: with forty entries, "amo" reaching AMOLED and
     * "swipe" reaching both swipe settings is the entire requirement, and a cleverer ranker would
     * be harder to predict without finding anything a simple one misses.
     */
    public static List<Entry> search(String query) {
        List<Entry> results = new ArrayList<>();
        String text = normalize(query);
        if (text.isEmpty()) return results;
        String[] terms = text.split(" ");

        List<Entry> entries = all();
        List<int[]> scored = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            int score = score(entries.get(i), terms);
            if (score > 0) scored.add(new int[]{score, i});
        }
        // Descending by score, and by original position when scores tie, so the list is stable and
        // a keystroke that changes nothing does not reorder what is on screen.
        scored.sort((a, b) -> a[0] != b[0] ? Integer.compare(b[0], a[0]) : Integer.compare(a[1], b[1]));
        for (int[] row : scored) {
            if (results.size() >= MAX_RESULTS) break;
            results.add(entries.get(row[1]));
        }
        return results;
    }

    /**
     * How well one entry answers a query, or zero for no match.
     *
     * <p>Every term has to match something, which is what stops "chat text" scoring on any entry
     * containing the word "chat". Where a term matched decides the weight: a title is what the user
     * is looking for, an alias is what they typed instead of it, and a description is the weakest
     * because a word in a help sentence is usually incidental.
     */
    static int score(Entry entry, String[] terms) {
        if (entry == null || terms == null || terms.length == 0) return 0;
        String title = normalize(entry.title);
        String description = normalize(entry.description);
        String section = normalize(entry.sectionName);
        int total = 0;
        for (String term : terms) {
            if (term.isEmpty()) continue;
            int best = 0;
            if (title.equals(term)) best = 100;
            else if (title.startsWith(term)) best = 70;
            else if (containsWordStart(title, term)) best = 55;
            else if (title.contains(term)) best = 40;
            for (String alias : entry.aliases) {
                int aliasScore = alias.equals(term) ? 65
                        : alias.startsWith(term) ? 50
                        : alias.contains(term) ? 30 : 0;
                if (aliasScore > best) best = aliasScore;
            }
            if (best == 0 && containsWordStart(section, term)) best = 22;
            if (best == 0 && containsWordStart(description, term)) best = 18;
            if (best == 0 && description.contains(term)) best = 10;
            // Every term has to land somewhere, or this entry is not what was asked for.
            if (best == 0) return 0;
            total += best;
        }
        // A result that can take somebody to the exact control is worth more than one that can only
        // open the section it lives in, because that is the whole promise of settings search.
        return entry.hasControl() ? total + 3 : total;
    }

    /** Whether a term begins a word inside a phrase, rather than landing mid-word. */
    static boolean containsWordStart(String haystack, String term) {
        if (haystack.isEmpty() || term.isEmpty()) return false;
        int from = 0;
        while (true) {
            int at = haystack.indexOf(term, from);
            if (at < 0) return false;
            if (at == 0 || !Character.isLetterOrDigit(haystack.charAt(at - 1))) return true;
            from = at + 1;
        }
    }

    static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.US).replaceAll("\\s+", " ").trim();
    }

    /**
     * The index itself.
     *
     * <p>One line per control the user can reach, in roughly the order Settings presents them. Kept
     * bounded on purpose: this is the list of things somebody goes looking for, not a transcript of
     * the settings screen. A control that is only ever reached by first choosing something else -
     * an individual Theme Studio swatch, a per-routine option - belongs to the screen that owns it
     * rather than here.
     */
    private static List<Entry> buildEntries() {
        List<Entry> out = new ArrayList<>();

        out.add(new Entry(OrbitText.get(R.string.ui_make_orbit_default_assistant_7a5864, "Make Orbit default assistant"),
                OrbitText.get(R.string.ui_set_orbit_as_the_digital_assistant_app_and_9190f6, "Set Orbit as the Digital assistant app and map the Side button."),
                OrbitText.get(R.string.ui_assistant_setup_a047e8, S_ASSISTANT), "assistant", KEY_DEFAULT_ASSISTANT,
                "side button", "bixby", "assistant", "default", "long press", "setup"));
        out.add(new Entry(OrbitText.get(R.string.ui_quick_settings_tiles_4b5739, "Quick Settings tiles"),
                OrbitText.get(R.string.ui_open_orbit_or_run_a_routine_from_the_quick_ac33c3, "Open Orbit or run a Routine from the Quick Settings panel."),
                OrbitText.get(R.string.ui_assistant_setup_a047e8, S_ASSISTANT), "assistant", KEY_QUICK_TILES,
                "tile", "quick settings", "shade", "pull down"));
        out.add(new Entry(OrbitText.get(R.string.ui_home_screen_widgets_8fea88, "Home-screen widgets"),
                OrbitText.get(R.string.ui_add_the_ask_orbit_run_routine_and_quick_ac_e53e33, "Add the Ask Orbit, Run Routine and Quick Actions widgets."),
                OrbitText.get(R.string.ui_assistant_setup_a047e8, S_ASSISTANT), "assistant", KEY_WIDGETS,
                "widget", "home screen", "launcher"));

        out.add(new Entry(OrbitText.get(R.string.ui_ai_provider_879125, "AI provider"),
                OrbitText.get(R.string.ui_choose_which_ai_backend_orbit_sends_reques_237ffa, "Choose which AI backend Orbit sends requests to."),
                OrbitText.get(R.string.ui_ai_account_6fa54f, S_AI), "ai", KEY_PROVIDER,
                "provider", "backend", "openrouter", "relay", "orbit local", "chatgpt", "account"));
        out.add(new Entry(OrbitText.get(R.string.ui_chatgpt_sign_in_e846bf, "ChatGPT sign-in"),
                OrbitText.get(R.string.ui_connect_or_disconnect_your_chatgpt_account_15e889, "Connect or disconnect your ChatGPT account."),
                OrbitText.get(R.string.ui_ai_account_6fa54f, S_AI), "ai", KEY_CHATGPT_ACCOUNT,
                "chatgpt", "sign in", "log in", "sign out", "account", "openai", "codex"));
        out.add(new Entry(OrbitText.get(R.string.ui_default_ai_for_new_chats_30d2da, "Default AI for new chats"),
                OrbitText.get(R.string.ui_provider_model_and_strength_new_chats_star_ebd204, "Provider, model and strength new chats start with."),
                OrbitText.get(R.string.ui_ai_account_6fa54f, S_AI), "ai", KEY_DEFAULT_AI,
                "model", "ai model", "provider", "strength", "reasoning", "effort", "default",
                "gpt", "gpt-6", "luna", "sol", "astra", "low", "medium", "high", "max",
                // Auto and its provider switches live in the same card.
                "auto", "smart routing", "routing", "auto can use"));
        out.add(new Entry(OrbitText.get(R.string.ui_thinking_updates_1f86f3, "Thinking updates"),
                OrbitText.get(R.string.ui_brief_status_while_orbit_prepares_an_answe_33117a, "Brief status while Orbit prepares an answer."),
                OrbitText.get(R.string.ui_ai_account_6fa54f, S_AI), "ai", KEY_THINKING_UPDATES,
                "thinking", "status", "progress", "updates"));
        out.add(new Entry(OrbitText.get(R.string.ui_sourced_images_in_answers_3a51b5, "Sourced images in answers"),
                OrbitText.get(R.string.ui_show_a_useful_sourced_picture_inside_an_an_de67f1, "Show a useful sourced picture inside an answer when it helps."),
                OrbitText.get(R.string.ui_ai_account_6fa54f, S_AI), "ai", KEY_RICH_ANSWERS,
                "images", "pictures", "photos", "rich answers", "visual", "web images"));

        out.add(new Entry(OrbitText.get(R.string.ui_allow_orbit_to_read_current_screen_text_560f73, "Allow Orbit to read current-screen text"),
                OrbitText.get(R.string.ui_screen_awareness_for_the_side_button_assis_be3031, "Screen awareness for the Side-button assistant."),
                OrbitText.get(R.string.ui_voice_context_permissions_4f431d, S_VOICE), "voice", KEY_SCREEN_TEXT,
                "screen", "context", "read screen", "awareness", "privacy"));
        out.add(new Entry(OrbitText.get(R.string.ui_allow_orbit_to_receive_screenshots_857a7a, "Allow Orbit to receive screenshots"),
                OrbitText.get(R.string.ui_send_a_picture_of_the_screen_with_a_reques_b28b2f, "Send a picture of the screen with a request."),
                OrbitText.get(R.string.ui_voice_context_permissions_4f431d, S_VOICE), "voice", KEY_SCREENSHOTS,
                "screenshot", "screen image", "capture", "privacy"));
        out.add(new Entry(OrbitText.get(R.string.ui_attach_current_screen_by_default_f37800, "Attach current screen by default"),
                OrbitText.get(R.string.ui_start_each_overlay_request_with_the_screen_c7bfb5, "Start each overlay request with the screen already attached."),
                OrbitText.get(R.string.ui_voice_context_permissions_4f431d, S_VOICE), "voice", KEY_ATTACH_SCREEN,
                "attach", "screen", "default"));
        out.add(new Entry(OrbitText.get(R.string.ui_show_contextual_screen_action_chips_when_a_14fb25, "Show contextual screen-action chips when attached"),
                OrbitText.get(R.string.ui_suggested_actions_for_whatever_is_on_scree_7b7d80, "Suggested actions for whatever is on screen."),
                OrbitText.get(R.string.ui_voice_context_permissions_4f431d, S_VOICE), "voice", KEY_CONTEXT_CHIPS,
                "chips", "suggestions", "actions", "context"));
        out.add(new Entry(OrbitText.get(R.string.ui_speak_replies_to_voice_requests_149b60, "Speak replies to voice requests"),
                OrbitText.get(R.string.ui_read_answers_aloud_when_you_asked_by_voice_95d948, "Read answers aloud when you asked by voice."),
                OrbitText.get(R.string.ui_voice_context_permissions_4f431d, S_VOICE), "voice", KEY_SPEAK,
                "speak", "voice", "tts", "read aloud", "sound", "talk"));
        out.add(new Entry(OrbitText.get(R.string.ui_allow_longer_pauses_while_speaking_c7b5a7, "Allow longer pauses while speaking"),
                OrbitText.get(R.string.ui_keep_listening_through_a_pause_in_a_senten_31b516, "Keep listening through a pause in a sentence."),
                OrbitText.get(R.string.ui_voice_context_permissions_4f431d, S_VOICE), "voice", KEY_VOICE_PAUSE,
                "pause", "voice", "dictation", "listening"));
        out.add(new Entry(OrbitText.get(R.string.ui_start_listening_when_overlay_opens_f2765c, "Start listening when overlay opens"),
                OrbitText.get(R.string.ui_begin_voice_input_as_soon_as_orbit_appears_bb6bdf, "Begin voice input as soon as Orbit appears."),
                OrbitText.get(R.string.ui_voice_context_permissions_4f431d, S_VOICE), "voice", KEY_AUTO_LISTEN,
                "listen", "microphone", "mic", "voice", "auto"));
        out.add(new Entry(OrbitText.get(R.string.ui_hands_free_voice_follow_ups_df5a6a, "Hands-free voice follow-ups"),
                OrbitText.get(R.string.ui_keep_the_conversation_going_without_tappin_61581c, "Keep the conversation going without tapping."),
                OrbitText.get(R.string.ui_voice_context_permissions_4f431d, S_VOICE), "voice", KEY_FOLLOW_UPS,
                "follow up", "hands free", "voice", "conversation"));
        out.add(new Entry(OrbitText.get(R.string.ui_smart_follow_ups_e8e282, "Smart follow-ups"),
                OrbitText.get(R.string.ui_offer_a_sensible_next_question_after_an_an_8420f9, "Offer a sensible next question after an answer."),
                OrbitText.get(R.string.ui_voice_context_permissions_4f431d, S_VOICE), "voice", KEY_SMART_FOLLOW_UPS,
                "follow up", "suggestions", "smart"));
        out.add(new Entry(OrbitText.get(R.string.ui_keyboard_aware_assistant_invocation_701c19, "Keyboard-aware assistant invocation"),
                OrbitText.get(R.string.ui_how_orbit_opens_while_a_keyboard_is_showin_499a65, "How Orbit opens while a keyboard is showing."),
                OrbitText.get(R.string.ui_voice_context_permissions_4f431d, S_VOICE), "voice", KEY_KEYBOARD_AWARE,
                "keyboard", "ime", "invocation"));
        out.add(new Entry(OrbitText.get(R.string.ui_permissions_capabilities_9853eb, "Permissions & capabilities"),
                OrbitText.get(R.string.ui_microphone_contacts_camera_notifications_a_0ab0ac, "Microphone, contacts, camera, notifications and calendar access."),
                OrbitText.get(R.string.ui_voice_context_permissions_4f431d, S_VOICE), "voice", KEY_PERMISSIONS,
                "permission", "microphone", "mic", "camera", "contacts", "calendar", "access"));

        out.add(new Entry(OrbitText.get(R.string.ui_orbit_memory_05d7a8, "Orbit Memory"),
                OrbitText.get(R.string.ui_what_orbit_remembers_about_you_between_cha_233ca0, "What Orbit remembers about you between chats."),
                OrbitText.get(R.string.ui_personalization_data_853fcd, S_DATA), "data", KEY_MEMORY,
                "memory", "remember", "personalization", "facts", "forget"));
        out.add(new Entry(OrbitText.get(R.string.ui_orbit_reminders_ffff18, "Orbit reminders"),
                OrbitText.get(R.string.ui_reminders_orbit_has_scheduled_058c10, "Reminders Orbit has scheduled."),
                OrbitText.get(R.string.ui_personalization_data_853fcd, S_DATA), "data", KEY_REMINDERS,
                "reminder", "alarm", "notify", "schedule"));
        out.add(new Entry(OrbitText.get(R.string.ui_saved_places_594844, "Saved places"),
                OrbitText.get(R.string.ui_home_work_and_other_places_orbit_knows_666353, "Home, work and other places Orbit knows."),
                OrbitText.get(R.string.ui_personalization_data_853fcd, S_DATA), "data", KEY_PLACES,
                "places", "location", "home", "work", "address", "map"));
        out.add(new Entry(OrbitText.get(R.string.ui_app_profiles_45f1fe, "App profiles"),
                OrbitText.get(R.string.ui_how_orbit_behaves_over_each_app_ea5d54, "How Orbit behaves over each app."),
                OrbitText.get(R.string.ui_personalization_data_853fcd, S_DATA), "data", KEY_APP_PROFILES,
                "apps", "per app", "profile", "privacy"));
        out.add(new Entry(OrbitText.get(R.string.ui_notification_intelligence_b32b4b, "Notification intelligence"),
                OrbitText.get(R.string.ui_what_orbit_may_read_from_your_notification_06c6e6, "What Orbit may read from your notifications."),
                OrbitText.get(R.string.ui_personalization_data_853fcd, S_DATA), "data", KEY_NOTIFICATIONS,
                "notifications", "alerts", "privacy", "retention"));
        out.add(new Entry(OrbitText.get(R.string.ui_orbit_vault_ecac6d, "Orbit Vault"),
                OrbitText.get(R.string.ui_the_things_you_have_chosen_to_keep_90e0ec, "The things you have chosen to keep."),
                OrbitText.get(R.string.ui_personalization_data_853fcd, S_DATA), "data", KEY_VAULT,
                "vault", "saved", "saved stuff", "keep", "collection", "clips", "bookmarks",
                "smart vault", "text in pictures", "search by meaning", "topics"));
        out.add(new Entry(OrbitText.get(R.string.ui_weather_a0bba6, "Weather"),
                OrbitText.get(R.string.ui_default_weather_location_and_units_b84b33, "Default weather location and units."),
                OrbitText.get(R.string.ui_personalization_data_853fcd, S_DATA), "data", KEY_WEATHER,
                "weather", "temperature", "celsius", "fahrenheit", "units", "forecast"));
        out.add(new Entry(OrbitText.get(R.string.ui_gallery_app_eabbd2, "Gallery app"),
                OrbitText.get(R.string.ui_which_app_orbit_opens_for_photos_4b7af3, "Which app Orbit opens for photos."),
                OrbitText.get(R.string.ui_personalization_data_853fcd, S_DATA), "data", KEY_GALLERY,
                "gallery", "photos", "pictures", "album"));
        out.add(new Entry(OrbitText.get(R.string.ui_backup_restore_007696, "Backup & restore"),
                OrbitText.get(R.string.ui_export_or_restore_your_orbit_data_085b59, "Export or restore your Orbit data."),
                OrbitText.get(R.string.ui_personalization_data_853fcd, S_DATA), "data", KEY_BACKUP,
                "backup", "restore", "export", "import", "transfer", "copy"));

        out.add(new Entry(OrbitText.get(R.string.ui_orbit_deck_9ba2c9, "Orbit Deck"),
                OrbitText.get(R.string.ui_shortcuts_to_the_things_orbit_already_does_7f717c, "Shortcuts to the things Orbit already does."),
                OrbitText.get(R.string.ui_orbit_deck_9ba2c9, S_DECK), "deck", KEY_DECK,
                "deck", "shortcuts", "tiles", "suggestions"));

        out.add(new Entry(OrbitText.get(R.string.ui_start_a_new_chat_each_time_orbit_opens_ae9f24, "Start a new chat each time Orbit opens"),
                OrbitText.get(R.string.ui_whether_the_overlay_resumes_the_last_chat_435aff, "Whether the overlay resumes the last chat."),
                OrbitText.get(R.string.ui_conversations_1d432f, S_CHATS), "conversations", KEY_NEW_CHAT,
                "new chat", "resume", "continue", "overlay"));
        out.add(new Entry(OrbitText.get(R.string.ui_save_recent_chats_on_this_device_92b8c3, "Save recent chats on this device"),
                OrbitText.get(R.string.ui_keep_conversation_history_locally_fbddb3, "Keep conversation history locally."),
                OrbitText.get(R.string.ui_conversations_1d432f, S_CHATS), "conversations", KEY_HISTORY,
                "history", "chats", "save", "privacy"));
        out.add(new Entry(OrbitText.get(R.string.ui_save_screen_attachment_thumbnails_in_chat_c2f84e, "Save screen attachment thumbnails in chat history"),
                OrbitText.get(R.string.ui_keep_the_pictures_attached_to_past_turns_8befec, "Keep the pictures attached to past turns."),
                OrbitText.get(R.string.ui_conversations_1d432f, S_CHATS), "conversations", KEY_THUMBNAILS,
                "thumbnails", "attachments", "screenshots", "storage"));
        out.add(new Entry(OrbitText.get(R.string.ui_show_stop_button_while_replying_56b08e, "Show Stop button while replying"),
                OrbitText.get(R.string.ui_offer_a_stop_control_during_a_reply_59733d, "Offer a Stop control during a reply."),
                OrbitText.get(R.string.ui_conversations_1d432f, S_CHATS), "conversations", KEY_STOP_BUTTON,
                "stop", "cancel", "interrupt"));
        out.add(new Entry(OrbitText.get(R.string.ui_clear_orbit_conversation_history_23817e, "Clear Orbit conversation history"),
                OrbitText.get(R.string.ui_delete_every_stored_chat_on_this_device_75ed95, "Delete every stored chat on this device."),
                OrbitText.get(R.string.ui_conversations_1d432f, S_CHATS), "conversations", KEY_CLEAR_HISTORY,
                "clear", "delete chats", "erase", "history", "wipe"));

        out.add(new Entry(OrbitText.get(R.string.ui_theme_studio_d64bfb, "Theme Studio"),
                OrbitText.get(R.string.ui_accent_amoled_surfaces_and_bubble_colors_b0c391, "Accent, AMOLED, surfaces and bubble colors."),
                OrbitText.get(R.string.ui_look_feel_a4103a, S_LOOK), "appearance", KEY_THEME_STUDIO,
                "amoled", "dark mode", "dark", "light", "theme", "accent", "color", "colour",
                "black", "appearance", "bubble", "nova", "palette"));
        out.add(new Entry(OrbitText.get(R.string.ui_app_font_cad029, "App font"),
                OrbitText.get(R.string.ui_the_typeface_orbit_uses_everywhere_a99fa6, "The typeface Orbit uses everywhere."),
                OrbitText.get(R.string.ui_look_feel_a4103a, S_LOOK), "appearance", KEY_FONT,
                "font", "typeface", "typography", "text"));
        out.add(new Entry(OrbitText.get(R.string.ui_chat_text_size_dff793, "Chat text size"),
                OrbitText.get(R.string.ui_how_large_answers_and_messages_are_drawn_a52fd6, "How large answers and messages are drawn."),
                OrbitText.get(R.string.ui_look_feel_a4103a, S_LOOK), "appearance", KEY_CHAT_TEXT_SIZE,
                "text size", "font size", "bigger text", "larger", "small", "readable"));
        out.add(new Entry(OrbitText.get(R.string.ui_overlay_2af93a, "Overlay"),
                OrbitText.get(R.string.ui_customize_how_orbit_appears_over_other_app_b45a09, "Customize how Orbit appears over other apps."),
                OrbitText.get(R.string.ui_look_feel_a4103a, S_LOOK), "appearance", KEY_OVERLAY,
                "overlay", "customize overlay", "side button", "assistant overlay", "overlay settings"));
        out.add(new Entry(OrbitText.get(R.string.ui_overlay_preview_2082d6, "Overlay preview"),
                OrbitText.get(R.string.ui_a_live_picture_of_the_overlay_as_you_custo_65ce2e, "A live picture of the overlay as you customize it."),
                OrbitText.get(R.string.ui_look_feel_a4103a, S_LOOK), "appearance", KEY_OVERLAY_PREVIEW,
                "preview", "overlay preview", "how it looks"));
        out.add(new Entry(OrbitText.get(R.string.ui_overlay_style_5b130b, "Overlay style"),
                OrbitText.get(R.string.ui_modern_float_or_classic_look_for_the_side_73ada3, "Modern, Float or Classic look for the Side-button assistant."),
                OrbitText.get(R.string.ui_look_feel_a4103a, S_LOOK), "appearance", KEY_OVERLAY_STYLE,
                "overlay", "side button", "assistant look", "modern", "float", "compact", "classic",
                "landscape", "tablet", "peek", "minimise", "minimize"));
        out.add(new Entry(OrbitText.get(R.string.ui_quick_controls_4b79a9, "Quick controls"),
                OrbitText.get(R.string.ui_choose_which_buttons_stay_on_the_overlay_h_bcf3e3, "Choose which buttons stay on the overlay. Hidden ones move to its More menu."),
                OrbitText.get(R.string.ui_look_feel_a4103a, S_LOOK), "appearance", KEY_OVERLAY_QUICK_CONTROLS,
                "overlay buttons", "buttons", "hide buttons", "quick controls", "more menu",
                "model chip", "history button", "new chat button", "screen buttons",
                "attach button", "mic button", "voice button", "minimal overlay"));
        out.add(new Entry(OrbitText.get(R.string.ui_haptic_feedback_153d8b, "Haptic feedback"),
                OrbitText.get(R.string.ui_whether_orbit_vibrates_on_interactions_f60639, "Whether Orbit vibrates on interactions."),
                OrbitText.get(R.string.ui_look_feel_a4103a, S_LOOK), "appearance", KEY_HAPTICS,
                "haptics", "vibrate", "vibration", "feedback", "buzz"));
        out.add(new Entry(OrbitText.get(R.string.ui_page_transitions_635bbc, "Page transitions"),
                OrbitText.get(R.string.ui_how_screens_move_when_orbit_changes_page_efa182, "How screens move when Orbit changes page."),
                OrbitText.get(R.string.ui_look_feel_a4103a, S_LOOK), "appearance", KEY_PAGE_TRANSITIONS,
                "transition", "animation", "motion", "effects"));
        out.add(new Entry(OrbitText.get(R.string.ui_swipe_to_go_back_cb5e33, "Swipe to go back"),
                OrbitText.get(R.string.ui_interactive_back_gesture_inside_orbit_abf60d, "Interactive back gesture inside Orbit."),
                OrbitText.get(R.string.ui_look_feel_a4103a, S_LOOK), "appearance", KEY_SWIPE_BACK,
                "swipe", "gesture", "back", "navigation"));
        out.add(new Entry(OrbitText.get(R.string.ui_chat_swipe_actions_736fe6, "Chat swipe actions"),
                OrbitText.get(R.string.ui_swipe_a_chat_in_the_list_to_act_on_it_9203c6, "Swipe a chat in the list to act on it."),
                OrbitText.get(R.string.ui_look_feel_a4103a, S_LOOK), "appearance", KEY_CHAT_SWIPE,
                "swipe", "gesture", "chats", "delete", "pin", "actions"));

        out.add(new Entry(OrbitText.get(R.string.ui_orbit_diagnostics_c8a737, "Orbit diagnostics"),
                OrbitText.get(R.string.ui_local_routing_context_and_troubleshooting_e42fc7, "Local routing, context and troubleshooting information."),
                OrbitText.get(R.string.ui_advanced_9f088d, S_ADVANCED), "advanced", KEY_DIAGNOSTICS,
                "diagnostics", "debug", "troubleshoot", "logs", "developer", "astra", "model"));

        // Three destinations that are whole screens rather than rows. They carry no control key,
        // because there is no row to pulse: choosing one opens the screen it names, which is the
        // exact place the user was heading.
        out.add(new Entry(OrbitText.get(R.string.ui_routines_61b7bb, "Routines"),
                OrbitText.get(R.string.ui_saved_multi_step_routines_triggers_and_cus_8e03ce, "Saved multi-step Routines, triggers and Custom Commands."),
                OrbitText.get(R.string.ui_routines_61b7bb, S_ROUTINES), "routines", "",
                "routine", "automation", "trigger", "custom command", "schedule"));
        out.add(new Entry(OrbitText.get(R.string.ui_extensions_fe3436, "Extensions"),
                OrbitText.get(R.string.ui_declarative_orbit_extensions_and_their_set_b212a0, "Declarative Orbit extensions and their setup."),
                OrbitText.get(R.string.ui_extensions_fe3436, S_EXTENSIONS), "extensions", "",
                "extension", "orbitext", "integration", "plugin", "api"));
        out.add(new Entry(OrbitText.get(R.string.ui_about_updates_1b0c20, "About & updates"),
                OrbitText.get(R.string.ui_version_update_channel_and_release_notes_295c3c, "Version, update channel and release notes."),
                OrbitText.get(R.string.ui_about_updates_1b0c20, S_UPDATES), "updates", "",
                "update", "version", "beta", "stable", "channel", "release notes", "changelog",
                "about", "upgrade"));

        return Collections.unmodifiableList(out);
    }
}
