package app.exteraless.settings;

import static org.telegram.messenger.LocaleController.getString;

import org.telegram.messenger.R;

import tw.nekomimi.nekogram.settings.NekoExperimentalSettingsActivity;

/**
 * Экран «Experimental Features» в mauve Preferences.
 *
 * Тумблеры — 1:1 с {@link NekoExperimentalSettingsActivity}, общие настройки
 * Neko/NagramX, различие только в заголовке: экран открывается из корневого
 * списка mauve Preferences и виден он только при включённом флаге
 * «Allow Experimental Features».
 */
public class OpenExteraExperimentalActivity extends NekoExperimentalSettingsActivity {

    @Override
    public String getTitle() {
        return getString(R.string.OEExperimentalTitle);
    }
}
