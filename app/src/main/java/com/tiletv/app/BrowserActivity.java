package com.tiletv.app;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.tiletv.app.widget.FocusOverlayView;
import com.tiletv.app.widget.VirtualCursorView;
import com.tiletv.app.ws.WebSocketManager;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Browser Activity - displays web content in two modes.
 *
 * Server mode (default when connected):
 * - Full screen ImageView displays JPEG screenshots pushed from the backend via WebSocket
 * - D-pad keys send navigation commands to the server; AI decides how to operate
 * - FocusOverlayView shows the focus rectangle overlay on top of the screenshot
 * - Top bar shows site name + AI status text, auto-hides after 3 seconds
 * - Double-tap BACK to toggle between AI navigation mode and cursor mode
 *
 * Local mode (offline fallback when server is not connected):
 * - WebView loads the URL directly
 * - VirtualCursorView overlay for cursor-based navigation
 * - Similar to the v1 Level 3 cursor mode
 */
public class BrowserActivity extends AppCompatActivity {

    private ImageView ivBrowser;
    private WebView webView;
    private FocusOverlayView focusOverlay;
    private VirtualCursorView cursorView;
    private View topBar;
    private TextView tvTitle;
    private TextView tvAiStatus;
    private TextView tvModeIndicator;

    private String mode;
    private String siteName;
    private String siteUrl;
    private int siteLevel;

    private boolean cursorMode = false;
    private long lastBackTime = 0;
    private static final long DOUBLE_BACK_THRESHOLD = 550;

