package app.exteraless.badges;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Draws two name icons next to each other. Views like {@link org.telegram.ui.ActionBar.SimpleTextView}
 * give a name only two side drawables, so the mauve icon shares one of them with the status icon.
 */
public final class NameIconPairDrawable extends Drawable {

    private final Drawable first;
    private final Drawable second;
    private final int gap;

    public NameIconPairDrawable(Drawable first, Drawable second, int gap) {
        this.first = first;
        this.second = second;
        this.gap = gap;
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        Rect bounds = getBounds();
        int x = drawIcon(canvas, first, bounds.left, bounds.centerY());
        drawIcon(canvas, second, x + gap, bounds.centerY());
    }

    private static int drawIcon(Canvas canvas, Drawable drawable, int x, int centerY) {
        int width = drawable.getIntrinsicWidth();
        int height = drawable.getIntrinsicHeight();
        drawable.setBounds(x, centerY - height / 2, x + width, centerY + height / 2);
        drawable.draw(canvas);
        return x + width;
    }

    @Override
    public int getIntrinsicWidth() {
        return first.getIntrinsicWidth() + gap + second.getIntrinsicWidth();
    }

    @Override
    public int getIntrinsicHeight() {
        return Math.max(first.getIntrinsicHeight(), second.getIntrinsicHeight());
    }

    @Override
    public void setAlpha(int alpha) {
        first.setAlpha(alpha);
        second.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        first.setColorFilter(colorFilter);
        second.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSPARENT;
    }
}
