package com.tiletv.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.widget.TextView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.gson.Gson;
import com.tiletv.app.adapter.CategoryAdapter;
import com.tiletv.app.adapter.TileAdapter;
import com.tiletv.app.model.TileCategory;
import com.tiletv.app.model.TileConfig;
import com.tiletv.app.model.TileItem;
import com.tiletv.app.util.AssetUtil;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 主界面 - Apple TV 风格
 * 顶部标题栏 + 时钟 + 分类行垂直滚动列表
 */
public class MainActivity extends Activity implements TileAdapter.OnTileClickListener {

    private RecyclerView rvCategories;
    private TextView tvClock;
    private Handler clockHandler;
    private Runnable clockRunnable;
    private List<TileCategory> categories;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        rvCategories = findViewById(R.id.rv_categories);
        tvClock = findViewById(R.id.tv_clock);

        loadTiles();
        setupCategoryList();
        startClock();
    }

    /**
     * 从 assets/tiles.json 读取并解析磁贴配置
     */
    private void loadTiles() {
        String json = AssetUtil.readAsset(this, "tiles.json");
        Gson gson = new Gson();
        TileConfig config = gson.fromJson(json, TileConfig.class);
        categories = config != null ? config.getCategories() : null;
    }

    /**
     * 设置分类行垂直列表
     */
    private void setupCategoryList() {
        LinearLayoutManager layoutManager = new LinearLayoutManager(
                this, LinearLayoutManager.VERTICAL, false);
        rvCategories.setLayoutManager(layoutManager);

        CategoryAdapter adapter = new CategoryAdapter(this, categories, this);
        rvCategories.setAdapter(adapter);

        // 让第一行第一个卡片获取焦点
        rvCategories.post(new Runnable() {
            @Override
            public void run() {
                // 找到第一个分类行的 ViewHolder
                RecyclerView.ViewHolder categoryHolder = rvCategories.findViewHolderForAdapterPosition(0);
                if (categoryHolder != null) {
                    final RecyclerView innerRv = categoryHolder.itemView.findViewById(R.id.rv_tiles_row);
                    if (innerRv != null) {
                        innerRv.post(new Runnable() {
                            @Override
                            public void run() {
                                RecyclerView.ViewHolder tileHolder = innerRv.findViewHolderForAdapterPosition(0);
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
     * 顶部时钟，每分钟更新
     */
    private void startClock() {
        clockHandler = new Handler();
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
     * 卡片点击回调 - 跳转 WebViewActivity
     */
    @Override
    public void onTileClick(TileItem item) {
        Intent intent = new Intent(this, WebViewActivity.class);
        intent.putExtra("url", item.getUrl());
        intent.putExtra("level", item.getLevel());
        intent.putExtra("name", item.getName());
        startActivity(intent);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (clockHandler != null) {
            clockHandler.removeCallbacks(clockRunnable);
        }
    }
}
