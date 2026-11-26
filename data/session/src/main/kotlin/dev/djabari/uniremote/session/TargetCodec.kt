package dev.djabari.uniremote.session

import dev.djabari.uniremote.model.RemoteTarget
import dev.djabari.uniremote.model.TvBrand
import org.json.JSONArray
import org.json.JSONObject

/**
 * Saved targets are stored as JSON rather than a delimited string: TV names are
 * user-editable and routinely contain punctuation, which a delimiter scheme silently
 * corrupts.
 *
 * Entries without a Bluetooth address were saved by the removed Wi-Fi transport. Nothing can
 * reach them any more, so decoding drops them.
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
                    .put("bluetoothAddress", target.bluetoothAddress),
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
            val bluetoothAddress = entry.optStringOrNull("bluetoothAddress") ?: return@mapNotNull null
            RemoteTarget(
                id = id,
                displayName = entry.optString("displayName").ifBlank { id },
                brand = runCatching { TvBrand.valueOf(entry.optString("brand")) }.getOrDefault(TvBrand.UNKNOWN),
                bluetoothAddress = bluetoothAddress,
            )
        }
    }

    private fun JSONObject.optStringOrNull(name: String): String? =
        if (isNull(name)) null else optString(name).ifBlank { null }
}
