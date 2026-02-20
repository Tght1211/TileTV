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
 * Category row adapter - Apple TV style.
 *
 * Each row in the outer vertical RecyclerView contains a category title
 * and a horizontally scrolling RecyclerView of tile cards.
 * clipToPadding and clipChildren are false to allow cards to scale up
 * beyond their bounds on focus.
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

        // Horizontal RecyclerView for tile cards
        LinearLayoutManager layoutManager = new LinearLayoutManager(
                context, LinearLayoutManager.HORIZONTAL, false);
        holder.rvTilesRow.setLayoutManager(layoutManager);

        TileAdapter tileAdapter = new TileAdapter(context, category.getTiles(), tileClickListener);
        holder.rvTilesRow.setAdapter(tileAdapter);

        // Allow inner RecyclerView children to receive focus for D-pad navigation
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
