package app.exteraless.glyph;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class GlyphVoiceLevelTest {

    private static final GlyphChannel SINGLE = GlyphChannel.layout(1)[0];

    private static final class FakeClock implements GlyphVoiceLevel.Clock {
        long now;

        @Override
        public long nowMs() {
            return now;
        }
    }

    private final FakeClock clock = new FakeClock();

    private GlyphVoiceLevel newLevel() {
        return new GlyphVoiceLevel(clock);
    }

    private int brightness(GlyphEffect effect) {
        return effect.brightness(0, SINGLE);
    }

    @Test
    public void silenceKeepsTheFloor() {
        GlyphEffect effect = GlyphEffects.voice(newLevel());
        assertEquals(GlyphEffects.VOICE_FLOOR, brightness(effect));
        clock.now += 5000;
        assertEquals(GlyphEffects.VOICE_FLOOR, brightness(effect));
        assertEquals(-1, effect.durationMs());
    }

    @Test
    public void fullLevelReachesTheTopWithinFortyMilliseconds() {
        GlyphVoiceLevel level = newLevel();
        GlyphEffect effect = GlyphEffects.voice(level);
        level.push(1f);
        clock.now += 40;
        assertEquals(GlyphEffects.MAX_BRIGHTNESS, brightness(effect));
    }

    @Test
    public void levelFallsAfterTheSamplesStop() {
        GlyphVoiceLevel level = newLevel();
        GlyphEffect effect = GlyphEffects.voice(level);
        level.push(1f);
        clock.now += 40;
        int loud = brightness(effect);

        clock.now += 300;
        int falling = brightness(effect);
        assertTrue(falling < loud);

        clock.now += 2000;
        assertEquals(GlyphEffects.VOICE_FLOOR, brightness(effect));
    }

    @Test
    public void quietTargetStaysBelowFull() {
        GlyphVoiceLevel level = newLevel();
        level.push(0.5f);
        clock.now += 40;
        int half = brightness(GlyphEffects.voice(level));
        assertTrue(half > GlyphEffects.VOICE_FLOOR);
        assertTrue(half < GlyphEffects.MAX_BRIGHTNESS);
    }

    @Test
    public void pushesAreClamped() {
        GlyphVoiceLevel level = newLevel();
        GlyphEffect effect = GlyphEffects.voice(level);
        level.push(5f);
        clock.now += 40;
        assertEquals(GlyphEffects.MAX_BRIGHTNESS, brightness(effect));

        level.reset();
        level.push(-1f);
        clock.now += 40;
        assertEquals(GlyphEffects.VOICE_FLOOR, brightness(effect));
    }

    @Test
    public void resetDropsToTheFloorAtOnce() {
        GlyphVoiceLevel level = newLevel();
        GlyphEffect effect = GlyphEffects.voice(level);
        level.push(1f);
        clock.now += 40;
        assertEquals(GlyphEffects.MAX_BRIGHTNESS, brightness(effect));

        level.reset();
        assertEquals(GlyphEffects.VOICE_FLOOR, brightness(effect));
    }

    @Test
    public void sameTimeGivesTheSameValue() {
        GlyphVoiceLevel level = newLevel();
        level.push(0.8f);
        clock.now += 17;
        assertEquals(level.current(), level.current(), 0f);
    }

    @Test
    public void previewStaysInRangeAndVaries() {
        assertEquals(-1, GlyphEffects.VOICE_PREVIEW.durationMs());
        GlyphChannel[] channels = GlyphChannel.layout(2);
        int first = GlyphEffects.VOICE_PREVIEW.brightness(0, channels[0]);
        assertEquals(GlyphEffects.VOICE_FLOOR, first);
        boolean varied = false;
        for (long t = -5; t <= 1000; t += 5) {
            int value = GlyphEffects.VOICE_PREVIEW.brightness(t, channels[0]);
            assertTrue("at " + t, value >= GlyphEffects.VOICE_FLOOR
                    && value <= GlyphEffects.MAX_BRIGHTNESS);
            assertEquals("at " + t, value,
                    GlyphEffects.VOICE_PREVIEW.brightness(t, channels[1]));
            varied |= value != first;
        }
        assertTrue(varied);
        assertNotEquals(GlyphEffects.VOICE_FLOOR,
                GlyphEffects.VOICE_PREVIEW.brightness(400, channels[0]));
    }
}
