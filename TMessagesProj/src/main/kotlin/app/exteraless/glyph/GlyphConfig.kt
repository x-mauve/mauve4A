package app.exteraless.glyph

import android.content.SharedPreferences
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.FileLog
import tw.nekomimi.nekogram.NekoConfig
import tw.nekomimi.nekogram.config.ConfigItem

/**
 * Settings of the Nothing Glyph integration. Stored like
 * [app.exteraless.OpenExteraConfig]: same SharedPreferences, same [ConfigItem].
 */
object GlyphConfig {

    private val sync = Any()
    private val configs = ArrayList<ConfigItem>()

    /** Per-event toggles that the rules replaced. */
    private val retiredKeys = arrayOf("OEGlyphNewMessage", "OEGlyphCalls", "OEGlyphRecording")

    @JvmStatic
    fun getConfigTypes(): Map<String, Int> = configs.associate { it.key to it.type }

    @Volatile
    private var configLoaded = false

    @JvmStatic
    fun getPreferences(): SharedPreferences = NekoConfig.getPreferences()

    /** Master switch. While it is off the controller never binds to the glyph service. */
    @JvmField
    val enabled = addConfig("OEGlyphEnabled", ConfigItem.configTypeBool, false)

    /** React to messages only while the screen is off. */
    @JvmField
    val screenOffOnly = addConfig("OEGlyphScreenOff", ConfigItem.configTypeBool, false)

    /** The rule set as JSON, parsed and written by [GlyphRules]. */
    @JvmField
    val rules = addConfig("OEGlyphRules", ConfigItem.configTypeString, "")

    @JvmStatic
    fun enabled(): Boolean = enabled.Bool()

    @JvmStatic
    fun screenOffOnly(): Boolean = screenOffOnly.Bool()

    private fun addConfig(key: String, type: Int, defaultValue: Any?): ConfigItem {
        val item = ConfigItem(key, type, defaultValue)
        configs.add(item)
        return item
    }

    @JvmStatic
    fun init() {
        loadConfig(false)
    }

    @JvmStatic
    fun loadConfig(force: Boolean) {
        synchronized(sync) {
            if (configLoaded && !force) return
            if (ApplicationLoader.applicationContext == null) return
            val preferences = getPreferences()
            if (retiredKeys.any { preferences.contains(it) }) {
                val editor = preferences.edit()
                retiredKeys.forEach { editor.remove(it) }
                editor.apply()
            }
            for (item in configs) {
                try {
                    when (item.type) {
                        ConfigItem.configTypeBool ->
                            item.value = preferences.getBoolean(item.key, item.defaultValue as Boolean)
                        ConfigItem.configTypeString ->
                            item.value = preferences.getString(item.key, item.defaultValue as String)
                                ?: item.defaultValue
                    }
                } catch (e: Exception) {
                    FileLog.e(e)
                }
            }
            GlyphRules.load(rules.String())
            configLoaded = true
        }
    }
}
