package com.tiletv.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.gson.Gson;
import com.tiletv.app.adapter.TileAdapter;
import com.tiletv.app.model.TileCategory;
import com.tiletv.app.model.TileConfig;
import com.tiletv.app.model.TileItem;
import com.tiletv.app.util.AssetUtil;
import java.util.ArrayList;
import java.util.List;

/**
 * 主 Launcher 界面
 *
 * 从 assets/tiles.json 读取配置，用 RecyclerView 网格展示所有磁贴卡片。
 * 点击卡片跳转 WebViewActivity，传递 url、level、name。
 */
public class MainActivity extends Activity implements TileAdapter.OnTileClickListener {

    private RecyclerView recyclerView;
    private TileAdapter adapter;
    private List<TileItem> allTiles;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        recyclerView = findViewById(R.id.rv_tiles);

        loadTiles();
        setupGrid();
    }

    /**
     * 从 assets/tiles.json 读取并解析磁贴配置
     * 将所有分类下的 tiles 合并为一个扁平列表
     */
    private void loadTiles() {
        String json = AssetUtil.readAsset(this, "tiles.json");
        Gson gson = new Gson();
        TileConfig config = gson.fromJson(json, TileConfig.class);

        allTiles = new ArrayList<TileItem>();
        if (config != null && config.getCategories() != null) {
            for (TileCategory category : config.getCategories()) {
                if (category.getTiles() != null) {
                    allTiles.addAll(category.getTiles());
                }
            }
        }
    }

    /**
     * 设置 RecyclerView 网格布局（4列）并绑定适配器
     */
    private void setupGrid() {
        GridLayoutManager layoutManager = new GridLayoutManager(this, 4);
        recyclerView.setLayoutManager(layoutManager);
        adapter = new TileAdapter(this, allTiles, this);
        recyclerView.setAdapter(adapter);

        // 让第一个卡片自动获取焦点
        recyclerView.post(new Runnable() {
            @Override
            public void run() {
                RecyclerView.ViewHolder holder = recyclerView.findViewHolderForAdapterPosition(0);
                if (holder != null) {
                    holder.itemView.requestFocus();
                }
            }
        });
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
}
