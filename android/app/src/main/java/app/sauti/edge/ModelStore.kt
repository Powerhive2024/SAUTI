package app.sauti.edge

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Keeps model files in app-private storage. Models are *imported* from a file the user picks
 * (SD card, Bluetooth transfer, USB, a download) so communities can share one copy without
 * everyone paying for mobile data.
 */
class ModelStore(private val context: Context) {
    private val dir = File(context.filesDir, "models").apply { mkdirs() }

    fun list(): List<File> = dir.listFiles()?.filter { it.isFile }?.sortedBy { it.name } ?: emptyList()

    /** Copies the picked file into private storage and returns its absolute path. */
    suspend fun import(uri: Uri, onProgress: (Float) -> Unit = {}): File = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        var name = "model.litertlm"
        var size = -1L
        resolver.query(uri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                c.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let { name = c.getString(it) }
                c.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 }?.let { size = c.getLong(it) }
            }
        }
        val safe = name.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val out = File(dir, safe)
        val tmp = File(dir, "$safe.part")
        resolver.openInputStream(uri)!!.use { input ->
            tmp.outputStream().use { output ->
                val buf = ByteArray(1 shl 20)
                var done = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    output.write(buf, 0, n)
                    done += n
                    if (size > 0) onProgress(done.toFloat() / size)
                }
            }
        }
        if (out.exists()) out.delete()
        tmp.renameTo(out)
        out
    }

    fun isAdapter(f: File) = f.name.contains("lora", ignoreCase = true)
}
