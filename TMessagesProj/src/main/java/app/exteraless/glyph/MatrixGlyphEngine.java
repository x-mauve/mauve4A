package app.exteraless.glyph;

import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import com.nothing.ketchum.GlyphMatrixFrame;
import com.nothing.ketchum.GlyphMatrixManager;
import com.nothing.ketchum.GlyphMatrixObject;
import com.nothing.ketchum.GlyphMatrixUtils;

import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;

import java.util.Set;

/**
 * Glyph Matrix: shows the app logo. There are no zones to pick, so an effect
 * only decides between a timed logo and a breathing one.
 */
final class MatrixGlyphEngine implements GlyphEngine {

    /** How long the logo stays up for a finite effect. */
    private static final long LOGO_SHOW_MS = 3000L;

    /** Brightness uses the SDK's 0..255 scale. */
    private static final long BREATH_STEP_MS = 120L;
    private static final int BREATH_STEP = 10;
    private static final int BREATH_MIN = 12;
    private static final int BREATH_MAX = 255;

    /** Stops the breathing after about ten minutes in case the stop event never comes. */
    private static final int MAX_BREATH_STEPS = 5000;

    /** A play request older than this is dropped when the service finally connects. */
    private static final long PENDING_PLAY_MS = 2000;

    /** A bind that has not produced a usable service by then is retried on the next play. */
    private static final long REBIND_AFTER_MS = 5000;

    private final Context context;
    private final GlyphDevice device;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private GlyphMatrixManager manager;
    /** Bumped on every bind and unbind, so callbacks of an old binding are ignored. */
    private int generation;
    private boolean bound;
    private long boundAt;
    private boolean registered;

    /** Rendering the vector on every breathing step would be wasteful. */
    private Bitmap logoBitmap;

    private boolean breathing;
    private int breathBrightness = BREATH_MAX;
    private int breathDirection = -1;
    private int breathSteps;

    private GlyphEffect pendingEffect;
    private long pendingAt;

    private final Runnable hideLogoRunnable = this::hideLogo;

    private final Runnable breathRunnable = new Runnable() {
        @Override
        public void run() {
            if (!breathing || !registered) {
                return;
            }
            if (breathSteps++ > MAX_BREATH_STEPS) {
                stopBreathing();
                return;
            }
            breathBrightness += breathDirection * BREATH_STEP;
            if (breathBrightness <= BREATH_MIN) {
                breathBrightness = BREATH_MIN;
                breathDirection = 1;
            } else if (breathBrightness >= BREATH_MAX) {
                breathBrightness = BREATH_MAX;
                breathDirection = -1;
            }
            showLogo(breathBrightness);
            handler.postDelayed(this, BREATH_STEP_MS);
        }
    };

    MatrixGlyphEngine(Context context, GlyphDevice device) {
        this.context = context.getApplicationContext();
        this.device = device;
    }

    @Override
    public void start() {
        handler.post(this::bind);
    }

    @Override
    public void stop() {
        handler.post(this::unbind);
    }

    @Override
    public void play(Set<String> zoneIds, GlyphEffect effect) {
        if (effect == null) {
            return;
        }
        handler.post(() -> playNow(effect));
    }

    @Override
    public void stopEffect() {
        handler.post(() -> {
            pendingEffect = null;
            stopBreathing();
        });
    }

    private void bind() {
        if (bound) {
            return;
        }
        try {
            final int bindGeneration = ++generation;
            manager = GlyphMatrixManager.getInstance(context);
            manager.init(new GlyphMatrixManager.Callback() {
                @Override
                public void onServiceConnected(ComponentName componentName) {
                    handler.post(() -> onConnected(bindGeneration));
                }

                @Override
                public void onServiceDisconnected(ComponentName componentName) {
                    handler.post(() -> onDisconnected(bindGeneration));
                }
            });
            bound = true;
            boundAt = SystemClock.uptimeMillis();
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    private void onConnected(int bindGeneration) {
        if (bindGeneration != generation) {
            return;
        }
        try {
            registered = manager.register(device.sdkDevice);
        } catch (Throwable t) {
            registered = false;
            FileLog.e(t);
        }
        if (!registered) {
            FileLog.e("glyph: the service refused to register " + device.sdkDevice);
            return;
        }
        GlyphEffect pending = pendingEffect;
        boolean fresh = SystemClock.uptimeMillis() - pendingAt < PENDING_PLAY_MS;
        pendingEffect = null;
        if (pending != null && fresh) {
            playNow(pending);
        }
    }

    private void onDisconnected(int bindGeneration) {
        if (bindGeneration != generation) {
            return;
        }
        // The SDK drops its service reference and never reconnects, so let go of
        // the binding too. The next play() binds again.
        releaseBinding();
    }

    private void playNow(GlyphEffect effect) {
        if (!registered) {
            pendingEffect = effect;
            pendingAt = SystemClock.uptimeMillis();
            if (bound && pendingAt - boundAt > REBIND_AFTER_MS) {
                releaseBinding();
            }
            bind();
            return;
        }
        handler.removeCallbacks(breathRunnable);
        handler.removeCallbacks(hideLogoRunnable);
        if (effect.durationMs() >= 0) {
            breathing = false;
            showLogo(BREATH_MAX);
            handler.postDelayed(hideLogoRunnable, LOGO_SHOW_MS);
        } else {
            breathing = true;
            breathSteps = 0;
            breathBrightness = BREATH_MAX;
            breathDirection = -1;
            handler.post(breathRunnable);
        }
    }

    private void stopBreathing() {
        breathing = false;
        handler.removeCallbacks(breathRunnable);
        handler.removeCallbacks(hideLogoRunnable);
        hideLogo();
    }

    private void showLogo(int brightness) {
        if (!registered) {
            return;
        }
        Bitmap bitmap = getLogoBitmap();
        if (bitmap == null) {
            return;
        }
        try {
            GlyphMatrixObject object = new GlyphMatrixObject.Builder()
                    .setImageSource(bitmap)
                    // The mark sits in the middle of the icon canvas; scaling up crops to it.
                    .setScale(150)
                    .setBrightness(brightness)
                    .build();
            GlyphMatrixFrame frame = new GlyphMatrixFrame.Builder()
                    .addTop(object)
                    .build(context);
            // The app layer sits below Glyph Toys, so the two never fight over the matrix.
            manager.setAppMatrixFrame(frame.render());
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    private Bitmap getLogoBitmap() {
        if (logoBitmap != null) {
            return logoBitmap;
        }
        try {
            Drawable icon = context.getDrawable(R.drawable.exteraless_icon_monochrome);
            if (icon == null) {
                return null;
            }
            // The SDK maps pixel lightness to LED brightness, so the black
            // monochrome mark has to be tinted white to show up at all.
            icon = icon.mutate();
            icon.setTint(Color.WHITE);
            logoBitmap = GlyphMatrixUtils.drawableToBitmap(icon);
        } catch (Throwable t) {
            FileLog.e(t);
        }
        return logoBitmap;
    }

    private void hideLogo() {
        if (!registered) {
            return;
        }
        try {
            manager.closeAppMatrix();
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    private void unbind() {
        pendingEffect = null;
        hideLogo();
        releaseBinding();
    }

    /** Forgets the current binding and everything that depended on it. */
    private void releaseBinding() {
        generation++;
        breathing = false;
        handler.removeCallbacks(breathRunnable);
        handler.removeCallbacks(hideLogoRunnable);
        registered = false;
        if (bound) {
            bound = false;
            try {
                manager.unInit();
            } catch (Throwable t) {
                FileLog.e(t);
            }
        }
    }
}
