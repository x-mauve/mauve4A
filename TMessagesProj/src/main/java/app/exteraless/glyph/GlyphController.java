package app.exteraless.glyph;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;

import java.util.List;
import java.util.Set;

/**
 * Entry point for Nothing Glyph support. Decides when the glyphs light up and
 * leaves the hardware to a {@link GlyphEngine}.
 */
public final class GlyphController {

    private static volatile GlyphController instance;

    public static GlyphController getInstance() {
        GlyphController local = instance;
        if (local == null) {
            synchronized (GlyphController.class) {
                local = instance;
                if (local == null) {
                    local = instance = new GlyphController();
                }
            }
        }
        return local;
    }

    /** Binding right after a reboot can fail silently, so give the system a moment. */
    private static final long START_DELAY_MS = 3000L;

    /** Minimum gap between message flashes, so a busy chat does not turn into a strobe. */
    private static final long FLASH_COOLDOWN_MS = 2500L;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private boolean initialized;
    private GlyphEngine engine;

    private boolean recordingActive;
    private boolean callActive;
    /** A recording or a call owns the glyphs, so message flashes are skipped. */
    private boolean glyphBusy;
    private long lastFlashAt;

    private final Runnable startRunnable = () -> {
        GlyphEngine local = activeEngine();
        if (local != null) {
            local.start();
        }
    };

    private GlyphController() {
    }

    public boolean isSupported() {
        return GlyphDevice.current().kind != GlyphDevice.Kind.NONE;
    }

    /** Safe to call repeatedly. Does nothing while the feature is off. */
    public synchronized void init() {
        if (initialized || !isSupported() || !GlyphConfig.enabled()) {
            return;
        }
        Context context = ApplicationLoader.applicationContext;
        if (context == null) {
            return;
        }
        if (engine == null) {
            GlyphDevice device = GlyphDevice.current();
            engine = device.kind == GlyphDevice.Kind.MATRIX
                    ? new MatrixGlyphEngine(context, device)
                    : new StripGlyphEngine(context, device);
        }
        initialized = true;
        handler.postDelayed(startRunnable, START_DELAY_MS);
    }

    /** Called when the feature is switched off in settings. */
    public synchronized void shutdown() {
        initialized = false;
        handler.removeCallbacks(startRunnable);
        if (engine != null) {
            engine.stop();
        }
        handler.post(() -> {
            recordingActive = false;
            callActive = false;
            glyphBusy = false;
            lastFlashAt = 0;
        });
    }

    public void onNewMessages(List<MessageObject> messages, int account) {
        if (!GlyphConfig.enabled() || !GlyphConfig.onNewMessage() || messages == null) {
            return;
        }
        // This hook runs before notifications are filtered, so muted chats are
        // skipped here: a flash without a notification would be misleading.
        MessagesController messagesController = MessagesController.getInstance(account);
        boolean hasIncoming = false;
        for (int i = 0; i < messages.size(); i++) {
            MessageObject message = messages.get(i);
            if (message != null && !message.isOutOwner()
                    && !messagesController.isDialogMuted(message.getDialogId())) {
                hasIncoming = true;
                break;
            }
        }
        if (!hasIncoming) {
            return;
        }
        handler.post(() -> {
            GlyphEngine local = readyEngine();
            if (local == null || glyphBusy) {
                return;
            }
            if (GlyphConfig.screenOffOnly() && isScreenOn()) {
                return;
            }
            long now = SystemClock.uptimeMillis();
            if (now - lastFlashAt < FLASH_COOLDOWN_MS) {
                return;
            }
            lastFlashAt = now;
            local.play(allZones(), GlyphEffects.PULSE);
        });
    }

    public void onRecordingStarted() {
        if (!GlyphConfig.enabled() || !GlyphConfig.onRecording()) {
            return;
        }
        handler.post(() -> {
            recordingActive = true;
            updateGlyphActivity();
        });
    }

    public void onRecordingStopped() {
        // No config check: the glyphs must go dark even if the toggle was
        // flipped in the middle of a recording.
        handler.post(() -> {
            recordingActive = false;
            updateGlyphActivity();
        });
    }

    public void onIncomingCallStarted() {
        if (!GlyphConfig.enabled() || !GlyphConfig.onCall()) {
            return;
        }
        handler.post(() -> {
            callActive = true;
            updateGlyphActivity();
        });
    }

    /** The call was answered or declined, or the service died. Safe to call repeatedly. */
    public void onIncomingCallStopped() {
        handler.post(() -> {
            callActive = false;
            updateGlyphActivity();
        });
    }

    /** Preview button in settings. */
    public void preview() {
        handler.post(() -> {
            GlyphEngine local = readyEngine();
            if (local != null) {
                local.play(allZones(), GlyphEffects.PULSE);
            }
        });
    }

    /** Breathing lasts as long as a recording or an incoming call is active. */
    private void updateGlyphActivity() {
        boolean active = recordingActive || callActive;
        if (active == glyphBusy) {
            return;
        }
        if (!active) {
            glyphBusy = false;
            GlyphEngine local = activeEngine();
            if (local != null) {
                local.stopEffect();
            }
            return;
        }
        GlyphEngine local = readyEngine();
        if (local == null) {
            return;
        }
        glyphBusy = true;
        local.play(allZones(), GlyphEffects.BREATHE);
    }

    /** The engine, initializing it first if the feature is on. Null when glyphs are unavailable. */
    private GlyphEngine readyEngine() {
        if (!GlyphConfig.enabled()) {
            return null;
        }
        init();
        return activeEngine();
    }

    private synchronized GlyphEngine activeEngine() {
        return initialized ? engine : null;
    }

    private static Set<String> allZones() {
        return GlyphDevice.current().zoneIds();
    }

    private boolean isScreenOn() {
        Context context = ApplicationLoader.applicationContext;
        if (context == null) {
            return true;
        }
        PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        return powerManager == null || powerManager.isInteractive();
    }
}
