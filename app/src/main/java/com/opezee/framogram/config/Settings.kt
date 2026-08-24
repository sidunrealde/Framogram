package com.opezee.framogram.config

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "framogram_settings")

data class RecentModel(val name: String, val uri: String)

data class AppSettings(
    /** One-point distance calibration scale factor. */
    val kCal: Float = 1f,
    /** Grid pattern: 0 = lines, 1 = dots, 2 = crosses. */
    val gridPattern: Int = 0,
    /** Index into [SettingsStore.GRID_COLORS]. */
    val gridColorIndex: Int = 0,
    val landscape: Boolean = false,
    /** True = IBL + key light; false = flat shadeless rendering. */
    val lit: Boolean = true,
    /** Key of the last loaded model: "asset:<file>" or "uri:<content-uri>". */
    val lastModel: String = "",
    val recents: List<RecentModel> = emptyList(),
    val debugOverlay: Boolean = false,
)

class SettingsStore(private val context: Context) {

    companion object {
        /** Preset grid colors (r, g, b in linear-ish sRGB floats). */
        val GRID_COLORS: List<Triple<Float, Float, Float>> = listOf(
            Triple(0.20f, 0.80f, 1.00f), // holo cyan
            Triple(0.45f, 1.00f, 0.55f), // green
            Triple(1.00f, 0.45f, 0.85f), // magenta
            Triple(1.00f, 0.75f, 0.25f), // amber
            Triple(0.85f, 0.88f, 0.95f), // white-blue
        )

        private val K_CAL = floatPreferencesKey("k_cal")
        private val GRID_PATTERN = intPreferencesKey("grid_pattern")
        private val GRID_COLOR = intPreferencesKey("grid_color")
        private val LANDSCAPE = booleanPreferencesKey("landscape")
        private val LIT = booleanPreferencesKey("lit")
        private val LAST_MODEL = stringPreferencesKey("last_model")
        private val RECENTS = stringPreferencesKey("recents")
        private val DEBUG = booleanPreferencesKey("debug_overlay")

        private const val FIELD_SEP = "\u0001"
        private const val ROW_SEP = "\u0002"
    }

    val flow: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            kCal = p[K_CAL] ?: 1f,
            gridPattern = p[GRID_PATTERN] ?: 0,
            gridColorIndex = p[GRID_COLOR] ?: 0,
            landscape = p[LANDSCAPE] ?: false,
            lit = p[LIT] ?: true,
            lastModel = p[LAST_MODEL] ?: "",
            recents = decodeRecents(p[RECENTS] ?: ""),
            debugOverlay = p[DEBUG] ?: false,
        )
    }

    suspend fun current(): AppSettings = flow.first()

    suspend fun setKCal(v: Float) = context.dataStore.edit { it[K_CAL] = v }
    suspend fun setGridPattern(v: Int) = context.dataStore.edit { it[GRID_PATTERN] = v }
    suspend fun setGridColorIndex(v: Int) = context.dataStore.edit { it[GRID_COLOR] = v }
    suspend fun setLandscape(v: Boolean) = context.dataStore.edit { it[LANDSCAPE] = v }
    suspend fun setLit(v: Boolean) = context.dataStore.edit { it[LIT] = v }
    suspend fun setLastModel(v: String) = context.dataStore.edit { it[LAST_MODEL] = v }
    suspend fun setDebugOverlay(v: Boolean) = context.dataStore.edit { it[DEBUG] = v }

    suspend fun addRecent(model: RecentModel, max: Int = 10) {
        context.dataStore.edit { p ->
            val list = decodeRecents(p[RECENTS] ?: "").filter { it.uri != model.uri }
            p[RECENTS] = encodeRecents((listOf(model) + list).take(max))
        }
    }

    private fun encodeRecents(list: List<RecentModel>): String =
        list.joinToString(ROW_SEP) { "${it.name}$FIELD_SEP${it.uri}" }

    private fun decodeRecents(s: String): List<RecentModel> =
        s.split(ROW_SEP).mapNotNull { row ->
            val parts = row.split(FIELD_SEP)
            if (parts.size == 2 && parts[1].isNotBlank()) RecentModel(parts[0], parts[1]) else null
        }
}
