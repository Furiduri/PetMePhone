package com.gcatcode.petmephone.core.data.config

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import com.gcatcode.petmephone.core.domain.config.ConfigField
import com.gcatcode.petmephone.core.domain.config.ConfigOverrideStore
import com.gcatcode.petmephone.core.domain.config.ConfigWriteResult
import com.gcatcode.petmephone.core.domain.config.StoredOverride
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/**
 * Implements [ConfigOverrideStore] over the existing `petmephone_prefs` [DataStore] instance
 * (design decisions 7, 8) — no second `DataStore<Preferences>` is created here. Exactly one
 * private [editStore] helper is the sole call site every public write routes through, which is
 * what [ConfigStoreNoBulkWriteCodeTest] pins structurally: `set` and `reset` cannot diverge into
 * a second `edit` block that touches more than one field.
 */
class PreferencesConfigOverrideStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : ConfigOverrideStore {

    override fun <T : Comparable<T>> override(field: ConfigField<T>): Flow<StoredOverride<T>> =
        dataStore.data
            // A corrupt/failed read is absence, never a partial or zeroed value (design decision
            // 7, `config-override-store` spec — "Absence never resolves to zero").
            .catch { throwable -> if (throwable is IOException) emit(emptyPreferences()) else throw throwable }
            .map { preferences -> readStored(field, preferences) }

    override suspend fun <T : Comparable<T>> set(field: ConfigField<T>, value: T): ConfigWriteResult {
        if (value < field.min || value > field.max) {
            return ConfigWriteResult.OutOfRange(key = field.key, min = field.min, max = field.max, offending = value)
        }
        editStore { preferences ->
            preferences[keyFor(field, field.key)] = value
            field.group.currentVersion?.let { currentVersion ->
                preferences[versionKeyFor(field)] = currentVersion
            }
        }
        return ConfigWriteResult.Accepted
    }

    override suspend fun <T : Comparable<T>> reset(field: ConfigField<T>) {
        // Deletes the entry; never rewrites the current default as an explicit value (spec —
        // "Reset deletes the entry, never rewrites the current default as a value"). The version
        // stamp goes with it: a field with no override has nothing to date.
        editStore { preferences ->
            preferences.remove(keyFor(field, field.key))
            preferences.remove(versionKeyFor(field))
        }
    }

    /** The one `edit` call site in this file. Every public write routes through it. */
    private suspend fun editStore(transform: (MutablePreferences) -> Unit) {
        dataStore.edit { preferences -> transform(preferences) }
    }

    private fun <T : Comparable<T>> readStored(field: ConfigField<T>, preferences: Preferences): StoredOverride<T> {
        val storedValue = preferences[keyFor(field, field.key)]
            ?: field.previousKeys.firstNotNullOfOrNull { legacyKey -> preferences[keyFor(field, legacyKey)] }
        return if (storedValue == null) {
            StoredOverride.Absent
        } else {
            val writtenUnderVersion =
                if (field.group.currentVersion != null) preferences[versionKeyFor(field)] else null
            StoredOverride.Present(value = storedValue, writtenUnderVersion = writtenUnderVersion)
        }
    }

    /** Exhaustive over [ConfigField]'s closed hierarchy (design decision 1a) — no reflection. */
    @Suppress("UNCHECKED_CAST")
    private fun <T : Comparable<T>> keyFor(field: ConfigField<T>, keyString: String): Preferences.Key<T> =
        when (field) {
            is ConfigField.IntField -> intPreferencesKey(keyString) as Preferences.Key<T>
            is ConfigField.LongField -> longPreferencesKey(keyString) as Preferences.Key<T>
            is ConfigField.DoubleField -> doublePreferencesKey(keyString) as Preferences.Key<T>
        }

    /**
     * The version stamp is **per field**, not per group (issue #91 — "store the `version` that was
     * current when each override was written").
     *
     * A single stamp shared across a group cannot answer the question staleness exists to answer.
     * Tune one field under version 1, ship version 2, tune a second field: a group-wide stamp moves
     * to 2 and the first field — untouched since version 1 — starts reading as a fresh decision.
     * That is precisely the state the issue calls out, a stale number indistinguishable from a
     * current one.
     */
    private fun <T : Comparable<T>> versionKeyFor(field: ConfigField<T>) =
        intPreferencesKey("${field.key}.written_under_version")
}
