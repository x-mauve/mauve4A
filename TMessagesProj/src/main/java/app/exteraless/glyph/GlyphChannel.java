package app.exteraless.glyph;

/** Where a channel sits among the channels of one rule. Free of Android types. */
final class GlyphChannel {

    /** Position among all channels of the rule, in layout order. */
    final int index;
    /** Number of channels in the rule. */
    final int count;
    /** The zone the channel belongs to, among the zones of the rule. */
    final int segment;
    /** Number of zones in the rule that have at least one channel. */
    final int segmentCount;

    private GlyphChannel(int index, int count, int segment, int segmentCount) {
        this.index = index;
        this.count = count;
        this.segment = segment;
        this.segmentCount = segmentCount;
    }

    /**
     * One entry per channel, in order. Segment sizes are the channel counts of
     * the zones of the rule; empty segments don't count and don't take a number.
     */
    static GlyphChannel[] layout(int... segmentSizes) {
        int count = 0;
        int segmentCount = 0;
        for (int size : segmentSizes) {
            if (size > 0) {
                count += size;
                segmentCount++;
            }
        }
        GlyphChannel[] channels = new GlyphChannel[count];
        int index = 0;
        int segment = 0;
        for (int size : segmentSizes) {
            if (size <= 0) {
                continue;
            }
            for (int i = 0; i < size; i++) {
                channels[index] = new GlyphChannel(index, count, segment, segmentCount);
                index++;
            }
            segment++;
        }
        return channels;
    }
}
