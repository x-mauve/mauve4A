package app.exteraless.updater;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.view.HapticFeedbackConstants;
import android.view.View;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.CubicBezierInterpolator;

/**
 * Pill-shaped update button for the mauve settings header.
 *
 * Follows GitHubUpdater: tap to download, download progress along the bottom
 * edge, tap to launch the installer. Progress ticks only move the line; the
 * dots and the icon spin run on their own timers and survive them.
 */
public class UpdateButton extends View {

    /** easeInOutBack, local so the upstream interpolator file stays untouched. */
    private static final CubicBezierInterpolator EASE_IN_OUT_BACK = new CubicBezierInterpolator(0.68, -0.6, 0.32, 1.6);

    private static final long DOTS_INTERVAL = 400;
    private static final long SPIN_DURATION = 600;
    private static final long SPIN_PAUSE = 600;
    private static final long FADE_DURATION = 200;
    private static final long RESIZE_DURATION = 220;

    private static final int HEIGHT = 40;
    private static final int PADDING = 16;
    private static final int ICON_SIZE = 20;
    private static final int ICON_GAP = 7;

    public interface UpdateDelegate {
        void onUpdateClicked();
    }

    private UpdateDelegate delegate;
    private int buttonState = GitHubUpdater.STATE_NONE;

    private int textColor;
    private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path pillPath = new Path();
    private final RectF pillRect = new RectF();
    private Drawable icon;

    private String text = "";
    private float textWidth;
    private int dots;

    private float drawWidth;
    private float contentAlpha = 1f;
    private float progressTarget, progressShown;
    private boolean indeterminate;

    private float iconAngle;
    private ValueAnimator spinAnimator, resizeAnimator, fadeAnimator;

    private final Runnable dotsRunnable = new Runnable() {
        @Override
        public void run() {
            dots = (dots + 1) % 4;
            invalidate();
            AndroidUtilities.runOnUIThread(this, DOTS_INTERVAL);
        }
    };

    public UpdateButton(Context context) {
        super(context);
        textPaint.setTypeface(AndroidUtilities.bold());
        textPaint.setTextSize(dp(15));
        setOnClickListener(v -> {
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP,
                    HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
            if (delegate != null) {
                delegate.onUpdateClicked();
            }
        });
        updateColors();
        setVisibility(GONE);
    }

    public void setUpdateDelegate(UpdateDelegate delegate) {
        this.delegate = delegate;
    }

    /**
     * Applies the shared updater state. A repeated state only updates the
     * progress, so per-percent ticks never restart any animation.
     */
    public void setState(int state, long done, long total, boolean animated) {
        indeterminate = total <= 0;
        progressTarget = total > 0 ? Math.min(1f, done / (float) total) : 0f;
        if (state == buttonState) {
            invalidate();
            return;
        }
        int previous = buttonState;
        buttonState = state;
        if (state == GitHubUpdater.STATE_NONE) {
            stopTimers();
            setVisibility(GONE);
            return;
        }

        int iconRes;
        if (state == GitHubUpdater.STATE_DOWNLOADING) {
            iconRes = R.drawable.msg_retry;
            text = LocaleController.getString(R.string.OEUpdateButtonDownloading);
            // Reserve room for all three dots so the width stays put while they cycle
            textWidth = textPaint.measureText(text + "...");
            progressShown = progressTarget;
        } else {
            iconRes = state == GitHubUpdater.STATE_READY ? R.drawable.msg_check : R.drawable.msg_download;
            text = LocaleController.getString(state == GitHubUpdater.STATE_READY
                    ? R.string.OEUpdateButtonReady : R.string.OEUpdateInstall);
            textWidth = textPaint.measureText(text);
        }
        setContentDescription(text);
        Drawable drawable = ContextCompat.getDrawable(getContext(), iconRes);
        icon = drawable == null ? null : drawable.mutate();
        if (icon != null) {
            icon.setBounds(0, 0, dp(ICON_SIZE), dp(ICON_SIZE));
            icon.setColorFilter(new PorterDuffColorFilter(textColor, PorterDuff.Mode.SRC_IN));
        }

        stopTimers();
        iconAngle = 0;
        dots = 0;
        startTimers();

        boolean appearing = previous == GitHubUpdater.STATE_NONE || getVisibility() != VISIBLE;
        animated &= isAttachedToWindow();
        resizeTo(targetWidth(), animated && !appearing);
        if (appearing) {
            setVisibility(VISIBLE);
            contentAlpha = 1f;
            if (animated) {
                setAlpha(0f);
                setScaleX(0.9f);
                setScaleY(0.9f);
                animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(220)
                        .setInterpolator(CubicBezierInterpolator.EASE_OUT).start();
            } else {
                setAlpha(1f);
                setScaleX(1f);
                setScaleY(1f);
            }
        } else if (animated) {
            fadeIn();
        } else {
            contentAlpha = 1f;
        }
        invalidate();
    }

    private float targetWidth() {
        return dp(PADDING) + dp(ICON_SIZE) + dp(ICON_GAP) + textWidth + dp(PADDING);
    }

    private void resizeTo(float width, boolean animated) {
        if (resizeAnimator != null) {
            resizeAnimator.cancel();
            resizeAnimator = null;
        }
        if (!animated || drawWidth <= 0) {
            drawWidth = width;
            requestLayout();
            return;
        }
        resizeAnimator = ValueAnimator.ofFloat(drawWidth, width);
        resizeAnimator.setDuration(RESIZE_DURATION);
        resizeAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        resizeAnimator.addUpdateListener(a -> {
            drawWidth = (float) a.getAnimatedValue();
            requestLayout();
            invalidate();
        });
        resizeAnimator.start();
    }

