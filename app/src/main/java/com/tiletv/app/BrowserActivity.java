package com.tiletv.app;

import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Build;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.webkit.WebSettingsCompat;
import androidx.webkit.WebViewFeature;

import com.tiletv.app.ai.MemoryStore;
import com.tiletv.app.ai.NavigationAgent;
import com.tiletv.app.ai.WebViewAutomation;
import com.tiletv.app.server.TileTVServer;
import com.tiletv.app.widget.VirtualCursorView;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Collections;
import java.util.HashSet;

/**
 * Browser Activity v3 - WebView + AI Agent + 内嵌服务器通信。
 * TV端直接显示WebView，AI通过WebViewAutomation控制。
 * H5端通过TileTVServer的WebSocket发送指令。
 */
public class BrowserActivity extends AppCompatActivity {

    private static final String TAG = "BrowserActivity";
    private static final String PREFS_NAME = "tiletv_prefs";

    private WebView webView;
    private VirtualCursorView cursorView;
    private View topBar;
    private View aiOverlay;
    private TextView tvTitle;
    private TextView tvAiStatus;
    private TextView tvAiStep;
    private TextView tvAiIcon;
    private TextView tvAiLog;
    private TextView tvH5Hint;

    private WebViewAutomation automation;
    private NavigationAgent agent;
    private MemoryStore memoryStore;
    private TileTVServer server;

    // Video fullscreen support
    private View customVideoView;
    private WebChromeClient.CustomViewCallback customViewCallback;
    private FrameLayout fullscreenContainer;

    private boolean cursorMode = false;
    private boolean showAiOnTV = true; // AI操作过程在TV上的展示开关
    private long lastBackTime = 0;
    private static final long DOUBLE_BACK_THRESHOLD = 550;

