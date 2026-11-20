package app.sauti.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import java.io.ByteArrayOutputStream

enum class AttachmentKind { PHOTO, VIDEO }

/** A photo or video ready for the model: JPEG frames (longest side <= 768) and a small preview. */
class Attachment(val id: Long, val kind: AttachmentKind, val frames: List<ByteArray>, val thumb: Bitmap)

/**
 * Turns a picked or captured photo/video into what Gemma 3n's image encoder takes. Everything happens
 * on this phone: nothing is uploaded, and nothing is kept after the message is sent.
 */
object MediaPrep {
    fun photo(context: Context, uri: Uri, id: Long): Attachment {
        val bmp = decode(context, uri)
        return Attachment(id, AttachmentKind.PHOTO, listOf(jpeg(bmp)), thumb(bmp)).also { if (!bmp.isRecycled) bmp.recycle() }
    }

    fun video(context: Context, uri: Uri, id: Long, frames: Int = MediaMath.VIDEO_FRAMES): Attachment {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(context, uri)
            val ms = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val (tw, th) = MediaMath.fit(w, h)
            val bitmaps = MediaMath.frameTimesUs(ms, frames).mapNotNull { t ->
                val f = if (Build.VERSION.SDK_INT >= 27 && w > 0 && h > 0)
                    r.getScaledFrameAtTime(t, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, tw, th)
                else r.getFrameAtTime(t, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?.let { scaled(it) }
                f
            }
            require(bitmaps.isNotEmpty()) { "no frames" }
            val out = Attachment(id, AttachmentKind.VIDEO, bitmaps.map { jpeg(it) }, thumb(bitmaps[bitmaps.size / 2]))
            bitmaps.forEach { if (!it.isRecycled) it.recycle() }
            return out
        } finally {
            runCatching { r.release() }
        }
    }

    /** Decodes at a reduced size; ImageDecoder also applies the photo's rotation (EXIF). */
    private fun decode(context: Context, uri: Uri): Bitmap {
        if (Build.VERSION.SDK_INT >= 28) {
            val src = ImageDecoder.createSource(context.contentResolver, uri)
            return ImageDecoder.decodeBitmap(src) { d, info, _ ->
                val (w, h) = MediaMath.fit(info.size.width, info.size.height)
                d.setTargetSize(w, h)
                d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, bounds) }
        val opts = BitmapFactory.Options().apply { inSampleSize = MediaMath.sampleSize(bounds.outWidth, bounds.outHeight) }
        val b = context.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: error("not an image")
        return scaled(b)
    }

    private fun scaled(b: Bitmap): Bitmap {
        val (w, h) = MediaMath.fit(b.width, b.height)
        return if (w == b.width && h == b.height) b else Bitmap.createScaledBitmap(b, w, h, true).also { b.recycle() }
    }

    private fun jpeg(b: Bitmap): ByteArray =
        ByteArrayOutputStream().use { b.compress(Bitmap.CompressFormat.JPEG, 88, it); it.toByteArray() }

    private fun thumb(b: Bitmap): Bitmap {
        val (w, h) = MediaMath.fit(b.width, b.height, 192)
        return Bitmap.createScaledBitmap(b, w, h, true)
    }
}