    private void fadeIn() {
        if (fadeAnimator != null) {
            fadeAnimator.cancel();
        }
        contentAlpha = 0f;
        fadeAnimator = ValueAnimator.ofFloat(0f, 1f);
        fadeAnimator.setDuration(FADE_DURATION);
        fadeAnimator.addUpdateListener(a -> {
            contentAlpha = (float) a.getAnimatedValue();
            invalidate();
        });
        fadeAnimator.start();
    }

    private void startTimers() {
        if (buttonState != GitHubUpdater.STATE_DOWNLOADING || !isAttachedToWindow()) {
            return;
        }
        AndroidUtilities.runOnUIThread(dotsRunnable, DOTS_INTERVAL);
        // One endless animator: a half turn, then a pause, then the next half turn
        // continues from where the previous one stopped
        final float spinPart = SPIN_DURATION / (float) (SPIN_DURATION + SPIN_PAUSE);
        final float[] base = {iconAngle};
        spinAnimator = ValueAnimator.ofFloat(0f, 1f);
        spinAnimator.setDuration(SPIN_DURATION + SPIN_PAUSE);
        spinAnimator.setRepeatCount(ValueAnimator.INFINITE);
        spinAnimator.setInterpolator(null);
        spinAnimator.addUpdateListener(a -> {
            float t = Math.min(1f, a.getAnimatedFraction() / spinPart);
            iconAngle = (base[0] + 180f * EASE_IN_OUT_BACK.getInterpolation(t)) % 360f;
            invalidate();
        });
        spinAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationRepeat(Animator animation) {
                base[0] = (base[0] + 180f) % 360f;
            }
        });
        spinAnimator.start();
    }

    private void stopTimers() {
        AndroidUtilities.cancelRunOnUIThread(dotsRunnable);
        if (spinAnimator != null) {
            spinAnimator.cancel();
            spinAnimator = null;
        }
    }

    /** Recomputes colors, also called when the theme changes. */
    public void updateColors() {
        int primary = Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4);
        textColor = Theme.getColor(Theme.key_featuredStickers_buttonText);
        textPaint.setColor(textColor);
        if (icon != null) {
            icon.setColorFilter(new PorterDuffColorFilter(textColor, PorterDuff.Mode.SRC_IN));
        }
        setBackground(Theme.createSimpleSelectorRoundRectDrawable(dp(HEIGHT / 2f), primary,
                ColorUtils.blendARGB(primary, Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), 0.16f)));
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension((int) Math.ceil(drawWidth), dp(HEIGHT));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        float r = h / 2f;
        float startX = (w - targetWidth()) / 2f + dp(PADDING);
        int alpha = (int) (255 * contentAlpha);

        if (icon != null) {
            float half = dp(ICON_SIZE) / 2f;
            canvas.save();
            canvas.translate(startX + half, r);
            canvas.rotate(iconAngle);
            canvas.translate(-half, -half);
            icon.setAlpha(alpha);
            icon.draw(canvas);
            canvas.restore();
        }

        String visible = buttonState == GitHubUpdater.STATE_DOWNLOADING ? text + "...".substring(0, dots) : text;
        Paint.FontMetrics metrics = textPaint.getFontMetrics();
        textPaint.setAlpha(alpha);
        canvas.drawText(visible, startX + dp(ICON_SIZE) + dp(ICON_GAP),
                r - (metrics.ascent + metrics.descent) / 2f, textPaint);

        if (buttonState == GitHubUpdater.STATE_DOWNLOADING) {
            drawProgress(canvas, w, h, r);
        }
    }

    private void drawProgress(Canvas canvas, int w, int h, float r) {
        float t = dp(2.5f);
        // Full width: the pill clip bends the line into the rounded ends, so it
        // has no visible start or end
        float left = 0, right = w;
        pillRect.set(0, 0, w, h);
        pillPath.reset();
        pillPath.addRoundRect(pillRect, r, r, Path.Direction.CW);
        canvas.save();
        canvas.clipPath(pillPath);

        progressPaint.setColor(ColorUtils.setAlphaComponent(textColor, 0x40));
        canvas.drawRect(left, h - t, right, h, progressPaint);
        progressPaint.setColor(textColor);
        if (indeterminate) {
            // Unknown size: a segment travels back and forth along the line
            float seg = (right - left) * 0.3f;
            float travel = (float) Math.abs(Math.sin(System.currentTimeMillis() / 600.0));
            float from = left + (right - left - seg) * travel;
            canvas.drawRect(from, h - t, from + seg, h, progressPaint);
            invalidate();
        } else {
            canvas.drawRect(left, h - t, left + (right - left) * progressShown, h, progressPaint);
            // Ticks arrive per percent; ease the line toward the latest value
            if (Math.abs(progressTarget - progressShown) > 0.001f) {
                progressShown += (progressTarget - progressShown) * 0.2f;
                invalidate();
            } else {
                progressShown = progressTarget;
            }
        }
        canvas.restore();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        updateColors();
        stopTimers();
        startTimers();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stopTimers();
        if (resizeAnimator != null) {
            resizeAnimator.end();
        }
        if (fadeAnimator != null) {
            fadeAnimator.end();
        }
    }
}
