package com.example.wppsticker.util

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object CacheCleaner {
    
    /**
     * Cleans up imported media and temporary animated WebP candidates.
     */
    suspend fun cleanVideoCache(context: Context) = withContext(Dispatchers.IO) {
        try {
            val cacheDir = context.cacheDir
            val files = cacheDir.listFiles { _, name ->
                name.startsWith("temp_input_") || name.startsWith("selected-") ||
                    name.startsWith("static-animated-")
            }
            
            val count = files?.size ?: 0
            if (count > 0) {
                Log.d("CacheCleaner", "Found $count imported media files. Cleaning...")
                files?.forEach { file ->
                    try {
                        if (file.delete()) {
                            // Log.v("CacheCleaner", "Deleted: ${file.name}")
                        }
                    } catch (e: Exception) {
                        Log.w("CacheCleaner", "Failed to delete ${file.name}")
                    }
                }
                Log.d("CacheCleaner", "Cache cleanup finished.")
            }
            File(cacheDir, "animated-sticker-export").takeIf { it.isDirectory }?.deleteRecursively()
        } catch (e: Exception) {
            Log.e("CacheCleaner", "Error cleaning cache", e)
        }
    }
}
