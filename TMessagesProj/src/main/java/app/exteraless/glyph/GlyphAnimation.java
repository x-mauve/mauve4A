package app.exteraless.glyph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
    CHASE("chase", GlyphEffects.CHASE, GlyphEffects.CHASE_LOOP);

    public static final GlyphAnimation DEFAULT_ONE_SHOT = PULSE;
    public static final GlyphAnimation DEFAULT_LASTING = BREATHE;

    private static final List<GlyphAnimation> ONE_SHOT;
    private static final List<GlyphAnimation> LASTING;

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
        LASTING = Collections.unmodifiableList(lasting);
    }

    /** Stored in the rules JSON. */
    public final String id;
    private final GlyphEffect oneShotEffect;
    private final GlyphEffect lastingEffect;

    GlyphAnimation(String id, GlyphEffect oneShotEffect, GlyphEffect lastingEffect) {
        this.id = id;
        this.oneShotEffect = oneShotEffect;
        this.lastingEffect = lastingEffect;
    }

    /** True when the animation fits one-off events such as messages. */
    public boolean supportsOneShot() {
        return oneShotEffect != null;
    }

    /** True when the animation fits events that last, such as calls and voice recording. */
    public boolean supportsLasting() {
        return lastingEffect != null;
    }

    /** The one-shot effect, null when the animation does not fit one-off events. */
    GlyphEffect oneShotEffect() {
        return oneShotEffect;
    }

    /** The lasting effect, null when the animation does not fit events that last. */
    GlyphEffect lastingEffect() {
        return lastingEffect;
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

    /** Animations for events that last, such as calls and voice recording. */
    public static List<GlyphAnimation> lasting() {
        return LASTING;
    }
}
