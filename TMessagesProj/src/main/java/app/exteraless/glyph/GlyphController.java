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

    /** How long a preview of a call or recording rule keeps breathing. */
    private static final long PREVIEW_BREATHE_MS = 3000L;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private boolean initialized;
    private GlyphEngine engine;

    private boolean recordingActive;
    private boolean callActive;
    private long callDialogId;
    /** The call or recording the glyphs are breathing for, null when there is none. */
    private GlyphEvent activeEvent;
    private long lastFlashAt;
    private Set<String> lastFlashZones;

    private final Runnable startRunnable = () -> {
        GlyphEngine local = activeEngine();
        if (local != null) {
            local.start();
        }
    };

    private final Runnable previewStopRunnable = () -> {
        // A real call or recording that started meanwhile owns the glyphs now.
        if (callActive || recordingActive) {
            return;
        }
        GlyphEngine local = activeEngine();
        if (local != null) {
            local.stopEffect();
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
        handler.removeCallbacks(previewStopRunnable);
        if (engine != null) {
            engine.stop();
        }
        handler.post(() -> {
            recordingActive = false;
            callActive = false;
            callDialogId = 0;
            activeEvent = null;
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
        if (!GlyphConfig.enabled()) {
            return;
        }
        handler.post(() -> {
            GlyphEngine local = readyEngine();
            if (local == null) {
                return;
            }
            if (GlyphConfig.screenOffOnly() && isScreenOn()) {
                return;
            }
            // A call or a recording outranks a message and keeps the glyphs.
            if (callActive || recordingActive) {
                return;
            }
            Set<String> zones = resolveZones(GlyphEvent.MESSAGE, dialogId);
            if (zones == null) {
                return;
            }
            long now = SystemClock.uptimeMillis();
            if (zones.equals(lastFlashZones) && now - lastFlashAt < RETRIGGER_GUARD_MS) {
                return;
            }
            lastFlashAt = now;
            lastFlashZones = zones;
            // A preview that is still breathing must not cut this flash short.
            handler.removeCallbacks(previewStopRunnable);
            local.play(zones, GlyphEffects.PULSE);
        });
    }

    public void onRecordingStarted() {
        if (!GlyphConfig.enabled()) {
            return;
        }
        handler.post(() -> {
            // A disabled rule means the event is not tracked at all, so it
            // neither breathes nor holds back message flashes.
            if (!GlyphRules.resolve(GlyphEvent.RECORDING, 0).enabled) {
                return;
            }
            recordingActive = true;
            updateActiveEvent();
        });
    }

    public void onRecordingStopped() {
        // No config check: the glyphs must go dark even if the toggle was
        // flipped in the middle of a recording.
        handler.post(() -> {
            recordingActive = false;
            updateActiveEvent();
        });
    }

    public void onIncomingCallStarted(long dialogId, int account) {
        if (!GlyphConfig.enabled()) {
            return;
        }
        handler.post(() -> {
            if (!GlyphRules.resolve(GlyphEvent.CALL, dialogId).enabled) {
                return;
            }
            callActive = true;
            callDialogId = dialogId;
            updateActiveEvent();
        });
    }

    /** The call was answered or declined, or the service died. Safe to call repeatedly. */
    public void onIncomingCallStopped() {
        handler.post(() -> {
            callActive = false;
            callDialogId = 0;
            updateActiveEvent();
        });
    }

    /**
     * Plays a rule from the settings the way {@code event} would show it. An
     * empty set stands for every zone. The screen-off mode, the retrigger
     * guard and the rule's own switch do not apply, but a real call or
     * recording keeps the glyphs.
     */
    public void preview(Set<String> zones, GlyphEvent event) {
        handler.post(() -> {
            if (callActive || recordingActive) {
                return;
            }
            GlyphEngine local = readyEngine();
            if (local == null) {
                return;
            }
            handler.removeCallbacks(previewStopRunnable);
            Set<String> target = zones == null || zones.isEmpty() ? allZones() : zones;
            if (event == GlyphEvent.MESSAGE) {
                local.play(target, GlyphEffects.PULSE);
            } else {
                local.play(target, GlyphEffects.BREATHE);
                handler.postDelayed(previewStopRunnable, PREVIEW_BREATHE_MS);
            }
        });
    }

    /**
     * Breathing follows the most important ongoing event: a call outranks a
     * recording. It restarts whenever that event changes, so the zones are
     * always the ones of the event in charge.
     */
    private void updateActiveEvent() {
        GlyphEvent event = callActive ? GlyphEvent.CALL
                : recordingActive ? GlyphEvent.RECORDING : null;
        if (event == activeEvent) {
            return;
        }
        activeEvent = event;
        handler.removeCallbacks(previewStopRunnable);
        if (event == null) {
            GlyphEngine local = activeEngine();
            if (local != null) {
                local.stopEffect();
            }
            return;
        }
        GlyphEngine local = readyEngine();
        if (local != null) {
            long dialogId = event == GlyphEvent.CALL ? callDialogId : 0;
            Set<String> zones = resolveZones(event, dialogId);
            local.play(zones != null ? zones : allZones(), GlyphEffects.BREATHE);
        }
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

    /**
     * Zones to light for an event, or null when its rule is turned off.
     * {@code dialogId} is 0 when the event has no chat.
     */
    private Set<String> resolveZones(GlyphEvent event, long dialogId) {
        GlyphRule rule = GlyphRules.resolve(event, dialogId);
        if (!rule.enabled) {
            return null;
        }
        return rule.zones.isEmpty() ? allZones() : rule.zones;
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
