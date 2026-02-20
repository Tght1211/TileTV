package com.tiletv.app;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.TextView;
import com.tiletv.app.util.AssetUtil;
import com.tiletv.app.widget.VirtualCursorView;

/**
 * WebView 浏览界面，包含三级导航策略
 *
 * Level 1: TV模式 - 直接加载，遥控器事件直接传递给 WebView
 * Level 2: 智能导航 - 加载后注入 spatial_nav.js 实现空间焦点导航
 * Level 3: 光标模式 - 启用 VirtualCursorView 覆盖层模拟鼠标光标
 *
 * 遥控器按键映射:
 * - 方向键 → 上下左右移动/导航
 * - OK键(DPAD_CENTER/ENTER) → 右键点击
 * - 菜单键(MENU) → 左键点击（主确认操作）
 * - 双击返回键 → 在 Level 2 和 Level 3 之间切换导航模式
 */
public class WebViewActivity extends Activity {

    private WebView webView;
    private VirtualCursorView virtualCursor;
    private TextView tvTitle;
    private FrameLayout fullscreenContainer;
    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;

    private String url;
    private int level;
    private String name;
    private String spatialNavJs;

    private Handler handler = new Handler();
    private Runnable hideTitleRunnable;

    private long lastBackPress = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 全屏显示
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        );

        setContentView(R.layout.activity_webview);

        webView = findViewById(R.id.webview);
        virtualCursor = findViewById(R.id.virtual_cursor);
        tvTitle = findViewById(R.id.tv_webview_title);
        fullscreenContainer = findViewById(R.id.fullscreen_container);

        url = getIntent().getStringExtra("url");
        level = getIntent().getIntExtra("level", 2);
        name = getIntent().getStringExtra("name");

        // 预加载空间导航 JS 脚本
        spatialNavJs = AssetUtil.readAsset(this, "js/spatial_nav.js");

        setupTitle();
        setupWebView();
        setupCursor();

        webView.loadUrl(url);
    }

    /**
     * 设置标题栏显示当前页面名称和导航级别
     * 3秒后自动淡出隐藏
     */
    private void setupTitle() {
        String levelLabel;
        switch (level) {
            case 1: levelLabel = "[TV模式]"; break;
            case 2: levelLabel = "[智能导航]"; break;
            case 3: levelLabel = "[光标模式]"; break;
            default: levelLabel = ""; break;
        }
        tvTitle.setText(name + " " + levelLabel);
        tvTitle.setAlpha(1f);

        // 3秒后自动隐藏标题
        hideTitleRunnable = new Runnable() {
            @Override
            public void run() {
                tvTitle.animate().alpha(0f).setDuration(300).start();
            }
        };
        handler.postDelayed(hideTitleRunnable, 3000);
    }

    /**
     * 短暂显示标题栏（任意按键时调用），2秒后再次隐藏
     */
    private void flashTitle() {
        handler.removeCallbacks(hideTitleRunnable);
        tvTitle.setAlpha(1f);
        handler.postDelayed(hideTitleRunnable, 2000);
    }

    /**
     * 配置 WebView：启用 JS、DOM Storage，设置桌面 UA
     * 在页面加载完成后根据 level 注入相应的导航脚本
     */
    @SuppressWarnings("deprecation")
    private void setupWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setBuiltInZoomControls(false);
        // API 17 使用 setMediaPlaybackRequiresUserGesture 需要版本检查
        if (android.os.Build.VERSION.SDK_INT >= 17) {
            settings.setMediaPlaybackRequiresUserGesture(false);
        }

        // 桌面 UA，避免部分网站返回极简移动版
        String ua = settings.getUserAgentString();
        settings.setUserAgentString(ua.replace("Mobile", "").replace("Android", "Linux"));

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                // Level 2: 页面加载完成后注入空间导航 JS
                if (level == 2 && spatialNavJs != null && !spatialNavJs.isEmpty()) {
                    view.loadUrl("javascript:" + spatialNavJs);
                }
            }

            @SuppressWarnings("deprecation")
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                view.loadUrl(url);
                return true;
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                // 全屏视频处理
                customView = view;
                customViewCallback = callback;
                fullscreenContainer.addView(view);
                fullscreenContainer.setVisibility(View.VISIBLE);
                webView.setVisibility(View.GONE);
            }

            @Override
            public void onHideCustomView() {
                if (customView != null) {
                    fullscreenContainer.removeView(customView);
                    customView = null;
                }
                if (customViewCallback != null) {
                    customViewCallback.onCustomViewHidden();
                    customViewCallback = null;
                }
                fullscreenContainer.setVisibility(View.GONE);
                webView.setVisibility(View.VISIBLE);
            }
        });
    }

    /**
     * 设置虚拟光标，Level 3 模式下延迟启用
     */
    private void setupCursor() {
        virtualCursor.setWebView(webView);
        if (level == 3) {
            // 等页面加载完再启用光标
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    virtualCursor.setCursorEnabled(true);
                }
            }, 1500);
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // 任意按键短暂显示标题
        flashTitle();

        // 光标模式下，VirtualCursorView 优先处理按键
        if (level == 3 && virtualCursor.isCursorEnabled()) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP ||
                keyCode == KeyEvent.KEYCODE_DPAD_DOWN ||
                keyCode == KeyEvent.KEYCODE_DPAD_LEFT ||
                keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ||
                keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                keyCode == KeyEvent.KEYCODE_ENTER ||
                keyCode == KeyEvent.KEYCODE_MENU) {
                return virtualCursor.onKeyDown(keyCode, event);
            }
        }

        // 返回键处理
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            // 如果在全屏视频中，退出全屏
            if (customView != null) {
                if (customViewCallback != null) {
                    customViewCallback.onCustomViewHidden();
                }
                return true;
            }

            // 双击 BACK 键：切换导航模式 (Level 2 <-> Level 3)
            long now = System.currentTimeMillis();
            if (now - lastBackPress < 500) {
                toggleNavigationMode();
                lastBackPress = 0; // 重置，防止连续触发
                return true;
            }
            lastBackPress = now;

            // 单击 BACK：WebView 后退或退出
            // 延迟处理，等待判断是否为双击
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    // 如果 lastBackPress 已被重置（双击已触发），则不执行后退
                    if (lastBackPress == 0) return;
                    if (webView.canGoBack()) {
                        webView.goBack();
                    } else {
                        finish();
                    }
                }
            }, 550);
            return true;
        }

        return super.onKeyDown(keyCode, event);
    }

    /**
     * 切换导航模式：Level 2(智能导航) <-> Level 3(光标模式)
     */
    private void toggleNavigationMode() {
        if (level == 2) {
            level = 3;
            virtualCursor.setCursorEnabled(true);
            setupTitle();
        } else if (level == 3) {
            level = 2;
            virtualCursor.setCursorEnabled(false);
            // 重新注入空间导航 JS
            if (spatialNavJs != null && !spatialNavJs.isEmpty()) {
                webView.loadUrl("javascript:" + spatialNavJs);
            }
            setupTitle();
        }
        flashTitle();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
        }
        handler.removeCallbacksAndMessages(null);
    }
}
