package com.orbit.assistant;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

/** Content-sized reveal above the persistent editor. The editor never joins the height animation. */
final class AssistantDockPanel extends FrameLayout {
    private boolean expanded;
    private int limit;
    private int revealed;
    private int target;
    private ValueAnimator animator;
    private boolean animateChanges = true;
    private boolean scheduled;

    AssistantDockPanel(Context context) {
        super(context);
        setTag("orbit_dock_content");
        setClipChildren(true);
        setClipToPadding(true);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        limit = UiKit.dp(context, 420);
    }

    boolean isExpanded() { return expanded; }

    void setExpanded(boolean value, boolean animate) {
        expanded = value;
        animateChanges = animate;
        setImportantForAccessibility(value ? IMPORTANT_FOR_ACCESSIBILITY_AUTO
                : IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        requestLayout();
    }

    void setHeightLimit(int pixels) {
        pixels = Math.max(0, pixels);
        if (limit == pixels) return;
        limit = pixels;
        // A shrinking window/IME must take effect immediately, never clip the editor off screen.
        if (revealed > limit) revealed = limit;
        requestLayout();
    }

    void cancelMotion() {
        if (animator != null) animator.cancel();
        animator = null;
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec);
        int available = MeasureSpec.getMode(heightSpec) == MeasureSpec.UNSPECIFIED
                ? limit : Math.min(limit, MeasureSpec.getSize(heightSpec));
        int natural = 0;
        if (getChildCount() > 0) {
            View child = getChildAt(0);
            child.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(available, MeasureSpec.AT_MOST));
            natural = child.getMeasuredHeight();
        }
        int desired = expanded ? Math.min(available, natural) : 0;
        if (!animateChanges || !UiKit.animationsEnabled() || getWidth() == 0) {
            cancelMotion();
            target = desired;
            revealed = desired;
        } else if (target != desired) {
            target = desired;
            if (!scheduled) {
                scheduled = true;
                post(() -> { scheduled = false; animateToTarget(); });
            }
        }
        int height = Math.min(available, Math.max(0, revealed));
        // An explicit clip is needed even for an empty rectangle. Some renderers do not clip
        // overflowing children when the parent's measured height is zero.
        setClipBounds(new Rect(0, 0, width, height));
        if (getChildCount() > 0) getChildAt(0).setVisibility(height == 0 ? INVISIBLE : VISIBLE);
        setMeasuredDimension(width, height);
    }

    private void animateToTarget() {
        cancelMotion();
        if (revealed == target) return;
        animator = ValueAnimator.ofInt(revealed, target);
        animator.setDuration(240);
        animator.setInterpolator(UiKit.motionEasing());
        animator.addUpdateListener(a -> {
            revealed = (int) a.getAnimatedValue();
            requestLayout();
        });
        animator.start();
    }

    @Override protected void onDetachedFromWindow() {
        cancelMotion();
        super.onDetachedFromWindow();
    }
}
