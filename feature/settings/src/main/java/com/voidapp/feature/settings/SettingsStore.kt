package com.voidapp.feature.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.voidapp.core.ai.Tier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class ThemeMode { SYSTEM, LIGHT, DARK }

interface SettingsStore {
    val theme: StateFlow<ThemeMode>

    /**
     * The tier the user wants, or null for "let the router decide".
     */
    val preferredTier: StateFlow<Tier?>

    fun setTheme(mode: ThemeMode)
    fun setPreferredTier(tier: Tier?)
}

private val Context.settingsDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "void_settings")

/** The app's settings store, backed by DataStore. */
fun settingsDataStore(context: Context): DataStore<Preferences> = context.settingsDataStore

/**
 * DataStore-backed settings with an in-memory mirror, so the UI and the
 * ViewModel can read synchronously. Writes update the mirror first and then
 * persist.
 *
 * Only stored values are applied to the mirror: a value written before
 * DataStore's first emission is never overwritten by the older stored value.
 */
class DataStoreSettingsStore(
    private val dataStore: DataStore<Preferences>,
    private val scope: CoroutineScope,
) : SettingsStore {

    private val _theme = MutableStateFlow(ThemeMode.SYSTEM)
    override val theme: StateFlow<ThemeMode> = _theme.asStateFlow()

    private val _preferredTier = MutableStateFlow<Tier?>(null)
    override val preferredTier: StateFlow<Tier?> = _preferredTier.asStateFlow()

    init {
        scope.launch {
            dataStore.data.collect { preferences ->
                preferences[THEME]
                    ?.let { stored -> ThemeMode.entries.firstOrNull { it.name == stored } }
                    ?.let { _theme.value = it }

                preferences[PREFERRED_TIER]
                    ?.let { stored -> Tier.entries.firstOrNull { it.name == stored } }
                    ?.let { _preferredTier.value = it }
            }
        }
    }

    override fun setTheme(mode: ThemeMode) {
        _theme.value = mode
        scope.launch { dataStore.edit { it[THEME] = mode.name } }
    }

    override fun setPreferredTier(tier: Tier?) {
        _preferredTier.value = tier
        scope.launch {
            dataStore.edit { preferences ->
                if (tier == null) {
                    preferences.remove(PREFERRED_TIER)
                } else {
                    preferences[PREFERRED_TIER] = tier.name
                }
            }
        }
    }

    private companion object {
        val THEME = stringPreferencesKey("theme")
        val PREFERRED_TIER = stringPreferencesKey("preferred_tier")
    }
}
