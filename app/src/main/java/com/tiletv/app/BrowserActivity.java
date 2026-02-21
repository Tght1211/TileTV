package com.tiletv.app;

import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.tiletv.app.ai.MemoryStore;
import com.tiletv.app.ai.NavigationAgent;
import com.tiletv.app.ai.WebViewAutomation;
import com.tiletv.app.server.TileTVServer;
import com.tiletv.app.widget.VirtualCursorView;

import org.json.JSONException;
import org.json.JSONObject;

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
    private ScrollView aiLogScroll;
    private TextView tvH5Hint;

    private WebViewAutomation automation;
    private NavigationAgent agent;
    private MemoryStore memoryStore;
    private TileTVServer server;

    private boolean cursorMode = false;
    private long lastBackTime = 0;
    private static final long DOUBLE_BACK_THRESHOLD = 550;

    private Handler uiHandler = new Handler(Looper.getMainLooper());
    private Runnable hideTopBarRunnable;
    private Runnable hideOverlayRunnable;
    private int stepCount = 0;
    private StringBuilder logBuilder = new StringBuilder();

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
        aiLogScroll = findViewById(R.id.ai_log_scroll);
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
        settings.setUserAgentString(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                        + "(KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                tvTitle.setText(view.getTitle() != null ? view.getTitle() : url);
                // Notify H5 clients about page change
                if (server != null) {
                    server.broadcastPageInfo(url, view.getTitle() != null ? view.getTitle() : "");
                }
            }
        });
        webView.setWebChromeClient(new WebChromeClient());
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
                tvH5Hint.setText("H5: " + server.getH5Url());
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
        logBuilder.setLength(0);

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
        if (logBuilder.length() > 0) logBuilder.append("\n");
        logBuilder.append(text);
        tvAiLog.setText(logBuilder.toString());
        aiLogScroll.setVisibility(View.VISIBLE);
        aiLogScroll.post(new Runnable() {
            @Override
            public void run() {
                aiLogScroll.fullScroll(ScrollView.FOCUS_DOWN);
            }
        });
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
}
