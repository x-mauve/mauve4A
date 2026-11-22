package app.exteraless.glyph;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class GlyphEffectsTest {

    private static final long STEP_MS = 5;

    @Test
    public void oneShotEffectsEndAtTheirDuration() {
        for (GlyphAnimation animation : GlyphAnimation.oneShot()) {
            GlyphEffect effect = animation.effect();
            long duration = effect.durationMs();
            assertTrue(animation.id, duration > 0);
            assertEquals(animation.id, 0, effect.brightness(duration, 0, 1));
            assertEquals(animation.id, 0, effect.brightness(duration + 1000, 0, 1));
        }
    }

    @Test
    public void brightnessStaysInRange() {
        for (GlyphAnimation animation : GlyphAnimation.values()) {
            GlyphEffect effect = animation.effect();
            long duration = effect.durationMs();
            long end = duration >= 0 ? duration : 3000;
            for (long t = -STEP_MS; t <= end; t += STEP_MS) {
                int value = effect.brightness(t, 0, 1);
                assertTrue(animation.id + " at " + t,
                        value >= 0 && value <= GlyphEffects.MAX_BRIGHTNESS);
            }
        }
    }

    @Test
    public void blinkIsHardOnAndOff() {
        assertEquals(900, GlyphEffects.BLINK.durationMs());
        assertEquals(GlyphEffects.MAX_BRIGHTNESS, GlyphEffects.BLINK.brightness(75, 0, 1));
        assertEquals(0, GlyphEffects.BLINK.brightness(225, 0, 1));
    }

    @Test
    public void fadeStartsFull() {
        assertEquals(1200, GlyphEffects.FADE.durationMs());
        assertEquals(GlyphEffects.MAX_BRIGHTNESS, GlyphEffects.FADE.brightness(0, 0, 1));
    }

    @Test
    public void lastingEffectsRunUntilStopped() {
        for (GlyphAnimation animation : GlyphAnimation.lasting()) {
            assertEquals(animation.id, -1, animation.effect().durationMs());
        }
        assertEquals(GlyphEffects.MAX_BRIGHTNESS, GlyphEffects.SOLID.brightness(60000, 0, 1));
    }

    @Test
    public void oneShotAndLastingCoverEveryAnimation() {
        assertEquals(GlyphAnimation.values().length,
                GlyphAnimation.oneShot().size() + GlyphAnimation.lasting().size());
        for (GlyphAnimation animation : GlyphAnimation.oneShot()) {
            assertFalse(animation.id, animation.lasting);
        }
        for (GlyphAnimation animation : GlyphAnimation.lasting()) {
            assertTrue(animation.id, animation.lasting);
        }
    }

    @Test
    public void animationIdsRoundTrip() {
        for (GlyphAnimation animation : GlyphAnimation.values()) {
            assertEquals(animation, GlyphAnimation.fromId(animation.id));
        }
        assertNull(GlyphAnimation.fromId(null));
        assertNull(GlyphAnimation.fromId(""));
        assertNull(GlyphAnimation.fromId("wave"));
    }
}
