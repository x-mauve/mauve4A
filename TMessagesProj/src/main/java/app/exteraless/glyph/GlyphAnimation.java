package app.exteraless.glyph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The animation a rule plays. Rules store the id, so ids must never change.
 * Free of Android types to stay unit-testable; the settings screen maps the
 * names to strings.
 */
public enum GlyphAnimation {
    PULSE("pulse", GlyphEffects.PULSE, null),
    BLINK("blink", GlyphEffects.BLINK, null),
    HEARTBEAT("heartbeat", GlyphEffects.HEARTBEAT, null),
    FADE("fade", GlyphEffects.FADE, null),
    BREATHE("breathe", null, GlyphEffects.BREATHE),
    SOLID("solid", null, GlyphEffects.SOLID),
    WAVE("wave", GlyphEffects.WAVE, GlyphEffects.WAVE_LOOP),
    CHASE("chase", GlyphEffects.CHASE, GlyphEffects.CHASE_LOOP),
    PING_PONG("pingpong", GlyphEffects.PING_PONG, GlyphEffects.PING_PONG_LOOP),
    SPARKLE("sparkle", GlyphEffects.SPARKLE, GlyphEffects.SPARKLE_LOOP),
    VOICE("voice", null, GlyphEffects.VOICE, true);

    public static final GlyphAnimation DEFAULT_ONE_SHOT = PULSE;
    public static final GlyphAnimation DEFAULT_LASTING = BREATHE;

    private static final List<GlyphAnimation> ONE_SHOT;
    private static final Map<GlyphEvent, List<GlyphAnimation>> LASTING;

    static {
        List<GlyphAnimation> oneShot = new ArrayList<>();
        List<GlyphAnimation> lasting = new ArrayList<>();
        for (GlyphAnimation animation : values()) {
            if (animation.supportsOneShot()) {
                oneShot.add(animation);
            }
            if (animation.supportsLasting()) {
                lasting.add(animation);
            }
        }
        ONE_SHOT = Collections.unmodifiableList(oneShot);
        Map<GlyphEvent, List<GlyphAnimation>> byEvent = new EnumMap<>(GlyphEvent.class);
        for (GlyphEvent event : GlyphEvent.values()) {
            List<GlyphAnimation> list = new ArrayList<>();
            for (GlyphAnimation animation : lasting) {
                if (animation.fits(event)) {
                    list.add(animation);
                }
            }
            byEvent.put(event, Collections.unmodifiableList(list));
        }
        LASTING = Collections.unmodifiableMap(byEvent);
    }

    /** Stored in the rules JSON. */
    public final String id;
    private final GlyphEffect oneShotEffect;
    private final GlyphEffect lastingEffect;
    /** Voice only makes sense while the microphone records, never during a call. */
    private final boolean recordingOnly;

    GlyphAnimation(String id, GlyphEffect oneShotEffect, GlyphEffect lastingEffect) {
        this(id, oneShotEffect, lastingEffect, false);
    }

    GlyphAnimation(String id, GlyphEffect oneShotEffect, GlyphEffect lastingEffect,
                   boolean recordingOnly) {
        this.id = id;
        this.oneShotEffect = oneShotEffect;
        this.lastingEffect = lastingEffect;
        this.recordingOnly = recordingOnly;
    }

    /** True when the animation fits one-off events such as messages. */
    public boolean supportsOneShot() {
        return oneShotEffect != null;
    }

    /** True when the animation fits events that last, such as calls and voice recording. */
    public boolean supportsLasting() {
        return lastingEffect != null;
    }

    /** True when the animation may play for this event. */
    public boolean fits(GlyphEvent event) {
        switch (event) {
            case MESSAGE:
                return supportsOneShot();
            case CALL:
                return supportsLasting() && !recordingOnly;
            case RECORDING:
            default:
                return supportsLasting();
        }
    }

    /** The one-shot effect, null when the animation does not fit one-off events. */
    GlyphEffect oneShotEffect() {
        return oneShotEffect;
    }

    /** The lasting effect, null when the animation does not fit events that last. */
    GlyphEffect lastingEffect() {
        return lastingEffect;
    }

    /** What the editor plays for this lasting animation: a stand-in for Voice. */
    GlyphEffect previewLastingEffect() {
        return this == VOICE ? GlyphEffects.VOICE_PREVIEW : lastingEffect;
    }

    /** Null for an unknown or missing id. */
    public static GlyphAnimation fromId(String id) {
        if (id != null) {
            for (GlyphAnimation animation : values()) {
                if (animation.id.equals(id)) {
                    return animation;
                }
            }
        }
        return null;
    }

    /** Animations for one-off events such as messages. */
    public static List<GlyphAnimation> oneShot() {
        return ONE_SHOT;
    }

    /** Animations that fit events that last, filtered for the event in charge. */
    public static List<GlyphAnimation> lasting(GlyphEvent event) {
        return LASTING.get(event);
    }
}
