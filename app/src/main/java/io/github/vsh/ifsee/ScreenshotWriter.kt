package io.github.vsh.ifsee

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class ScreenshotWriter private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val executor = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, THREAD_NAME) }
    private val timeFormat = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US)

    fun enqueue(bitmap: Bitmap) {
        executor.execute {
            try {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, "shot_" + timeFormat.format(Date()) + ".jpg")
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$ALBUM")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val uri = appContext.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                if (uri == null) {
                    Log.w(TAG, "Failed to create MediaStore image entry")
                    return@execute
                }
                try {
                    val stream = appContext.contentResolver.openOutputStream(uri)
                    if (stream == null) {
                        Log.w(TAG, "Failed to open MediaStore output stream")
                    } else {
                        stream.use {
                            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)) {
                                Log.w(TAG, "Failed to compress screenshot")
                            }
                        }
                    }
                } finally {
                    val published = ContentValues().apply {
                        put(MediaStore.Images.Media.IS_PENDING, 0)
                    }
                    appContext.contentResolver.update(uri, published, null, null)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to save screenshot", e)
            } finally {
                try {
                    bitmap.recycle()
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to recycle screenshot bitmap", e)
                }
            }
        }
    }

    companion object {
        private const val TAG = "ScreenshotWriter"
        private const val THREAD_NAME = "screenshot-writer"
        private const val ALBUM = "Ifsee"

        @Volatile
        private var instance: ScreenshotWriter? = null

        fun get(context: Context): ScreenshotWriter =
            instance ?: synchronized(this) {
                instance ?: ScreenshotWriter(context).also { instance = it }
            }
    }
}
