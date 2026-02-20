package com.tiletv.app.adapter;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.tiletv.app.R;
import com.tiletv.app.model.TileItem;
import java.util.List;

/**
 * 磁贴卡片网格适配器
 * 支持 D-pad 焦点导航，焦点时放大+高亮，失焦恢复
 */
public class TileAdapter extends RecyclerView.Adapter<TileAdapter.TileViewHolder> {

    public interface OnTileClickListener {
        void onTileClick(TileItem item);
    }

    private final List<TileItem> items;
    private final OnTileClickListener listener;
    private final Context context;

    // 预定义一组卡片背景色
    private static final int[] TILE_COLORS = {
        Color.parseColor("#E53935"), // 红色
        Color.parseColor("#1E88E5"), // 蓝色
        Color.parseColor("#43A047"), // 绿色
        Color.parseColor("#FB8C00"), // 橙色
        Color.parseColor("#8E24AA"), // 紫色
        Color.parseColor("#00ACC1"), // 青色
        Color.parseColor("#3949AB"), // 靛蓝
        Color.parseColor("#D81B60"), // 粉色
    };

    public TileAdapter(Context context, List<TileItem> items, OnTileClickListener listener) {
        this.context = context;
        this.items = items;
        this.listener = listener;
    }

    @NonNull
    @Override
    public TileViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_tile, parent, false);
        return new TileViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TileViewHolder holder, int position) {
        final TileItem item = items.get(position);
        holder.tvName.setText(item.getName());

        // level 指示文字
        String levelText;
        switch (item.getLevel()) {
            case 1: levelText = "TV"; break;
            case 2: levelText = "智能导航"; break;
            case 3: levelText = "光标模式"; break;
            default: levelText = ""; break;
        }
        holder.tvLevel.setText(levelText);

        // 背景色，使用 GradientDrawable 实现圆角+颜色
        int colorIndex = position % TILE_COLORS.length;
        float cornerRadius = context.getResources().getDisplayMetrics().density * 8; // 8dp
        GradientDrawable bgDrawable = new GradientDrawable();
        bgDrawable.setShape(GradientDrawable.RECTANGLE);
        bgDrawable.setCornerRadius(cornerRadius);
        bgDrawable.setColor(TILE_COLORS[colorIndex]);
        holder.cardBg.setBackground(bgDrawable);

        // 焦点处理 - 获得焦点时放大+高亮边框，失去焦点时恢复
        final int bgColor = TILE_COLORS[colorIndex];
        final float cr = cornerRadius;
        holder.itemView.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    v.animate().scaleX(1.1f).scaleY(1.1f).setDuration(150).start();
                    // 焦点时加高亮边框
                    GradientDrawable focused = new GradientDrawable();
                    focused.setShape(GradientDrawable.RECTANGLE);
                    focused.setCornerRadius(cr);
                    focused.setColor(bgColor);
                    focused.setStroke((int)(3 * context.getResources().getDisplayMetrics().density),
                            Color.parseColor("#FF6B35"));
                    View bg = v.findViewById(R.id.tile_card_bg);
                    if (bg != null) bg.setBackground(focused);
                    if (Build.VERSION.SDK_INT >= 21) {
                        v.setElevation(12f);
                    }
                } else {
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start();
                    GradientDrawable normal = new GradientDrawable();
                    normal.setShape(GradientDrawable.RECTANGLE);
                    normal.setCornerRadius(cr);
                    normal.setColor(bgColor);
                    View bg = v.findViewById(R.id.tile_card_bg);
                    if (bg != null) bg.setBackground(normal);
                    if (Build.VERSION.SDK_INT >= 21) {
                        v.setElevation(2f);
                    }
                }
            }
        });

        // 点击事件（遥控器 OK 键或菜单键触发）
        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) {
                    listener.onTileClick(item);
                }
            }
        });
    }

    @Override
    public int getItemCount() {
        return items != null ? items.size() : 0;
    }

    static class TileViewHolder extends RecyclerView.ViewHolder {
        TextView tvName;
        TextView tvLevel;
        View cardBg;

        TileViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tv_tile_name);
            tvLevel = itemView.findViewById(R.id.tv_tile_level);
            cardBg = itemView.findViewById(R.id.tile_card_bg);
            // 确保卡片可以接收 D-pad 焦点
            itemView.setFocusable(true);
            itemView.setFocusableInTouchMode(true);
        }
    }
}
