package app.exteraless.glyph;

import android.content.ComponentName;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import com.nothing.ketchum.Common;
import com.nothing.ketchum.Glyph;
import com.nothing.ketchum.GlyphMatrixManager;

import org.telegram.messenger.FileLog;

import java.util.Set;

/**
 * Glyph Matrix: shows the app logo. There are no zones to pick, so an effect
 * only decides between a timed logo and a breathing one.
 */
final class MatrixGlyphEngine implements GlyphEngine {

    /** How long the logo stays up for a finite effect. */
    private static final long LOGO_SHOW_MS = 3000L;

    /** Logo brightness, 0..255. */
    private static final long BREATH_STEP_MS = 120L;
    private static final int BREATH_STEP = 10;
    private static final int BREATH_MIN = 12;
    private static final int BREATH_MAX = 255;

    /** Stops the breathing after about ten minutes in case the stop event never comes. */
    private static final int MAX_BREATH_STEPS = 5000;

    private static final int MAX_LED = 4095;

    /** The left chevron is drawn at 55% opacity in the app icon. */
    private static final int LOGO_DIM_PERCENT = 55;

    private static final byte LEVEL_OFF = 0;
    private static final byte LEVEL_DIM = 1;
    private static final byte LEVEL_BRIGHT = 2;

    // One string per matrix row: '.' off, '-' dim chevron, '#' bright chevron.
    // Hand-drawn for each matrix size; scaling the vector icon smears the strokes.

    /** Phone (3). */
    private static final String[] LOGO_25 = {
            ".........................",
            ".........................",
            ".........................",
            ".........................",
            ".........................",
            "...........---...###.....",
            "..........----..####.....",
            ".........----..####......",
            "........----..####.......",
            ".......----..####........",
            "......----..####.........",
            ".....----..####..........",
            ".....---...###...........",
            ".....----..####..........",
            "......----..####.........",
            ".......----..####........",
            "........----..####.......",
            ".........----..####......",
            "..........----..####.....",
            "...........---...###.....",
            ".........................",
            ".........................",
            ".........................",
            ".........................",
            "........................."
    };

    /** Phone (4a) Pro. */
    private static final String[] LOGO_13 = {
            ".............",
            ".............",
            ".....--..##..",
            "....--..##...",
            "...--..##....",
            "..--..##.....",
            ".--..##......",
            "..--..##.....",
            "...--..##....",
            "....--..##...",
            ".....--..##..",
            ".............",
            "............."
    };

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

    /** LEVEL_* per LED, row by row. Null when the matrix size is unknown. */
    private final byte[] logoLevels;
    private final int[] logoFrame;

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
        logoLevels = loadLogo();
        logoFrame = logoLevels == null ? null : new int[logoLevels.length];
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
        if (!registered || logoLevels == null) {
            return;
        }
        int bright = MAX_LED * Math.max(0, Math.min(BREATH_MAX, brightness)) / BREATH_MAX;
        int dim = bright * LOGO_DIM_PERCENT / 100;
        for (int i = 0; i < logoLevels.length; i++) {
            byte level = logoLevels[i];
            logoFrame[i] = level == LEVEL_BRIGHT ? bright : level == LEVEL_DIM ? dim : 0;
        }
        try {
            // The app layer sits below Glyph Toys, so the two never fight over the matrix.
            manager.setAppMatrixFrame(logoFrame);
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    private static byte[] loadLogo() {
        int side = 0;
        try {
            side = Common.getDeviceMatrixLength();
        } catch (Throwable t) {
            FileLog.e(t);
        }
        String[] rows;
        if (side == Glyph.DEVICE_23112_MATRIX_LENGTH) {
            rows = LOGO_25;
        } else if (side == Glyph.DEVICE_25111p_MATRIX_LENGTH) {
            rows = LOGO_13;
        } else {
            FileLog.e("glyph: no logo for a matrix with side " + side);
            return null;
        }
        if (rows.length != side) {
            FileLog.e("glyph: the logo does not fit a matrix with side " + side);
            return null;
        }
        byte[] levels = new byte[side * side];
        for (int row = 0; row < side; row++) {
            String line = rows[row];
            if (line.length() != side) {
                FileLog.e("glyph: the logo does not fit a matrix with side " + side);
                return null;
            }
            for (int col = 0; col < side; col++) {
                char c = line.charAt(col);
                levels[row * side + col] = c == '#' ? LEVEL_BRIGHT : c == '-' ? LEVEL_DIM : LEVEL_OFF;
            }
        }
        return levels;
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
