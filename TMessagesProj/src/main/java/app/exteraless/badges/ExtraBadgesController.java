package app.exteraless.badges;

import android.content.SharedPreferences;
import android.text.TextUtils;

import com.exteragram.messenger.api.dto.BadgeDTO;
import com.exteragram.messenger.api.model.ProfileStatus;
import com.exteragram.messenger.badges.BadgesController;
import com.exteragram.messenger.badges.source.BadgeInfo;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildConfig;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import app.exteraless.OpenExteraConfig;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Loads badges and client marks from the extraBadges server.
 *
 * Views call {@link #request} while binding; ids are batched into a single
 * lookup and the result is announced with {@link NotificationCenter#extraBadgesDidLoad}.
 * Badges land in {@link BadgesController}'s source, so existing drawing code and
 * plugins see them without changes.
 */
public final class ExtraBadgesController {

    private static final long LOOKUP_TTL = TimeUnit.HOURS.toMillis(1);
    private static final long CONFIG_TTL = TimeUnit.HOURS.toMillis(24);
    private static final long BATCH_DELAY = 300;
    private static final int BATCH_LIMIT = 100;
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private static final String PREFS_NAME = "exteraless_extrabadges";
    private static final String KEY_MARK_EMOJI = "mark_emoji_id";
    private static final String KEY_MARK_TEXT = "mark_text";
    private static final String KEY_CONFIG_TIME = "config_time";

    private static final class Entry {
        final long time;
        final boolean client;

        Entry(long time, boolean client) {
            this.time = time;
            this.client = client;
        }
    }

    private static final ConcurrentHashMap<Long, Entry> entries = new ConcurrentHashMap<>();
    private static final HashSet<Long> pending = new HashSet<>();
    private static final HashSet<Long> inFlight = new HashSet<>();
    private static boolean flushScheduled;
    private static boolean configLoading;
    private static OkHttpClient client;

    private ExtraBadgesController() {
    }

    public static boolean isEnabled() {
        return !TextUtils.isEmpty(BuildConfig.EXTRABADGES_URL) && OpenExteraConfig.showExtraBadges.Bool();
    }

    /** Queues a lookup for the user unless fresh data is already cached. */
    public static void request(long userId) {
        if (userId <= 0 || !isEnabled()) {
            return;
        }
        Entry entry = entries.get(userId);
        if (entry != null && System.currentTimeMillis() - entry.time < LOOKUP_TTL) {
            return;
        }
        synchronized (pending) {
            if (inFlight.contains(userId) || !pending.add(userId) || flushScheduled) {
                return;
            }
            flushScheduled = true;
        }
        AndroidUtilities.runOnUIThread(ExtraBadgesController::flush, BATCH_DELAY);
    }

    /**
     * Returns the badge to draw next to a name outside the profile, or null.
     * There the badge takes the emoji status slot, so it is shown only when the
     * user has neither verification nor Premium.
     */
    public static BadgeDTO getChatBadge(TLRPC.User user, int account) {
        if (user == null || !isEnabled() || user.verified || user.scam || user.fake || UserObject.isUserSelf(user)
                || DialogObject.getEmojiStatusDocumentId(user.emoji_status) != 0
                || MessagesController.getInstance(account).isPremiumUser(user)) {
            return null;
        }
        request(user.id);
        return BadgesController.INSTANCE.getApiBadgeSource().getBadge(user.id, true);
    }

    public static boolean isClient(long userId) {
        if (!isEnabled()) {
            return false;
        }
        Entry entry = entries.get(userId);
        return entry != null && entry.client;
    }

    public static long getClientMarkEmojiId() {
        loadConfig();
        return prefs().getLong(KEY_MARK_EMOJI, 0);
    }

    public static String getClientMarkText() {
        loadConfig();
        return prefs().getString(KEY_MARK_TEXT, null);
    }

    /** Forgets every loaded badge and mark, e.g. after badges were turned off. */
    public static void clear() {
        entries.clear();
        BadgesController.INSTANCE.getApiBadgeSource().clear();
    }

    /** Drops cached data for the user, e.g. after the own mark was toggled. */
    public static void invalidate(long userId) {
        entries.remove(userId);
    }

    static String url(String path) {
        String base = BuildConfig.EXTRABADGES_URL;
        return base.endsWith("/") ? base.substring(0, base.length() - 1) + path : base + path;
    }

    static OkHttpClient client() {
        synchronized (ExtraBadgesController.class) {
            if (client == null) {
                client = new OkHttpClient.Builder()
                        .connectTimeout(6, TimeUnit.SECONDS)
                        .readTimeout(6, TimeUnit.SECONDS)
                        .callTimeout(10, TimeUnit.SECONDS)
                        .build();
            }
            return client;
        }
    }

    static RequestBody jsonBody(JSONObject json) {
        return RequestBody.create(json.toString(), JSON);
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS_NAME, 0);
    }

    private static void flush() {
        ArrayList<Long> batch = new ArrayList<>();
        synchronized (pending) {
            flushScheduled = false;
            Iterator<Long> iterator = pending.iterator();
            while (iterator.hasNext() && batch.size() < BATCH_LIMIT) {
                Long id = iterator.next();
                iterator.remove();
                batch.add(id);
            }
            inFlight.addAll(batch);
            if (!pending.isEmpty()) {
                flushScheduled = true;
                AndroidUtilities.runOnUIThread(ExtraBadgesController::flush, BATCH_DELAY);
            }
        }
        if (batch.isEmpty()) {
            return;
        }
        JSONObject body = new JSONObject();
        try {
            body.put("ids", new JSONArray(batch));
        } catch (Exception e) {
            FileLog.e(e);
            finish(batch);
            return;
        }
        Request request = new Request.Builder().url(url("/v1/lookup")).post(jsonBody(body)).build();
        client().newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                finish(batch);
            }

            @Override
            public void onResponse(Call call, Response response) {
                try (response) {
                    if (response.isSuccessful()) {
                        applyLookup(batch, new JSONObject(response.body().string()).optJSONObject("users"));
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                } finally {
                    finish(batch);
                }
            }
        });
    }

    private static void finish(ArrayList<Long> batch) {
        synchronized (pending) {
            inFlight.removeAll(batch);
        }
    }

    private static void applyLookup(ArrayList<Long> batch, JSONObject users) {
        long now = System.currentTimeMillis();
        // Ids missing from the response have neither a mark nor a badge
        for (Long id : batch) {
            JSONObject user = users != null ? users.optJSONObject(String.valueOf(id)) : null;
            BadgeDTO badge = null;
            boolean isClient = false;
            if (user != null) {
                isClient = user.optBoolean("client");
                JSONObject badgeJson = user.optJSONObject("badge");
                if (badgeJson != null && badgeJson.optLong("emoji_id") > 0) {
                    badge = new BadgeDTO(badgeJson.optLong("emoji_id"), badgeJson.optString("description", ""));
                }
            }
            entries.put(id, new Entry(now, isClient));
            BadgesController.INSTANCE.getApiBadgeSource().put(id, badge != null ? new BadgeInfo(badge, ProfileStatus.DEFAULT, false) : null);
        }
        AndroidUtilities.runOnUIThread(() -> NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.extraBadgesDidLoad, batch));
    }

    private static void loadConfig() {
        if (TextUtils.isEmpty(BuildConfig.EXTRABADGES_URL) || configLoading
                || System.currentTimeMillis() - prefs().getLong(KEY_CONFIG_TIME, 0) < CONFIG_TTL) {
            return;
        }
        configLoading = true;
        Request request = new Request.Builder().url(url("/v1/config")).get().build();
        client().newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                configLoading = false;
            }

            @Override
            public void onResponse(Call call, Response response) {
                try (response) {
                    if (!response.isSuccessful()) {
                        return;
                    }
                    JSONObject mark = new JSONObject(response.body().string()).optJSONObject("client_mark");
                    SharedPreferences.Editor editor = prefs().edit().putLong(KEY_CONFIG_TIME, System.currentTimeMillis());
                    if (mark != null) {
                        editor.putLong(KEY_MARK_EMOJI, mark.optLong("emoji_id")).putString(KEY_MARK_TEXT, mark.optString("text", null));
                    } else {
                        editor.remove(KEY_MARK_EMOJI).remove(KEY_MARK_TEXT);
                    }
                    editor.apply();
                    AndroidUtilities.runOnUIThread(() -> NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.extraBadgesDidLoad, new ArrayList<Long>()));
                } catch (Exception e) {
                    FileLog.e(e);
                } finally {
                    configLoading = false;
                }
            }
        });
    }
}
