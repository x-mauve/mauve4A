package app.exteraless.glyph;

/** Brightness over time for the channels of one rule. Free of Android and SDK types. */
interface GlyphEffect {

    /** Total length in ms, or -1 for an effect that runs until stopped. */
    long durationMs();

    /** Brightness 0..4095 for a channel at time tMs since the effect started. */
    int brightness(long tMs, int channelIndexInRule, int channelCountInRule);
}
