package app.exteraless.updater;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.ForegroundColorSpan;
import android.view.HapticFeedbackConstants;
import android.view.ViewGroup;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.CubicBezierInterpolator;

/**
 * Pill-shaped action button for the mauve settings header.
 *
 * Three visible states driven by GitHubUpdater: tap to download, live download
 * progress with a bottom line, tap to launch the installer. Everything is drawn
 * by hand so the progress line and the icon rotation stay precise.
 */
public class UpdateButton extends android.view.View {

    /** easeInOutBack, local so the upstream interpolator file stays untouched. */
    private static final CubicBezierInterpolator EASE_IN_OUT_BACK = new CubicBezierInterpolator(0.68, -0.6, 0.32, 1.6);

    private static final long DOTS_INTERVAL = 400;
    private static final long SPIN_DURATION = 600;
    private static final long SPIN_PAUSE = 600;

    public interface UpdateDelegate {
        void onUpdateClicked();
    }

    private static final long FADE_DURATION = 200;

    private int buttonState = GitHubUpdater.STATE_NONE;
    private GitHubUpdater.Release release;
    private long progressDone, progressTotal;
    private UpdateDelegate delegate;

    private int primaryColor, textColor;
    private final TextPaint textPaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
    private Drawable icon;

    // Current animated text: built once per state/dot change
    private StaticLayout textLayout;
    // Fixed width of the longest variant ("Downloading...") so the dots cycle
    // without resizing the pill
    private int textWidthDownloading;
    private int textWidthCurrent;
    private float textAlpha = 1f, iconAlpha = 1f;

    private final RectF tempRect = new RectF();
    // Smoothly followed progress for the bottom line
    private float progressTarget, progressShown;
    private float iconAngle, spinBase;
    private ValueAnimator widthAnimator, spinAnimator;
    private AnimatorSet appearAnimator;
    private final Runnable dotsRunnable = new Runnable() {
        @Override
        public void run() {
            if (buttonState != GitHubUpdater.STATE_DOWNLOADING) {
                return;
            }
            dots = (dots + 1) % 4;
            buildTextLayout();
            invalidate();
            AndroidUtilities.runOnUIThread(dotsRunnable, DOTS_INTERVAL);
        }
    };
    private int dots;

    public UpdateButton(Context context) {
        super(context);
        setOnClickListener(v -> {
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP,
                    HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
            if (delegate != null) {
                delegate.onUpdateClicked();
            }
        });
        textPaint.setTypeface(AndroidUtilities.bold());
        textPaint.setTextSize(dp(15));
        updateColors();
        setVisibility(GONE);
    }

    public void setUpdateDelegate(UpdateDelegate delegate) {
        this.delegate = delegate;
    }

    private int contentWidth() {
        return dp(16) + dp(20) + dp(7) + textWidthCurrent + dp(16);
    }

    /**
     * Apply a state update coming from GitHubUpdater. The width change between
     * states is animated; progress numbers are applied immediately.
     */
    public void setState(int state, GitHubUpdater.Release release, long done, long total, boolean animated) {
        ensureStableWidths();
        boolean becomeDownloading = state == GitHubUpdater.STATE_DOWNLOADING;
        boolean leaveDownloading = buttonState == GitHubUpdater.STATE_DOWNLOADING && !becomeDownloading;
        int oldWidth = contentWidth();
        buttonState = state;
        this.release = release;
        progressDone = done;
        progressTotal = total;
        progressTarget = total > 0 ? done / (float) total : 0f;

        switch (state) {
            case GitHubUpdater.STATE_AVAILABLE:
                icon = getResources().getDrawable(R.drawable.msg_download).mutate();
                icon.setColorFilter(new PorterDuffColorFilter(textColor, PorterDuff.Mode.SRC_IN));
                icon.setBounds(0, 0, dp(20), dp(20));
                textWidthCurrent = measure(LocaleController.getString(R.string.OEUpdateInstall));
                break;
            case GitHubUpdater.STATE_DOWNLOADING:
                icon = getResources().getDrawable(R.drawable.msg_retry).mutate();
                icon.setColorFilter(new PorterDuffColorFilter(textColor, PorterDuff.Mode.SRC_IN));
                icon.setBounds(0, 0, dp(20), dp(20));
                textWidthCurrent = textWidthDownloading;
                dots = 0;
                buildTextLayout();
                break;
            case GitHubUpdater.STATE_READY:
                icon = getResources().getDrawable(R.drawable.msg_check).mutate();
                icon.setColorFilter(new PorterDuffColorFilter(textColor, PorterDuff.Mode.SRC_IN));
                icon.setBounds(0, 0, dp(20), dp(20));
                textWidthCurrent = measure(LocaleController.getString(R.string.OEUpdateButtonReady));
                break;
            default:
                setVisibility(GONE);
                stopAnimations();
                return;
        }
        if (leaveDownloading || state != GitHubUpdater.STATE_DOWNLOADING) {
            buildTextLayout();
        }
        setContentDescription(getTextForState());
        applyAnimations(oldWidth, newWidth(), becomeDownloading, animated);
    }

