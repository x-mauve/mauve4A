package app.exteraless.player.api;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.MessageObject;

/**
 * A playback control call made to {@link org.telegram.messenger.MediaController}.
 * Unused fields are -1.
 */
public final class PlayerCall {

    @NonNull
    public final PlayerAction action;
    /** Target message for PLAY, PAUSE and SEEK, null otherwise. */
    @Nullable
    public final MessageObject message;
    /** Seek target as a 0..1 fraction. */
    public final float progress;
    /** Seek target in milliseconds. */
    public final long positionMs;
    /** Order type for SET_ORDER, repeat mode for SET_REPEAT. */
    public final int mode;

    private PlayerCall(@NonNull PlayerAction action, @Nullable MessageObject message, float progress, long positionMs, int mode) {
        this.action = action;
        this.message = message;
        this.progress = progress;
        this.positionMs = positionMs;
        this.mode = mode;
    }

    /**
     * PLAY, PAUSE and SEEK also go through for voice messages, round videos and
     * PhotoViewer videos. Interceptors should usually pass anything that isn't music.
     */
    public boolean isMusic() {
        return message != null && message.isMusic();
    }

    public static PlayerCall play(MessageObject message) {
        return new PlayerCall(PlayerAction.PLAY, message, -1, -1, -1);
    }

    public static PlayerCall pause(MessageObject message) {
        return new PlayerCall(PlayerAction.PAUSE, message, -1, -1, -1);
    }

    public static PlayerCall next() {
        return new PlayerCall(PlayerAction.NEXT, null, -1, -1, -1);
    }

    public static PlayerCall previous() {
        return new PlayerCall(PlayerAction.PREVIOUS, null, -1, -1, -1);
    }

    public static PlayerCall seek(MessageObject message, float progress) {
        return new PlayerCall(PlayerAction.SEEK, message, progress, -1, -1);
    }

    public static PlayerCall seekMs(MessageObject message, long positionMs) {
        return new PlayerCall(PlayerAction.SEEK, message, -1, positionMs, -1);
    }

    public static PlayerCall setOrder(int type) {
        return new PlayerCall(PlayerAction.SET_ORDER, null, -1, -1, type);
    }

    public static PlayerCall setRepeat(int mode) {
        return new PlayerCall(PlayerAction.SET_REPEAT, null, -1, -1, mode);
    }

    @NonNull
    @Override
    public String toString() {
        return "PlayerCall{" + action
                + (message != null ? ", msg=" + message.getId() + (isMusic() ? " music" : "") : "")
                + (progress >= 0 ? ", progress=" + progress : "")
                + (positionMs >= 0 ? ", ms=" + positionMs : "")
                + (mode >= 0 ? ", mode=" + mode : "")
                + "}";
    }
}
