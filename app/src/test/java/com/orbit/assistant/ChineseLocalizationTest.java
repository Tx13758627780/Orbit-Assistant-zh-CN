package com.orbit.assistant;

import static org.junit.Assert.*;

import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.ArrayList;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {29, 35}, qualifiers = "zh-rCN")
public final class ChineseLocalizationTest {
    private Context context;

    @Before public void setUp() {
        context = RuntimeEnvironment.getApplication();
        OrbitText.initialize(context);
        Prefs.get(context).edit().clear().commit();
    }

    @Test public void systemResourcesAndMenusUseChinese() {
        assertEquals("Orbit 助手", context.getString(R.string.app_name));
        assertArrayEquals(new String[]{"相机", "图库", "文件", "屏幕", "剪贴板", "收藏库"},
                ChatActivity.attachmentMenuLabels(true));
        assertArrayEquals(new String[]{"复制", "保存到收藏库", "重试"},
                MessageActions.assistantLabels(true, true));
    }

    @Test public void chineseSynonymsFindTheActualSettingsControl() {
        assertEquals(SettingsSearchIndex.KEY_HAPTICS,
                SettingsSearchIndex.search("震动").get(0).controlKey);
        assertEquals(SettingsSearchIndex.KEY_THEME_STUDIO,
                SettingsSearchIndex.search("深色").get(0).controlKey);
        assertEquals(SettingsSearchIndex.KEY_DEFAULT_ASSISTANT,
                SettingsSearchIndex.search("电源键").get(0).controlKey);
    }

    @Test public void changingLocaleDoesNotLeaveTheSearchIndexInThePreviousLanguage() {
        assertEquals("触感反馈", SettingsSearchIndex.search("震动").get(0).title);
        RuntimeEnvironment.setQualifiers("en-rUS");
        assertEquals("Haptic feedback", SettingsSearchIndex.search("haptic").get(0).title);
        RuntimeEnvironment.setQualifiers("zh-rCN");
        assertEquals("触感反馈", SettingsSearchIndex.search("震动").get(0).title);
    }

    @Test public void userTextThatLooksLikeALabelIsNeverTranslated() {
        assertEquals("Copy", MessageActions.userCopyText("Copy"));
        assertEquals("Settings", MessageActions.userCopyText("Settings"));
        assertEquals("复制", MessageActions.userCopyText("复制"));
    }

    @Test public void translatedMetadataKeepsBackendAndPreferenceIdsStable() {
        assertEquals("高", AiStrength.HIGH.displayLabel());
        assertEquals("high", AiStrength.HIGH.id);
        assertEquals(AiStrength.HIGH, AiStrength.fromId("high"));
        AiSelection selection = AiSelection.of("chatgpt", "test-model", AiStrength.HIGH);
        assertTrue(selection.label().endsWith("高"));
        assertEquals(selection, AiSelection.decode(selection.encode()));
        assertEquals("现代", OverlayStyle.MODERN.displayLabel());
        assertEquals(Prefs.OVERLAY_STYLE_MODERN, OverlayStyle.MODERN.id);
        RuntimeEnvironment.setQualifiers("en-rUS");
        assertEquals("High", AiStrength.HIGH.displayLabel());
        assertEquals("Modern", OverlayStyle.MODERN.displayLabel());
    }

    @Test public void tappingChineseFileLabelStillOpensTheDocumentPicker() {
        ConversationStore.save(context, "chinese-menu", new ArrayList<>());
        Intent intent = new Intent(context, ChatActivity.class)
                .putExtra(ChatActivity.EXTRA_CONVERSATION_ID, "chinese-menu");
        try (ActivityController<ChatActivity> controller =
                     Robolectric.buildActivity(ChatActivity.class, intent).setup()) {
            ChatActivity activity = controller.get();
            activity.showAttachmentMenuForTest();
            TextView file = findText(activity.findViewById(android.R.id.content), "文件");
            assertNotNull(file);
            ((View) file.getParent()).performClick();
            assertEquals(Intent.ACTION_OPEN_DOCUMENT,
                    Shadows.shadowOf(activity).getNextStartedActivityForResult().intent.getAction());
        }
    }

    private static TextView findText(View view, String label) {
        if (view instanceof TextView && label.contentEquals(((TextView) view).getText())) {
            return (TextView) view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                TextView found = findText(group.getChildAt(i), label);
                if (found != null) return found;
            }
        }
        return null;
    }
}