    private Handler uiHandler = new Handler(Looper.getMainLooper());
    private Runnable hideTopBarRunnable;
    private Runnable hideOverlayRunnable;
    private int stepCount = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_browser);
        hideSystemUI();

        initViews();
        setupWebView();
        setupAI();
        setupServer();

        String url = getIntent().getStringExtra("url");
        String name = getIntent().getStringExtra("name");
        if (name != null) tvTitle.setText(name);
        if (url != null && !url.isEmpty()) {
            webView.loadUrl(url);
        }

        hideTopBarRunnable = new Runnable() {
            @Override
            public void run() {
                if (topBar != null) topBar.animate().alpha(0f).setDuration(300).start();
            }
        };
        hideOverlayRunnable = new Runnable() {
            @Override
            public void run() {
                if (aiOverlay != null) aiOverlay.animate().alpha(0f).setDuration(500).withEndAction(new Runnable() {
                    @Override
                    public void run() { aiOverlay.setVisibility(View.GONE); }
                }).start();
            }
        };

        showTopBar();
    }

    @SuppressWarnings("deprecation")
    private void hideSystemUI() {
        View decorView = getWindow().getDecorView();
        if (Build.VERSION.SDK_INT >= 19) {
            decorView.setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
    }

    private void initViews() {
        webView = findViewById(R.id.webview);
        cursorView = findViewById(R.id.virtual_cursor);
        topBar = findViewById(R.id.top_bar);
        aiOverlay = findViewById(R.id.ai_overlay);
        tvTitle = findViewById(R.id.tv_title);
        tvAiStatus = findViewById(R.id.tv_ai_status);
        tvAiStep = findViewById(R.id.tv_ai_step);
        tvAiIcon = findViewById(R.id.tv_ai_icon);
        tvAiLog = findViewById(R.id.tv_ai_log);
        tvH5Hint = findViewById(R.id.tv_h5_hint);
    }

    @SuppressWarnings("deprecation")
    private void setupWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setMediaPlaybackRequiresUserGesture(false);

        // 允许混合内容（HTTPS页面加载HTTP资源）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }

        // 允许文件访问
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);

        // 数据库存储
        settings.setDatabaseEnabled(true);

        // PC Chrome UA — 最新版本，不包含任何 WebView 标识
        settings.setUserAgentString(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                        + "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36");

        // 移除 X-Requested-With 请求头（WebView默认发送包名，暴露WebView身份）
        removeXRequestedWithHeader(settings);

        // 不设置硬件层类型（LAYER_TYPE_NONE），让系统通过窗口级硬件加速渲染
        // 注意：LAYER_TYPE_HARDWARE 会导致 HTML5 video 黑屏（SurfaceTexture无法合成到离屏纹理）
        webView.setLayerType(View.LAYER_TYPE_NONE, null);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                // 尽早注入PC伪装脚本，在页面JS执行之前
                injectPCBrowserEmulation(view);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                // 页面加载���成后再次注入（确保覆盖动态检测）
                injectPCBrowserEmulation(view);
                tvTitle.setText(view.getTitle() != null ? view.getTitle() : url);
                // Notify H5 clients about page change
                if (server != null) {
                    server.broadcastPageInfo(url, view.getTitle() != null ? view.getTitle() : "");
                }
            }

            @SuppressWarnings("deprecation")
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                // 拦截跳转到App下载页，留在当前浏览器
                if (url != null && (url.contains("app.bilibili.com")
                        || url.contains("play.google.com/store")
                        || url.contains("itunes.apple.com"))) {
                    return true; // 阻止跳转
                }
                return false;
            }
        });

        // 增强WebChromeClient — 支持视频全屏和权限请求
        fullscreenContainer = findViewById(android.R.id.content);
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                // 视频全屏
                if (customVideoView != null) {
                    callback.onCustomViewHidden();
                    return;
                }
                customVideoView = view;
                customViewCallback = callback;
                webView.setVisibility(View.GONE);
                fullscreenContainer.addView(customVideoView,
                        new FrameLayout.LayoutParams(
                                FrameLayout.LayoutParams.MATCH_PARENT,
                                FrameLayout.LayoutParams.MATCH_PARENT));
                hideSystemUI();
            }

            @Override
            public void onHideCustomView() {
                if (customVideoView == null) return;
                fullscreenContainer.removeView(customVideoView);
                customVideoView = null;
                webView.setVisibility(View.VISIBLE);
                if (customViewCallback != null) {
                    customViewCallback.onCustomViewHidden();
                    customViewCallback = null;
                }
                hideSystemUI();
            }

            @Override
            public void onPermissionRequest(PermissionRequest request) {
                // 自动授权媒体播放权限（DRM等）
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    request.grant(request.getResources());
                }
            }
        });
    }

    /**
     * 注入JS脚本深度伪装成PC Chrome浏览器，绕过所有移动端/WebView检测。
     */
    private void injectPCBrowserEmulation(WebView view) {
        String js = "(function(){"

            // === Navigator 属性覆盖 ===
            + "try{Object.defineProperty(navigator,'platform',{get:function(){return 'Win32';},configurable:true});}catch(e){}"
            + "try{Object.defineProperty(navigator,'maxTouchPoints',{get:function(){return 0;},configurable:true});}catch(e){}"
            + "try{Object.defineProperty(navigator,'vendor',{get:function(){return 'Google Inc.';},configurable:true});}catch(e){}"
            + "try{Object.defineProperty(navigator,'webdriver',{get:function(){return false;},configurable:true});}catch(e){}"
            + "try{Object.defineProperty(navigator,'languages',{get:function(){return['zh-CN','zh','en'];},configurable:true});}catch(e){}"
            + "try{Object.defineProperty(navigator,'userAgent',{get:function(){return 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36';},configurable:true});}catch(e){}"
            + "try{Object.defineProperty(navigator,'appVersion',{get:function(){return '5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36';},configurable:true});}catch(e){}"

            // === window.chrome 对象（真Chrome有，WebView没有）===
            + "if(!window.chrome){window.chrome={runtime:{id:undefined,connect:function(){},sendMessage:function(){},onMessage:{addListener:function(){}}},app:{isInstalled:false},csi:function(){return{};},loadTimes:function(){return{};}};};"

            // === navigator.plugins ===
            + "try{Object.defineProperty(navigator,'plugins',{get:function(){"
            + "var p=[{name:'Chrome PDF Plugin',filename:'internal-pdf-viewer',description:'Portable Document Format',length:1},"
            + "{name:'Chrome PDF Viewer',filename:'mhjfbmdgcfjbbpaeojofohoefgiehjai',description:'',length:1},"
            + "{name:'Native Client',filename:'internal-nacl-plugin',description:'',length:2}];"
            + "p.namedItem=function(n){for(var i=0;i<this.length;i++){if(this[i].name===n)return this[i];}return null;};"
            + "p.refresh=function(){};"
            + "return p;},configurable:true});}catch(e){}"

            // === navigator.mimeTypes ===
            + "try{Object.defineProperty(navigator,'mimeTypes',{get:function(){"
            + "var m=[{type:'application/pdf',suffixes:'pdf',description:'Portable Document Format'}];"
            + "m.namedItem=function(n){for(var i=0;i<this.length;i++){if(this[i].type===n)return this[i];}return null;};"
            + "return m;},configurable:true});}catch(e){}"

            // === Screen 尺寸 ===
            + "try{"
            + "Object.defineProperty(screen,'width',{get:function(){return 1920;},configurable:true});"
            + "Object.defineProperty(screen,'height',{get:function(){return 1080;},configurable:true});"
            + "Object.defineProperty(screen,'availWidth',{get:function(){return 1920;},configurable:true});"
            + "Object.defineProperty(screen,'availHeight',{get:function(){return 1040;},configurable:true});"
            + "Object.defineProperty(screen,'colorDepth',{get:function(){return 24;},configurable:true});"
            + "Object.defineProperty(screen,'pixelDepth',{get:function(){return 24;},configurable:true});"
            + "}catch(e){}"

            // === Window 尺寸（匹配桌面视口）===
            + "try{"
            + "Object.defineProperty(window,'innerWidth',{get:function(){return 1920;},configurable:true});"
            + "Object.defineProperty(window,'innerHeight',{get:function(){return 969;},configurable:true});"
            + "Object.defineProperty(window,'outerWidth',{get:function(){return 1920;},configurable:true});"
            + "Object.defineProperty(window,'outerHeight',{get:function(){return 1040;},configurable:true});"
            + "Object.defineProperty(document.documentElement,'clientWidth',{get:function(){return 1920;},configurable:true});"
            + "Object.defineProperty(document.documentElement,'clientHeight',{get:function(){return 969;},configurable:true});"
            + "}catch(e){}"

            // === 触摸事件检测 ===
            + "try{Object.defineProperty(window,'ontouchstart',{get:function(){return undefined;},set:function(){},configurable:true});}catch(e){}"
            + "try{Object.defineProperty(window,'ontouchend',{get:function(){return undefined;},set:function(){},configurable:true});}catch(e){}"
            + "try{Object.defineProperty(window,'ontouchmove',{get:function(){return undefined;},set:function(){},configurable:true});}catch(e){}"

            // === 移除 window.orientation（移动端特有）===
            + "try{Object.defineProperty(window,'orientation',{get:function(){return undefined;},configurable:true});}catch(e){}"

            // === 清除 WebView 标识 ===
            + "try{delete window.__IS_WEBVIEW__;delete window.__IS_ANDROID__;delete window.__wbRenderOpt;delete window.opera;}catch(e){}"

            // === navigator.connection（移除移动端网络信息）===
            + "try{Object.defineProperty(navigator,'connection',{get:function(){return undefined;},configurable:true});}catch(e){}"

            // === matchMedia 拦截（桌面指针/hover检测）===
            + "try{"
            + "var origMatch=window.matchMedia.bind(window);"
            + "window.matchMedia=function(q){"
            + "  if(q.indexOf('pointer')!==-1&&q.indexOf('coarse')!==-1)q=q.replace('coarse','fine');"
            + "  if(q.indexOf('hover')!==-1&&q.indexOf('none')!==-1)q=q.replace('hover: none','hover: hover').replace('hover:none','hover:hover');"
            + "  return origMatch(q);"
            + "};"
            + "}catch(e){}"

            // === devicePixelRatio（桌面通常为1）===
            + "try{Object.defineProperty(window,'devicePixelRatio',{get:function(){return 1;},configurable:true});}catch(e){}"

            // === Permissions API（桌面Chrome特征）===
            + "try{if(navigator.permissions&&navigator.permissions.query){"
            + "var origQuery=navigator.permissions.query.bind(navigator.permissions);"
            + "navigator.permissions.query=function(desc){"
            + "  if(desc.name==='notifications')return Promise.resolve({state:'prompt'});"
            + "  return origQuery(desc);"
            + "};"
            + "}}catch(e){}"

            + "})();";
        view.evaluateJavascript(js, null);
    }

    private void setupAI() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String apiKey = prefs.getString("api_key", "");
        String baseUrl = prefs.getString("api_base_url", "https://api.anthropic.com");
        String model = prefs.getString("api_model", "claude-haiku-4-5-20251001");

        memoryStore = new MemoryStore(this);
        automation = new WebViewAutomation(webView);
        agent = new NavigationAgent(apiKey, baseUrl, model, automation, memoryStore);
    }

    private void setupServer() {
        // Get the server instance from the application/singleton
        server = TileTVApp.getServer();
        if (server != null) {
            if (tvH5Hint != null) {
                String h5Url = server.getH5Url();
                String ip = server.getLocalIpAddress();
                if ("127.0.0.1".equals(ip) || ip.startsWith("10.0.2.") || ip.startsWith("10.0.3.") || ip.startsWith("192.168.232.")) {
                    // 模拟器环境，提示使用 adb forward
                    tvH5Hint.setText("H5: 用Mac局域网IP:9870 (需adb forward)");
                } else {
                    tvH5Hint.setText("H5: " + h5Url);
                }
            }
            server.setMessageListener(new TileTVServer.MessageListener() {
                @Override
                public void onClientMessage(String json) {
                    handleClientMessage(json);
                }

                @Override
                public void onClientConnected() {
                    uiHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            showAiOverlay("H5 客户端已连接", "done");
                        }
                    });
                }

                @Override
                public void onClientDisconnected() {}
            });
        }
    }

    private void handleClientMessage(final String json) {
        try {
            JSONObject msg = new JSONObject(json);
            String type = msg.optString("type", "");

            switch (type) {
                case "voice":
                    final String text = msg.optString("text", "");
                    if (!text.isEmpty()) {
                        uiHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                executeVoiceCommand(text);
                            }
                        });
                    }
                    break;

                case "interrupt":
                    if (agent != null) {
                        agent.interrupt();
                    }
                    break;

                case "toggle_overlay":
                    uiHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            showAiOnTV = !showAiOnTV;
                            if (!showAiOnTV) {
                                aiOverlay.setVisibility(View.GONE);
                            }
                            Toast.makeText(BrowserActivity.this,
                                "AI展示: " + (showAiOnTV ? "开" : "关"), Toast.LENGTH_SHORT).show();
                            if (server != null) server.broadcastToast("TV AI展示: " + (showAiOnTV ? "开" : "关"));
                        }
                    });
                    break;

                case "ping":
                    // Send current page info + screenshot
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            if (server != null && automation != null) {
                                String frame = automation.screenshot();
                                String url = automation.getCurrentUrl();
                                String title = automation.getCurrentTitle();
                                try {
                                    JSONObject pong = new JSONObject();
                                    pong.put("type", "pong");
                                    pong.put("url", url);
                                    pong.put("title", title);
                                    pong.put("frame", frame);
                                    server.broadcastJson(pong.toString());
                                } catch (JSONException e) {
                                    Log.e(TAG, "ping response error", e);
                                }
                            }
                        }
                    }).start();
                    break;
            }
        } catch (JSONException e) {
            Log.e(TAG, "handleClientMessage error", e);
        }
    }

    private void executeVoiceCommand(String text) {
        stepCount = 0;
        tvAiLog.setText("");
        tvAiLog.setVisibility(View.GONE);

        agent.handleVoiceCommand(text, new NavigationAgent.Callback() {
            @Override
            public void onStatus(final String text, final String level) {
                uiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        showAiOverlay(text, level);
                        if ("thinking".equals(level)) {
                            stepCount++;
                            tvAiStep.setText("第" + stepCount + "步");
                            appendLog(text);
                        }
                    }
                });
                // Also broadcast to H5
                if (server != null) server.broadcastStatus(text, level);
            }

            @Override
            public void onFrame(final String base64) {
                // Broadcast screenshot to H5
                if (server != null) server.broadcastFrame(base64);
            }

            @Override
            public void onComplete(final String summary) {
                uiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        showAiOverlay(summary, "done");
                        // Auto-hide after 4 seconds
                        uiHandler.removeCallbacks(hideOverlayRunnable);
                        uiHandler.postDelayed(hideOverlayRunnable, 4000);
                    }
                });
                if (server != null) {
                    server.broadcastStatus(summary, "done");
                    // Send final page info
                    String url = automation.getCurrentUrl();
                    String title = automation.getCurrentTitle();
                    server.broadcastPageInfo(url, title);
                }
            }

            @Override
            public void onError(final String error) {
                uiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        showAiOverlay(error, "error");
                    }
                });
                if (server != null) server.broadcastStatus(error, "error");
            }
        });
    }

    // ====== AI Overlay ======

    private void showAiOverlay(String text, String level) {
        // 如果TV端展示关闭，只有 error 级别才强制显示
        if (!showAiOnTV && !"error".equals(level)) {
            return;
        }
        uiHandler.removeCallbacks(hideOverlayRunnable);
        aiOverlay.setVisibility(View.VISIBLE);
        aiOverlay.setAlpha(1f);
        tvAiStatus.setText(text);

        if ("thinking".equals(level)) {
            tvAiIcon.setTextColor(0xFF0A84FF);
            tvAiStatus.setTextColor(0xFFFFFFFF);
        } else if ("done".equals(level)) {
            tvAiIcon.setTextColor(0xFF30D158);
            tvAiStatus.setTextColor(0xFF30D158);
        } else if ("error".equals(level)) {
            tvAiIcon.setTextColor(0xFFFF453A);
            tvAiStatus.setTextColor(0xFFFF453A);
        } else {
            tvAiIcon.setTextColor(0xFF98989D);
            tvAiStatus.setTextColor(0xFFFFFFFF);
        }
    }

    private void appendLog(String text) {
        tvAiLog.setText(text);
        tvAiLog.setVisibility(View.VISIBLE);
    }

    // ====== Top Bar ======

    private void showTopBar() {
        if (topBar == null) return;
        topBar.setVisibility(View.VISIBLE);
        topBar.setAlpha(1f);
        uiHandler.removeCallbacks(hideTopBarRunnable);
        uiHandler.postDelayed(hideTopBarRunnable, 4000);
    }

    // ====== Key Handling ======

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // Double-tap BACK to toggle cursor mode
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            long now = System.currentTimeMillis();
            if (now - lastBackTime < DOUBLE_BACK_THRESHOLD) {
                cursorMode = !cursorMode;
                cursorView.setCursorEnabled(cursorMode);
                cursorView.setVisibility(cursorMode ? View.VISIBLE : View.GONE);
                Toast.makeText(this, cursorMode ? "光标模式" : "普通模式", Toast.LENGTH_SHORT).show();
                lastBackTime = 0;
                return true;
            }
            lastBackTime = now;
            if (webView.canGoBack()) {
                webView.goBack();
                return true;
            }
            finish();
            return true;
        }

        // MENU键切换AI展示
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            showAiOnTV = !showAiOnTV;
            if (!showAiOnTV) {
                // 立即隐藏overlay
                aiOverlay.setVisibility(View.GONE);
                Toast.makeText(this, "AI展示: 关", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "AI展示: 开", Toast.LENGTH_SHORT).show();
            }
            // 通知H5客户端
            if (server != null) server.broadcastToast("TV AI展示: " + (showAiOnTV ? "开" : "关"));
            return true;
        }

        if (cursorMode) {
            return cursorView.handleKeyEvent(keyCode, event);
        }

        // D-pad controls scrolling
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
                webView.scrollBy(0, -200);
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                webView.scrollBy(0, 200);
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                webView.scrollBy(-200, 0);
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                webView.scrollBy(200, 0);
                return true;
        }

        showTopBar();
        return super.onKeyDown(keyCode, event);
    }

    // ====== Lifecycle ======

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUI();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        uiHandler.removeCallbacksAndMessages(null);
        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
        }
    }

    /**
     * 移除 X-Requested-With 请求头。
     * WebView默认在每个HTTP请求中发送 X-Requested-With: <package-name>，
     * 这让服务端能轻松识别WebView并限制内容（如bilibili视频黑屏）。
     */
    @SuppressWarnings("deprecation")
    private void removeXRequestedWithHeader(WebSettings settings) {
        // 方案1: androidx.webkit (WebView 102+)
        try {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.REQUESTED_WITH_HEADER_ALLOW_LIST)) {
                WebSettingsCompat.setRequestedWithHeaderOriginAllowList(settings, new HashSet<String>());
                Log.d(TAG, "X-Requested-With header disabled via WebSettingsCompat");
                return;
            }
        } catch (Exception e) {
            Log.d(TAG, "WebSettingsCompat failed: " + e.getMessage());
        }

        // 方案2: 反射调用隐藏API (Chrome 96+)
        try {
            java.lang.reflect.Method m = WebSettings.class.getMethod("setRequestedWithHeaderMode", int.class);
            m.invoke(settings, 2); // NO_HEADER = 2
            Log.d(TAG, "X-Requested-With header disabled via reflection");
            return;
        } catch (Exception e) {
            Log.d(TAG, "Reflection method not available: " + e.getMessage());
        }

        Log.d(TAG, "Cannot remove X-Requested-With header on this WebView version");
    }
}
