package app.exteraless.settings;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.app.Activity;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import android.view.View;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

import java.util.Locale;

/**
 * Окно согласия на экспериментальные функции.
 *
 * Показывается по включению «Allow Experimental Features» в Other →
 * Experimental: мобильная-подобная карточка с описанием рисков, красной
 * плашкой и кнопкой «Allow», которая первые тридцать секунд заблокирована
 * отсчётом — чтобы включение было осознанным, а не случайным тапом.
 */
public class ExperimentalFeaturesBottomSheet extends BottomSheet {

    private static final int ALLOW_DELAY_SECONDS = 10;
    private final Runnable onAllow;

    public ExperimentalFeaturesBottomSheet(BaseFragment fragment, Runnable onAllow) {
        super(fragment.getParentActivity(), false, fragment.getResourceProvider());
        this.onAllow = onAllow;
        Activity activity = fragment.getParentActivity();
        fixNavigationBar();
        setApplyBottomPadding(false);
        setApplyTopPadding(false);

        final int red = getThemedColor(Theme.key_text_RedBold);

        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(24), 0, dp(24), dp(12));

        // Круглая иконка, как у окон согласия Material 3: треугольник
        // предупреждения на светло-красном круге. baseline_stop_24 рисуется
        // квадратом, поэтому на нормальном глифе он выглядел красным квадратом.
        FrameLayout badge = new FrameLayout(activity);
        badge.setBackground(Theme.createRoundRectDrawable(dp(200), ColorUtils.setAlphaComponent(red, 38)));
        ImageView icon = new ImageView(activity);
        icon.setImageResource(R.drawable.warning_sign);
        icon.setColorFilter(red);
        badge.addView(icon, LayoutHelper.createFrame(42, 42, Gravity.CENTER));
        content.addView(badge, LayoutHelper.createLinear(72, 72, Gravity.CENTER_HORIZONTAL, 0, 24, 0, 0));

