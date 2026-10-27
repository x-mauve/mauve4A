package app.exteraless.player.api;

import androidx.annotation.NonNull;

public interface PlayerInterceptor {

    /**
     * Runs on the caller's thread, usually the UI thread, so it must not block.
     * Returning true skips the original MediaController logic. In that case the
     * original state is left as is: SharedConfig keeps the old order and repeat
     * mode, and audioProgress isn't updated after a seek.
     *
     * @return true if the call was handled, false to pass it through
     */
    boolean intercept(@NonNull PlayerCall call);
}
