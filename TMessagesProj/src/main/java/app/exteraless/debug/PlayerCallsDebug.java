package app.exteraless.debug;

import androidx.annotation.NonNull;

import org.telegram.messenger.FileLog;

import app.exteraless.player.api.PlayerCall;
import app.exteraless.player.api.PlayerHooks;
import app.exteraless.player.api.PlayerInterceptor;

/** Debug interceptor for checking that every playback path goes through the Player API. */
public final class PlayerCallsDebug implements PlayerInterceptor {

    public static final int OFF = 0;
    public static final int LOG = 1;
    public static final int BLOCK = 2;

    private static final PlayerCallsDebug INSTANCE = new PlayerCallsDebug();
    private static volatile int mode = OFF;

    private PlayerCallsDebug() {
    }

    public static int getMode() {
        return mode;
    }

    public static void setMode(int newMode) {
        mode = newMode;
        if (newMode == OFF) {
            PlayerHooks.unregister(INSTANCE);
        } else {
            PlayerHooks.register(INSTANCE);
        }
    }

    @Override
    public boolean intercept(@NonNull PlayerCall call) {
        // Only music is blocked so voice messages and videos keep working.
        boolean block = mode == BLOCK && (call.message == null || call.isMusic());
        FileLog.d("PlayerApi: " + call + (block ? " blocked" : ""));
        return block;
    }
}
