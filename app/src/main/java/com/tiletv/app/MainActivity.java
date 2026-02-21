package com.tiletv.app;

import android.content.Intent;
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
import com.tiletv.app.server.TileTVServer;

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
    private View btnSettings;

    private Handler clockHandler;
    private Runnable clockRunnable;
    private List<TileCategory> categories;

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
        btnSettings = findViewById(R.id.btn_settings);

        // 设置按钮点击 → 打开设置页
        btnSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
            }
        });

        // 设置按钮焦点效果
        btnSettings.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    v.animate().scaleX(1.08f).scaleY(1.08f).setDuration(150).start();
                    ((TextView) v).setTextColor(0xFFFFFFFF);
                } else {
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start();
                    ((TextView) v).setTextColor(0xFF0A84FF);
                }
            }
        });
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

    private void connectServer() {
        TileTVServer server = TileTVApp.getServer();
        if (server != null) {
            statusDot.setBackgroundResource(R.drawable.dot_green);
            tvServerStatus.setText("AI 就绪");
            if (tvServerInfo != null) {
                tvServerInfo.setText("H5遥控: " + server.getH5Url());
            }
        } else {
            statusDot.setBackgroundResource(R.drawable.dot_red);
            tvServerStatus.setText("服务器错误");
        }
    }

    @Override
    public void onTileClick(TileItem item) {
        Intent intent = new Intent(this, BrowserActivity.class);
        intent.putExtra("url", item.getUrl());
        intent.putExtra("name", item.getName());
        startActivity(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUI();
        TileTVServer server = TileTVApp.getServer();
        if (server != null) {
            statusDot.setBackgroundResource(R.drawable.dot_green);
            tvServerStatus.setText("AI 就绪");
            if (tvServerInfo != null) {
                tvServerInfo.setText("H5遥控: " + server.getH5Url());
            }
        }
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
