package app.exteraless.glyph;

/** A group of LED channels the user can pick as one unit. */
public final class GlyphZone {

    /** Stable identifier, stored in settings. */
    public final String id;
    public final String label;
    private final int[] channels;

    GlyphZone(String id, int... channels) {
        this.id = id;
        this.label = id;
        this.channels = channels.clone();
    }

    public int channelCount() {
        return channels.length;
    }

    /** Index into the device frame for the n-th channel of this zone. */
    public int channel(int n) {
        return channels[n];
    }
}
