package app.exteraless.glyph;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class GlyphEffectsTest {

    private static final long STEP_MS = 5;
    private static final long WAVE_PASS_MS = 1000;
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
        assertNull(GlyphAnimation.fromId("confetti"));
    }

    @Test
    public void movingAnimationsAreOfferedForBothKinds() {
        GlyphAnimation[] both = {GlyphAnimation.WAVE, GlyphAnimation.CHASE,
                GlyphAnimation.PING_PONG, GlyphAnimation.SPARKLE};
        for (GlyphAnimation animation : both) {
            assertTrue(animation.id, GlyphAnimation.oneShot().contains(animation));
            assertTrue(animation.id, GlyphAnimation.lasting().contains(animation));
            assertTrue(animation.id, animation.oneShotEffect().durationMs() > 0);
            assertEquals(animation.id, -1, animation.lastingEffect().durationMs());
        }
    }

    @Test
    public void waveHasTwoPasses() {
        assertEquals(WAVE_PASS_MS * 2, GlyphEffects.WAVE.durationMs());
        assertEquals(-1, GlyphEffects.WAVE_LOOP.durationMs());
    }

    @Test
    public void waveMovesFromFirstToLast() {
        long previousPeak = -1;
        for (GlyphChannel channel : GlyphChannel.layout(10)) {
            long peakAt = -1;
            int peak = -1;
            for (long t = 0; t <= WAVE_PASS_MS; t += STEP_MS) {
                int value = GlyphEffects.WAVE.brightness(t, channel);
                if (value > peak) {
                    peak = value;
                    peakAt = t;
                }
            }
            assertTrue("channel " + channel.index, peak >= GlyphEffects.MAX_BRIGHTNESS * 9 / 10);
            assertTrue("channel " + channel.index + " peaks after the previous one",
                    peakAt > previousPeak);
            previousPeak = peakAt;
        }
    }

    @Test
    public void waveStartsAndEndsDark() {
        GlyphChannel[] channels = GlyphChannel.layout(4);
        for (long t : new long[]{0, 2000, 5000}) {
            for (GlyphChannel channel : channels) {
                assertEquals("at " + t, 0, GlyphEffects.WAVE.brightness(t, channel));
            }
        }
    }

    @Test
    public void waveLoopRepeats() {
        GlyphChannel[] channels = GlyphChannel.layout(10);
        for (long t = 0; t <= WAVE_PASS_MS; t += STEP_MS) {
            for (GlyphChannel channel : channels) {
                assertEquals("at " + t, GlyphEffects.WAVE_LOOP.brightness(t, channel),
                        GlyphEffects.WAVE_LOOP.brightness(t + WAVE_PASS_MS, channel));
            }
        }
        boolean lit = false;
        for (GlyphChannel channel : channels) {
            lit |= GlyphEffects.WAVE_LOOP.brightness(60500, channel) > 0;
        }
        assertTrue(lit);
    }

    @Test
    public void waveOnOneChannelFallsBack() {
        GlyphChannel channel = GlyphChannel.layout(1)[0];
        for (long t = 0; t <= 1500; t += STEP_MS) {
            assertEquals("at " + t, GlyphEffects.PULSE.brightness(t, channel),
                    GlyphEffects.WAVE.brightness(t, channel));
            assertEquals("at " + t, GlyphEffects.BREATHE.brightness(t, channel),
                    GlyphEffects.WAVE_LOOP.brightness(t, channel));
        }
    }

    @Test
    public void waveStaysInRange() {
        int[][] layouts = {{1}, {4}, {11, 5, 20}};
        for (int[] layout : layouts) {
            for (GlyphChannel channel : GlyphChannel.layout(layout)) {
                for (long t = -STEP_MS; t <= 2000; t += STEP_MS) {
                    int value = GlyphEffects.WAVE.brightness(t, channel);
                    assertTrue("wave " + layout.length + " at " + t,
                            value >= 0 && value <= GlyphEffects.MAX_BRIGHTNESS);
                }
                for (long t = -STEP_MS; t <= 3000; t += STEP_MS) {
                    int value = GlyphEffects.WAVE_LOOP.brightness(t, channel);
                    assertTrue("wave loop " + layout.length + " at " + t,
                            value >= 0 && value <= GlyphEffects.MAX_BRIGHTNESS);
                }
            }
        }
    }

    @Test
    public void chaseHasTwelveSteps() {
        assertEquals(1800, GlyphEffects.CHASE.durationMs());
        assertEquals(-1, GlyphEffects.CHASE_LOOP.durationMs());
    }

    @Test
    public void chaseLightsOneSegmentAtATime() {
        GlyphChannel[] channels = GlyphChannel.layout(2, 3, 1);
        long[] times = {75, 225, 375, 525};
        int[] lit = {0, 1, 2, 0};
        for (int i = 0; i < times.length; i++) {
            for (GlyphChannel channel : channels) {
                int expected = channel.segment == lit[i] ? GlyphEffects.MAX_BRIGHTNESS : 0;
                assertEquals("at " + times[i] + " channel " + channel.index,
                        expected, GlyphEffects.CHASE.brightness(times[i], channel));
            }
        }
    }

    @Test
    public void chasePlaysWholePasses() {
        GlyphChannel[] five = GlyphChannel.layout(1, 1, 1, 1, 1);
        assertTrue(anyLit(GlyphEffects.CHASE, 1499, five));
        assertFalse(anyLit(GlyphEffects.CHASE, 1500, five));

        GlyphChannel[] eleven = GlyphChannel.layout(1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1);
        assertTrue(anyLit(GlyphEffects.CHASE, 1649, eleven));
        assertFalse(anyLit(GlyphEffects.CHASE, 1650, eleven));

        GlyphChannel[] four = GlyphChannel.layout(1, 1, 1, 1);
        assertEquals(GlyphEffects.MAX_BRIGHTNESS, GlyphEffects.CHASE.brightness(1799, four[3]));
    }

    @Test
    public void chaseLoopRepeats() {
        GlyphChannel[] channels = GlyphChannel.layout(1, 1, 1);
        assertEquals(GlyphEffects.MAX_BRIGHTNESS,
                GlyphEffects.CHASE_LOOP.brightness(150 * 300 + 75, channels[0]));
    }

    @Test
    public void chaseOnOneSegmentFallsBack() {
        for (GlyphChannel channel : GlyphChannel.layout(8)) {
            for (long t = 0; t <= 1500; t += STEP_MS) {
                assertEquals("at " + t, GlyphEffects.PULSE.brightness(t, channel),
                        GlyphEffects.CHASE.brightness(t, channel));
                assertEquals("at " + t, GlyphEffects.BREATHE.brightness(t, channel),
                        GlyphEffects.CHASE_LOOP.brightness(t, channel));
            }
        }
    }

    @Test
    public void pingPongGoesThereAndBack() {
        GlyphChannel[] channels = GlyphChannel.layout(10);
        assertEquals(GlyphEffects.MAX_BRIGHTNESS,
                GlyphEffects.PING_PONG_LOOP.brightness(0, channels[0]));
        assertEquals(GlyphEffects.MAX_BRIGHTNESS,
                GlyphEffects.PING_PONG_LOOP.brightness(500, channels[9]));
        assertEquals(GlyphEffects.MAX_BRIGHTNESS,
                GlyphEffects.PING_PONG_LOOP.brightness(1000, channels[0]));
        assertEquals(0, GlyphEffects.PING_PONG_LOOP.brightness(0, channels[9]));
        assertEquals(0, GlyphEffects.PING_PONG_LOOP.brightness(500, channels[0]));
    }

    @Test
    public void pingPongIsSymmetric() {
        for (GlyphChannel channel : GlyphChannel.layout(10)) {
            for (long t = 0; t <= 1000; t += STEP_MS) {
                int value = GlyphEffects.PING_PONG_LOOP.brightness(t, channel);
                assertEquals("at " + t + " channel " + channel.index, value,
                        GlyphEffects.PING_PONG_LOOP.brightness(1000 - t, channel));
                assertEquals("at " + t + " channel " + channel.index, value,
                        GlyphEffects.PING_PONG_LOOP.brightness(t + 1000, channel));
            }
        }
    }

    @Test
    public void pingPongStartsAndEndsDark() {
        GlyphChannel[] channels = GlyphChannel.layout(4);
        for (long t : new long[]{0, 2000, 5000}) {
            for (GlyphChannel channel : channels) {
                assertEquals("at " + t, 0, GlyphEffects.PING_PONG.brightness(t, channel));
            }
        }
        assertEquals(GlyphEffects.MAX_BRIGHTNESS,
                GlyphEffects.PING_PONG.brightness(500, channels[3]));
    }

    @Test
    public void pingPongLeavesNoGaps() {
        int[][] layouts = {{4}, {11, 5, 20}};
        for (int[] layout : layouts) {
            GlyphChannel[] channels = GlyphChannel.layout(layout);
            for (long t = 0; t <= 1000; t += STEP_MS) {
                assertTrue("layout " + layout.length + " at " + t,
                        anyLit(GlyphEffects.PING_PONG_LOOP, t, channels));
            }
        }
    }

    @Test
    public void pingPongOnOneChannelFallsBack() {
        GlyphChannel channel = GlyphChannel.layout(1)[0];
        for (long t = 0; t <= 1500; t += STEP_MS) {
            assertEquals("at " + t, GlyphEffects.PULSE.brightness(t, channel),
                    GlyphEffects.PING_PONG.brightness(t, channel));
            assertEquals("at " + t, GlyphEffects.BREATHE.brightness(t, channel),
                    GlyphEffects.PING_PONG_LOOP.brightness(t, channel));
        }
    }

    @Test
    public void pingPongStaysInRange() {
        int[][] layouts = {{1}, {4}, {11, 5, 20}};
        for (int[] layout : layouts) {
            for (GlyphChannel channel : GlyphChannel.layout(layout)) {
                for (long t = -STEP_MS; t <= 3000; t += STEP_MS) {
                    int value = GlyphEffects.PING_PONG.brightness(t, channel);
                    assertTrue("ping-pong " + layout.length + " at " + t,
                            value >= 0 && value <= GlyphEffects.MAX_BRIGHTNESS);
                    value = GlyphEffects.PING_PONG_LOOP.brightness(t, channel);
                    assertTrue("ping-pong loop " + layout.length + " at " + t,
                            value >= 0 && value <= GlyphEffects.MAX_BRIGHTNESS);
                }
            }
        }
    }

    @Test
    public void sparkleLightsEverySlot() {
        int[][] layouts = {{2}, {4}, {6}, {11, 5, 20}};
        for (int[] layout : layouts) {
            GlyphChannel[] channels = GlyphChannel.layout(layout);
            for (int slot = 0; slot < 8; slot++) {
                boolean lit = false;
                for (long t = slot * 240L; t < (slot + 2) * 240L && !lit; t += STEP_MS) {
                    lit = anyLit(GlyphEffects.SPARKLE, t, channels);
                }
                assertTrue("layout " + layout.length + " slot " + slot, lit);
            }
        }
    }

    @Test
    public void sparkleChannelsDiffer() {
        GlyphChannel[] channels = GlyphChannel.layout(6);
        boolean differ = false;
        for (long t = 0; t <= 1920 && !differ; t += STEP_MS) {
            boolean lit = false;
            boolean dark = false;
            for (GlyphChannel channel : channels) {
                int value = GlyphEffects.SPARKLE.brightness(t, channel);
                lit |= value > 0;
                dark |= value == 0;
            }
            differ = lit && dark;
        }
        assertTrue(differ);
    }

    @Test
    public void sparkleReachesEveryChannel() {
        for (GlyphChannel channel : GlyphChannel.layout(11, 5, 20)) {
            int peak = 0;
            for (long t = 0; t <= 60000; t += STEP_MS) {
                peak = Math.max(peak, GlyphEffects.SPARKLE_LOOP.brightness(t, channel));
            }
            assertTrue("channel " + channel.index,
                    peak > GlyphEffects.MAX_BRIGHTNESS * 9 / 10);
        }
    }

    @Test
    public void sparkleStartsAndEndsDark() {
        GlyphChannel[] channels = GlyphChannel.layout(4);
        for (long t : new long[]{0, 2160, 5000}) {
            for (GlyphChannel channel : channels) {
                assertEquals("at " + t, 0, GlyphEffects.SPARKLE.brightness(t, channel));
            }
        }
    }

    @Test
    public void sparkleLoopContinuesTheOneShot() {
        GlyphChannel[] channels = GlyphChannel.layout(6);
        for (long t = 0; t <= 1915; t += STEP_MS) {
            for (GlyphChannel channel : channels) {
                assertEquals("at " + t, GlyphEffects.SPARKLE.brightness(t, channel),
                        GlyphEffects.SPARKLE_LOOP.brightness(t, channel));
            }
        }
        boolean lit = false;
        for (long t = 60000; t < 60480 && !lit; t += STEP_MS) {
            lit = anyLit(GlyphEffects.SPARKLE_LOOP, t, channels);
        }
        assertTrue(lit);
    }

    @Test
    public void sparkleOnOneChannelFallsBack() {
        GlyphChannel channel = GlyphChannel.layout(1)[0];
        for (long t = 0; t <= 1500; t += STEP_MS) {
            assertEquals("at " + t, GlyphEffects.PULSE.brightness(t, channel),
                    GlyphEffects.SPARKLE.brightness(t, channel));
            assertEquals("at " + t, GlyphEffects.BREATHE.brightness(t, channel),
                    GlyphEffects.SPARKLE_LOOP.brightness(t, channel));
        }
    }

    @Test
    public void sparkleStaysInRange() {
        int[][] layouts = {{1}, {4}, {11, 5, 20}};
        for (int[] layout : layouts) {
            for (GlyphChannel channel : GlyphChannel.layout(layout)) {
                for (long t = -STEP_MS; t <= 3000; t += STEP_MS) {
                    int value = GlyphEffects.SPARKLE.brightness(t, channel);
                    assertTrue("sparkle " + layout.length + " at " + t,
                            value >= 0 && value <= GlyphEffects.MAX_BRIGHTNESS);
                    value = GlyphEffects.SPARKLE_LOOP.brightness(t, channel);
                    assertTrue("sparkle loop " + layout.length + " at " + t,
                            value >= 0 && value <= GlyphEffects.MAX_BRIGHTNESS);
                }
            }
        }
    }

    @Test
    public void brightnessScalesEffects() {
        assertEquals(GlyphEffects.MAX_BRIGHTNESS * 15 / 100,
                GlyphBrightness.LOW.apply(GlyphEffects.SOLID).brightness(100, SINGLE));
        assertEquals(GlyphEffects.MAX_BRIGHTNESS * 45 / 100,
                GlyphBrightness.MEDIUM.apply(GlyphEffects.SOLID).brightness(100, SINGLE));
        assertSame(GlyphEffects.SOLID, GlyphBrightness.HIGH.apply(GlyphEffects.SOLID));
    }

    @Test
    public void brightnessKeepsDuration() {
        for (GlyphBrightness brightness : GlyphBrightness.values()) {
            assertEquals(GlyphEffects.PULSE.durationMs(),
                    brightness.apply(GlyphEffects.PULSE).durationMs());
            assertEquals(-1, brightness.apply(GlyphEffects.BREATHE).durationMs());
        }
    }

    @Test
    public void brightnessLevelsAreOrdered() {
        int low = GlyphBrightness.LOW.apply(GlyphEffects.SOLID).brightness(0, SINGLE);
        int medium = GlyphBrightness.MEDIUM.apply(GlyphEffects.SOLID).brightness(0, SINGLE);
        int high = GlyphBrightness.HIGH.apply(GlyphEffects.SOLID).brightness(0, SINGLE);
        assertTrue(low > 0);
        assertTrue(low < medium);
        assertTrue(medium < high);
    }

    @Test
    public void brightnessIdsRoundTrip() {
        for (GlyphBrightness brightness : GlyphBrightness.values()) {
            assertEquals(brightness, GlyphBrightness.fromId(brightness.id));
        }
        assertNull(GlyphBrightness.fromId(null));
        assertNull(GlyphBrightness.fromId(""));
        assertNull(GlyphBrightness.fromId("blinding"));
    }

    private static boolean anyLit(GlyphEffect effect, long t, GlyphChannel[] channels) {
        for (GlyphChannel channel : channels) {
            if (effect.brightness(t, channel) > 0) {
                return true;
            }
        }
        return false;
    }
}
