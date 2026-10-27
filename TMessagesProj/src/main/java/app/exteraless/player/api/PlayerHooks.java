package app.exteraless.player.api;

import androidx.annotation.NonNull;

import org.telegram.messenger.FileLog;

import java.util.concurrent.CopyOnWriteArrayList;

import xyz.nextalone.nagram.NaConfig;

/**
 * Entry point for the hooks in MediaController and SharedConfig. With the
 * Experimental Features toggle off or no interceptors registered, the player
 * behaves exactly like upstream.
 */
public final class PlayerHooks {

    private static final CopyOnWriteArrayList<PlayerInterceptor> interceptors = new CopyOnWriteArrayList<>();
    // Lets an interceptor call MediaController itself without looping back into the hooks.
    private static final ThreadLocal<Boolean> dispatching = new ThreadLocal<>();

    private PlayerHooks() {
    }

    public static void register(@NonNull PlayerInterceptor interceptor) {
        interceptors.addIfAbsent(interceptor);
    }

    public static void unregister(@NonNull PlayerInterceptor interceptor) {
        interceptors.remove(interceptor);
    }

    /** Cheap check so hooks don't allocate a PlayerCall when nothing listens. */
    public static boolean isActive() {
        return !interceptors.isEmpty() && NaConfig.INSTANCE.getEnablePlayerApi().Bool();
    }

    /** @return true if an interceptor handled the call and the original logic must be skipped */
    public static boolean dispatch(@NonNull PlayerCall call) {
        if (dispatching.get() != null) {
            return false;
        }
        dispatching.set(Boolean.TRUE);
        try {
            for (PlayerInterceptor interceptor : interceptors) {
                try {
                    if (interceptor.intercept(call)) {
                        return true;
                    }
                } catch (Throwable e) {
                    // A broken interceptor must never break playback.
                    FileLog.e(e);
                }
            }
            return false;
        } finally {
            dispatching.remove();
        }
    }
}
