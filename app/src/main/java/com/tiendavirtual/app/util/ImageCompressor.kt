package com.tiendavirtual.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Base64
import androidx.exifinterface.media.ExifInterface
import com.tiendavirtual.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * Convierte la foto elegida (cámara o galería) en una imagen JPEG pequeña
 * codificada como "data URI" (data:image/jpeg;base64,...), que se guarda
 * directamente en el documento del producto en Firestore.
 *
 * Así las imágenes persisten en la nube sin usar Firebase Storage, que desde
 * 2026 exige el plan de pago Blaze. Un documento de Firestore admite hasta
 * 1 MB, por eso la imagen se reduce a 800 px por lado y se comprime hasta
 * quedar por debajo de [MAX_BYTES].
 */
object ImageCompressor {

    private const val MAX_SIDE = 800
    private const val MAX_BYTES = 300 * 1024

    /** Lee, reduce, corrige la rotación y comprime la imagen (en un hilo de E/S). */
    suspend fun toDataUri(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver

        // 1. Lee solo las dimensiones para calcular cuánto reducir sin cargar la foto completa.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= MAX_SIDE && bounds.outHeight / (sample * 2) >= MAX_SIDE) {
            sample *= 2
        }

        // 2. Decodifica la imagen ya reducida.
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: throw AppException(R.string.error_image_read)

        // 3. Ajusta al tamaño máximo y corrige la orientación EXIF (fotos de cámara giradas).
        val rotation = resolver.openInputStream(uri)?.use {
            when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f
        val scale = minOf(1f, MAX_SIDE.toFloat() / maxOf(decoded.width, decoded.height))
        val matrix = Matrix().apply { postScale(scale, scale); postRotate(rotation) }
        val bitmap = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)

        // 4. Comprime bajando la calidad hasta que quepa en el límite.
        var quality = 80
        var bytes: ByteArray
        do {
            bytes = ByteArrayOutputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
                out.toByteArray()
            }
            quality -= 10
        } while (bytes.size > MAX_BYTES && quality >= 30)

        "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
    }
}
