package com.colorsafe.trim.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import com.colorsafe.trim.model.TrimError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 補正済みの写真をMediaStoreへ保存し、ギャラリーに表示させる。 */
class PhotoSaver(private val context: Context) {

    suspend fun saveToGallery(bitmap: Bitmap, displayName: String): Uri = withContext(Dispatchers.IO) {
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_DCIM + "/Camera")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val resolver = context.contentResolver
        val itemUri = resolver.insert(collection, values)
            ?: throw TrimException(TrimError.Unknown("MediaStoreへの登録に失敗しました"))

        try {
            resolver.openOutputStream(itemUri)?.use { out ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)) {
                    throw TrimException(TrimError.Unknown("画像の書き出しに失敗しました"))
                }
            } ?: throw TrimException(TrimError.Unknown("出力ストリームを開けませんでした"))

            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(itemUri, values, null, null)
        } catch (e: Exception) {
            resolver.delete(itemUri, null, null)
            throw e
        }

        itemUri
    }
}
