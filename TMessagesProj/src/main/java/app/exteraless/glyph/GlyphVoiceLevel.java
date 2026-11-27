package app.exteraless.glyph;

/**
 * The microphone level of the ongoing voice or video recording, smoothed for
 * the glyphs. Audio threads push raw levels, the glyph thread reads the
 * envelope. Free of Android types so the smoothing stays unit-testable.
 */
final class GlyphVoiceLevel {

    /** Injectable time source; the production clock is monotonic. */
    interface Clock {
        long nowMs();
    }

    /** A louder target is reached in this time, so the glyphs react at once. */
    private static final long ATTACK_MS = 40;
    /** Quiet follows the voice more slowly, so the glyphs don't flicker. */
    private static final double RELEASE_MS = 200;
    /** No sample for this long means the recorder paused or stalled; fade out. */
    private static final long STALE_MS = 300;

    static final GlyphVoiceLevel SHARED = new GlyphVoiceLevel();

    private final Clock clock;
    private float target;
    private long targetAt;
    private float envelope;
    private long envelopeAt;

    private GlyphVoiceLevel() {
        this(() -> System.nanoTime() / 1_000_000L);
    }

    GlyphVoiceLevel(Clock clock) {
        this.clock = clock;
        targetAt = envelopeAt = clock.nowMs();
    }

    /** Called on the audio thread for every buffer. */
    synchronized void push(float level) {
        target = Math.max(0f, Math.min(1f, level));
        targetAt = clock.nowMs();
    }

    /** Drops to silence, so the next recording never starts at the last level. */
    synchronized void reset() {
        target = 0f;
        envelope = 0f;
        targetAt = envelopeAt = clock.nowMs();
    }

    /** The smoothed level at the current time, 0..1. */
    synchronized float current() {
        long now = clock.nowMs();
        long dt = Math.max(0L, now - envelopeAt);
        envelopeAt = now;
        // A stale target means the samples stopped, so fall back to silence.
        float goal = now - targetAt >= STALE_MS ? 0f : target;
        if (goal >= envelope) {
            float step = dt >= ATTACK_MS ? 1f : (float) dt / ATTACK_MS;
            envelope += (goal - envelope) * step;
        } else {
            envelope = goal + (envelope - goal) * (float) Math.exp(-dt / RELEASE_MS);
        }
        return envelope;
    }
}
