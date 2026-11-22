package app.exteraless.glyph;

final class GlyphEffects {

    static final int MAX_BRIGHTNESS = 4095;

    private static final long PULSE_RAMP_MS = 300;
    private static final long PULSE_GAP_MS = 120;
    private static final long PULSE_FLASH_MS = PULSE_RAMP_MS * 2;
    private static final long PULSE_TOTAL_MS = PULSE_FLASH_MS * 2 + PULSE_GAP_MS;

    private static final long BREATHE_RAMP_MS = 750;

    /** Two flashes with a short gap, for one-off events. */
    static final GlyphEffect PULSE = new GlyphEffect() {
        @Override
        public long durationMs() {
            return PULSE_TOTAL_MS;
        }

        @Override
        public int brightness(long tMs, int channelIndexInRule, int channelCountInRule) {
            if (tMs < 0 || tMs >= PULSE_TOTAL_MS) {
                return 0;
            }
            if (tMs < PULSE_FLASH_MS) {
                return triangle(tMs, PULSE_RAMP_MS);
            }
            long second = tMs - PULSE_FLASH_MS - PULSE_GAP_MS;
            return second < 0 ? 0 : triangle(second, PULSE_RAMP_MS);
        }
    };

    /** Endless slow fade in and out, for events that last. */
    static final GlyphEffect BREATHE = new GlyphEffect() {
        @Override
        public long durationMs() {
            return -1;
        }

        @Override
        public int brightness(long tMs, int channelIndexInRule, int channelCountInRule) {
            if (tMs < 0) {
                return 0;
            }
            return triangle(tMs % (BREATHE_RAMP_MS * 2), BREATHE_RAMP_MS);
        }
    };

    /** Linear rise to full brightness over rampMs, then a fall back to zero of the same length. */
    private static int triangle(long tMs, long rampMs) {
        long phase = tMs <= rampMs ? tMs : rampMs * 2 - tMs;
        if (phase <= 0) {
            return 0;
        }
        return (int) (MAX_BRIGHTNESS * phase / rampMs);
    }

    private GlyphEffects() {
    }
}
