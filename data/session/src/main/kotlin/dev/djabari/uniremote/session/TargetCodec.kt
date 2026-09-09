package dev.djabari.uniremote.session

import dev.djabari.uniremote.model.RemoteTarget
import dev.djabari.uniremote.model.TransportId
import dev.djabari.uniremote.model.TvBrand
import org.json.JSONArray
import org.json.JSONObject

/**
 * Saved targets are stored as JSON rather than a delimited string: TV names are
 * user-editable and routinely contain punctuation, which a delimiter scheme silently
 * corrupts.
 */
internal object TargetCodec {

    fun encode(targets: List<RemoteTarget>): String {
        val array = JSONArray()
        targets.forEach { target ->
            array.put(
                JSONObject()
                    .put("id", target.id)
                    .put("displayName", target.displayName)
                    .put("brand", target.brand.name)
                    .put("bluetoothAddress", target.bluetoothAddress)
                    .put("ipAddress", target.ipAddress)
                    .put("macAddress", target.macAddress)
                    .put("preferredTransport", target.preferredTransport?.value),
            )
        }
        return array.toString()
    }

    fun decode(raw: String): List<RemoteTarget> {
        if (raw.isBlank()) return emptyList()
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()

        return (0 until array.length()).mapNotNull { index ->
            val entry = array.optJSONObject(index) ?: return@mapNotNull null
            val id = entry.optString("id").ifBlank { return@mapNotNull null }
            RemoteTarget(
                id = id,
                displayName = entry.optString("displayName").ifBlank { id },
                brand = runCatching { TvBrand.valueOf(entry.optString("brand")) }.getOrDefault(TvBrand.UNKNOWN),
                bluetoothAddress = entry.optStringOrNull("bluetoothAddress"),
                ipAddress = entry.optStringOrNull("ipAddress"),
                macAddress = entry.optStringOrNull("macAddress"),
                preferredTransport = entry.optStringOrNull("preferredTransport")?.let(::TransportId),
            )
        }
    }

    private fun JSONObject.optStringOrNull(name: String): String? =
        if (isNull(name)) null else optString(name).ifBlank { null }
}
