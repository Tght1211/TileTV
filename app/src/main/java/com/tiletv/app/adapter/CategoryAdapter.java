package com.tiletv.app.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.tiletv.app.R;
import com.tiletv.app.model.TileCategory;
import java.util.List;

/**
 * 分类行适配器 - Apple TV 风格
 * 每个 item 包含分类标题 + 水平滚动的卡片行
 */
public class CategoryAdapter extends RecyclerView.Adapter<CategoryAdapter.CategoryViewHolder> {

    private final Context context;
    private final List<TileCategory> categories;
    private final TileAdapter.OnTileClickListener tileClickListener;

    public CategoryAdapter(Context context, List<TileCategory> categories,
                           TileAdapter.OnTileClickListener tileClickListener) {
        this.context = context;
        this.categories = categories;
        this.tileClickListener = tileClickListener;
    }

    @NonNull
    @Override
    public CategoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_category_row, parent, false);
        return new CategoryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CategoryViewHolder holder, int position) {
        TileCategory category = categories.get(position);
        holder.tvCategoryName.setText(category.getName());

        // 每行一个水平 RecyclerView
        LinearLayoutManager layoutManager = new LinearLayoutManager(
                context, LinearLayoutManager.HORIZONTAL, false);
        holder.rvTilesRow.setLayoutManager(layoutManager);

        TileAdapter tileAdapter = new TileAdapter(context, category.getTiles(), tileClickListener);
        holder.rvTilesRow.setAdapter(tileAdapter);

        // 允许子 RecyclerView 接收焦点用于 D-pad 导航
        holder.rvTilesRow.setFocusable(false);
        holder.rvTilesRow.setNestedScrollingEnabled(false);
    }

    @Override
    public int getItemCount() {
        return categories != null ? categories.size() : 0;
    }

    static class CategoryViewHolder extends RecyclerView.ViewHolder {
        TextView tvCategoryName;
        RecyclerView rvTilesRow;

        CategoryViewHolder(@NonNull View itemView) {
            super(itemView);
            tvCategoryName = itemView.findViewById(R.id.tv_category_name);
            rvTilesRow = itemView.findViewById(R.id.rv_tiles_row);
        }
    }
}
