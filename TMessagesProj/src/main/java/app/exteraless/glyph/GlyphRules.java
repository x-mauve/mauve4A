package app.exteraless.glyph;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.FileLog;

import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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

    private static final int VERSION = 1;

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
        save();
    }

    public static synchronized void setChatRule(long dialogId, GlyphRule rule) {
        Map<Long, GlyphRule> chats = new HashMap<>(snapshot.chats);
        chats.put(dialogId, rule);
        snapshot = new Snapshot(snapshot.events, chats);
        save();
    }

    public static synchronized void removeChatRule(long dialogId) {
        Map<Long, GlyphRule> chats = new HashMap<>(snapshot.chats);
        chats.remove(dialogId);
        snapshot = new Snapshot(snapshot.events, chats);
        save();
    }

    /** Replaces the rule set with the stored one. Anything unreadable falls back to defaults. */
    static synchronized void load(String json) {
        snapshot = parse(json);
    }

    private static void save() {
        GlyphConfig.rules.setConfigString(serialize(snapshot));
    }

    private static Snapshot parse(String json) {
        if (json == null || json.isEmpty()) {
            return EMPTY;
        }
        try {
            JSONObject root = new JSONObject(json);
            if (root.optInt("v") != VERSION) {
                return EMPTY;
            }
            Map<GlyphEvent, GlyphRule> events = new EnumMap<>(GlyphEvent.class);
            JSONObject eventsJson = root.optJSONObject("events");
            if (eventsJson != null) {
                for (GlyphEvent event : GlyphEvent.values()) {
                    JSONObject ruleJson = eventsJson.optJSONObject(eventKey(event));
                    if (ruleJson != null) {
                        events.put(event, parseRule(ruleJson));
                    }
                }
            }
            Map<Long, GlyphRule> chats = new HashMap<>();
            JSONObject chatsJson = root.optJSONObject("chats");
            if (chatsJson != null) {
                for (Iterator<String> it = chatsJson.keys(); it.hasNext(); ) {
                    String key = it.next();
                    JSONObject ruleJson = chatsJson.optJSONObject(key);
                    if (ruleJson == null) {
                        continue;
                    }
                    try {
                        chats.put(Long.parseLong(key), parseRule(ruleJson));
                    } catch (NumberFormatException ignore) {
                    }
                }
            }
            return new Snapshot(events, chats);
        } catch (Exception e) {
            FileLog.e(e);
            return EMPTY;
        }
    }

    private static GlyphRule parseRule(JSONObject json) {
        Set<String> zones = new LinkedHashSet<>();
        JSONArray zonesJson = json.optJSONArray("zones");
        if (zonesJson != null) {
            for (int i = 0; i < zonesJson.length(); i++) {
                String zone = zonesJson.optString(i, "");
                if (!zone.isEmpty()) {
                    zones.add(zone);
                }
            }
        }
        return new GlyphRule(json.optBoolean("on", true), zones);
    }

    private static String serialize(Snapshot local) {
        try {
            JSONObject events = new JSONObject();
            for (Map.Entry<GlyphEvent, GlyphRule> entry : local.events.entrySet()) {
                events.put(eventKey(entry.getKey()), serializeRule(entry.getValue()));
            }
            JSONObject chats = new JSONObject();
            for (Map.Entry<Long, GlyphRule> entry : local.chats.entrySet()) {
                chats.put(String.valueOf(entry.getKey()), serializeRule(entry.getValue()));
            }
            return new JSONObject()
                    .put("v", VERSION)
                    .put("events", events)
                    .put("chats", chats)
                    .toString();
        } catch (Exception e) {
            FileLog.e(e);
            return "";
        }
    }

    private static JSONObject serializeRule(GlyphRule rule) throws Exception {
        return new JSONObject()
                .put("on", rule.enabled)
                .put("zones", new JSONArray(rule.zones));
    }

    private static String eventKey(GlyphEvent event) {
        return event.name().toLowerCase(Locale.ROOT);
    }

    private static GlyphRule eventRule(Snapshot local, GlyphEvent event) {
        GlyphRule rule = local.events.get(event);
        return rule != null ? rule : GlyphRule.DEFAULT;
    }
}
