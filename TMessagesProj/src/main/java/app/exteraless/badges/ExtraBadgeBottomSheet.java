package app.exteraless.badges;

import android.content.Context;
import android.graphics.Outline;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
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
 * Explains what the extraBadges badge next to a user's name means.
 */
public class ExtraBadgeBottomSheet extends BottomSheet {

    public ExtraBadgeBottomSheet(BaseFragment fragment) {
        super(fragment.getParentActivity(), false, fragment.getResourceProvider());
        Context context = fragment.getParentActivity();
        fixNavigationBar();

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);

        // The launcher icon is adaptive and isn't masked outside the launcher, so clip it by hand
        ImageView icon = new ImageView(context);
        icon.setImageResource(R.mipmap.ic_launcher_exteraless_round);
        icon.setScaleType(ImageView.ScaleType.CENTER_CROP);
        icon.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setOval(0, 0, view.getWidth(), view.getHeight());
            }
        });
        icon.setClipToOutline(true);
        content.addView(icon, LayoutHelper.createLinear(90, 90, Gravity.CENTER_HORIZONTAL, 0, 24, 0, 0));

        TextView title = new TextView(context);
        title.setGravity(Gravity.CENTER);
        title.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        title.setTypeface(AndroidUtilities.bold());
        title.setText(LocaleController.getString(R.string.ExtraBadgeSheetTitle));
        content.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 24, 16, 24, 0));

        TextView subtitle = new TextView(context);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        subtitle.setText(LocaleController.getString(R.string.ExtraBadgeSheetSubtitle));
        content.addView(subtitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 24, 8, 24, 12));

        content.addView(featureRow(context, R.drawable.msg_admins,
                R.string.ExtraBadgeSheetTeamTitle, R.string.ExtraBadgeSheetTeamText));
        content.addView(featureRow(context, R.drawable.msg_emoji_gem,
                R.string.ExtraBadgeSheetUniqueTitle, R.string.ExtraBadgeSheetUniqueText));
        content.addView(featureRow(context, R.drawable.msg_contacts,
                R.string.ExtraBadgeSheetVisibleTitle, R.string.ExtraBadgeSheetVisibleText));

        // Same pill button as ExperimentalFeaturesBottomSheet
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

        setCustomView(content);
    }

    private View featureRow(Context context, int iconRes, int titleRes, int textRes) {
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

        LinearLayout.LayoutParams params = LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 24, 12, 24, 0);
        row.setLayoutParams(params);
        return row;
    }
}
