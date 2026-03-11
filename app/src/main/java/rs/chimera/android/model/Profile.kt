package rs.chimera.android.model

import org.json.JSONObject
import java.util.UUID

enum class ProfileType {
    LOCAL,
    REMOTE,
}

data class Profile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val filePath: String,
    val createdAt: Long = System.currentTimeMillis(),
    val isActive: Boolean = false,
    val fileSize: Long = 0,
    val type: ProfileType = ProfileType.LOCAL,
    val url: String? = null,
    val lastUpdated: Long? = null,
    val autoUpdate: Boolean = false,
    val userAgent: String? = null,
    val proxyUrl: String? = null,
) {
    constructor(jsonObject: JSONObject) : this(
        id = jsonObject.getString("id"),
        name = jsonObject.getString("name"),
        filePath = jsonObject.getString("filePath"),
        createdAt = jsonObject.getLong("createdAt"),
        isActive = jsonObject.getBoolean("isActive"),
        fileSize = jsonObject.getLong("fileSize"),
        type = ProfileType.valueOf(jsonObject.optString("type", ProfileType.LOCAL.name)),
        url = jsonObject.optString("url").takeIf { it.isNotBlank() },
        lastUpdated = jsonObject.takeIf { it.has("lastUpdated") }?.getLong("lastUpdated"),
        autoUpdate = jsonObject.optBoolean("autoUpdate", false),
        userAgent = jsonObject.optString("userAgent").takeIf { it.isNotBlank() },
        proxyUrl = jsonObject.optString("proxyUrl").takeIf { it.isNotBlank() },
    )

    fun asJsonObject(): JSONObject {
        val jsonObject = JSONObject()
        jsonObject.put("id", id)
        jsonObject.put("name", name)
        jsonObject.put("filePath", filePath)
        jsonObject.put("createdAt", createdAt)
        jsonObject.put("isActive", isActive)
        jsonObject.put("fileSize", fileSize)
        jsonObject.put("type", type.name)
        url?.let { jsonObject.put("url", it) }
        lastUpdated?.let { jsonObject.put("lastUpdated", it) }
        jsonObject.put("autoUpdate", autoUpdate)
        userAgent?.let { jsonObject.put("userAgent", it) }
        proxyUrl?.let { jsonObject.put("proxyUrl", it) }
        return jsonObject
    }
}
