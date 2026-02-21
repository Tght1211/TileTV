package com.tiletv.app.ai;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Base64;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.webkit.WebView;

import java.io.ByteArrayOutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * WebView自动化控制 - 提供截图、点击、输入、滚动等操作。
 * 所有UI操作通过Handler在主线程执行。
 */
public class WebViewAutomation {

    private static final String TAG = "WebViewAutomation";
    private WebView webView;
    private Handler uiHandler = new Handler(Looper.getMainLooper());

    public WebViewAutomation(WebView webView) {
        this.webView = webView;
    }

    public void setWebView(WebView webView) {
        this.webView = webView;
    }

    /**
     * 截取WebView当前页面截图,返回base64 JPEG。
     * 在主线程上执行绘制,然后返回压缩后的base64。
     */
    public String screenshot() {
        if (webView == null) return "";

        final AtomicReference<String> result = new AtomicReference<>("");
        final CountDownLatch latch = new CountDownLatch(1);

        uiHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    int w = webView.getWidth();
                    int h = webView.getHeight();
                    if (w <= 0 || h <= 0) {
                        w = 1280;
                        h = 720;
                    }
                    Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                    Canvas canvas = new Canvas(bitmap);
                    webView.draw(canvas);

                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 60, bos);
                    bitmap.recycle();

