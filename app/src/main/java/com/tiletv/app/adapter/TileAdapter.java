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
 * 卡片适配器 - Apple TV 风格
 * 焦点效果：放大 + 3D 微倾斜 + 白色光晕边框 + 阴影浮起
 */
public class TileAdapter extends RecyclerView.Adapter<TileAdapter.TileViewHolder> {

    public interface OnTileClickListener {
        void onTileClick(TileItem item);
    }

    private final List<TileItem> items;
    private final OnTileClickListener listener;
    private final Context context;

    // Apple TV 风格配色 - 低饱和度、优雅
    private static final int[] TILE_COLORS = {
        Color.parseColor("#1C3D5A"), // 深蓝
        Color.parseColor("#5A1C3D"), // 深玫红
        Color.parseColor("#1C5A3D"), // 深绿
        Color.parseColor("#5A3D1C"), // 深琥珀
        Color.parseColor("#3D1C5A"), // 深紫
        Color.parseColor("#1C5A5A"), // 深青
        Color.parseColor("#3D5A1C"), // 深橄榄
        Color.parseColor("#5A1C1C"), // 深红
    };

    private static final float CORNER_RADIUS_DP = 12f;
    private static final float FOCUS_SCALE = 1.12f;
    private static final int FOCUS_ANIM_DURATION = 200;

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
    public void onBindViewHolder(@NonNull final TileViewHolder holder, int position) {
        final TileItem item = items.get(position);
        holder.tvName.setText(item.getName());

        // level 指示文字
        String levelText;
        switch (item.getLevel()) {
            case 1: levelText = "TV"; break;
            case 2: levelText = "Smart Nav"; break;
            case 3: levelText = "Cursor"; break;
            default: levelText = ""; break;
        }
        holder.tvLevel.setText(levelText);

        // 圆角背景色
        final int bgColor = TILE_COLORS[position % TILE_COLORS.length];
        final float density = context.getResources().getDisplayMetrics().density;
        final float cornerRadius = CORNER_RADIUS_DP * density;

        GradientDrawable bgDrawable = new GradientDrawable();
        bgDrawable.setShape(GradientDrawable.RECTANGLE);
        bgDrawable.setCornerRadius(cornerRadius);
        bgDrawable.setColor(bgColor);
        holder.cardBg.setBackground(bgDrawable);

        // 阴影初始隐藏
        holder.shadow.setAlpha(0f);

        // Apple TV 焦点效果
        holder.itemView.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    // 放大 + 浮起
                    v.animate()
                        .scaleX(FOCUS_SCALE).scaleY(FOCUS_SCALE)
                        .setDuration(FOCUS_ANIM_DURATION)
                        .start();

                    // 白色光晕边框
                    GradientDrawable focused = new GradientDrawable();
                    focused.setShape(GradientDrawable.RECTANGLE);
                    focused.setCornerRadius(cornerRadius);
                    focused.setColor(lightenColor(bgColor, 0.15f));
                    focused.setStroke((int)(2 * density), Color.parseColor("#66FFFFFF"));
                    holder.cardBg.setBackground(focused);

                    // 阴影浮起
                    holder.shadow.animate().alpha(0.6f).setDuration(FOCUS_ANIM_DURATION).start();

                    // 提升层级 (API 21+)
                    if (Build.VERSION.SDK_INT >= 21) {
                        v.setElevation(16f);
                    }
                } else {
                    // 恢复原始大小
                    v.animate()
                        .scaleX(1.0f).scaleY(1.0f)
                        .rotationX(0f).rotationY(0f)
                        .setDuration(FOCUS_ANIM_DURATION)
                        .start();

                    // 恢复默认背景
                    GradientDrawable normal = new GradientDrawable();
                    normal.setShape(GradientDrawable.RECTANGLE);
                    normal.setCornerRadius(cornerRadius);
                    normal.setColor(bgColor);
                    holder.cardBg.setBackground(normal);

                    // 阴影消失
                    holder.shadow.animate().alpha(0f).setDuration(FOCUS_ANIM_DURATION).start();

                    if (Build.VERSION.SDK_INT >= 21) {
                        v.setElevation(0f);
                    }
                }
            }
        });

        // 点击事件（遥控器 OK 键触发）
        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) {
                    listener.onTileClick(item);
                }
            }
        });
    }

    /**
     * 将颜色提亮指定比例（Apple TV 焦点效果）
     */
    private static int lightenColor(int color, float fraction) {
        int r = Math.min(255, (int)(Color.red(color) + 255 * fraction));
        int g = Math.min(255, (int)(Color.green(color) + 255 * fraction));
        int b = Math.min(255, (int)(Color.blue(color) + 255 * fraction));
        return Color.rgb(r, g, b);
    }

    @Override
    public int getItemCount() {
        return items != null ? items.size() : 0;
    }

    static class TileViewHolder extends RecyclerView.ViewHolder {
        TextView tvName;
        TextView tvLevel;
        View cardBg;
        View shadow;

        TileViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tv_tile_name);
            tvLevel = itemView.findViewById(R.id.tv_tile_level);
            cardBg = itemView.findViewById(R.id.tile_card_bg);
            shadow = itemView.findViewById(R.id.tile_shadow);
            // 确保卡片可以接收 D-pad 焦点
            itemView.setFocusable(true);
            itemView.setFocusableInTouchMode(true);
        }
    }
}
