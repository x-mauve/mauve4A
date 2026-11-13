package app.exteraless.glyph;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;

import org.telegram.messenger.ApplicationLoader;

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

    /**
     * A batch of messages is reported one by one within a few milliseconds.
     * Restarting the same flash for each of them would only make it stutter.
     */
    private static final long RETRIGGER_GUARD_MS = 300L;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private boolean initialized;
    private GlyphEngine engine;

    private boolean recordingActive;
    private boolean callActive;
    private long callDialogId;
    /** A recording or a call owns the glyphs, so message flashes are skipped. */
    private boolean glyphBusy;
    private long lastFlashAt;
    private Set<String> lastFlashZones;

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
            callDialogId = 0;
            glyphBusy = false;
            lastFlashAt = 0;
            lastFlashZones = null;
        });
    }

    /**
     * A message the client is about to notify about. Muted chats, the chat
     * that is open right now and filtered messages never get here.
     */
    public void onMessageNotified(long dialogId, int account) {
        // Called on the notifications queue for every message, so bail out
        // early and decide on the controller thread.
        if (!GlyphConfig.enabled() || !GlyphConfig.onNewMessage()) {
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
            Set<String> zones = resolveZones(GlyphEvent.MESSAGE, dialogId);
            long now = SystemClock.uptimeMillis();
            if (zones.equals(lastFlashZones) && now - lastFlashAt < RETRIGGER_GUARD_MS) {
                return;
            }
            lastFlashAt = now;
            lastFlashZones = zones;
            local.play(zones, GlyphEffects.PULSE);
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

    public void onIncomingCallStarted(long dialogId, int account) {
        if (!GlyphConfig.enabled() || !GlyphConfig.onCall()) {
            return;
        }
        handler.post(() -> {
            callActive = true;
            callDialogId = dialogId;
            updateGlyphActivity();
        });
    }

    /** The call was answered or declined, or the service died. Safe to call repeatedly. */
    public void onIncomingCallStopped() {
        handler.post(() -> {
            callActive = false;
            callDialogId = 0;
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
        Set<String> zones = callActive
                ? resolveZones(GlyphEvent.CALL, callDialogId)
                : resolveZones(GlyphEvent.RECORDING, 0);
        local.play(zones, GlyphEffects.BREATHE);
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

    /** Zones to light for an event. {@code dialogId} is 0 when the event has no chat. */
    private Set<String> resolveZones(GlyphEvent event, long dialogId) {
        return allZones();
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
