package com.tiletv.app;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.gson.Gson;
import com.tiletv.app.adapter.CategoryAdapter;
import com.tiletv.app.adapter.TileAdapter;
import com.tiletv.app.model.TileCategory;
import com.tiletv.app.model.TileConfig;
import com.tiletv.app.model.TileItem;
import com.tiletv.app.util.AssetUtil;
import com.tiletv.app.ws.WebSocketManager;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Main Activity - Apple TV dark frosted glass style home screen.
 *
 * Features:
 * - Top status bar with TileTV title, clock, and server connection status indicator
 * - Vertical RecyclerView with category rows, each containing horizontally scrollable tile cards
 * - Tiles loaded from assets/tiles.json via Gson
 * - Bottom displays server address info from SharedPreferences
 * - Click a card: if server connected, send open command via WebSocket and launch BrowserActivity
 *   in server mode; if not connected, fallback to local WebView mode
 * - MENU key opens SettingsActivity
 * - Auto-connects to WebSocket server on launch
 */
public class MainActivity extends AppCompatActivity implements TileAdapter.OnTileClickListener {

    private RecyclerView rvCategories;
    private TextView tvClock;
    private View statusDot;
    private TextView tvServerStatus;
    private TextView tvServerInfo;

    private Handler clockHandler;
    private Runnable clockRunnable;
    private List<TileCategory> categories;

    private static final String PREFS_NAME = "tiletv_prefs";
    private static final String KEY_SERVER_HOST = "server_host";
    private static final String KEY_SERVER_PORT = "server_port";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        hideSystemUI();
        setupViews();
        loadTiles();
        setupCategoryList();
        startClock();
        connectServer();
        autoFocusFirstTile();
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

    private void setupViews() {
        rvCategories = findViewById(R.id.rv_categories);
        tvClock = findViewById(R.id.tv_clock);
        statusDot = findViewById(R.id.status_dot);
        tvServerStatus = findViewById(R.id.tv_server_status);
        tvServerInfo = findViewById(R.id.tv_server_info);
    }

    /**
     * Load tiles from assets/tiles.json using Gson.
     */
    private void loadTiles() {
        String json = AssetUtil.readAsset(this, "tiles.json");
        Gson gson = new Gson();
        TileConfig config = gson.fromJson(json, TileConfig.class);
        categories = config != null ? config.getCategories() : null;
    }

    /**
     * Setup the vertical RecyclerView with category rows.
     */
    private void setupCategoryList() {
        LinearLayoutManager layoutManager = new LinearLayoutManager(
                this, LinearLayoutManager.VERTICAL, false);
        rvCategories.setLayoutManager(layoutManager);

        CategoryAdapter adapter = new CategoryAdapter(this, categories, this);
        rvCategories.setAdapter(adapter);
    }

    /**
     * Auto-focus the first tile card for D-pad navigation.
     */
    private void autoFocusFirstTile() {
        rvCategories.post(new Runnable() {
            @Override
            public void run() {
                RecyclerView.ViewHolder categoryHolder =
                        rvCategories.findViewHolderForAdapterPosition(0);
                if (categoryHolder != null) {
                    final RecyclerView innerRv =
                            categoryHolder.itemView.findViewById(R.id.rv_tiles_row);
                    if (innerRv != null) {
                        innerRv.post(new Runnable() {
                            @Override
                            public void run() {
                                RecyclerView.ViewHolder tileHolder =
                                        innerRv.findViewHolderForAdapterPosition(0);
                                if (tileHolder != null) {
                                    tileHolder.itemView.requestFocus();
                                }
                            }
                        });
                    }
                }
            }
        });
    }

    /**
     * Update clock display every minute.
     */
    private void startClock() {
        clockHandler = new Handler(Looper.getMainLooper());
        clockRunnable = new Runnable() {
            @Override
            public void run() {
                SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
                tvClock.setText(sdf.format(new Date()));
                clockHandler.postDelayed(this, 60000);
            }
        };
        clockRunnable.run();
    }

    /**
     * Connect to the WebSocket server using settings from SharedPreferences.
     */
    private void connectServer() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String host = prefs.getString(KEY_SERVER_HOST, "");
        int port = prefs.getInt(KEY_SERVER_PORT, 9870);

        if (host.isEmpty()) {
            tvServerStatus.setText("未配置服务器");
            statusDot.setBackgroundResource(R.drawable.dot_gray);
            if (tvServerInfo != null) {
                tvServerInfo.setText("按 Menu 键打开设置");
            }
            return;
        }

        final String wsUrl = "ws://" + host + ":" + port;
        if (tvServerInfo != null) {
            tvServerInfo.setText(host + ":" + port);
        }

        // Connect on background thread (WebSocket library does blocking connect)
        new Thread(new Runnable() {
            @Override
            public void run() {
                WebSocketManager.getInstance().connect(wsUrl, new WebSocketManager.Callback() {
                    @Override
                    public void onConnected() {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                statusDot.setBackgroundResource(R.drawable.dot_green);
                                tvServerStatus.setText("已连接");
                                // Refresh adapter to update AI mode labels on tiles
                                if (rvCategories.getAdapter() != null) {
                                    rvCategories.getAdapter().notifyDataSetChanged();
                                }
                            }
                        });
                    }

                    @Override
                    public void onDisconnected() {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                statusDot.setBackgroundResource(R.drawable.dot_red);
                                tvServerStatus.setText("未连接");
                                if (rvCategories.getAdapter() != null) {
                                    rvCategories.getAdapter().notifyDataSetChanged();
                                }
                            }
                        });
                    }

                    @Override
                    public void onMessage(String message) {
                        // Home screen generally does not handle WebSocket messages
                    }
                });
            }
        }).start();
    }

    /**
     * Tile click callback - launch BrowserActivity in server or local mode.
     */
    @Override
    public void onTileClick(TileItem item) {
        Intent intent = new Intent(this, BrowserActivity.class);
        intent.putExtra("url", item.getUrl());
        intent.putExtra("name", item.getName());
        intent.putExtra("level", item.getLevel());

        if (WebSocketManager.getInstance().isConnected()) {
            intent.putExtra("mode", "server");
            // Send open command to backend
            WebSocketManager.getInstance().send("{\"type\":\"open\",\"url\":\""
                    + item.getUrl() + "\",\"name\":\"" + item.getName() + "\"}");
        } else {
            intent.putExtra("mode", "local");
        }

        startActivity(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUI();
        // Refresh connection status display
        if (WebSocketManager.getInstance().isConnected()) {
            statusDot.setBackgroundResource(R.drawable.dot_green);
            tvServerStatus.setText("已连接");
        } else {
            // Might have been disconnected while in another activity
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            String host = prefs.getString(KEY_SERVER_HOST, "");
            if (host.isEmpty()) {
                statusDot.setBackgroundResource(R.drawable.dot_gray);
                tvServerStatus.setText("未配置服务器");
            } else {
                statusDot.setBackgroundResource(R.drawable.dot_red);
                tvServerStatus.setText("未连接");
            }
        }
        // Refresh tile labels
        if (rvCategories.getAdapter() != null) {
            rvCategories.getAdapter().notifyDataSetChanged();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (clockHandler != null) {
            clockHandler.removeCallbacks(clockRunnable);
        }
    }
}
