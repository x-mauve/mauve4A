package app.exteraless.badges;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

/**
 * Shared layout for sheets that explain a profile badge: header, title, feature rows, close button.
 */
public abstract class BadgeInfoBottomSheet extends BottomSheet {

    protected final Context context;
    protected final LinearLayout content;

    protected BadgeInfoBottomSheet(BaseFragment fragment) {
        super(fragment.getParentActivity(), false, fragment.getResourceProvider());
        context = fragment.getParentActivity();
        fixNavigationBar();
        content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        setCustomView(content);
    }

    protected void addHeader(View header, int size) {
        content.addView(header, LayoutHelper.createLinear(size, size, Gravity.CENTER_HORIZONTAL, 0, 24, 0, 0));
    }

    // Same tinted circle as ExperimentalFeaturesBottomSheet, but in the theme accent
    protected void addIconHeader(int iconRes) {
        int accent = getThemedColor(Theme.key_windowBackgroundWhiteBlueText4);
        FrameLayout circle = new FrameLayout(context);
        circle.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(200), ColorUtils.setAlphaComponent(accent, 38)));
        ImageView icon = new ImageView(context);
        icon.setImageResource(iconRes);
        icon.setColorFilter(new PorterDuffColorFilter(accent, PorterDuff.Mode.SRC_IN));
        circle.addView(icon, LayoutHelper.createFrame(42, 42, Gravity.CENTER));
        addHeader(circle, 72);
    }

    protected void addTitle(int titleRes, int subtitleRes) {
        TextView title = new TextView(context);
        title.setGravity(Gravity.CENTER);
        title.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        title.setTypeface(AndroidUtilities.bold());
        title.setText(LocaleController.getString(titleRes));
        content.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 24, 16, 24, 0));

        TextView subtitle = new TextView(context);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        subtitle.setText(LocaleController.getString(subtitleRes));
        content.addView(subtitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 24, 8, 24, 12));
    }

    protected void addFeatureRow(int iconRes, int titleRes, int textRes) {
        FrameLayout row = new FrameLayout(context);

        ImageView icon = new ImageView(context);
        icon.setImageResource(iconRes);
        icon.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_windowBackgroundWhiteBlackText), PorterDuff.Mode.SRC_IN));
        row.addView(icon, LayoutHelper.createFrame(24, 24, Gravity.TOP | Gravity.LEFT, 0, 2, 0, 0));

        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(context);
        title.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        title.setTypeface(AndroidUtilities.bold());
        title.setText(LocaleController.getString(titleRes));
        texts.addView(title);

        TextView text = new TextView(context);
        text.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
        text.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        text.setText(LocaleController.getString(textRes));
        texts.addView(text, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));

        row.addView(texts, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP, 44, 0, 0, 0));
        content.addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 24, 12, 24, 0));
    }

    // Same pill button as ExperimentalFeaturesBottomSheet
    protected void addCloseButton() {
        final int primary = getThemedColor(Theme.key_windowBackgroundWhiteBlueText4);
        TextView button = new TextView(context);
        button.setGravity(Gravity.CENTER);
        button.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        button.setTypeface(AndroidUtilities.bold());
        button.setTextColor(getThemedColor(Theme.key_featuredStickers_buttonText));
        button.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(24), primary,
                ColorUtils.blendARGB(primary, getThemedColor(Theme.key_windowBackgroundWhiteBlackText), 0.16f)));
        button.setText(LocaleController.getString(R.string.Close));
        button.setOnClickListener(v -> dismiss());
        content.addView(button, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 16, 16, 16, 16));
    }
}
