package com.voidapp.feature.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.stringPreferencesKey
import com.voidapp.core.ai.Tier
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The mirror must stay in step with DataStore: the settings screen reads the
 * mirror, and the next app start reads the file.
 */
class DataStoreSettingsStoreTest {

    @Test
    fun `the preferred tier is written to disk`() = runTest {
        val file = settingsFile()
        val dataStore = PreferenceDataStoreFactory.create(scope = backgroundScope) { file }
        val store = DataStoreSettingsStore(dataStore, backgroundScope)

        store.setPreferredTier(Tier.RED_DWARF)

        assertEquals(Tier.RED_DWARF, store.preferredTier.value)
        val stored = dataStore.data.first { it[TIER_KEY] == Tier.RED_DWARF.name }
        assertEquals(Tier.RED_DWARF.name, stored[TIER_KEY])
        assertTrue(file.readText().contains(Tier.RED_DWARF.name))
    }

    @Test
    fun `auto clears the stored tier`() = runTest {
        val file = settingsFile()
        val dataStore = PreferenceDataStoreFactory.create(scope = backgroundScope) { file }
        val store = DataStoreSettingsStore(dataStore, backgroundScope)

        store.setPreferredTier(Tier.NEUTRON_STAR)
        store.setPreferredTier(null)

        assertNull(store.preferredTier.value)
        val stored = dataStore.data.first { it[TIER_KEY] == null }
        assertNull(stored[TIER_KEY])
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

        store.setPreferredTier(Tier.WHITE_DWARF)

        assertEquals(Tier.WHITE_DWARF, store.preferredTier.value)
    }

    private fun settingsFile(): File {
        val directory = Files.createTempDirectory("void-settings").toFile()
        return File(directory, "settings.preferences_pb")
    }

    private companion object {
        val TIER_KEY = stringPreferencesKey("preferred_tier")
        val THEME_KEY = stringPreferencesKey("theme")
    }
}
