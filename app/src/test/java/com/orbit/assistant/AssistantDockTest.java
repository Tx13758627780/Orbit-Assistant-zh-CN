package com.orbit.assistant;

import static org.junit.Assert.*;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.drawable.ColorDrawable;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowLooper;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "zh-rCN-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public final class AssistantDockTest {
    private Context context;
    private OrbitSession session;
    private FrameLayout root;

    @Before public void setup() {
        RuntimeEnvironment.setQualifiers("zh-rCN-w412dp-h915dp-mdpi");
        context = RuntimeEnvironment.getApplication();
        OrbitText.initialize(context);
        Prefs.get(context).edit().clear().commit();
        Prefs.setOverlayStyle(context, Prefs.OVERLAY_STYLE_MODERN);
        UiKit.syncTheme(context);
        OrbitSessionService service = Robolectric.buildService(OrbitSessionService.class).create().get();
        session = (OrbitSession) service.onNewSession(null);
        root = (FrameLayout) session.onCreateContentView();
        android.app.Activity host = Robolectric.buildActivity(android.app.Activity.class).setup().get();
        host.setContentView(root);
        sheet().setVisibility(View.VISIBLE);
        layout(412, 915);
    }

    @Test public void invocationIsOnlyACompactEditorOverATransparentDesktop() throws Exception {
        assertEquals(0, panel().getHeight());
        assertTrue("compact editor", root.findViewWithTag("orbit_dock_composer").getHeight() <= UiKit.dp(context, 84));
        assertTrue("editor plus optional screen capsule", sheet().getHeight() <= UiKit.dp(context, 148));
        assertEquals(0, Color.alpha(((ColorDrawable) root.getBackground()).getColor()));
        assertEquals(0, Color.alpha(((ColorDrawable) root.getChildAt(0).getBackground()).getColor()));
        assertEquals("语音对话", ((ImageButton) field("sendButton")).getContentDescription());
        assertFalse(editor().hasFocus());
        assertNotNull(findDescription(root, "语音输入"));
    }

    @Test public void typingOnlyChangesTheActionAndDoesNotOpenAConversation() throws Exception {
        editor().setText("你好，Orbit");
        assertEquals("发送消息", ((ImageButton) field("sendButton")).getContentDescription());
        assertFalse(panel().isExpanded());
        editor().setText("");
        assertEquals("语音对话", ((ImageButton) field("sendButton")).getContentDescription());
        setField("busy", true);
        invoke("updateComposerAction");
        assertEquals(context.getString(R.string.ui_stop_response_d5ca57),
                ((ImageButton) field("sendButton")).getContentDescription());
        setField("busy", false);
    }

    @Test public void conversationGrowsWithContentAndIsCappedAboveTheKeyboard() throws Exception {
        history().add(new AssistantClient.History("user", "帮我安排今天的学习计划"));
        history().add(new AssistantClient.History("assistant", "先用二十五分钟完成最重要的一项任务，然后休息五分钟。"));
        invoke("renderConversation");
        expand(true);
        layout(412, 915);
        int shortHeight = panel().getHeight();
        assertTrue(shortHeight > 0);
        assertTrue("short answers fit content", shortHeight < UiKit.dp(context, 360));
        history().add(new AssistantClient.History("assistant", "这是一段较长的回复，用于验证高度和滚动。\n".repeat(60)));
        invoke("renderConversation");
        layout(412, 915);
        assertTrue(panel().getHeight() > shortHeight);
        applyInsets(24, 32, 320, 0, 0);
        layout(412, 915);
        assertTrue(sheet().getBottom() <= UiKit.dp(context, 915 - 320 - 12));
        assertTrue(sheet().getTop() >= UiKit.dp(context, 32));
        assertTrue(editor().getMeasuredHeight() >= UiKit.dp(context, 40));
        assertTrue(((ScrollView) field("messageScroll")).getMeasuredHeight() < UiKit.dp(context, 500));
    }

    @Test public void collapsingKeepsTheEditorHistoryAndRunningRequest() throws Exception {
        EditText original = editor();
        original.setText("未发送的草稿");
        history().add(new AssistantClient.History("user", "保留这个对话"));
        invoke("renderConversation");
        setField("busy", true);
        expand(true);
        layout(412, 915);
        expand(false);
        layout(412, 915);
        assertSame(original, editor());
        assertEquals("未发送的草稿", editor().getText().toString());
        assertEquals(1, history().size());
        assertEquals(true, field("busy"));
        assertEquals(0, panel().getHeight());
    }

    @Test public void gesturesCollapseAndReopenTheSameConversation() throws Exception {
        history().add(new AssistantClient.History("assistant", "下滑可以收起，上滑可以展开。"));
        invoke("renderConversation");
        expand(true);
        layout(412, 915);
        swipe(root.findViewWithTag("orbit_dock_handle"), 90);
        assertFalse(panel().isExpanded());
        swipe(root.findViewWithTag("orbit_dock_handle"), -90);
        assertTrue(panel().isExpanded());
        assertEquals(1, history().size());
    }

    @Test public void aRequestRevealsContentAndManualCollapseSurvivesLaterUpdates() throws Exception {
        setField("busy", true);
        invoke("showThinkingIndicator");
        assertTrue(panel().isExpanded());
        layout(412, 915);
        ShadowLooper.idleMainLooper(300, java.util.concurrent.TimeUnit.MILLISECONDS);
        layout(412, 915);
        assertTrue(panel().getHeight() > 0);
        Method collapse = OrbitSession.class.getDeclaredMethod("setDockExpanded", boolean.class, boolean.class);
        collapse.setAccessible(true); collapse.invoke(session, false, true);
        layout(412, 915);
        ShadowLooper.idleMainLooper(300, java.util.concurrent.TimeUnit.MILLISECONDS);
        layout(412, 915);
        assertEquals(0, panel().getHeight());
        invoke("showThinkingIndicator");
        assertFalse(panel().isExpanded());
        invoke("stopThinkingIndicator");
        setField("busy", false);
    }

    @Test public void plusMenuRetainsModelHistoryNewChatScreenAndClose() throws Exception {
        findDescription(root, "更多选项").performClick();
        for (String label : new String[]{"添加附件", "AI 模型", "最近聊天", "新建聊天", "屏幕", "关闭 Orbit"})
            assertNotNull(label, findText(root, label));
        // Opening Screen routes through the existing screen/region selection menu.
        ((View) findText(root, "屏幕").getParent()).performClick();
        ShadowLooper.idleMainLooper(32, java.util.concurrent.TimeUnit.MILLISECONDS);
        assertNotNull(findText(root, context.getString(R.string.ui_select_or_mark_area_99bb33)));
    }

    @Test public void gestureInsetsLandscapeAndLargeTextKeepControlsInTheWindow() throws Exception {
        RuntimeEnvironment.setFontScale(1.5f);
        editor().setText("较大的字体也应保留输入框和全部常用按钮");
        layout(915, 412);
        applyInsets(24, 24, 0, 32, 0);
        layout(915, 412);
        assertTrue(sheet().getLeft() >= UiKit.dp(context, 44));
        assertTrue(sheet().getBottom() <= UiKit.dp(context, 376));
        assertTrue(sheet().getWidth() <= UiKit.dp(context, 520));
        assertTrue(editor().getMeasuredWidth() > 0);
        RuntimeEnvironment.setFontScale(1f);
    }

    @Test public void renderLightAndDarkActualViewTrees() throws Exception {
        String directory = System.getProperty("orbit.dock.render.dir", "build/dock-renders");
        for (String mode : new String[]{"light", "dark"}) {
            RuntimeEnvironment.setQualifiers("zh-rCN-w412dp-h915dp-" + (mode.equals("dark") ? "night" : "notnight") + "-mdpi");
            Prefs.get(context).edit().putString(Prefs.THEME_SURFACE, "classic")
                    .putString(Prefs.THEME_BACKGROUND, "classic").commit();
            UiKit.syncTheme(context);
            session.onCreateContentView();
            root = (FrameLayout) field("root");
            android.app.Activity host = Robolectric.buildActivity(android.app.Activity.class).setup().get();
            host.setContentView(root);
            sheet().setVisibility(View.VISIBLE);
            setField("screenText", "桌面示例：天气与今天的安排");
            invoke("updateContextUi");
            layout(412, 915);
            ShadowLooper.idleMainLooper(500, java.util.concurrent.TimeUnit.MILLISECONDS);
            layout(412, 915);
            assertEquals("classic", Prefs.get(context).getString(Prefs.THEME_SURFACE, "classic"));
            assertEquals(mode.equals("light"), Color.luminance(UiKit.SURFACE) > .5f);
            Bitmap collapsed = render(new File(directory, mode + "-collapsed.png"));
            int[] composerAt = new int[2];
            root.findViewWithTag("orbit_dock_composer").getLocationInWindow(composerAt);
            assertEquals("no collapsed header may paint on the desktop", Color.rgb(141,155,150),
                    collapsed.getPixel(UiKit.dp(context, 24), composerAt[1] - UiKit.dp(context, 24)));
            history().add(new AssistantClient.History("user", "如何专注完成今天的任务？"));
            history().add(new AssistantClient.History("assistant", "先选一件最重要的事，专注二十五分钟。休息后，再开始下一轮。"));
            invoke("renderConversation"); expand(true); layout(412, 915);
            ShadowLooper.idleMainLooper(500, java.util.concurrent.TimeUnit.MILLISECONDS);
            layout(412, 915);
            render(new File(directory, mode + "-expanded.png"));
            history().clear();
        }
    }

    private Bitmap render(File file) throws Exception {
        file.getParentFile().mkdirs();
        Bitmap image = Bitmap.createBitmap(root.getWidth(), root.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(image);
        canvas.drawColor(Color.rgb(141, 155, 150));
        root.draw(canvas);
        try (FileOutputStream out = new FileOutputStream(file)) { image.compress(Bitmap.CompressFormat.PNG, 100, out); }
        return image;
    }

    private void applyInsets(int nav, int top, int ime, int left, int right) {
        root.dispatchApplyWindowInsets(new WindowInsets.Builder()
                .setInsets(WindowInsets.Type.systemBars(), Insets.of(left, top, right, nav))
                .setInsets(WindowInsets.Type.mandatorySystemGestures(), Insets.of(0, 0, 0, nav))
                .setInsets(WindowInsets.Type.ime(), Insets.of(0, 0, 0, ime))
                .setVisible(WindowInsets.Type.ime(), ime > 0).build());
    }
    private void layout(int w, int h) {
        root.measure(View.MeasureSpec.makeMeasureSpec(UiKit.dp(context, w), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(UiKit.dp(context, h), View.MeasureSpec.EXACTLY));
        root.layout(0, 0, root.getMeasuredWidth(), root.getMeasuredHeight());
    }
    private View sheet() { return root.getChildAt(1); }
    private AssistantDockPanel panel() { return root.findViewWithTag("orbit_dock_content"); }
    private EditText editor() throws Exception { return (EditText) field("input"); }
    @SuppressWarnings("unchecked") private List<AssistantClient.History> history() throws Exception {
        return (List<AssistantClient.History>) field("history");
    }
    private Object field(String name) throws Exception {
        Field field = OrbitSession.class.getDeclaredField(name); field.setAccessible(true); return field.get(session);
    }
    private void setField(String name, Object value) throws Exception {
        Field field = OrbitSession.class.getDeclaredField(name); field.setAccessible(true); field.set(session, value);
    }
    private void invoke(String name) throws Exception {
        Method method = OrbitSession.class.getDeclaredMethod(name); method.setAccessible(true); method.invoke(session);
    }
    private void expand(boolean open) throws Exception {
        Method method = OrbitSession.class.getDeclaredMethod("setDockExpanded", boolean.class, boolean.class);
        method.setAccessible(true); method.invoke(session, open, false);
    }
    private static View findDescription(View root, String text) {
        if (text.contentEquals(root.getContentDescription() == null ? "" : root.getContentDescription())) return root;
        if (root instanceof ViewGroup) for (int i=0;i<((ViewGroup) root).getChildCount();i++) {
            View found=findDescription(((ViewGroup) root).getChildAt(i),text); if(found!=null)return found;
        }
        return null;
    }
    private static TextView findText(View root, String text) {
        if (root instanceof TextView && text.contentEquals(((TextView) root).getText())) return (TextView) root;
        if (root instanceof ViewGroup) for (int i=0;i<((ViewGroup) root).getChildCount();i++) {
            TextView found=findText(((ViewGroup) root).getChildAt(i),text); if(found!=null)return found;
        }
        return null;
    }
    private static void swipe(View target, int dy) {
        long t=SystemClock.uptimeMillis();
        for(int[] step:new int[][]{{MotionEvent.ACTION_DOWN,0},{MotionEvent.ACTION_MOVE,dy},{MotionEvent.ACTION_UP,dy}}) {
            MotionEvent e=MotionEvent.obtain(t,t+80,step[0],20,80+step[1],0); target.dispatchTouchEvent(e);e.recycle();
        }
    }
}
