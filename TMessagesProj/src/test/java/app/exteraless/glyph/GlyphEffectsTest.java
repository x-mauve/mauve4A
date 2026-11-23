package app.exteraless.glyph;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class GlyphEffectsTest {

    private static final long STEP_MS = 5;
    private static final GlyphChannel SINGLE = GlyphChannel.layout(1)[0];

    @Test
    public void channelLayoutSkipsEmptySegments() {
        GlyphChannel[] channels = GlyphChannel.layout(2, 0, 1);
        assertEquals(3, channels.length);
        assertEquals(2, channels[2].index);
        assertEquals(3, channels[2].count);
        assertEquals(1, channels[2].segment);
        assertEquals(2, channels[2].segmentCount);
        assertEquals(1, channels[1].index);
        assertEquals(0, channels[1].segment);
        assertEquals(3, channels[1].count);
        assertEquals(0, GlyphChannel.layout().length);
    }

    @Test
    public void oneShotEffectsEndAtTheirDuration() {
        for (GlyphAnimation animation : GlyphAnimation.oneShot()) {
            GlyphEffect effect = animation.oneShotEffect();
            long duration = effect.durationMs();
            assertTrue(animation.id, duration > 0);
            assertEquals(animation.id, 0, effect.brightness(duration, SINGLE));
            assertEquals(animation.id, 0, effect.brightness(duration + 1000, SINGLE));
        }
    }

    @Test
    public void brightnessStaysInRange() {
        for (GlyphAnimation animation : GlyphAnimation.values()) {
            if (animation.supportsOneShot()) {
                assertBrightnessInRange(animation.id, animation.oneShotEffect());
            }
            if (animation.supportsLasting()) {
                assertBrightnessInRange(animation.id, animation.lastingEffect());
            }
        }
    }

    private static void assertBrightnessInRange(String id, GlyphEffect effect) {
        long duration = effect.durationMs();
        long end = duration >= 0 ? duration : 3000;
        for (long t = -STEP_MS; t <= end; t += STEP_MS) {
            int value = effect.brightness(t, SINGLE);
            assertTrue(id + " at " + t, value >= 0 && value <= GlyphEffects.MAX_BRIGHTNESS);
        }
    }

    @Test
    public void blinkIsHardOnAndOff() {
        assertEquals(900, GlyphEffects.BLINK.durationMs());
        assertEquals(GlyphEffects.MAX_BRIGHTNESS, GlyphEffects.BLINK.brightness(75, SINGLE));
        assertEquals(0, GlyphEffects.BLINK.brightness(225, SINGLE));
    }

    @Test
    public void fadeStartsFull() {
        assertEquals(1200, GlyphEffects.FADE.durationMs());
        assertEquals(GlyphEffects.MAX_BRIGHTNESS, GlyphEffects.FADE.brightness(0, SINGLE));
    }

    @Test
    public void lastingEffectsRunUntilStopped() {
        for (GlyphAnimation animation : GlyphAnimation.lasting()) {
            assertEquals(animation.id, -1, animation.lastingEffect().durationMs());
        }
        assertEquals(GlyphEffects.MAX_BRIGHTNESS, GlyphEffects.SOLID.brightness(60000, SINGLE));
    }

    @Test
    public void everyAnimationSupportsAKind() {
        for (GlyphAnimation animation : GlyphAnimation.values()) {
            assertTrue(animation.id, animation.supportsOneShot() || animation.supportsLasting());
            assertEquals(animation.id, animation.supportsOneShot(),
                    GlyphAnimation.oneShot().contains(animation));
            assertEquals(animation.id, animation.supportsLasting(),
                    GlyphAnimation.lasting().contains(animation));
        }
    }

    @Test
    public void wrongKindFallsBackToDefault() {
        GlyphRule rule = new GlyphRule(true, null, GlyphAnimation.BREATHE, GlyphAnimation.PULSE);
        assertEquals(GlyphAnimation.PULSE, rule.messageAnimationOrDefault());
        assertEquals(GlyphAnimation.BREATHE, rule.lastingAnimationOrDefault());
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
