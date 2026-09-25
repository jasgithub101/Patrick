package com.patrick.faceid

import android.content.res.AssetManager
import org.json.JSONObject

/**
 * The reproducible LFW evaluation set built by tools/prepare_dataset.py, read from androidTest
 * assets. Roles: "enroll" (gallery), "probe" (held-out images of enrolled people, must be
 * recognised), "unknown" (people never enrolled, must NOT be matched).
 */
class EvalSet(private val assets: AssetManager) {

    data class Item(val path: String, val identity: String, val role: String)

    val items: List<Item>
    val source: String

    init {
        val manifest = JSONObject(assets.open("manifest.json").use { it.readBytes().decodeToString() })
        val files = manifest.getJSONArray("files")
        items = (0 until files.length()).map { i ->
            val o = files.getJSONObject(i)
            Item(o.getString("path"), o.getString("identity"), o.getString("role"))
        }
        val src = manifest.getJSONObject("source")
        source = "${src.getString("dataset")}@${src.getString("revision").take(7)}"
    }

    fun byRole(role: String): List<Item> = items.filter { it.role == role }

    fun bytes(item: Item): ByteArray = assets.open(item.path).use { it.readBytes() }

    companion object {
        /** True when the eval set has been generated; tests skip themselves otherwise. */
        fun isAvailable(assets: AssetManager): Boolean =
            runCatching { assets.open("manifest.json").close() }.isSuccess
    }
}
