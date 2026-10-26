package app.exteraless.badges;

import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BaseFragment;

/**
 * Explains the "uses mauve" mark shown in the profile info section.
 */
public class ClientMarkBottomSheet extends BadgeInfoBottomSheet {

    public ClientMarkBottomSheet(BaseFragment fragment) {
        super(fragment);
        addIconHeader(R.drawable.msg_exteraless_seal);
        addTitle(R.string.ClientMarkSheetTitle, R.string.ClientMarkSheetSubtitle);
        addFeatureRow(R.drawable.msg_plugins, R.string.ClientMarkSheetPluginsTitle, R.string.ClientMarkSheetPluginsText);
        addFeatureRow(R.drawable.msg_palette, R.string.ClientMarkSheetCustomizeTitle, R.string.ClientMarkSheetCustomizeText);
        addFeatureRow(R.drawable.msg_download, R.string.ClientMarkSheetDownloadTitle, R.string.ClientMarkSheetDownloadText);
        addCloseButton();
    }
}
