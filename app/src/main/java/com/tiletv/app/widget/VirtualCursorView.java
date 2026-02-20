package com.tiletv.app.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.WebView;

/**
 * 虚拟光标覆盖层 View，用于 Level 3 降级模式
 *
 * 在 WebView 上方覆盖一个透明 View，绘制十字准星样式的光标。
 * 遥控器方向键移动光标，OK键模拟右键点击，菜单键模拟左键点击。
 * 兼容 API 17，不使用 evaluateJavascript。
 */
public class VirtualCursorView extends View {

    private float cursorX;
    private float cursorY;
    private int stepSize = 20;       // 普通移动步长（像素）
    private int fastStepSize = 50;   // 长按加速步长（像素）
    private boolean cursorEnabled = false;
    private WebView webView;

    private Paint cursorPaint;
    private Paint cursorOutlinePaint;
    private Paint crosshairPaint;

    private long lastKeyTime = 0;
    private static final long FAST_THRESHOLD = 300; // 毫秒，长按加速阈值

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
        // 内圆画笔 - 橙红色填充
        cursorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        cursorPaint.setColor(Color.parseColor("#FF6B35"));
        cursorPaint.setStyle(Paint.Style.FILL);

        // 外圈画笔 - 白色描边
        cursorOutlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        cursorOutlinePaint.setColor(Color.WHITE);
        cursorOutlinePaint.setStyle(Paint.Style.STROKE);
        cursorOutlinePaint.setStrokeWidth(3f);

        // 十字线画��� - 白色描边
        crosshairPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        crosshairPaint.setColor(Color.WHITE);
        crosshairPaint.setStyle(Paint.Style.STROKE);
        crosshairPaint.setStrokeWidth(2f);

        setFocusable(true);
        setFocusableInTouchMode(true);
    }

    /**
     * 关联 WebView，用于在光标位置模拟点击
     */
    public void setWebView(WebView webView) {
        this.webView = webView;
    }

    /**
     * 开启/关闭光标模式
     */
    public void setCursorEnabled(boolean enabled) {
        this.cursorEnabled = enabled;
        if (enabled) {
            // 初始位置：屏幕中央
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

    /**
     * 获取当前光标 X 坐标
     */
    public float getCursorX() {
        return cursorX;
    }

    /**
     * 获取当前光标 Y 坐标
     */
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

        // 外圈
        canvas.drawCircle(cursorX, cursorY, 18f, cursorOutlinePaint);
        // 内圆
        canvas.drawCircle(cursorX, cursorY, 8f, cursorPaint);
        // 十字线（上下左右四段短线）
        float len = 28f;
        canvas.drawLine(cursorX - len, cursorY, cursorX - 12, cursorY, crosshairPaint);
        canvas.drawLine(cursorX + 12, cursorY, cursorX + len, cursorY, crosshairPaint);
        canvas.drawLine(cursorX, cursorY - len, cursorX, cursorY - 12, crosshairPaint);
        canvas.drawLine(cursorX, cursorY + 12, cursorX, cursorY + len, crosshairPaint);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (!cursorEnabled) return super.onKeyDown(keyCode, event);

        // 根据按键间隔判断是否长按加速
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
                // OK键 → 右键点击（触发 contextmenu 事件）
                simulateClick(cursorX, cursorY, true);
                return true;
            case KeyEvent.KEYCODE_MENU:
                // 菜单键 → 左��点击（主确认操作）
                simulateClick(cursorX, cursorY, false);
                return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    /**
     * 在光标位置模拟点击
     * 通过 WebView.loadUrl("javascript:...") 执行 JS 来实现（兼容 API 17）
     *
     * @param x          View 坐标 X
     * @param y          View 坐标 Y
     * @param isRightClick 是否为右键点击
     */
    private void simulateClick(float x, float y, boolean isRightClick) {
        if (webView == null) return;

        // 将 View 坐标转换为 WebView 内的网页坐标
        float scale = webView.getScale();
        float jsX = (x + webView.getScrollX()) / scale;
        float jsY = (y + webView.getScrollY()) / scale;

        String js;
        if (isRightClick) {
            // 右键：触发 contextmenu 事件
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
            // 左键：触发 click
            js = "javascript:void((function(){" +
                 "var el=document.elementFromPoint(" + jsX + "," + jsY + ");" +
                 "if(el){el.click();}" +
                 "})())";
        }
        webView.loadUrl(js);
    }
}
