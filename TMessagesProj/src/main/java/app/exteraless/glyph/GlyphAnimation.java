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
    PULSE("pulse", false),
    BLINK("blink", false),
    HEARTBEAT("heartbeat", false),
    FADE("fade", false),
    BREATHE("breathe", true),
    SOLID("solid", true);

    public static final GlyphAnimation DEFAULT_ONE_SHOT = PULSE;
    public static final GlyphAnimation DEFAULT_LASTING = BREATHE;

    private static final List<GlyphAnimation> ONE_SHOT;
    private static final List<GlyphAnimation> LASTING;

    static {
        List<GlyphAnimation> oneShot = new ArrayList<>();
        List<GlyphAnimation> lasting = new ArrayList<>();
        for (GlyphAnimation animation : values()) {
            (animation.lasting ? lasting : oneShot).add(animation);
        }
        ONE_SHOT = Collections.unmodifiableList(oneShot);
        LASTING = Collections.unmodifiableList(lasting);
    }

    /** Stored in the rules JSON. */
    public final String id;
    /** True when the animation runs until stopped: calls and voice recording. */
    public final boolean lasting;

    GlyphAnimation(String id, boolean lasting) {
        this.id = id;
        this.lasting = lasting;
    }

    GlyphEffect effect() {
        switch (this) {
            case BLINK:
                return GlyphEffects.BLINK;
            case HEARTBEAT:
                return GlyphEffects.HEARTBEAT;
            case FADE:
                return GlyphEffects.FADE;
            case BREATHE:
                return GlyphEffects.BREATHE;
            case SOLID:
                return GlyphEffects.SOLID;
            case PULSE:
            default:
                return GlyphEffects.PULSE;
        }
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