    private Handler uiHandler = new Handler(Looper.getMainLooper());
    private Runnable hideTopBarRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_browser);
        hideSystemUI();

        mode = getIntent().getStringExtra("mode");
        siteUrl = getIntent().getStringExtra("url");
        siteName = getIntent().getStringExtra("name");
        siteLevel = getIntent().getIntExtra("level", 2);

        if (mode == null) {
            mode = "local";
        }

        initViews();

        hideTopBarRunnable = new Runnable() {
            @Override
            public void run() {
                if (topBar != null) {
                    topBar.animate().alpha(0f).setDuration(300).start();
                }
            }
        };

        if ("server".equals(mode)) {
            setupServerMode();
        } else {
            setupLocalMode();
        }
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
        } else {
            decorView.setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LOW_PROFILE
                            | View.SYSTEM_UI_FLAG_FULLSCREEN);
        }
    }

    private void initViews() {
        ivBrowser = findViewById(R.id.iv_browser);
        webView = findViewById(R.id.webview);
        focusOverlay = findViewById(R.id.focus_overlay);
        cursorView = findViewById(R.id.virtual_cursor);
        topBar = findViewById(R.id.top_bar);
        tvTitle = findViewById(R.id.tv_title);
        tvAiStatus = findViewById(R.id.tv_ai_status);
        tvModeIndicator = findViewById(R.id.tv_mode_indicator);
    }

    // ========================================================================
    // Server Mode
    // ========================================================================

    private void setupServerMode() {
        ivBrowser.setVisibility(View.VISIBLE);
        webView.setVisibility(View.GONE);
        focusOverlay.setVisibility(View.VISIBLE);
        cursorView.setVisibility(View.GONE);
        cursorView.setServerMode(true);

        if (tvModeIndicator != null) {
            tvModeIndicator.setText("AI Nav");
            tvModeIndicator.setVisibility(View.VISIBLE);
        }

        // Register WebSocket message listener
        WebSocketManager.getInstance().setCallback(new WebSocketManager.Callback() {
            @Override
            public void onConnected() {
                uiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        tvAiStatus.setText("已连接");
                        showTopBar();
                    }
                });
            }

            @Override
            public void onDisconnected() {
                uiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        tvAiStatus.setText("连接断开");
                        showTopBar();
                    }
                });
            }

            @Override
            public void onMessage(final String message) {
                uiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        handleServerMessage(message);
                    }
                });
            }
        });

        tvTitle.setText(siteName);
        showTopBar();
    }

    /**
     * Handle incoming WebSocket messages from the server.
     */
    private void handleServerMessage(String json) {
        try {
            JSONObject msg = new JSONObject(json);
            String type = msg.getString("type");

            if ("frame".equals(type)) {
                // Decode base64 JPEG and display
                String data = msg.getString("data");
                byte[] bytes = Base64.decode(data, Base64.DEFAULT);
                Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                if (bitmap != null) {
                    // Recycle old bitmap to prevent memory leak
                    Bitmap old = (Bitmap) ivBrowser.getTag();
                    ivBrowser.setImageBitmap(bitmap);
                    ivBrowser.setTag(bitmap);
                    if (old != null && !old.isRecycled()) {
                        old.recycle();
                    }
                }
            } else if ("status".equals(type)) {
                String text = msg.optString("text", "");
                String level = msg.optString("level", "info");
                tvAiStatus.setText(text);

                // Color based on status level
                if ("error".equals(level)) {
                    tvAiStatus.setTextColor(0xFFFF453A); // Red
                } else if ("thinking".equals(level)) {
                    tvAiStatus.setTextColor(0xFFFFD60A); // Yellow
                } else if ("done".equals(level)) {
                    tvAiStatus.setTextColor(0xFF30D158); // Green
                } else {
                    tvAiStatus.setTextColor(0xFF8E8E93); // Gray
                }
                showTopBar();
            } else if ("focus".equals(type)) {
                if (msg.isNull("rect")) {
                    focusOverlay.clearFocus();
                } else {
                    JSONObject rect = msg.getJSONObject("rect");
                    // Scale from server viewport coordinates to local view coordinates
                    int viewWidth = ivBrowser.getWidth();
                    int viewHeight = ivBrowser.getHeight();
                    int serverWidth = msg.optInt("width", 1280);
                    int serverHeight = msg.optInt("height", 720);

                    float scaleX = viewWidth > 0 ? (float) viewWidth / serverWidth : 1f;
                    float scaleY = viewHeight > 0 ? (float) viewHeight / serverHeight : 1f;

                    focusOverlay.setFocusRect(
                            (int) (rect.getInt("x") * scaleX),
                            (int) (rect.getInt("y") * scaleY),
                            (int) (rect.getInt("w") * scaleX),
                            (int) (rect.getInt("h") * scaleY)
                    );

                    String label = msg.optString("label", "");
                    if (label.length() > 0) {
                        focusOverlay.setLabel(label);
                    }
                }
            } else if ("toast".equals(type)) {
                String text = msg.getString("text");
                Toast.makeText(BrowserActivity.this, text, Toast.LENGTH_SHORT).show();
                // If server says "this is the last page", finish the activity
                if ("\u5DF2\u662F\u6700\u540E\u4E00\u9875".equals(text)) {
                    finish();
                }
            } else if ("pong".equals(type)) {
                String title = msg.optString("title", "");
                if (title.length() > 0) {
                    tvTitle.setText(title);
                }
            } else if ("memory".equals(type)) {
                String summary = msg.optString("summary", "");
                if (summary.length() > 0) {
                    tvAiStatus.setText(summary);
                    showTopBar();
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    // ========================================================================
    // Local Mode (offline fallback)
    // ========================================================================

    @SuppressWarnings("deprecation")
    private void setupLocalMode() {
        ivBrowser.setVisibility(View.GONE);
        webView.setVisibility(View.VISIBLE);
        focusOverlay.setVisibility(View.GONE);
        cursorView.setVisibility(View.VISIBLE);
        cursorView.setServerMode(false);
        cursorView.attachToWebView(webView);
        cursorView.setCursorEnabled(true);
        cursorMode = true;

        if (tvModeIndicator != null) {
            tvModeIndicator.setText("离线");
            tvModeIndicator.setVisibility(View.VISIBLE);
        }

        // Configure WebView
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setBuiltInZoomControls(false);
        settings.setUserAgentString(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                        + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");

        webView.setWebViewClient(new WebViewClient());
        webView.loadUrl(siteUrl);

        tvTitle.setText(siteName + " (离线模式)");
        showTopBar();
    }

    // ========================================================================
    // Key Handling
    // ========================================================================

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if ("server".equals(mode)) {
            return handleServerModeKey(keyCode, event);
        } else {
            return handleLocalModeKey(keyCode, event);
        }
    }

    /**
     * Handle key events in server mode.
     * Double-tap BACK toggles cursor mode. D-pad sends navigation commands.
     */
    private boolean handleServerModeKey(int keyCode, KeyEvent event) {
        WebSocketManager ws = WebSocketManager.getInstance();

        // Double-tap BACK to toggle cursor mode
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            long now = System.currentTimeMillis();
            if (now - lastBackTime < DOUBLE_BACK_THRESHOLD) {
                cursorMode = !cursorMode;
                cursorView.setCursorEnabled(cursorMode);
                cursorView.setVisibility(cursorMode ? View.VISIBLE : View.GONE);
                focusOverlay.setVisibility(cursorMode ? View.GONE : View.VISIBLE);
                if (tvModeIndicator != null) {
                    tvModeIndicator.setText(cursorMode ? "Cursor" : "AI Nav");
                }
                Toast.makeText(this,
                        cursorMode ? "光标模式" : "AI导航模式",
                        Toast.LENGTH_SHORT).show();
                lastBackTime = 0;
                return true;
            }
            lastBackTime = now;
            ws.send("{\"type\":\"back\"}");
            return true;
        }

        // In cursor mode, delegate to VirtualCursorView
        if (cursorMode) {
            return cursorView.handleKeyEvent(keyCode, event);
        }

        // AI navigation mode: send D-pad commands to server
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
                ws.send("{\"type\":\"dpad\",\"direction\":\"up\"}");
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                ws.send("{\"type\":\"dpad\",\"direction\":\"down\"}");
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                ws.send("{\"type\":\"dpad\",\"direction\":\"left\"}");
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                ws.send("{\"type\":\"dpad\",\"direction\":\"right\"}");
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                ws.send("{\"type\":\"dpad\",\"direction\":\"center\"}");
                return true;
            case KeyEvent.KEYCODE_HOME:
                ws.send("{\"type\":\"home\"}");
                return true;
        }

        showTopBar();
        return super.onKeyDown(keyCode, event);
    }

    /**
     * Handle key events in local (offline) mode.
     * BACK navigates WebView history; other keys delegate to cursor.
     */
    private boolean handleLocalModeKey(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (webView != null && webView.canGoBack()) {
                webView.goBack();
                return true;
            }
            finish();
            return true;
        }
        return cursorView.handleKeyEvent(keyCode, event);
    }

    // ========================================================================
    // Top Bar Management
    // ========================================================================

    private void showTopBar() {
        if (topBar == null) return;
        topBar.setVisibility(View.VISIBLE);
        topBar.setAlpha(1f);
        uiHandler.removeCallbacks(hideTopBarRunnable);
        uiHandler.postDelayed(hideTopBarRunnable, 3000);
    }

    // ========================================================================
    // Lifecycle
    // ========================================================================

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
        // Clean up bitmap memory
        if (ivBrowser != null) {
            Bitmap old = (Bitmap) ivBrowser.getTag();
            if (old != null && !old.isRecycled()) {
                old.recycle();
            }
            ivBrowser.setImageBitmap(null);
        }
    }
}
