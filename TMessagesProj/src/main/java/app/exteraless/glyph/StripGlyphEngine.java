package app.exteraless.glyph;

import android.content.ComponentName;
import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;

import com.nothing.ketchum.GlyphManager;

import org.telegram.messenger.FileLog;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * LED zones, rendered frame by frame. The SDK's own animate() can only do one
 * shape at full brightness, so every frame is built here and sent raw.
 */
final class StripGlyphEngine implements GlyphEngine {

    private static final long FRAME_MS = 20;

    /**
     * How long the session stays open after an effect. Long enough not to reopen
     * it for every message in a busy chat, short enough to give the glyphs back:
     * an open session blocks the system Essential Notifications indicator.
     */
    private static final long SESSION_IDLE_MS = 3000;

    /** A play request older than this is dropped when the service finally connects. */
    private static final long PENDING_PLAY_MS = 2000;

    /** A bind that has not produced a usable service by then is retried on the next play. */
    private static final long REBIND_AFTER_MS = 5000;

    private final Context context;
    private final GlyphDevice device;
    // setFrameColors is a blocking binder call, so all SDK access lives on this thread.
    private final Handler handler;
    private final int[] frame;

    private GlyphManager manager;
    /** Bumped on every bind and unbind, so callbacks of an old binding are ignored. */
    private int generation;
    private boolean bound;
    private long boundAt;
    private boolean ready;
    private boolean sessionOpen;

    private GlyphEffect effect;
    private int[] effectChannels;
    private long effectStartedAt;

    private Set<String> pendingZoneIds;
    private GlyphEffect pendingEffect;
    private long pendingAt;

    private final Runnable frameRunnable = this::renderFrame;
    private final Runnable closeSessionRunnable = this::closeSession;

    StripGlyphEngine(Context context, GlyphDevice device) {
        this.context = context.getApplicationContext();
        this.device = device;
        this.frame = new int[device.channelCount];
        HandlerThread thread = new HandlerThread("glyph");
        thread.start();
        handler = new Handler(thread.getLooper());
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
        Set<String> zones = zoneIds == null ? new HashSet<>() : new HashSet<>(zoneIds);
        handler.post(() -> playNow(zones, effect));
    }

    @Override
    public void stopEffect() {
        handler.post(() -> {
            clearPending();
            if (effect != null) {
                finishEffect();
            }
        });
    }

    private void bind() {
        if (bound) {
            return;
        }
        try {
            final int bindGeneration = ++generation;
            manager = GlyphManager.getInstance(context);
            manager.init(new GlyphManager.Callback() {
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
        boolean registered = false;
        try {
            registered = manager.register(device.sdkDevice);
        } catch (Throwable t) {
            FileLog.e(t);
        }
        ready = registered;
        if (!registered) {
            FileLog.e("glyph: the service refused to register " + device.sdkDevice);
            return;
        }
        Set<String> zoneIds = pendingZoneIds;
        GlyphEffect pending = pendingEffect;
        boolean fresh = SystemClock.uptimeMillis() - pendingAt < PENDING_PLAY_MS;
        clearPending();
        if (pending != null && fresh) {
            playNow(zoneIds, pending);
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

    private void playNow(Set<String> zoneIds, GlyphEffect newEffect) {
        if (!ready) {
            pendingZoneIds = zoneIds;
            pendingEffect = newEffect;
            pendingAt = SystemClock.uptimeMillis();
            if (bound && pendingAt - boundAt > REBIND_AFTER_MS) {
                releaseBinding();
            }
            bind();
            return;
        }
        handler.removeCallbacks(frameRunnable);
        handler.removeCallbacks(closeSessionRunnable);
        if (!sessionOpen) {
            try {
                manager.openSession();
                sessionOpen = true;
            } catch (Throwable t) {
                FileLog.e(t);
                effect = null;
                return;
            }
        }
        effect = newEffect;
        effectChannels = resolveChannels(zoneIds);
        effectStartedAt = SystemClock.uptimeMillis();
        renderFrame();
    }

    private void renderFrame() {
        GlyphEffect current = effect;
        if (current == null) {
            return;
        }
        long t = SystemClock.uptimeMillis() - effectStartedAt;
        long duration = current.durationMs();
        if (duration >= 0 && t >= duration) {
            finishEffect();
            return;
        }
        Arrays.fill(frame, 0);
        int count = effectChannels.length;
        for (int i = 0; i < count; i++) {
            int value = current.brightness(t, i, count);
            frame[effectChannels[i]] = Math.max(0, Math.min(GlyphEffects.MAX_BRIGHTNESS, value));
        }
        if (!sendFrame()) {
            effect = null;
            handler.postDelayed(closeSessionRunnable, SESSION_IDLE_MS);
            return;
        }
        handler.postDelayed(frameRunnable, FRAME_MS);
    }

    private void finishEffect() {
        effect = null;
        handler.removeCallbacks(frameRunnable);
        if (!sessionOpen) {
            return;
        }
        Arrays.fill(frame, 0);
        sendFrame();
        handler.removeCallbacks(closeSessionRunnable);
        handler.postDelayed(closeSessionRunnable, SESSION_IDLE_MS);
    }

    private boolean sendFrame() {
        try {
            manager.setFrameColors(frame);
            return true;
        } catch (Throwable t) {
            FileLog.e(t);
            return false;
        }
    }

    private void closeSession() {
        if (!sessionOpen) {
            return;
        }
        sessionOpen = false;
        try {
            manager.closeSession();
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    private void unbind() {
        clearPending();
        if (sessionOpen) {
            Arrays.fill(frame, 0);
            sendFrame();
        }
        handler.removeCallbacks(closeSessionRunnable);
        closeSession();
        releaseBinding();
    }

    /** Forgets the current binding and everything that depended on it. */
    private void releaseBinding() {
        generation++;
        effect = null;
        handler.removeCallbacks(frameRunnable);
        handler.removeCallbacks(closeSessionRunnable);
        sessionOpen = false;
        ready = false;
        if (bound) {
            bound = false;
            try {
                manager.unInit();
            } catch (Throwable t) {
                FileLog.e(t);
            }
        }
    }

    private void clearPending() {
        pendingZoneIds = null;
        pendingEffect = null;
    }

    /** Frame indices of the requested zones in layout order. No known zone means all of them. */
    private int[] resolveChannels(Set<String> zoneIds) {
        boolean any = false;
        for (GlyphZone zone : device.zones) {
            if (zoneIds.contains(zone.id)) {
                any = true;
                break;
            }
        }
        int[] channels = new int[device.channelCount];
        int count = 0;
        for (GlyphZone zone : device.zones) {
            if (any && !zoneIds.contains(zone.id)) {
                continue;
            }
            for (int i = 0; i < zone.channelCount() && count < channels.length; i++) {
                int channel = zone.channel(i);
                // The indices come from SDK constants; never let a bad one overrun the frame.
                if (channel >= 0 && channel < frame.length) {
                    channels[count++] = channel;
                }
            }
        }
        return Arrays.copyOf(channels, count);
    }
}
