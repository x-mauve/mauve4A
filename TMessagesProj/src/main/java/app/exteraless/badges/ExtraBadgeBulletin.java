package app.exteraless.badges;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;

import androidx.core.content.ContextCompat;

import com.exteragram.messenger.api.dto.BadgeDTO;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.BulletinFactory;

/**
 * Bulletins shown on a badge or client mark tap, with a shortcut to the matching sheet.
 */
public final class ExtraBadgeBulletin {

    private static final int ICON_SIZE_DP = 30;

    private ExtraBadgeBulletin() {
    }

    public static Bulletin show(BaseFragment fragment, BadgeDTO badge) {
        if (fragment == null || fragment.getParentActivity() == null || badge == null) {
            return null;
        }
        CharSequence title = TextUtils.isEmpty(badge.getText())
                ? LocaleController.getString(R.string.ExtraBadgeSheetTitle) : badge.getText();
        Bulletin bulletin = BulletinFactory.of(fragment).createSimpleBulletin(
                roundAppIcon(fragment),
                title,
                LocaleController.getString(R.string.ExtraBadgeBulletinText),
                LocaleController.getString(R.string.ExtraBadgeLearnMore),
                () -> new ExtraBadgeBottomSheet(fragment).show());
        bulletin.show();
        return bulletin;
    }

    public static Bulletin showClientMark(BaseFragment fragment) {
        if (fragment == null || fragment.getParentActivity() == null) {
            return null;
        }
        Drawable seal = ContextCompat.getDrawable(fragment.getParentActivity(), R.drawable.msg_exteraless_seal);
        if (seal != null) {
            seal = seal.mutate();
            seal.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_undo_infoColor, fragment.getResourceProvider()), PorterDuff.Mode.SRC_IN));
        }
        Bulletin bulletin = BulletinFactory.of(fragment).createSimpleBulletin(
                seal,
                LocaleController.getString(R.string.OEProfileClientMark),
                LocaleController.getString(R.string.ClientMarkBulletinText),
                LocaleController.getString(R.string.ExtraBadgeLearnMore),
                () -> new ClientMarkBottomSheet(fragment).show());
        bulletin.show();
        return bulletin;
    }

    // The bulletin image view centers the drawable at its intrinsic size, so prerender a small circle
    private static Drawable roundAppIcon(BaseFragment fragment) {
        Drawable icon = ContextCompat.getDrawable(fragment.getParentActivity(), R.mipmap.ic_launcher_exteraless_round);
        if (icon == null) {
            return null;
        }
        int size = AndroidUtilities.dp(ICON_SIZE_DP);
        Bitmap source = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        icon.setBounds(0, 0, size, size);
        icon.draw(new Canvas(source));

        Bitmap result = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setShader(new BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
        new Canvas(result).drawCircle(size / 2f, size / 2f, size / 2f, paint);
        source.recycle();
        return new BitmapDrawable(fragment.getParentActivity().getResources(), result);
    }
}
