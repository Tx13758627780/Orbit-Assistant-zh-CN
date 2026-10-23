package com.orbit.assistant;

import static org.junit.Assert.*;

import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.LinearLayout;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** Chinese UI must distinguish checking from a failed component call. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {29, 35}, qualifiers = "zh-rCN")
public final class LocalAiConnectionRegressionTest {
    private static void advance(long ms) {
        ShadowLooper.idleMainLooper(ms, TimeUnit.MILLISECONDS);
    }

    @Test public void aMissingCallbackTimesOutAndPollingCanRecover() {
        List<Consumer<OrbitLocalStatus>> requests = new ArrayList<>();
        List<OrbitLocalStatus> delivered = new ArrayList<>();
        OrbitLocalStatusPoller poller = new OrbitLocalStatusPoller(
                new Handler(Looper.getMainLooper()), requests::add,
                (status, changed) -> delivered.add(status), null);
        poller.start();
        advance(OrbitLocalStatusPoller.READ_TIMEOUT_MS);
        assertEquals(1, delivered.size());
        assertNull(delivered.get(0));
        advance(OrbitLocalStatusPoller.IDLE_MS);
        assertEquals(2, requests.size());
        // The timed out callback must not overwrite a newer request.
        requests.get(0).accept(OrbitLocalStatus.from(new android.os.Bundle()));
        advance(0);
        assertEquals(1, delivered.size());
        requests.get(1).accept(OrbitLocalStatus.from(new android.os.Bundle()));
        advance(0);
        assertEquals(2, delivered.size());
        assertNotNull(poller.last());
        poller.stop();
    }

    @Test public void leavingAndReturningDoesNotKeepAnOldCheckInFlight() {
        List<Consumer<OrbitLocalStatus>> requests = new ArrayList<>();
        List<OrbitLocalStatus> delivered = new ArrayList<>();
        OrbitLocalStatusPoller poller = new OrbitLocalStatusPoller(
                new Handler(Looper.getMainLooper()), requests::add,
                (status, changed) -> delivered.add(status), null);
        poller.start();
        poller.stop();
        poller.start();
        assertEquals(2, requests.size());
        requests.get(0).accept(OrbitLocalStatus.from(new android.os.Bundle()));
        advance(0);
        assertTrue(delivered.isEmpty());
        requests.get(1).accept(null);
        advance(0);
        assertEquals(1, delivered.size());
        poller.stop();
    }

    @Test public void aThrownStatusCheckStillFinishesAndSchedulesAnotherRead() {
        int[] attempts = {0};
        int[] delivered = {0};
        OrbitLocalStatusPoller poller = new OrbitLocalStatusPoller(
                new Handler(Looper.getMainLooper()), answer -> {
                    attempts[0]++;
                    throw new SecurityException("signature permission denied");
                }, (status, changed) -> { assertNull(status); delivered[0]++; }, null);
        poller.start();
        assertEquals(1, delivered[0]);
        advance(OrbitLocalStatusPoller.IDLE_MS);
        assertEquals(2, attempts[0]);
        assertEquals(2, delivered[0]);
        poller.stop();
    }

    @Test public void bothChineseModelCardsOfferRetryAfterAConnectionFailure() throws Exception {
        OrbitText.initialize(RuntimeEnvironment.getApplication());
        try (ActivityController<LocalAiActivity> controller =
                     Robolectric.buildActivity(LocalAiActivity.class).create()) {
            LocalAiActivity activity = controller.get();
            Field checked = LocalAiActivity.class.getDeclaredField("statusChecked");
            checked.setAccessible(true);
            for (String name : new String[]{"modelDetails", "actionModelDetails"}) {
                Method details = LocalAiActivity.class.getDeclaredMethod(name, boolean.class);
                details.setAccessible(true);
                checked.setBoolean(activity, false);
                assertTrue(((String) details.invoke(activity, true)).contains("正在检查"));
                checked.setBoolean(activity, true);
                String text = (String) details.invoke(activity, true);
                assertTrue(text, text.contains("无法连接"));
                assertFalse(text, text.contains("正在检查"));
            }
            for (String name : new String[]{"buildModelActions", "buildActionModelActions"}) {
                checked.setBoolean(activity, true);
                LinearLayout actions = new LinearLayout(activity);
                Method build = LocalAiActivity.class.getDeclaredMethod(name, LinearLayout.class, boolean.class);
                build.setAccessible(true);
                build.invoke(activity, actions, true);
                assertEquals(1, actions.getChildCount());
                Button retry = (Button) actions.getChildAt(0);
                assertEquals("重试连接", retry.getText().toString());
                retry.performClick();
                assertFalse(checked.getBoolean(activity));
            }
        }
    }
}
