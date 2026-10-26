package app.exteraless.badges;

import android.graphics.Outline;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;

import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BaseFragment;

/**
 * Explains what the extraBadges badge next to a user's name means.
 */
public class ExtraBadgeBottomSheet extends BadgeInfoBottomSheet {

    public ExtraBadgeBottomSheet(BaseFragment fragment) {
        super(fragment);

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
        addHeader(icon, 90);

        addTitle(R.string.ExtraBadgeSheetTitle, R.string.ExtraBadgeSheetSubtitle);
        addFeatureRow(R.drawable.msg_admins, R.string.ExtraBadgeSheetTeamTitle, R.string.ExtraBadgeSheetTeamText);
        addFeatureRow(R.drawable.msg_emoji_gem, R.string.ExtraBadgeSheetUniqueTitle, R.string.ExtraBadgeSheetUniqueText);
        addFeatureRow(R.drawable.msg_contacts, R.string.ExtraBadgeSheetVisibleTitle, R.string.ExtraBadgeSheetVisibleText);
        addCloseButton();
    }
}