    private int newWidth() {
        return contentWidth();
    }

    /** "Tap to update" translated per state, used for a11y. */
    private CharSequence getTextForState() {
        switch (buttonState) {
            case GitHubUpdater.STATE_AVAILABLE:
                return LocaleController.getString(R.string.OEUpdateInstall);
            case GitHubUpdater.STATE_DOWNLOADING:
                return LocaleController.getString(R.string.OEUpdateButtonDownloading);
            case GitHubUpdater.STATE_READY:
                return LocaleController.getString(R.string.OEUpdateButtonReady);
            default:
                return null;
        }
    }

    private int measure(String text) {
        float width = textPaint.measureText(text);
        return (int) Math.ceil(width);
    }

    /**
     * "Downloading" with the dots cycle drawn transparent at the fixed tail of
     * "Downloading..." keeps the measured width constant.
     */
    private void buildTextLayout() {
        String base;
        switch (buttonState) {
            case GitHubUpdater.STATE_DOWNLOADING:
                base = LocaleController.getString(R.string.OEUpdateButtonDownloading) + "...";
                break;
            case GitHubUpdater.STATE_READY:
                base = LocaleController.getString(R.string.OEUpdateButtonReady);
                break;
            case GitHubUpdater.STATE_AVAILABLE:
            default:
                base = LocaleController.getString(R.string.OEUpdateInstall);
                break;
        }
        textPaint.setColor(textColor);
        SpannableStringBuilder builder = new SpannableStringBuilder(base);
        int textSize = base.length();
        if (buttonState == GitHubUpdater.STATE_DOWNLOADING) {
            int visible = Math.min(3, dots);
            int dotStart = textSize - 3;
            if (visible == 0) {
                // hide all three dots
            }
            for (int i = dotStart; i < textSize; i++) {
                boolean shown = i - dotStart < visible;
                builder.setSpan(new ForegroundColorSpan(shown ? textColor : Color.TRANSPARENT),
                        i, i + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        textLayout = new StaticLayout(builder, textPaint, (int) Math.ceil(textPaint.measureText(base)) + 1,
                Layout.Alignment.ALIGN_NORMAL, 1f, 0f, true);
    }

    /**
     * Widths of both variants measured up front: "Downloading" is the widest at
     * runtime anyway; all three measured for cross-state smoothness.
     */
    private void ensureStableWidths() {
        if (textWidthDownloading == 0) {
            String text = LocaleController.getString(R.string.OEUpdateButtonDownloading) + "...";
            textWidthDownloading = measure(text);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = buttonState == GitHubUpdater.STATE_NONE ? 0 : contentWidth();
        setMeasuredDimension(width, dp(40));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth(), h = getHeight(), r = h / 2;
        canvas.save();
        canvas.translate(0, 0);
        float contentWidth = dp(16) + dp(20) + dp(7) + textWidthCurrent + dp(16);
        float startX = (w - contentWidth) / 2f + dp(16);
        if (iconAlpha > 0 && icon != null) {
            canvas.save();
            canvas.translate(startX + dp(10), r);
            canvas.rotate(iconAngle);
            canvas.translate(-dp(10), -(float) (icon.getBounds().height()) / 2f);
            icon.setAlpha((int) (iconAlpha * 255));
            icon.draw(canvas);
            canvas.restore();
        }
        if (textLayout != null && textAlpha > 0) {
            int saveCount = canvas.saveLayerAlpha(
                    startX + dp(20) + dp(7), (h - textLayout.getHeight()) / 2f,
                    startX + dp(20) + dp(7) + textLayout.getWidth(), (h + textLayout.getHeight()) / 2f,
                    (int) (textAlpha * 255));
            canvas.translate(startX + dp(20) + dp(7), (h - textLayout.getHeight()) / 2f);
            textLayout.draw(canvas);
            canvas.restoreToCount(saveCount);
        }
        canvas.restore();
        if (buttonState == GitHubUpdater.STATE_DOWNLOADING) {
            drawProgress(canvas, w, h, r);
        }
        // lerp the line toward its target on the way to the next percent step
        if (Math.abs(progressShown - progressTarget) > 0.001f) {
            progressShown += (progressTarget - progressShown) * 0.25f;
            invalidate();
        }
    }

    private void drawProgress(Canvas canvas, int w, int h, int r) {
        float t = dp(2.5f);
        float left = r, right = w - r;
        if (right - left <= 0) {
            return;
        }
        int saveCount = canvas.save();
        if (pillPath == null) {
            pillPath = new android.graphics.Path();
        }
        pillPath.reset();
        pillPath.addRoundRect(new RectF(0, 0, w, h), r, r, android.graphics.Path.Direction.CW);
        canvas.clipPath(pillPath);
        int trackColor = ColorUtils.setAlphaComponent(textColor, 0x40);
        canvas.drawRect(left, h - t, right, h, getProgressPaint(trackColor));
        float fillEnd = left + (right - left) * progressShown;
        canvas.drawRect(left, h - t, fillEnd, h, getProgressPaint(textColor));
        if (progressTotal <= 0) {
            // indeterminate: a segment travels back and forth within the straight part
            float travel = (float) Math.abs(Math.sin(System.currentTimeMillis() / 900.0));
            float seg = (right - left) * 0.3f;
            canvas.drawRect(left + (right - left - seg) * travel, h - t,
                    left + (right - left - seg) * travel + seg, h, getProgressPaint(textColor));
            invalidate();
        }
        canvas.restoreToCount(saveCount);
    }

    private android.graphics.Paint progressPaint;
    private android.graphics.Path pillPath;
    private android.graphics.Paint getProgressPaint(int color) {
        if (progressPaint == null) {
            progressPaint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        }
        progressPaint.setColor(color);
        return progressPaint;
    }

    public void setProgress(long done, long total) {
        progressDone = done;
        progressTotal = total;
        progressTarget = total > 0 ? done / (float) total : progressTarget;
        invalidate();
    }

    /** Recompute colors; called from the cell when theming changes. */
    public void updateColors() {
        primaryColor = Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4);
        textColor = Theme.getColor(Theme.key_featuredStickers_buttonText);
        textPaint.setColor(textColor);
        Drawable background = Theme.createSimpleSelectorRoundRectDrawable(dp(20), primaryColor,
                ColorUtils.blendARGB(primaryColor,
                        Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), 0.16f));
        setBackground(background);
        if (textLayout != null) {
            buildTextLayout();
        }
        invalidate();
    }

    // ---- animation drivers ----

    private void applyAnimations(int oldWidth, int newWidth, boolean enteringDownloading, boolean animated) {
        boolean wasGone = getVisibility() != VISIBLE;
        if (wasGone) {
            setVisibility(VISIBLE);
        }
        boolean disappearing = buttonState == GitHubUpdater.STATE_NONE;
        if (disappearing) {
            setVisibility(GONE);
            stopAnimations();
            return;
        }
        startDotsIfDownloading();
        startSpinIfDownloading();
        cancelWidthAnimator();
        if (oldWidth != newWidth) {
            setLayoutWidth(oldWidth);
            widthAnimator = ValueAnimator.ofFloat(0f, 1f);
            widthAnimator.setDuration(animated ? 200L : 0L);
            widthAnimator.setInterpolator(CubicBezierInterpolator.EASE_BOTH);
            widthAnimator.addUpdateListener(a -> {
                float v = (float) a.getAnimatedValue();
                setLayoutWidth((int) (oldWidth + (newWidth - oldWidth) * v));
                invalidate();
            });
            widthAnimator.start();
        }
        if (animated) {
            // cross-fade the old content into the new one
            textAlpha = 0f;
            iconAlpha = 0f;
            if (appearAnimator != null) {
                appearAnimator.cancel();
            }
            appearAnimator = new AnimatorSet();
            ValueAnimator a1 = ValueAnimator.ofFloat(0f, 1f).setDuration(FADE_DURATION);
            ValueAnimator a2 = ValueAnimator.ofFloat(0f, 1f).setDuration(FADE_DURATION);
            a1.addUpdateListener(a -> {
                textAlpha = Math.max(0f, Math.min(1f, (float) a.getAnimatedValue()));
                invalidate();
            });
            a2.addUpdateListener(a -> {
                iconAlpha = Math.max(0f, Math.min(1f, (float) a.getAnimatedValue()));
                invalidate();
            });
            appearAnimator.playTogether(a1, a2);
            appearAnimator.start();
        }
        if (wasGone) {
            // fade + scale 0.9→1 for the appearance
            setAlpha(0f);
            setScaleX(0.9f);
            setScaleY(0.9f);
            animate().alpha(1f).scaleX(1f).scaleY(1f)
                    .setDuration(animated ? 220L : 0L)
                    .setInterpolator(CubicBezierInterpolator.EASE_BOTH)
                    .setListener(null)
                    .start();
        }
    }

    private void setLayoutWidth(int width) {
        if (getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
            getLayoutParams().width = width;
            requestLayout();
        }
    }

    private void startDotsIfDownloading() {
        AndroidUtilities.cancelRunOnUIThread(dotsRunnable);
        if (buttonState == GitHubUpdater.STATE_DOWNLOADING) {
            AndroidUtilities.runOnUIThread(dotsRunnable, DOTS_INTERVAL);
        }
    }

    private void stopAnimations() {
        AndroidUtilities.cancelRunOnUIThread(dotsRunnable);
        if (spinAnimator != null) {
            spinAnimator.cancel();
            spinAnimator = null;
        }
        cancelWidthAnimator();
        if (appearAnimator != null) {
            appearAnimator.cancel();
            appearAnimator = null;
        }
    }

    private void cancelWidthAnimator() {
        if (widthAnimator != null) {
            widthAnimator.cancel();
            widthAnimator = null;
        }
    }

    private void startSpinIfDownloading() {
        if (spinAnimator != null) {
            spinAnimator.cancel();
            spinAnimator = null;
        }
        if (buttonState != GitHubUpdater.STATE_DOWNLOADING) {
            return;
        }
        // each repeat accumulates: the drawable is flipped by another 180°
        final float base = spinBase;
        spinAnimator = ValueAnimator.ofFloat(0f, 180f);
        spinAnimator.setDuration(SPIN_DURATION);
        spinAnimator.setInterpolator(EASE_IN_OUT_BACK);
        spinAnimator.addUpdateListener(a -> {
            iconAngle = base + (float) a.getAnimatedValue();
            invalidate();
        });
        spinAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (spinAnimator != animation || buttonState != GitHubUpdater.STATE_DOWNLOADING) {
                    return;
                }
                spinBase = base + 180f;
                AndroidUtilities.runOnUIThread(() -> {
                    if (buttonState == GitHubUpdater.STATE_DOWNLOADING && isAttachedToWindow()) {
                        startSpinIfDownloading();
                    }
                }, SPIN_PAUSE);
            }
        });
        spinAnimator.start();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        updateColors();
        startDotsIfDownloading();
        startSpinIfDownloading();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stopAnimations();
    }
}
