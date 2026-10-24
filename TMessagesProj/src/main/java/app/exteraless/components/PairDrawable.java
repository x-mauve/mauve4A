package app.exteraless.components;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;

/**
 * Two drawables laid out in a row, so a view with a single drawable slot can show both.
 * Both are vertically centered; invalidations of either are forwarded to the host.
 */
public class PairDrawable extends Drawable implements Drawable.Callback {

    private final Drawable first;
    private final Drawable second;
    private final int gap;

    public PairDrawable(@NonNull Drawable first, @NonNull Drawable second, int gap) {
        this.first = first;
        this.second = second;
        this.gap = gap;
        first.setCallback(this);
        second.setCallback(this);
    }

    public Drawable getFirst() {
        return first;
    }

    public Drawable getSecond() {
        return second;
    }

    @Override
    protected void onBoundsChange(@NonNull Rect bounds) {
        int firstWidth = scaled(first.getIntrinsicWidth(), bounds);
        int firstHeight = scaledHeight(first.getIntrinsicHeight(), bounds);
        int secondWidth = scaled(second.getIntrinsicWidth(), bounds);
        int secondHeight = scaledHeight(second.getIntrinsicHeight(), bounds);
        int cy = bounds.centerY();
        first.setBounds(bounds.left, cy - firstHeight / 2, bounds.left + firstWidth, cy + (firstHeight + 1) / 2);
        int left = bounds.right - secondWidth;
        second.setBounds(left, cy - secondHeight / 2, bounds.right, cy + (secondHeight + 1) / 2);
    }

    // The host may draw the pair smaller than its intrinsic size, so children shrink proportionally
    private int scaled(int width, Rect bounds) {
        int intrinsic = getIntrinsicWidth();
        return intrinsic <= 0 ? width : width * bounds.width() / intrinsic;
    }

    private int scaledHeight(int height, Rect bounds) {
        int intrinsic = getIntrinsicHeight();
        return intrinsic <= 0 ? height : height * bounds.height() / intrinsic;
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        first.draw(canvas);
        second.draw(canvas);
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
    public void setColorFilter(ColorFilter colorFilter) {
        first.setColorFilter(colorFilter);
        second.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    @Override
    public void invalidateDrawable(@NonNull Drawable who) {
        invalidateSelf();
    }

    @Override
    public void scheduleDrawable(@NonNull Drawable who, @NonNull Runnable what, long when) {
        scheduleSelf(what, when);
    }

    @Override
    public void unscheduleDrawable(@NonNull Drawable who, @NonNull Runnable what) {
        unscheduleSelf(what);
    }
}