                    String base64 = Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP);
                    result.set(base64);
                } catch (Exception e) {
                    Log.e(TAG, "Screenshot failed", e);
                } finally {
                    latch.countDown();
                }
            }
        });

        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Log.e(TAG, "Screenshot interrupted", e);
        }
        return result.get();
    }

    /**
     * 在指定坐标点击。使用MotionEvent模拟真实触摸。
     */
    public void click(final float x, final float y) {
        final CountDownLatch latch = new CountDownLatch(1);
        uiHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    long downTime = SystemClock.uptimeMillis();
                    MotionEvent downEvent = MotionEvent.obtain(
                            downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0);
                    webView.dispatchTouchEvent(downEvent);
                    downEvent.recycle();

                    long upTime = downTime + 100;
                    MotionEvent upEvent = MotionEvent.obtain(
                            downTime, upTime, MotionEvent.ACTION_UP, x, y, 0);
                    webView.dispatchTouchEvent(upEvent);
                    upEvent.recycle();
                } catch (Exception e) {
                    Log.e(TAG, "Click failed at (" + x + "," + y + ")", e);
                } finally {
                    latch.countDown();
                }
            }
        });
        try { latch.await(3, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
    }

    /**
     * 在当前焦点元素中输入文字。使用JS注入。
     */
    public void typeText(final String text) {
        final CountDownLatch latch = new CountDownLatch(1);
        uiHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    String escapedText = text.replace("\\", "\\\\")
                            .replace("'", "\\'")
                            .replace("\n", "\\n")
                            .replace("\r", "");
                    String js = "(function(text){"
                            // 找到目标input：优先activeElement，否则搜索页面可见input
                            + "var el=document.activeElement;"
                            + "if(!el||el===document.body||el===document.documentElement||"
                            + "  (el.tagName!=='INPUT'&&el.tagName!=='TEXTAREA'&&!el.isContentEditable)){"
                            + "  var inputs=document.querySelectorAll('input[type=text],input[type=search],input:not([type]),textarea');"
                            + "  for(var j=0;j<inputs.length;j++){"
                            + "    var r=inputs[j].getBoundingClientRect();"
                            + "    if(r.width>0&&r.height>0&&r.top>=0&&r.top<300){"
                            + "      el=inputs[j];break;"
                            + "    }"
                            + "  }"
                            + "}"
                            + "if(!el||el===document.body)return;"
                            + "el.focus();"
                            // 策略1：execCommand insertText（最兼容React/Vue）
                            + "try{"
                            + "  if(el.select)el.select();"
                            + "  else if(el.setSelectionRange)el.setSelectionRange(0,el.value?el.value.length:0);"
                            + "  var ok=document.execCommand('insertText',false,text);"
                            + "  if(ok&&el.value===text)return;"
                            + "}catch(e){}"
                            // 策略2：nativeInputValueSetter（绕过React拦截）
                            + "try{"
                            + "  var setter=Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype,'value');"
                            + "  if(!setter)setter=Object.getOwnPropertyDescriptor(window.HTMLTextAreaElement.prototype,'value');"
                            + "  if(setter&&setter.set){"
                            + "    setter.set.call(el,text);"
                            + "    el.dispatchEvent(new InputEvent('input',{bubbles:true,cancelable:true,inputType:'insertText',data:text}));"
                            + "    el.dispatchEvent(new Event('change',{bubbles:true}));"
                            + "    if(el.value===text)return;"
                            + "  }"
                            + "}catch(e){}"
                            // 策略3：直接设值+事件
                            + "el.value=text;"
                            + "el.dispatchEvent(new InputEvent('input',{bubbles:true,inputType:'insertText',data:text}));"
                            + "el.dispatchEvent(new Event('change',{bubbles:true}));"
                            + "})('" + escapedText + "');";
                    webView.evaluateJavascript(js, null);
                } catch (Exception e) {
                    Log.e(TAG, "TypeText failed", e);
                } finally {
                    latch.countDown();
                }
            }
        });
        try { latch.await(3, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
    }

    /**
     * 按键。支持 Enter, Escape, Tab, Backspace, ArrowUp/Down/Left/Right。
     * 组合键如 Control+a 先按下修饰键再按主键。
     */
    public void pressKey(final String key) {
        final CountDownLatch latch = new CountDownLatch(1);
        uiHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    if (key.contains("+")) {
                        // 组合键: 通过JS处理
                        handleComboKey(key);
                    } else {
                        int keyCode = mapKeyCode(key);
                        if (keyCode != -1) {
                            // 发送 Android native KeyEvent
                            webView.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, keyCode));
                            webView.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, keyCode));
                        }
                        // 始终同时发送 JS KeyboardEvent，确保 React/Vue 等框架能收到
                        dispatchJsKeyEvent(key);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "PressKey failed: " + key, e);
                } finally {
                    latch.countDown();
                }
            }
        });
        try { latch.await(3, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
    }

    private void handleComboKey(String combo) {
        String[] parts = combo.split("\\+");
        if (parts.length == 2 && "Control".equalsIgnoreCase(parts[0].trim())) {
            String key = parts[1].trim().toLowerCase();
            if ("a".equals(key)) {
                // Select all via JS
                String js = "(function(){"
                        + "var el=document.activeElement;"
                        + "if(el&&el.select){el.select();}"
                        + "else{document.execCommand('selectAll');}"
                        + "})();";
                webView.evaluateJavascript(js, null);
            } else if ("c".equals(key)) {
                webView.evaluateJavascript("document.execCommand('copy');", null);
            } else if ("v".equals(key)) {
                webView.evaluateJavascript("document.execCommand('paste');", null);
            }
        }
    }

    private void dispatchJsKeyEvent(String key) {
        int keyCode = getJsKeyCode(key);
        String jsKey = getJsKeyName(key);
        String js = "(function(){"
                + "var el=document.activeElement||document.body;"
                + "['keydown','keypress','keyup'].forEach(function(type){"
                + "  el.dispatchEvent(new KeyboardEvent(type,{"
                + "    key:'" + jsKey + "',keyCode:" + keyCode + ",which:" + keyCode
                + ",bubbles:true,cancelable:true}));"
                + "});"
                // Enter键特殊处理：尝试提交表单或点击搜索按钮
                + "if('" + jsKey + "'==='Enter'){"
                + "  if(el.form){try{el.form.submit();}catch(e){}}"
                + "  else{"
                + "    var btn=document.querySelector('[type=submit],button.search-btn,.nav-search-btn,.search-button');"
                + "    if(btn)try{btn.click();}catch(e){}"
                + "  }"
                + "}"
                + "})();";
        webView.evaluateJavascript(js, null);
    }

    private int mapKeyCode(String key) {
        switch (key.toLowerCase()) {
            case "enter": case "return": return KeyEvent.KEYCODE_ENTER;
            case "escape": return KeyEvent.KEYCODE_ESCAPE;
            case "tab": return KeyEvent.KEYCODE_TAB;
            case "backspace": return KeyEvent.KEYCODE_DEL;
            case "space": return KeyEvent.KEYCODE_SPACE;
            case "arrowup": return KeyEvent.KEYCODE_DPAD_UP;
            case "arrowdown": return KeyEvent.KEYCODE_DPAD_DOWN;
            case "arrowleft": return KeyEvent.KEYCODE_DPAD_LEFT;
            case "arrowright": return KeyEvent.KEYCODE_DPAD_RIGHT;
            default: return -1;
        }
    }

    private int getJsKeyCode(String key) {
        switch (key.toLowerCase()) {
            case "enter": case "return": return 13;
            case "escape": return 27;
            case "tab": return 9;
            case "backspace": return 8;
            case "space": return 32;
            case "arrowup": return 38;
            case "arrowdown": return 40;
            case "arrowleft": return 37;
            case "arrowright": return 39;
            default: return 0;
        }
    }

    private String getJsKeyName(String key) {
        switch (key.toLowerCase()) {
            case "enter": case "return": return "Enter";
            case "escape": return "Escape";
            case "tab": return "Tab";
            case "backspace": return "Backspace";
            case "space": return " ";
            case "arrowup": return "ArrowUp";
            case "arrowdown": return "ArrowDown";
            case "arrowleft": return "ArrowLeft";
            case "arrowright": return "ArrowRight";
            default: return key;
        }
    }

    /**
     * 滚动页面
     */
    public void scroll(final String direction, final int amount) {
        uiHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    int dy = "up".equals(direction) ? -amount : amount;
                    webView.evaluateJavascript("window.scrollBy(0," + dy + ");", null);
                } catch (Exception e) {
                    Log.e(TAG, "Scroll failed", e);
                }
            }
        });
    }

    /**
     * 导航到URL
     */
    public void navigate(final String url) {
        final CountDownLatch latch = new CountDownLatch(1);
        uiHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    webView.loadUrl(url);
                } catch (Exception e) {
                    Log.e(TAG, "Navigate failed: " + url, e);
                } finally {
                    latch.countDown();
                }
            }
        });
        try { latch.await(5, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
    }

    /**
     * 后退
     */
    public boolean goBack() {
        final AtomicReference<Boolean> result = new AtomicReference<>(false);
        final CountDownLatch latch = new CountDownLatch(1);
        uiHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    if (webView.canGoBack()) {
                        webView.goBack();
                        result.set(true);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "GoBack failed", e);
                } finally {
                    latch.countDown();
                }
            }
        });
        try { latch.await(3, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
        return result.get();
    }

    /**
     * 获取当前URL
     */
    public String getCurrentUrl() {
        final AtomicReference<String> result = new AtomicReference<>("about:blank");
        final CountDownLatch latch = new CountDownLatch(1);
        uiHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    result.set(webView.getUrl() != null ? webView.getUrl() : "about:blank");
                } catch (Exception e) {
                    Log.e(TAG, "getCurrentUrl failed", e);
                } finally {
                    latch.countDown();
                }
            }
        });
        try { latch.await(2, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
        return result.get();
    }

    /**
     * 获取当前页面标题
     */
    public String getCurrentTitle() {
        final AtomicReference<String> result = new AtomicReference<>("");
        final CountDownLatch latch = new CountDownLatch(1);
        uiHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    result.set(webView.getTitle() != null ? webView.getTitle() : "");
                } catch (Exception e) {
                    Log.e(TAG, "getCurrentTitle failed", e);
                } finally {
                    latch.countDown();
                }
            }
        });
        try { latch.await(2, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
        return result.get();
    }

    /**
     * 等待指定秒数
     */
    public void waitSeconds(int seconds) {
        try {
            Thread.sleep(Math.min(seconds, 10) * 1000L);
        } catch (InterruptedException ignored) {}
    }
}
