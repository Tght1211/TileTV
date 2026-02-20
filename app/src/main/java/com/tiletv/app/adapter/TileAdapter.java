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
import com.tiletv.app.ws.WebSocketManager;

import java.util.List;

/**
 * Tile card adapter - Apple TV frosted glass style.
 *
 * Each card has a low-saturation dark color background with rounded corners.
 * Focus effects include scale-up, brightness increase, white translucent border,
 * and blue glow shadow (elevation on API 21+).
 *
 * Cards display the site name centered and a mode label in the bottom-right corner.
 */
public class TileAdapter extends RecyclerView.Adapter<TileAdapter.TileViewHolder> {

    public interface OnTileClickListener {
        void onTileClick(TileItem item);
    }

    private final List<TileItem> items;
    private final OnTileClickListener listener;
    private final Context context;

    // Apple TV style low-saturation dark colors
    private static final int[] TILE_COLORS = {
            Color.parseColor("#1C3D5A"), // Deep blue
            Color.parseColor("#5A1C3D"), // Deep rose
            Color.parseColor("#1C5A3D"), // Deep green
            Color.parseColor("#5A3D1C"), // Deep amber
            Color.parseColor("#3D1C5A"), // Deep purple
            Color.parseColor("#1C5A5A"), // Deep teal
            Color.parseColor("#3D5A1C"), // Deep olive
            Color.parseColor("#5A1C1C"), // Deep red
    };

    private static final float CORNER_RADIUS_DP = 16f;
    private static final float FOCUS_SCALE = 1.1f;
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

        // Mode label text
        String levelText;
        boolean isServerConnected = WebSocketManager.getInstance().isConnected();
        if (isServerConnected) {
            levelText = "AI";
        } else {
            switch (item.getLevel()) {
                case 1:
                    levelText = "TV";
                    break;
                case 2:
                    levelText = "AI Nav";
                    break;
                case 3:
                    levelText = "Cursor";
                    break;
                default:
                    levelText = "";
                    break;
            }
        }
        holder.tvLevel.setText(levelText);

        // Mode label color: blue for AI mode
        if (isServerConnected) {
            holder.tvLevel.setTextColor(Color.parseColor("#0A84FF"));
        } else {
            holder.tvLevel.setTextColor(Color.parseColor("#8E8E93"));
        }

        // Rounded background
        final int bgColor = TILE_COLORS[position % TILE_COLORS.length];
        final float density = context.getResources().getDisplayMetrics().density;
        final float cornerRadius = CORNER_RADIUS_DP * density;

        GradientDrawable bgDrawable = new GradientDrawable();
        bgDrawable.setShape(GradientDrawable.RECTANGLE);
        bgDrawable.setCornerRadius(cornerRadius);
        bgDrawable.setColor(bgColor);
        holder.cardBg.setBackground(bgDrawable);

        // Shadow initially hidden
        holder.shadow.setAlpha(0f);

        // Apple TV focus effect
        holder.itemView.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    // Scale up
                    v.animate()
                            .scaleX(FOCUS_SCALE).scaleY(FOCUS_SCALE)
                            .setDuration(FOCUS_ANIM_DURATION)
                            .start();

                    // Brighten background + white translucent border
                    GradientDrawable focused = new GradientDrawable();
                    focused.setShape(GradientDrawable.RECTANGLE);
                    focused.setCornerRadius(cornerRadius);
                    focused.setColor(lightenColor(bgColor, 0.15f));
                    focused.setStroke((int) (2 * density), Color.parseColor("#40FFFFFF"));
                    holder.cardBg.setBackground(focused);

                    // Shadow glow
                    holder.shadow.animate().alpha(0.6f).setDuration(FOCUS_ANIM_DURATION).start();

                    // Elevation for API 21+
                    if (Build.VERSION.SDK_INT >= 21) {
                        v.setElevation(16f);
                    }
                } else {
                    // Restore original size
                    v.animate()
                            .scaleX(1.0f).scaleY(1.0f)
                            .setDuration(FOCUS_ANIM_DURATION)
                            .start();

                    // Restore default background
                    GradientDrawable normal = new GradientDrawable();
                    normal.setShape(GradientDrawable.RECTANGLE);
                    normal.setCornerRadius(cornerRadius);
                    normal.setColor(bgColor);
                    holder.cardBg.setBackground(normal);

                    // Hide shadow
                    holder.shadow.animate().alpha(0f).setDuration(FOCUS_ANIM_DURATION).start();

                    if (Build.VERSION.SDK_INT >= 21) {
                        v.setElevation(0f);
                    }
                }
            }
        });

        // Click event (remote OK button)
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
     * Lighten a color by a given fraction (Apple TV focus effect).
     */
    private static int lightenColor(int color, float fraction) {
        int r = Math.min(255, (int) (Color.red(color) + 255 * fraction));
        int g = Math.min(255, (int) (Color.green(color) + 255 * fraction));
        int b = Math.min(255, (int) (Color.blue(color) + 255 * fraction));
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
            // Ensure card can receive D-pad focus
            itemView.setFocusable(true);
            itemView.setFocusableInTouchMode(true);
        }
    }
}
