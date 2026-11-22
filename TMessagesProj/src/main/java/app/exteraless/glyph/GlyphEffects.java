package app.exteraless.glyph;

final class GlyphEffects {

    static final int MAX_BRIGHTNESS = 4095;

    private static final long PULSE_RAMP_MS = 300;
    private static final long PULSE_GAP_MS = 120;
    private static final long PULSE_FLASH_MS = PULSE_RAMP_MS * 2;
    private static final long PULSE_TOTAL_MS = PULSE_FLASH_MS * 2 + PULSE_GAP_MS;

    private static final long BREATHE_RAMP_MS = 750;

    private static final long BLINK_STEP_MS = 150;
    private static final int BLINK_COUNT = 3;
    private static final long BLINK_TOTAL_MS = BLINK_STEP_MS * 2 * BLINK_COUNT;

    private static final long HEARTBEAT_FIRST_RAMP_MS = 100;
    private static final long HEARTBEAT_GAP_MS = 120;
    private static final long HEARTBEAT_SECOND_RAMP_MS = 250;
    private static final long HEARTBEAT_SECOND_START_MS =
            HEARTBEAT_FIRST_RAMP_MS * 2 + HEARTBEAT_GAP_MS;
    private static final long HEARTBEAT_TOTAL_MS = HEARTBEAT_SECOND_START_MS + HEARTBEAT_SECOND_RAMP_MS * 2;

    private static final long FADE_MS = 1200;

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

    /** Hard on and off three times, for one-off events. */
    static final GlyphEffect BLINK = new GlyphEffect() {
        @Override
        public long durationMs() {
            return BLINK_TOTAL_MS;
        }

        @Override
        public int brightness(long tMs, int channelIndexInRule, int channelCountInRule) {
            if (tMs < 0 || tMs >= BLINK_TOTAL_MS) {
                return 0;
            }
            return tMs % (BLINK_STEP_MS * 2) < BLINK_STEP_MS ? MAX_BRIGHTNESS : 0;
        }
    };

    /** A short beat, a short gap, then a long beat, for one-off events. */
    static final GlyphEffect HEARTBEAT = new GlyphEffect() {
        @Override
        public long durationMs() {
            return HEARTBEAT_TOTAL_MS;
        }

        @Override
        public int brightness(long tMs, int channelIndexInRule, int channelCountInRule) {
            if (tMs < 0 || tMs >= HEARTBEAT_TOTAL_MS) {
                return 0;
            }
            if (tMs < HEARTBEAT_FIRST_RAMP_MS * 2) {
                return triangle(tMs, HEARTBEAT_FIRST_RAMP_MS);
            }
            if (tMs < HEARTBEAT_SECOND_START_MS) {
                return 0;
            }
            return triangle(tMs - HEARTBEAT_SECOND_START_MS, HEARTBEAT_SECOND_RAMP_MS);
        }
    };

    /** Full brightness at once, then an ease-out decay, for one-off events. */
    static final GlyphEffect FADE = new GlyphEffect() {
        @Override
        public long durationMs() {
            return FADE_MS;
        }

        @Override
        public int brightness(long tMs, int channelIndexInRule, int channelCountInRule) {
            if (tMs < 0 || tMs >= FADE_MS) {
                return 0;
            }
            long left = FADE_MS - tMs;
            return (int) (MAX_BRIGHTNESS * left * left / (FADE_MS * FADE_MS));
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

    /** Constant full brightness until stopped, for events that last. */
    static final GlyphEffect SOLID = new GlyphEffect() {
        @Override
        public long durationMs() {
            return -1;
        }

        @Override
        public int brightness(long tMs, int channelIndexInRule, int channelCountInRule) {
            return tMs < 0 ? 0 : MAX_BRIGHTNESS;
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
