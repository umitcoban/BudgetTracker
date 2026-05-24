package com.umit.budgettracker.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val themeKey = stringPreferencesKey("theme")
    private val currencyKey = stringPreferencesKey("currency")

    val themeFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[themeKey] ?: "system"
    }

    val currencyFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[currencyKey] ?: "TRY"
    }

    suspend fun setTheme(theme: String) {
        context.dataStore.edit { preferences ->
            preferences[themeKey] = theme
        }
    }

    suspend fun setCurrency(currency: String) {
        context.dataStore.edit { preferences ->
            preferences[currencyKey] = currency
        }
    }
}
