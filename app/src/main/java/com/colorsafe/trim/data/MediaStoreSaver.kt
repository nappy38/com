package com.colorsafe.trim.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import com.colorsafe.trim.model.TrimError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** トリム済みファイルをMediaStoreへ保存し、ギャラリーに表示させる。 */
class MediaStoreSaver(private val context: Context) {

    suspend fun saveToGallery(sourceFile: File, displayName: String, extension: String): Uri =
        withContext(Dispatchers.IO) {
            val mimeType = mimeTypeFor(extension)
            val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Video.Media.MIME_TYPE, mimeType)
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/ColorSafeTrim")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }

            val resolver = context.contentResolver
            val itemUri = resolver.insert(collection, values)
                ?: throw TrimException(TrimError.Unknown("MediaStoreへの登録に失敗しました"))

            try {
                resolver.openOutputStream(itemUri)?.use { out ->
                    sourceFile.inputStream().use { input ->
                        input.copyTo(out)
                    }
                } ?: throw TrimException(TrimError.Unknown("出力ストリームを開けませんでした"))

                values.clear()
                values.put(MediaStore.Video.Media.IS_PENDING, 0)
                resolver.update(itemUri, values, null, null)
            } catch (e: Exception) {
                resolver.delete(itemUri, null, null)
                throw e
            } finally {
                sourceFile.delete()
            }

            itemUri
        }

    private fun mimeTypeFor(extension: String): String = when (extension.lowercase()) {
        "mp4" -> "video/mp4"
        "mov" -> "video/quicktime"
        "mkv" -> "video/x-matroska"
        else -> "video/mp4"
    }
}
