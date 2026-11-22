package app.exteraless.glyph;

import java.util.Set;

/** Drives the glyph hardware of one device kind. Methods are safe to call from any thread. */
interface GlyphEngine {

    /** Binds to the system glyph service. */
    void start();

    /** Turns the glyphs off, releases them and unbinds. */
    void stop();

    /** Plays an effect on the given zones, replacing whatever is playing. */
    void play(Set<String> zoneIds, GlyphEffect effect);

    void stopEffect();
}
