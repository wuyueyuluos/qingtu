package com.example.qingtu

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.graphics.Bitmap

internal data class EncodedImageFormat(val format: Bitmap.CompressFormat, val extension: String, val mime: String)

internal fun getImageDisplayName(context: Context, uri: Uri): String {
    return try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } ?: uri.lastPathSegment?.substringAfterLast('/') ?: "图片"
    } catch (_: Exception) { uri.lastPathSegment?.substringAfterLast('/') ?: "图片" }
}

internal fun getImageBaseName(context: Context, uri: Uri): String {
    val name = getImageDisplayName(context, uri)
    return name.substringBeforeLast('.', name).ifBlank { "图片" }
}

internal fun imageFormatFromMime(mime: String, name: String = ""): EncodedImageFormat {
    val normalized = mime.lowercase()
    return when {
        normalized == "image/png" || name.endsWith(".png", true) -> EncodedImageFormat(Bitmap.CompressFormat.PNG, "png", "image/png")
        normalized == "image/webp" || name.endsWith(".webp", true) -> EncodedImageFormat(Bitmap.CompressFormat.WEBP, "webp", "image/webp")
        normalized == "image/jpeg" || normalized == "image/jpg" || name.endsWith(".jpg", true) || name.endsWith(".jpeg", true) ->
            EncodedImageFormat(Bitmap.CompressFormat.JPEG, if (name.endsWith(".jpeg", true)) "jpeg" else "jpg", "image/jpeg")
        else -> throw IllegalArgumentException("暂不支持保持该格式（" + (mime.ifBlank { "未知格式" }) + "），请先转换为 JPG、PNG 或 WebP。")
    }
}

internal fun outputImageName(context: Context, uri: Uri, extension: String): String = getImageBaseName(context, uri) + "." + extension
