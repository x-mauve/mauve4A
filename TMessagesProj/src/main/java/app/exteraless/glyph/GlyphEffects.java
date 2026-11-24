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

    private static final long WAVE_PASS_MS = 1000;
    private static final int WAVE_PASSES = 2;
    /** Length of the rise ahead of the crest and of the tail behind it, as parts of the strip. */
    private static final double WAVE_FRONT = 0.1;
    private static final double WAVE_TAIL = 0.4;
    /** The crest starts before the first channel and ends past the last, so every pass is dark at both ends. */
    private static final double WAVE_HEAD_START = -WAVE_FRONT;
    private static final double WAVE_HEAD_END = 1 + WAVE_TAIL;

    private static final long CHASE_STEP_MS = 150;
    private static final int CHASE_STEPS = 12;

    /** Two flashes with a short gap, for one-off events. */
    static final GlyphEffect PULSE = new GlyphEffect() {
        @Override
        public long durationMs() {
            return PULSE_TOTAL_MS;
        }

        @Override
        public int brightness(long tMs, GlyphChannel channel) {
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
        public int brightness(long tMs, GlyphChannel channel) {
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
        public int brightness(long tMs, GlyphChannel channel) {
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
        public int brightness(long tMs, GlyphChannel channel) {
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
        public int brightness(long tMs, GlyphChannel channel) {
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
        public int brightness(long tMs, GlyphChannel channel) {
            return tMs < 0 ? 0 : MAX_BRIGHTNESS;
        }
    };

    /** A crest running over all channels twice, for one-off events. */
    static final GlyphEffect WAVE = new GlyphEffect() {
        @Override
        public long durationMs() {
            return WAVE_PASS_MS * WAVE_PASSES;
        }

        @Override
        public int brightness(long tMs, GlyphChannel channel) {
            if (tMs < 0 || tMs >= WAVE_PASS_MS * WAVE_PASSES) {
                return 0;
            }
            return wave(tMs, channel, PULSE);
        }
    };

    /** A crest running over all channels forever, for events that last. */
    static final GlyphEffect WAVE_LOOP = new GlyphEffect() {
        @Override
        public long durationMs() {
            return -1;
        }

        @Override
        public int brightness(long tMs, GlyphChannel channel) {
            if (tMs < 0) {
                return 0;
            }
            return wave(tMs, channel, BREATHE);
        }
    };

    /** Whole zones lighting up one after another, for one-off events. */
    static final GlyphEffect CHASE = new GlyphEffect() {
        @Override
        public long durationMs() {
            return CHASE_STEP_MS * CHASE_STEPS;
        }

        @Override
        public int brightness(long tMs, GlyphChannel channel) {
            if (tMs < 0 || tMs >= CHASE_STEP_MS * CHASE_STEPS) {
                return 0;
            }
            return chase(tMs, channel, true, PULSE);
        }
    };

    /** Whole zones lighting up one after another forever, for events that last. */
    static final GlyphEffect CHASE_LOOP = new GlyphEffect() {
        @Override
        public long durationMs() {
            return -1;
        }

        @Override
        public int brightness(long tMs, GlyphChannel channel) {
            if (tMs < 0) {
                return 0;
            }
            return chase(tMs, channel, false, BREATHE);
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

    /**
     * A crest with a tail behind it, moving from the first channel to the
     * last over one pass, ignoring zone borders. A single channel has no
     * direction, so it gets the fallback that suits the kind.
     */
    private static int wave(long tMs, GlyphChannel channel, GlyphEffect fallback) {
        if (channel.count <= 1) {
            return fallback.brightness(tMs, channel);
        }
        double passTime = tMs % WAVE_PASS_MS;
        double head = WAVE_HEAD_START
                + (WAVE_HEAD_END - WAVE_HEAD_START) * passTime / WAVE_PASS_MS;
        double p = (double) channel.index / (channel.count - 1);
        double d = head - p;
        double level;
        if (d > -WAVE_FRONT && d < 0) {
            level = 1 + d / WAVE_FRONT;
        } else if (d >= 0 && d < WAVE_TAIL) {
            double fall = 1 - d / WAVE_TAIL;
            level = fall * fall;
        } else {
            return 0;
        }
        return (int) (MAX_BRIGHTNESS * level);
    }

    /**
     * Hard on and off, one zone at a time, with no fade. A one-shot chase plays
     * whole passes only; a single zone has no chase, so it gets the fallback.
     */
    private static int chase(long tMs, GlyphChannel channel, boolean oneShot,
                             GlyphEffect fallback) {
        if (channel.segmentCount <= 1) {
            return fallback.brightness(tMs, channel);
        }
        long step = tMs / CHASE_STEP_MS;
        if (oneShot && step >= wholePassSteps(channel.segmentCount)) {
            return 0;
        }
        return channel.segment == step % channel.segmentCount ? MAX_BRIGHTNESS : 0;
    }

    /** Steps a one-shot chase runs: whole passes only, so it never stops mid-pass. */
    private static long wholePassSteps(int segmentCount) {
        if (segmentCount > CHASE_STEPS) {
            return CHASE_STEPS;
        }
        return (CHASE_STEPS / segmentCount) * segmentCount;
    }

    private GlyphEffects() {
    }
}
