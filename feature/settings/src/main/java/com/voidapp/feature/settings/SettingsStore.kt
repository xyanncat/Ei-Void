package com.voidapp.feature.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class ThemeMode { SYSTEM, LIGHT, DARK }

interface SettingsStore {
    val defaultModel: StateFlow<String?>
    val theme: StateFlow<ThemeMode>

    fun setDefaultModel(modelId: String)
    fun setTheme(mode: ThemeMode)
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

    private val _defaultModel = MutableStateFlow<String?>(null)
    override val defaultModel: StateFlow<String?> = _defaultModel.asStateFlow()

    private val _theme = MutableStateFlow(ThemeMode.SYSTEM)
    override val theme: StateFlow<ThemeMode> = _theme.asStateFlow()

    init {
        scope.launch {
            dataStore.data.collect { preferences ->
                preferences[DEFAULT_MODEL]?.let { _defaultModel.value = it }
                preferences[THEME]
                    ?.let { stored -> ThemeMode.entries.firstOrNull { it.name == stored } }
                    ?.let { _theme.value = it }
            }
        }
    }

    override fun setDefaultModel(modelId: String) {
        _defaultModel.value = modelId
        scope.launch { dataStore.edit { it[DEFAULT_MODEL] = modelId } }
    }

    override fun setTheme(mode: ThemeMode) {
        _theme.value = mode
        scope.launch { dataStore.edit { it[THEME] = mode.name } }
    }

    private companion object {
        val DEFAULT_MODEL = stringPreferencesKey("default_model")
        val THEME = stringPreferencesKey("theme")
    }
}
