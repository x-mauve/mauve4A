package app.exteraless.glyph;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** Which zones light up for an event type or a chat. */
public final class GlyphRule {

    public static final GlyphRule DEFAULT = new GlyphRule(true, Collections.emptySet());

    /** A disabled rule keeps the glyphs dark. */
    public final boolean enabled;
    /** Zone ids. An empty set stands for every zone of the device. */
    public final Set<String> zones;

    public GlyphRule(boolean enabled, Set<String> zones) {
        this.enabled = enabled;
        this.zones = zones == null || zones.isEmpty()
                ? Collections.emptySet()
                : Collections.unmodifiableSet(new LinkedHashSet<>(zones));
    }
}
