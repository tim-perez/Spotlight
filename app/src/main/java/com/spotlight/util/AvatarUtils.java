package com.spotlight.util;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import com.spotlight.R;

public class AvatarUtils {

    public interface ColorSelectionListener {
        void onColorSelected(int color);
    }

    public static void setupColorSelection(Context context, View[] colorViews, ColorSelectionListener listener) {
        for (View v : colorViews) {
            v.setOnClickListener(view -> {
                for (View other : colorViews) {
                    other.setScaleX(1.0f);
                    other.setScaleY(1.0f);
                    other.setAlpha(0.6f);
                }
                view.setScaleX(1.2f);
                view.setScaleY(1.2f);
                view.setAlpha(1.0f);

                int colorResId = 0;
                String tag = (String) view.getTag();
                if ("blue".equals(tag)) colorResId = R.color.avatar_blue;
                else if ("green".equals(tag)) colorResId = R.color.avatar_green;
                else if ("orange".equals(tag)) colorResId = R.color.avatar_orange;
                else if ("purple".equals(tag)) colorResId = R.color.avatar_purple;
                else if ("red".equals(tag)) colorResId = R.color.avatar_red;
                else if ("teal".equals(tag)) colorResId = R.color.avatar_teal;
                else if ("pink".equals(tag)) colorResId = R.color.avatar_pink;

                if (colorResId != 0) {
                    listener.onColorSelected(context.getColor(colorResId));
                }
            });
        }
    }

    public static void resetColorSelection(View[] colorViews) {
        for (View v : colorViews) {
            v.setScaleX(1.0f);
            v.setScaleY(1.0f);
            v.setAlpha(0.6f);
        }
        if (colorViews.length > 0) {
            colorViews[0].performClick();
        }
    }

    public static void setAvatarColor(View view, int color) {
        if (color != 0) {
            GradientDrawable drawable = new GradientDrawable();
            drawable.setShape(GradientDrawable.OVAL);
            drawable.setColor(color);
            view.setBackground(drawable);
        } else {
            view.setBackgroundResource(android.R.color.darker_gray);
        }
    }
}
