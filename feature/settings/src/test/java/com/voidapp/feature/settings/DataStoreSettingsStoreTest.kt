package com.voidapp.feature.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The mirror must stay in step with DataStore: the settings screen reads the
 * mirror, and the next app start reads the file.
 */
class DataStoreSettingsStoreTest {

    @Test
    fun `the default model is written to disk`() = runTest {
        val file = settingsFile()
        val dataStore = PreferenceDataStoreFactory.create(scope = backgroundScope) { file }
        val store = DataStoreSettingsStore(dataStore, backgroundScope)

        store.setDefaultModel("google/gemini-2.0-flash")

        assertEquals("google/gemini-2.0-flash", store.defaultModel.value)
        val stored = dataStore.data.first { it[MODEL_KEY] == "google/gemini-2.0-flash" }
        assertEquals("google/gemini-2.0-flash", stored[MODEL_KEY])
        assertTrue(file.readText().contains("google/gemini-2.0-flash"))
    }

    @Test
    fun `the theme is written to disk`() = runTest {
        val file = settingsFile()
        val dataStore = PreferenceDataStoreFactory.create(scope = backgroundScope) { file }
        val store = DataStoreSettingsStore(dataStore, backgroundScope)

        store.setTheme(ThemeMode.DARK)

        assertEquals(ThemeMode.DARK, store.theme.value)
        val stored = dataStore.data.first { it[THEME_KEY] == ThemeMode.DARK.name }
        assertEquals(ThemeMode.DARK.name, stored[THEME_KEY])
    }

    @Test
    fun `a value written before the first emission is not overwritten`() = runTest {
        val file = settingsFile()
        val dataStore = PreferenceDataStoreFactory.create(scope = backgroundScope) { file }
        val store = DataStoreSettingsStore(dataStore, backgroundScope)

        store.setDefaultModel("written-first")

        assertEquals("written-first", store.defaultModel.value)
    }

    private fun settingsFile(): File {
        val directory = Files.createTempDirectory("void-settings").toFile()
        return File(directory, "settings.preferences_pb")
    }

    private companion object {
        val MODEL_KEY = stringPreferencesKey("default_model")
        val THEME_KEY = stringPreferencesKey("theme")
    }
}
