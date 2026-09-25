package com.patrick.faceid

import android.content.res.AssetManager
import com.patrick.faceid.bulk.BulkEnroller

/** Reads `<root>/<PersonName>/<image>` out of the test APK's assets. */
class AssetImageSource(
    private val assets: AssetManager,
    private val root: String,
) : BulkEnroller.ImageSource {

    override fun peopleNames(): List<String> = assets.list(root)?.sorted().orEmpty()

    override fun imagesFor(person: String): List<ByteArray> {
        val dir = root + "/" + person
        return assets.list(dir)?.sorted().orEmpty().map { name ->
            assets.open(dir + "/" + name).use { it.readBytes() }
        }
    }
}