        TextView title = new TextView(activity);
        title.setGravity(Gravity.CENTER);
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        title.setTypeface(org.telegram.messenger.AndroidUtilities.bold());
        title.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        title.setText(getString(R.string.OEExperimentalTitle));
        content.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 20, 0, 0));

        TextView subtitle = new TextView(activity);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        subtitle.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
        subtitle.setText(getString(R.string.OEExperimentalText));
        content.addView(subtitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 10, 0, 0));

        content.addView(createRow(activity, R.drawable.msg_report,
                getString(R.string.OEExperimentalUnstable), getString(R.string.OEExperimentalUnstableInfo)));
        content.addView(createRow(activity, R.drawable.baseline_code_24,
                getString(R.string.OEExperimentalChanges), getString(R.string.OEExperimentalChangesInfo)));
        content.addView(createRow(activity, R.drawable.baseline_lock_24,
                getString(R.string.OEExperimentalSelf), getString(R.string.OEExperimentalSelfInfo)));

        TextView warning = new TextView(activity);
        warning.setGravity(Gravity.CENTER);
        warning.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        warning.setTextColor(red);
        warning.setPadding(dp(16), dp(12), dp(16), dp(12));
        warning.setText(getString(R.string.OEExperimentalWarning));
        GradientDrawable warningBg = new GradientDrawable();
        warningBg.setCornerRadius(dp(20));
        warningBg.setColor(ColorUtils.setAlphaComponent(red, 38));
        warning.setBackground(warningBg);
        content.addView(warning, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 14, 0, 0));

        // Кнопки в порядке «безопасная» → «опасная»: Cancel сверху, Allow снизу
        // c отсчётом, как в окне согласия на неизвестные источники.
        content.addView(createButton(activity, getString(R.string.Cancel), true), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 0, 12, 0, 0));

        content.addView(createAllowButton(activity), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 0, 6, 0, 0));

        ScrollView scroll = new ScrollView(activity);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.addView(content);
        setCustomView(scroll);
    }

    /**
     * Плоская кнопка окна согласия без ButtonWithCounterView: тот заточен под
     * бусты сторис, и его таймер вне сторис не поднимал кнопку после отсчёта.
     * Здесь отсчёт ведём сами: номер идёт в подпись, клики режет setEnabled.
     */
    private TextView createButton(Activity activity, CharSequence label, boolean filled) {
        final int primary = getThemedColor(Theme.key_windowBackgroundWhiteBlueText4);
        final int text = getThemedColor(Theme.key_windowBackgroundWhiteBlackText);
        final int gray = getThemedColor(Theme.key_windowBackgroundWhiteGrayText);

        TextView button = new TextView(activity);
        button.setGravity(Gravity.CENTER);
        button.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        button.setTypeface(org.telegram.messenger.AndroidUtilities.bold());
        if (filled) {
            button.setTextColor(getThemedColor(Theme.key_buttonNeutralText));
            button.setBackground(Theme.createSimpleSelectorRoundRectDrawable(dp(24), primary,
                    ColorUtils.blendARGB(primary, text, 0.16f)));
        } else {
            button.setTextColor(gray);
            button.setBackground(Theme.createSimpleSelectorRoundRectDrawable(dp(24),
                    ColorUtils.setAlphaComponent(gray, 40),
                    ColorUtils.setAlphaComponent(gray, 70)));
        }
        button.setText(label);
        button.setOnClickListener(v -> dismiss());
        return button;
    }

    private TextView createAllowButton(Activity activity) {
        TextView allow = createButton(activity, getString(R.string.OEExperimentalAllow), false);
        allow.setOnClickListener(v -> {
            dismiss();
            if (onAllow != null) {
                onAllow.run();
            }
        });
        startAllowCountdown(allow, ALLOW_DELAY_SECONDS);
        return allow;
    }

    private void startAllowCountdown(TextView allow, int secondsLeft) {
        // Некликабельная, пока идёт отсчёт: disabled View не получает тапы,
        // поэтому setEnabled ровно то, чем должен быть «Allow 28» на фото.
        allow.setEnabled(false);
        allow.setText(String.format(Locale.US, "%s %d",
                getString(R.string.OEExperimentalAllow), secondsLeft));
        AndroidUtilities.runOnUIThread(() -> {
            if (secondsLeft > 1) {
                startAllowCountdown(allow, secondsLeft - 1);
            } else {
                allow.setText(getString(R.string.OEExperimentalAllow));
                allow.setEnabled(true);
            }
        }, 1000);
    }

    private static LinearLayout createRow(Activity activity, int iconRes, CharSequence title, CharSequence description) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(4), dp(10), dp(4), dp(10));

        ImageView icon = new ImageView(activity);
        icon.setImageResource(iconRes);
        icon.setColorFilter(getIconColor(activity));
        row.addView(icon, LayoutHelper.createLinear(24, 24, Gravity.CENTER_VERTICAL));

        LinearLayout texts = new LinearLayout(activity);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(dp(18), 0, 0, 0);
        texts.setGravity(Gravity.CENTER_VERTICAL);

        TextView titleView = new TextView(activity);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        titleView.setTextColor(getThemedStatic(Theme.key_windowBackgroundWhiteBlackText));
        titleView.setText(title);
        texts.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        TextView descriptionView = new TextView(activity);
        descriptionView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        descriptionView.setTextColor(getThemedStatic(Theme.key_windowBackgroundWhiteGrayText));
        descriptionView.setText(description);
        texts.addView(descriptionView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        row.addView(texts, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, Gravity.CENTER_VERTICAL));
        return row;
    }

    private static int getThemedStatic(int key) {
        return Theme.getColor(key);
    }

    private static int getIconColor(Activity activity) {
        return getThemedStatic(Theme.key_windowBackgroundWhiteGrayIcon);
    }
}
