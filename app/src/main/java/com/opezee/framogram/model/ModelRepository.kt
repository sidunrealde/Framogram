package com.opezee.framogram.model

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.nio.ByteBuffer

data class BundledModel(val name: String, val file: String)

/**
 * Sources of glTF models: bundled APK assets (listed in assets/models/index.json)
 * and user-picked .glb documents via the Storage Access Framework.
 */
class ModelRepository(private val context: Context) {

    val bundled: List<BundledModel> by lazy {
        try {
            val json = context.assets.open("models/index.json").use { it.readBytes() }
                .toString(Charsets.UTF_8)
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                BundledModel(o.getString("name"), o.getString("file"))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun readAsset(file: String): ByteBuffer = withContext(Dispatchers.IO) {
        context.assets.open(file).use { ByteBuffer.wrap(it.readBytes()) }
    }

    suspend fun readUri(uri: Uri): ByteBuffer = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.use { ByteBuffer.wrap(it.readBytes()) }
            ?: throw IllegalStateException("Cannot open $uri")
    }

    fun displayName(uri: Uri): String {
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && c.moveToFirst()) return c.getString(idx) ?: uri.lastPathSegment ?: "model"
            }
        } catch (_: Exception) {
        }
        return uri.lastPathSegment ?: "model"
    }
}
