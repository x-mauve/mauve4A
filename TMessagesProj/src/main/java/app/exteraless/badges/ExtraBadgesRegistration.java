package app.exteraless.badges;

import android.content.SharedPreferences;
import android.net.Uri;
import android.text.TextUtils;

import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildConfig;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import app.exteraless.OpenExteraConfig;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Tells the extraBadges server which accounts use the client.
 *
 * Ownership of an account is proven with Mini App init data: Telegram signs it
 * with the token of the extraBadges bot, so the server can trust the user id
 * inside. The Mini App itself is never opened, only its URL is requested.
 */
public final class ExtraBadgesRegistration {

    private static final long REGISTER_INTERVAL = TimeUnit.HOURS.toMillis(24);
    private static final String PREFS_NAME = "exteraless_extrabadges_registration";
    private static final String KEY_REGISTERED = "registered_";
    private static final String KEY_HIDDEN = "hidden_";

    private static final boolean[] running = new boolean[UserConfig.MAX_ACCOUNT_COUNT];

    private ExtraBadgesRegistration() {
    }

    private static boolean isConfigured() {
        return !TextUtils.isEmpty(BuildConfig.EXTRABADGES_URL) && !TextUtils.isEmpty(BuildConfig.EXTRABADGES_BOT);
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS_NAME, 0);
    }

    /** Registers or hides every logged-in account when its state is due. */
    public static void checkAll() {
        if (!isConfigured()) {
            return;
        }
        for (int account = 0; account < UserConfig.MAX_ACCOUNT_COUNT; account++) {
            if (UserConfig.getInstance(account).isClientActivated()) {
                check(account, false);
            }
        }
    }

    /** Applies a change of the client mark switch right away. */
    public static void onClientMarkChanged() {
        if (!isConfigured()) {
            return;
        }
        for (int account = 0; account < UserConfig.MAX_ACCOUNT_COUNT; account++) {
            if (UserConfig.getInstance(account).isClientActivated()) {
                check(account, true);
            }
        }
    }

    private static void check(int account, boolean force) {
        long userId = UserConfig.getInstance(account).getClientUserId();
        if (userId == 0 || running[account]) {
            return;
        }
        boolean show = OpenExteraConfig.showClientMark.Bool();
        SharedPreferences preferences = prefs();
        if (show) {
            long registered = preferences.getLong(KEY_REGISTERED + userId, 0);
            if (!force && System.currentTimeMillis() - registered < REGISTER_INTERVAL) {
                return;
            }
        } else if (preferences.getBoolean(KEY_HIDDEN + userId, false)) {
            return;
        }
        running[account] = true;
        requestInitData(account, initData -> {
            if (initData == null) {
                running[account] = false;
                return;
            }
            send(show, initData, success -> AndroidUtilities.runOnUIThread(() -> {
                running[account] = false;
                if (!success) {
                    return;
                }
                SharedPreferences.Editor editor = prefs().edit();
                if (show) {
                    editor.putLong(KEY_REGISTERED + userId, System.currentTimeMillis()).remove(KEY_HIDDEN + userId);
                } else {
                    editor.putBoolean(KEY_HIDDEN + userId, true).remove(KEY_REGISTERED + userId);
                }
                editor.apply();
                ExtraBadgesController.invalidate(userId);
            }));
        });
    }

    private static void requestInitData(int account, Utilities.Callback<String> callback) {
        MessagesController controller = MessagesController.getInstance(account);
        controller.getUserNameResolver().resolve(BuildConfig.EXTRABADGES_BOT, peerId -> {
            TLRPC.InputUser bot = peerId != null && peerId > 0 ? controller.getInputUser(peerId) : null;
            if (bot == null || bot instanceof TLRPC.TL_inputUserEmpty) {
                callback.run(null);
                return;
            }
            TLRPC.TL_messages_requestSimpleWebView req = new TLRPC.TL_messages_requestSimpleWebView();
            req.bot = bot;
            req.platform = "android";
            req.url = BuildConfig.EXTRABADGES_URL;
            req.flags |= 8;
            ConnectionsManager.getInstance(account).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                String initData = null;
                if (response instanceof TLRPC.TL_webViewResultUrl) {
                    initData = extractInitData(((TLRPC.TL_webViewResultUrl) response).url);
                    if (initData == null) {
                        FileLog.e("extraBadges: no init data in the Mini App URL");
                    }
                } else if (error != null) {
                    FileLog.e("extraBadges: init data request failed: " + error.text);
                }
                callback.run(initData);
            }));
        });
    }

    // Telegram puts the signed data into the URL fragment as tgWebAppData
    private static String extractInitData(String url) {
        if (TextUtils.isEmpty(url)) {
            return null;
        }
        String fragment = Uri.parse(url).getEncodedFragment();
        if (TextUtils.isEmpty(fragment)) {
            return null;
        }
        for (String part : fragment.split("&")) {
            if (part.startsWith("tgWebAppData=")) {
                return Uri.decode(part.substring("tgWebAppData=".length()));
            }
        }
        return null;
    }

    private static void send(boolean register, String initData, Utilities.Callback<Boolean> callback) {
        JSONObject body = new JSONObject();
        try {
            body.put("init_data", initData);
        } catch (Exception e) {
            FileLog.e(e);
            callback.run(false);
            return;
        }
        Request.Builder builder = new Request.Builder();
        if (register) {
            builder.url(ExtraBadgesController.url("/v1/clients/register")).post(ExtraBadgesController.jsonBody(body));
        } else {
            builder.url(ExtraBadgesController.url("/v1/clients/me")).delete(ExtraBadgesController.jsonBody(body));
        }
        ExtraBadgesController.client().newCall(builder.build()).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                FileLog.e("extraBadges: " + (register ? "register" : "hide") + " failed", e);
                callback.run(false);
            }

            @Override
            public void onResponse(Call call, Response response) {
                try (response) {
                    if (!response.isSuccessful()) {
                        FileLog.e("extraBadges: " + (register ? "register" : "hide") + " failed with " + response.code());
                    }
                    callback.run(response.isSuccessful());
                }
            }
        });
    }
}
