package app.exteraless.glyph;

/** How bright a rule lights the glyphs. Rules store the id, so ids must never change. */
public enum GlyphBrightness {
    LOW("low", 15), MEDIUM("medium", 45), HIGH("high", 100);

    public static final GlyphBrightness DEFAULT = HIGH;

    /** Stored in the rules JSON. */
    public final String id;
    private final int percent;

    GlyphBrightness(String id, int percent) {
        this.id = id;
        this.percent = percent;
    }

    /** Null for an unknown or missing id. */
    public static GlyphBrightness fromId(String id) {
        if (id != null) {
            for (GlyphBrightness brightness : values()) {
                if (brightness.id.equals(id)) {
                    return brightness;
                }
            }
        }
        return null;
    }

    /** The effect scaled to this level. Full brightness returns the effect itself. */
    GlyphEffect apply(GlyphEffect effect) {
        if (percent == 100) {
            return effect;
        }
        int scale = percent;
        return new GlyphEffect() {
            @Override
            public long durationMs() {
                return effect.durationMs();
            }

            @Override
            public int brightness(long tMs, GlyphChannel channel) {
                return effect.brightness(tMs, channel) * scale / 100;
            }
        };
    }
}
