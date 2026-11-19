package com.example.treedumper

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class DumpWriter private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val currentFile = File(appContext.getExternalFilesDir(null) ?: appContext.filesDir, FILE_NAME)
    private val oldFile = File(currentFile.parentFile, OLD_FILE_NAME)
    private val executor = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, THREAD_NAME) }
    private val lock = Any()
    private val screenshotTimeFormat = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US)

    fun getFile(): File = currentFile

    fun enqueue(timestamp: String, eventType: String, packageName: String, windows: Int, tree: String) {
        executor.execute {
            try {
                synchronized(lock) {
                    rotateIfNeeded()
                    val header = "===== $timestamp $eventType pkg=$packageName windows=$windows\n"
                    currentFile.appendText(header + tree + "\n", Charsets.UTF_8)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to write dump", e)
            }
        }
    }

    fun enqueueScreenshot(bitmap: Bitmap) {
        executor.execute {
            try {
                val directory = File(currentFile.parentFile, SCREENSHOT_DIR)
                if (!directory.exists() && !directory.mkdirs()) {
                    Log.w(TAG, "Failed to create screenshots directory")
                    return@execute
                }
                val name = "screenshot_" + screenshotTimeFormat.format(Date()) + ".png"
                FileOutputStream(File(directory, name)).use { stream ->
                    if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                        Log.w(TAG, "Failed to compress screenshot")
                    }
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

    fun clear() {
        executor.execute {
            synchronized(lock) {
                delete(currentFile)
                delete(oldFile)
            }
        }
        // Wait for the queue to drain so the UI can show the updated file size.
        try {
            executor.submit(Runnable {}).get(2, TimeUnit.SECONDS)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to wait for dump cleanup", e)
        }
    }

    private fun rotateIfNeeded() {
        if (!currentFile.exists() || currentFile.length() <= MAX_FILE_SIZE) return

        if (oldFile.exists() && !oldFile.delete()) {
            Log.w(TAG, "Failed to delete previous rotated dump")
            return
        }
        if (!currentFile.renameTo(oldFile)) {
            Log.w(TAG, "Failed to rotate dump file")
        }
    }

    private fun delete(file: File) {
        if (file.exists() && !file.delete()) {
            Log.w(TAG, "Failed to delete ${file.name}")
        }
    }

    companion object {
        private const val TAG = "DumpWriter"
        private const val FILE_NAME = "tree_dump.txt"
        private const val OLD_FILE_NAME = "tree_dump.old.txt"
        private const val THREAD_NAME = "tree-dump-writer"
        private const val SCREENSHOT_DIR = "screenshots"
        private const val MAX_FILE_SIZE = 25L * 1024L * 1024L

        @Volatile
        private var instance: DumpWriter? = null

        fun get(context: Context): DumpWriter =
            instance ?: synchronized(this) {
                instance ?: DumpWriter(context).also { instance = it }
            }
    }
}
