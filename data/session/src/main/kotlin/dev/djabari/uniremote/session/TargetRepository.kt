package dev.djabari.uniremote.session

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.djabari.uniremote.model.RemoteTarget
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The slice of target persistence the session needs. Split out so the session's transport
 * selection and reconnect logic can be exercised without DataStore or a device.
 */
interface TargetStore {
    val savedTargets: Flow<List<RemoteTarget>>
    val lastConnectedTargetId: Flow<String?>
    suspend fun saveTarget(target: RemoteTarget)
    suspend fun setLastConnectedTargetId(targetId: String)
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "uniremote_targets")

@Singleton
class TargetRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : TargetStore {
    private val keyTargets = stringPreferencesKey("saved_targets")
    private val keyLastTargetId = stringPreferencesKey("last_connected_target_id")

    override val savedTargets: Flow<List<RemoteTarget>> = context.dataStore.data.map { prefs ->
        val raw = prefs[keyTargets] ?: return@map emptyList<RemoteTarget>()
        TargetCodec.decode(raw)
    }

    override val lastConnectedTargetId: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[keyLastTargetId]
    }

    override suspend fun saveTarget(target: RemoteTarget) {
        context.dataStore.edit { prefs ->
            val current = TargetCodec.decode(prefs[keyTargets] ?: "").toMutableList()
            val index = current.indexOfFirst { it.id == target.id }
            if (index >= 0) {
                current[index] = target
            } else {
                current.add(target)
            }
            prefs[keyTargets] = TargetCodec.encode(current)
            prefs[keyLastTargetId] = target.id
        }
    }

    suspend fun removeTarget(targetId: String) {
        context.dataStore.edit { prefs ->
            val current = TargetCodec.decode(prefs[keyTargets] ?: "").filter { it.id != targetId }
            prefs[keyTargets] = TargetCodec.encode(current)
            if (prefs[keyLastTargetId] == targetId) {
                prefs.remove(keyLastTargetId)
            }
        }
    }

    override suspend fun setLastConnectedTargetId(targetId: String) {
        context.dataStore.edit { prefs ->
            prefs[keyLastTargetId] = targetId
        }
    }
}
