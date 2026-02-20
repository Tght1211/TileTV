package com.tiletv.app.widget;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/**
 * Focus overlay view that draws a highlight rectangle over the browser screenshot
 * to indicate the currently focused element as reported by the AI server.
 *
 * The rectangle animates smoothly when the focus position changes.
 */
public class FocusOverlayView extends View {

    private static final int FOCUS_COLOR = Color.parseColor("#FF6B35");
    private static final float STROKE_WIDTH_DP = 3f;
    private static final float CORNER_RADIUS_DP = 4f;
    private static final int ANIM_DURATION = 200;

    private Paint focusPaint;
    private Paint labelPaint;
    private Paint labelBgPaint;
    private RectF currentRect = new RectF();
    private RectF targetRect = new RectF();
    private String label = "";
    private boolean hasFocusRect = false;
    private float density;

    private ValueAnimator animator;

    public FocusOverlayView(Context context) {
        super(context);
        init();
    }

    public FocusOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public FocusOverlayView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        density = getResources().getDisplayMetrics().density;

        focusPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        focusPaint.setColor(FOCUS_COLOR);
        focusPaint.setStyle(Paint.Style.STROKE);
        focusPaint.setStrokeWidth(STROKE_WIDTH_DP * density);

        labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        labelPaint.setColor(Color.WHITE);
        labelPaint.setTextSize(12 * density);

        labelBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        labelBgPaint.setColor(Color.parseColor("#CC000000"));
        labelBgPaint.setStyle(Paint.Style.FILL);

        // This view should not intercept touch/focus events
        setFocusable(false);
        setClickable(false);
    }

    /**
     * Set the focus rectangle position and size.
     * The rectangle animates from the previous position.
     *
     * @param x X position in view coordinates
     * @param y Y position in view coordinates
     * @param w Width in view coordinates
     * @param h Height in view coordinates
     */
    public void setFocusRect(int x, int y, int w, int h) {
        targetRect.set(x, y, x + w, y + h);

        if (!hasFocusRect) {
            // First focus -- no animation
            currentRect.set(targetRect);
            hasFocusRect = true;
            invalidate();
            return;
        }

        // Animate from current to target
        if (animator != null && animator.isRunning()) {
            animator.cancel();
        }

        final float startLeft = currentRect.left;
        final float startTop = currentRect.top;
        final float startRight = currentRect.right;
        final float startBottom = currentRect.bottom;

        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(ANIM_DURATION);
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float fraction = (Float) animation.getAnimatedValue();
                currentRect.left = startLeft + (targetRect.left - startLeft) * fraction;
                currentRect.top = startTop + (targetRect.top - startTop) * fraction;
                currentRect.right = startRight + (targetRect.right - startRight) * fraction;
                currentRect.bottom = startBottom + (targetRect.bottom - startBottom) * fraction;
                invalidate();
            }
        });
        animator.start();
    }

    /**
     * Set the label text displayed near the focus rectangle.
     *
     * @param label The label text, or empty string to hide
     */
    public void setLabel(String label) {
        this.label = label != null ? label : "";
        invalidate();
    }

    /**
     * Clear the focus rectangle.
     */
    public void clearFocus() {
        hasFocusRect = false;
        label = "";
        if (animator != null && animator.isRunning()) {
            animator.cancel();
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!hasFocusRect) return;

        float cornerRadius = CORNER_RADIUS_DP * density;

        // Draw the focus rectangle
        canvas.drawRoundRect(currentRect, cornerRadius, cornerRadius, focusPaint);

        // Draw label if present
        if (label != null && label.length() > 0) {
            float textWidth = labelPaint.measureText(label);
            float padding = 4 * density;
            float labelX = currentRect.left;
            float labelY = currentRect.top - 6 * density;

            if (labelY - labelPaint.getTextSize() < 0) {
                // Not enough space above, draw below
                labelY = currentRect.bottom + labelPaint.getTextSize() + 4 * density;
            }

            // Background
            RectF labelBgRect = new RectF(
                    labelX - padding,
                    labelY - labelPaint.getTextSize(),
                    labelX + textWidth + padding,
                    labelY + padding
            );
            canvas.drawRoundRect(labelBgRect, 2 * density, 2 * density, labelBgPaint);

            // Text
            canvas.drawText(label, labelX, labelY, labelPaint);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (animator != null) {
            animator.cancel();
        }
    }
}
