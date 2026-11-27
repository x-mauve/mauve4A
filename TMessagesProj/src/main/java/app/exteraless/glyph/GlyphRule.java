package app.exteraless.glyph;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** Which zones light up for an event type or a chat, and how they animate. */
public final class GlyphRule {

    public static final GlyphRule DEFAULT = new GlyphRule(true, Collections.emptySet());

    /** A disabled rule keeps the glyphs dark. */
    public final boolean enabled;
    /** Zone ids. An empty set stands for every zone of the device. */
    public final Set<String> zones;
    /** One-shot animation for messages, null for the default. */
    public final GlyphAnimation messageAnimation;
    /** Lasting animation for calls and voice recording, null for the default. */
    public final GlyphAnimation lastingAnimation;
    /** How bright the rule lights the glyphs, null for the default. */
    public final GlyphBrightness brightness;

    public GlyphRule(boolean enabled, Set<String> zones) {
        this(enabled, zones, null, null);
    }

    public GlyphRule(boolean enabled, Set<String> zones,
                     GlyphAnimation messageAnimation, GlyphAnimation lastingAnimation) {
        this(enabled, zones, messageAnimation, lastingAnimation, null);
    }

    public GlyphRule(boolean enabled, Set<String> zones, GlyphAnimation messageAnimation,
                     GlyphAnimation lastingAnimation, GlyphBrightness brightness) {
        this.enabled = enabled;
        this.zones = zones == null || zones.isEmpty()
                ? Collections.emptySet()
                : Collections.unmodifiableSet(new LinkedHashSet<>(zones));
        this.messageAnimation = messageAnimation;
        this.lastingAnimation = lastingAnimation;
        this.brightness = brightness;
    }

    /** The message animation, or the default when none is stored or the stored one only lasts. */
    public GlyphAnimation messageAnimationOrDefault() {
        return messageAnimation == null || !messageAnimation.supportsOneShot()
                ? GlyphAnimation.DEFAULT_ONE_SHOT
                : messageAnimation;
    }

    /** The lasting animation when it fits the event, or the default otherwise. */
    public GlyphAnimation lastingAnimationOrDefault(GlyphEvent event) {
        return lastingAnimation == null || !lastingAnimation.fits(event)
                ? GlyphAnimation.DEFAULT_LASTING
                : lastingAnimation;
    }

    /** The stored level, or the default when none is stored or the stored one is unknown. */
    public GlyphBrightness brightnessOrDefault() {
        return brightness == null ? GlyphBrightness.DEFAULT : brightness;
    }
}
