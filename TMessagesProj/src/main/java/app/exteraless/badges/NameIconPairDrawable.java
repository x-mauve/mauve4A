package app.exteraless.badges;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.ui.Components.AnimatedEmojiDrawable;

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
        drawIcon(canvas, first, bounds.left, bounds.centerY());
        drawIcon(canvas, second, bounds.left + contentWidth(first) + gap, bounds.centerY());
    }

    /**
     * Width the drawable actually paints. A swap drawable sized for animated emoji draws a plain
     * icon (like the premium star) at its own smaller size, pinned to the left, which would
     * otherwise leave an empty strip before the next icon.
     */
    public static int contentWidth(Drawable drawable) {
        if (drawable instanceof AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable) {
            AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable swap = (AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable) drawable;
            Drawable inner = swap.getDrawable();
            if (!swap.center && inner != null && !(inner instanceof AnimatedEmojiDrawable)) {
                int width = inner.getIntrinsicWidth();
                if (width > 0 && width < swap.getIntrinsicWidth()) {
                    return width;
                }
            }
        }
        return drawable.getIntrinsicWidth();
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
        return contentWidth(first) + gap + second.getIntrinsicWidth();
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
