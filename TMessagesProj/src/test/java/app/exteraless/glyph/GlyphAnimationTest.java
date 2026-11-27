package app.exteraless.glyph;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

public class GlyphAnimationTest {

    @Test
    public void voiceFitsOnlyRecording() {
        assertTrue(GlyphAnimation.VOICE.fits(GlyphEvent.RECORDING));
        assertFalse(GlyphAnimation.VOICE.fits(GlyphEvent.CALL));
        assertFalse(GlyphAnimation.VOICE.fits(GlyphEvent.MESSAGE));
    }

    @Test
    public void lastingListsDependOnEvent() {
        assertFalse(GlyphAnimation.lasting(GlyphEvent.CALL).contains(GlyphAnimation.VOICE));
        List<GlyphAnimation> recording = GlyphAnimation.lasting(GlyphEvent.RECORDING);
        assertEquals(GlyphAnimation.VOICE, recording.get(recording.size() - 1));
        assertFalse(GlyphAnimation.lasting(GlyphEvent.MESSAGE).contains(GlyphAnimation.VOICE));
    }

    @Test
    public void voiceRuleFallsBackForCalls() {
        GlyphRule rule = new GlyphRule(true, null, null, GlyphAnimation.VOICE);
        assertEquals(GlyphAnimation.BREATHE, rule.lastingAnimationOrDefault(GlyphEvent.CALL));
        assertEquals(GlyphAnimation.VOICE, rule.lastingAnimationOrDefault(GlyphEvent.RECORDING));
    }

    @Test
    public void emptyRuleDefaultsForBoth() {
        GlyphRule rule = new GlyphRule(true, null);
        assertEquals(GlyphAnimation.BREATHE, rule.lastingAnimationOrDefault(GlyphEvent.CALL));
        assertEquals(GlyphAnimation.BREATHE, rule.lastingAnimationOrDefault(GlyphEvent.RECORDING));
    }

    @Test
    public void oneShotHasNoVoice() {
        assertFalse(GlyphAnimation.oneShot().contains(GlyphAnimation.VOICE));
    }

    @Test
    public void voicePreviewUsesTheStandIn() {
        assertSame(GlyphEffects.VOICE_PREVIEW, GlyphAnimation.VOICE.previewLastingEffect());
        assertSame(GlyphEffects.BREATHE, GlyphAnimation.BREATHE.previewLastingEffect());
        assertEquals(-1, GlyphAnimation.VOICE.previewLastingEffect().durationMs());
    }

    @Test
    public void everyLastingEffectRunsUntilStopped() {
        for (GlyphAnimation animation : GlyphAnimation.lasting(GlyphEvent.RECORDING)) {
            assertEquals(animation.id, -1, animation.lastingEffect().durationMs());
        }
        assertTrue(GlyphAnimation.lasting(GlyphEvent.RECORDING).contains(GlyphAnimation.VOICE));
    }
}
