package app.exteraless.glyph;

import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * The rule set: one rule per event type plus optional rules for single chats.
 * A chat rule covers messages and calls from that chat on every account.
 */
public final class GlyphRules {

    /** Immutable, so the controller thread can read it while settings replace it. */
    private static final class Snapshot {
        final Map<GlyphEvent, GlyphRule> events;
        final Map<Long, GlyphRule> chats;

        Snapshot(Map<GlyphEvent, GlyphRule> events, Map<Long, GlyphRule> chats) {
            this.events = events;
            this.chats = chats;
        }
    }

    private static final Snapshot EMPTY =
            new Snapshot(new EnumMap<>(GlyphEvent.class), Collections.emptyMap());

    private static volatile Snapshot snapshot = EMPTY;

    private GlyphRules() {
    }

    /**
     * The rule in charge of an event. A chat rule replaces the event rule as
     * a whole. {@code dialogId} is 0 when the event has no chat.
     */
    public static GlyphRule resolve(GlyphEvent event, long dialogId) {
        Snapshot local = snapshot;
        if (dialogId != 0 && (event == GlyphEvent.MESSAGE || event == GlyphEvent.CALL)) {
            GlyphRule rule = local.chats.get(dialogId);
            if (rule != null) {
                return rule;
            }
        }
        return eventRule(local, event);
    }

    public static GlyphRule eventRule(GlyphEvent event) {
        return eventRule(snapshot, event);
    }

    /** Null when the chat has no rule of its own. */
    public static GlyphRule chatRule(long dialogId) {
        return snapshot.chats.get(dialogId);
    }

    public static Map<Long, GlyphRule> chatRules() {
        return Collections.unmodifiableMap(snapshot.chats);
    }

    public static synchronized void setEventRule(GlyphEvent event, GlyphRule rule) {
        Map<GlyphEvent, GlyphRule> events = new EnumMap<>(snapshot.events);
        events.put(event, rule);
        snapshot = new Snapshot(events, snapshot.chats);
    }

    public static synchronized void setChatRule(long dialogId, GlyphRule rule) {
        Map<Long, GlyphRule> chats = new HashMap<>(snapshot.chats);
        chats.put(dialogId, rule);
        snapshot = new Snapshot(snapshot.events, chats);
    }

    public static synchronized void removeChatRule(long dialogId) {
        Map<Long, GlyphRule> chats = new HashMap<>(snapshot.chats);
        chats.remove(dialogId);
        snapshot = new Snapshot(snapshot.events, chats);
    }

    private static GlyphRule eventRule(Snapshot local, GlyphEvent event) {
        GlyphRule rule = local.events.get(event);
        return rule != null ? rule : GlyphRule.DEFAULT;
    }
}
