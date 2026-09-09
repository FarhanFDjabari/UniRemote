package dev.djabari.uniremote.transport.network

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Per-target protocol secrets: the Tizen session token and the webOS client key.
 *
 * Both are handed out by the TV the first time the user accepts the on-screen prompt, and
 * both must survive a restart — losing them means the user gets that prompt again on every
 * connect, which is exactly the friction this app exists to remove.
 */
interface NetworkCredentials {
    suspend fun get(targetId: String, name: String): String?
    suspend fun put(targetId: String, name: String, value: String)
}

private val Context.credentialStore: DataStore<Preferences> by preferencesDataStore(name = "uniremote_credentials")

@Singleton
class DataStoreNetworkCredentials @Inject constructor(
    @ApplicationContext private val context: Context,
) : NetworkCredentials {

    override suspend fun get(targetId: String, name: String): String? =
        context.credentialStore.data.first()[key(targetId, name)]

    override suspend fun put(targetId: String, name: String, value: String) {
        context.credentialStore.edit { it[key(targetId, name)] = value }
    }

    private fun key(targetId: String, name: String) = stringPreferencesKey("$targetId/$name")
}
