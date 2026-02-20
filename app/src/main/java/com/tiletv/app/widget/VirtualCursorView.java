package com.tiletv.app.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.WebView;

import com.tiletv.app.ws.WebSocketManager;

/**
 * Virtual cursor overlay view for D-pad navigation.
 *
 * Supports two modes:
 * - Server mode: cursor movements and clicks are sent via WebSocket to the backend
 * - Local mode: cursor operates directly on a WebView (legacy fallback)
 *
 * Draws an orange crosshair cursor. Direction keys move the cursor,
 * OK key (DPAD_CENTER) triggers a click, MENU key triggers an alternative click.
 *
 * Compatible with API 17+. Does not use evaluateJavascript().
 */
public class VirtualCursorView extends View {

    private float cursorX;
    private float cursorY;
    private int stepSize = 20;
    private int fastStepSize = 50;
    private boolean cursorEnabled = false;
    private boolean serverMode = false;
    private WebView webView;

    private Paint cursorPaint;
    private Paint cursorOutlinePaint;
    private Paint crosshairPaint;

    private long lastKeyTime = 0;
    private static final long FAST_THRESHOLD = 300;

    public VirtualCursorView(Context context) {
        super(context);
        init();
    }

    public VirtualCursorView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public VirtualCursorView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        // Inner circle paint - orange fill
        cursorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        cursorPaint.setColor(Color.parseColor("#FF6B35"));
        cursorPaint.setStyle(Paint.Style.FILL);

        // Outer ring paint - white stroke
        cursorOutlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        cursorOutlinePaint.setColor(Color.WHITE);
        cursorOutlinePaint.setStyle(Paint.Style.STROKE);
        cursorOutlinePaint.setStrokeWidth(3f);

        // Crosshair lines paint - white stroke
        crosshairPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        crosshairPaint.setColor(Color.WHITE);
        crosshairPaint.setStyle(Paint.Style.STROKE);
        crosshairPaint.setStrokeWidth(2f);

        setFocusable(true);
        setFocusableInTouchMode(true);
    }

    /**
     * Attach to a WebView for local (offline) mode.
     *
     * @param webView The WebView to interact with
     */
    public void attachToWebView(WebView webView) {
        this.webView = webView;
        this.serverMode = false;
    }

    /**
     * Set whether this cursor operates in server mode.
     * In server mode, cursor clicks are sent via WebSocket.
     *
     * @param serverMode true for server mode, false for local WebView mode
     */
    public void setServerMode(boolean serverMode) {
        this.serverMode = serverMode;
    }

    /**
     * Enable or disable the cursor.
     *
     * @param enabled true to show cursor, false to hide
     */
    public void setCursorEnabled(boolean enabled) {
        this.cursorEnabled = enabled;
        if (enabled) {
            cursorX = getWidth() / 2f;
            cursorY = getHeight() / 2f;
            setVisibility(View.VISIBLE);
            requestFocus();
        } else {
            setVisibility(View.GONE);
        }
        invalidate();
    }

    public boolean isCursorEnabled() {
        return cursorEnabled;
    }

    public float getCursorX() {
        return cursorX;
    }

    public float getCursorY() {
        return cursorY;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (cursorX == 0 && cursorY == 0) {
            cursorX = w / 2f;
            cursorY = h / 2f;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!cursorEnabled) return;

        // Outer ring
        canvas.drawCircle(cursorX, cursorY, 18f, cursorOutlinePaint);
        // Inner circle
        canvas.drawCircle(cursorX, cursorY, 8f, cursorPaint);
        // Crosshair lines
        float len = 28f;
        canvas.drawLine(cursorX - len, cursorY, cursorX - 12, cursorY, crosshairPaint);
        canvas.drawLine(cursorX + 12, cursorY, cursorX + len, cursorY, crosshairPaint);
        canvas.drawLine(cursorX, cursorY - len, cursorX, cursorY - 12, crosshairPaint);
        canvas.drawLine(cursorX, cursorY + 12, cursorX, cursorY + len, crosshairPaint);
    }

    /**
     * Handle a key event from the parent Activity.
     *
     * @param keyCode The key code
     * @param event   The key event
     * @return true if the event was consumed
     */
    public boolean handleKeyEvent(int keyCode, KeyEvent event) {
        if (!cursorEnabled) return false;

        long now = System.currentTimeMillis();
        int step = (now - lastKeyTime < FAST_THRESHOLD) ? fastStepSize : stepSize;
        lastKeyTime = now;

        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
                cursorY = Math.max(0, cursorY - step);
                invalidate();
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                cursorY = Math.min(getHeight(), cursorY + step);
                invalidate();
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                cursorX = Math.max(0, cursorX - step);
                invalidate();
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                cursorX = Math.min(getWidth(), cursorX + step);
                invalidate();
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                if (serverMode) {
                    sendCursorClick(cursorX, cursorY);
                } else {
                    simulateClick(cursorX, cursorY, false);
                }
                return true;
            case KeyEvent.KEYCODE_MENU:
                if (serverMode) {
                    sendCursorClick(cursorX, cursorY);
                } else {
                    simulateClick(cursorX, cursorY, true);
                }
                return true;
        }
        return false;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (handleKeyEvent(keyCode, event)) {
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    /**
     * Send cursor click event to the server via WebSocket.
     */
    private void sendCursorClick(float x, float y) {
        WebSocketManager ws = WebSocketManager.getInstance();
        ws.send("{\"type\":\"cursor\",\"action\":\"click\",\"x\":" + (int) x + ",\"y\":" + (int) y + "}");
    }

    /**
     * Simulate a click on the WebView at the given coordinates.
     * Uses loadUrl("javascript:...") for API 17 compatibility.
     *
     * @param x            View coordinate X
     * @param y            View coordinate Y
     * @param isLeftClick  true for left click (from MENU key), false for standard click
     */
    @SuppressWarnings("deprecation")
    private void simulateClick(float x, float y, boolean isLeftClick) {
        if (webView == null) return;

        float scale = webView.getScale();
        float jsX = (x + webView.getScrollX()) / scale;
        float jsY = (y + webView.getScrollY()) / scale;

        String js;
        if (isLeftClick) {
            js = "javascript:void((function(){" +
                    "var el=document.elementFromPoint(" + jsX + "," + jsY + ");" +
                    "if(el){" +
                    "var ev=document.createEvent('MouseEvents');" +
                    "ev.initMouseEvent('contextmenu',true,true,window,1," +
                    jsX + "," + jsY + "," + jsX + "," + jsY +
                    ",false,false,false,false,2,null);" +
                    "el.dispatchEvent(ev);" +
                    "}" +
                    "})())";
        } else {
            js = "javascript:void((function(){" +
                    "var el=document.elementFromPoint(" + jsX + "," + jsY + ");" +
                    "if(el){el.click();}" +
                    "})())";
        }
        webView.loadUrl(js);
    }
}
