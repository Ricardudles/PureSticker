package com.example.wppsticker.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.util.Log
import com.example.wppsticker.di.StickerProviderEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "StickerAppDebug"

class StickerContentProvider : ContentProvider() {

    companion object {
        lateinit var AUTHORITY: String

        // Paths per WhatsApp Documentation
        private const val METADATA = 1
        private const val METADATA_ID = 2
        private const val STICKERS_ID = 3
        private const val STICKERS_ASSET_ID = 5

        private val uriMatcher = UriMatcher(UriMatcher.NO_MATCH)
    }

    override fun onCreate(): Boolean {
        try {
            val context = context ?: return false
            AUTHORITY = "${context.packageName}.provider"
            
            // WhatsApp documentation paths
            uriMatcher.addURI(AUTHORITY, "metadata", METADATA)
            uriMatcher.addURI(AUTHORITY, "metadata/*", METADATA_ID) // Changed to * to support String identifier
            uriMatcher.addURI(AUTHORITY, "stickers/*", STICKERS_ID) // Changed to * to support String identifier
            uriMatcher.addURI(AUTHORITY, "stickers_asset/*/*", STICKERS_ASSET_ID) // Changed to * to support String identifier
            
            Log.d(TAG, "ContentProvider onCreate. Authority: $AUTHORITY")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "ContentProvider onCreate FAILED", e)
            return false
        }
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? {
        Log.d(TAG, "[QUERY] START URI: $uri")
        val context = context ?: return null
        
        return try {
            runBlocking {
                withContext(Dispatchers.IO) {
                    try {
                        val entryPoint = EntryPointAccessors.fromApplication(context, StickerProviderEntryPoint::class.java)
                        val stickerRepository = entryPoint.stickerRepository()

                        val match = uriMatcher.match(uri)
                        Log.d(TAG, "[QUERY] Match: $match for $uri")

                        when (match) {
                            METADATA -> {
                                Log.d(TAG, "[QUERY] Fetching ALL metadata")
                                val cursor = getPackCursorSchema()
                                val allPackages = stickerRepository.getStickerPackagesSync()
                                
                                for (pack in allPackages) {
                                    // Verify tray file exists
                                    val trayFile = File(context.filesDir, pack.trayImageFile)
                                    if (pack.trayImageFile.isBlank() || !trayFile.exists()) {
                                        Log.w(TAG, "[QUERY] Skipping pack ${pack.name} because tray file missing: ${pack.trayImageFile}")
                                        continue
                                    }

                                    val row = arrayOf(
                                        pack.identifier, // Use UUID identifier
                                        pack.name,
                                        pack.author,
                                        pack.trayImageFile,
                                        "", // android_play_store_link (optional)
                                        "", // ios_app_store_link (optional)
                                        pack.publisherEmail,
                                        pack.publisherWebsite,
                                        pack.privacyPolicyWebsite,
                                        pack.licenseAgreementWebsite,
                                        pack.imageDataVersion, 
                                        "0", // avoid_cache (deprecated)
                                        if (pack.animated) 1 else 0 // animated_sticker_pack (Renamed property)
                                    )
                                    cursor.addRow(row)
                                }
                                cursor
                            }
                            METADATA_ID -> {
                                val identifier = uri.lastPathSegment
                                if (identifier == null) return@withContext null

                                Log.d(TAG, "[QUERY] Fetching metadata for pack $identifier")
                                val stickerPackage = stickerRepository.getStickerPackageWithStickersByIdentifierSync(identifier)

                                if (stickerPackage == null) return@withContext null
                                
                                val trayFile = File(context.filesDir, stickerPackage.stickerPackage.trayImageFile)
                                if (stickerPackage.stickerPackage.trayImageFile.isBlank() || !trayFile.exists()) {
                                    Log.e(TAG, "[QUERY] Tray file missing for requested pack: ${stickerPackage.stickerPackage.trayImageFile}")
                                    return@withContext null
                                }

                                val cursor = getPackCursorSchema()
                                val row = arrayOf(
                                    stickerPackage.stickerPackage.identifier,
                                    stickerPackage.stickerPackage.name,
                                    stickerPackage.stickerPackage.author,
                                    stickerPackage.stickerPackage.trayImageFile,
                                    "", // android_play_store_link
                                    "", // ios_app_store_link
                                    stickerPackage.stickerPackage.publisherEmail,
                                    stickerPackage.stickerPackage.publisherWebsite,
                                    stickerPackage.stickerPackage.privacyPolicyWebsite,
                                    stickerPackage.stickerPackage.licenseAgreementWebsite,
                                    stickerPackage.stickerPackage.imageDataVersion, 
                                    "0", // avoid_cache
                                    if (stickerPackage.stickerPackage.animated) 1 else 0 // animated_sticker_pack (Renamed property)
                                )
                                cursor.addRow(row)
                                cursor
                            }
                            STICKERS_ID -> {
                                val identifier = uri.pathSegments[1] // stickers/<identifier>
                                if (identifier == null) return@withContext null

                                Log.d(TAG, "[QUERY] Fetching stickers for pack $identifier")
                                val stickerPackage = stickerRepository.getStickerPackageWithStickersByIdentifierSync(identifier)
                                
                                if (stickerPackage == null) return@withContext null

                                val cursor = MatrixCursor(arrayOf(
                                    "sticker_file_name",
                                    "sticker_emoji"
                                ))

                                stickerPackage.stickers.forEach {
                                    val stickerFile = File(context.filesDir, it.imageFile)
                                    if (stickerFile.exists()) {
                                        cursor.addRow(arrayOf(it.imageFile, it.emojis.joinToString(",")))
                                    } else {
                                        Log.w(TAG, "[QUERY] Skipping missing sticker file: ${it.imageFile}")
                                    }
                                }
                                cursor
                            }
                            else -> {
                                Log.w(TAG, "[QUERY] Unknown URI match: $match")
                                null
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "[QUERY] Logic Error", e)
                        null
                    }
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "[QUERY] FATAL ERROR", t)
            null
        }
    }

    private fun getPackCursorSchema(): MatrixCursor {
        return MatrixCursor(arrayOf(
            "sticker_pack_identifier",
            "sticker_pack_name",
            "sticker_pack_publisher",
            "sticker_pack_icon",
            "android_play_store_link",
            "ios_app_store_link",
            "sticker_pack_publisher_email",
            "sticker_pack_publisher_website",
            "sticker_pack_privacy_policy_website",
            "sticker_pack_license_agreement_website",
            "image_data_version",
            "avoid_cache",
            "animated_sticker_pack"
        ))
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        Log.d(TAG, "[OPEN FILE] START URI: $uri")
        return try {
            val fileName = uri.lastPathSegment ?: throw IllegalArgumentException("Invalid URI")
            
            if (fileName.contains("..") || fileName.contains("/")) {
                throw SecurityException("Invalid filename")
            }

            val file = File(context!!.filesDir, fileName)
            if (!file.exists()) {
                Log.e(TAG, "[OPEN FILE] File not found: ${file.absolutePath}")
                return null
            }
            Log.d(TAG, "[OPEN FILE] Serving: ${file.name} Size: ${file.length()}")
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        } catch (t: Throwable) {
            Log.e(TAG, "[OPEN FILE] FATAL ERROR", t)
            null
        }
    }
    
    override fun getType(uri: Uri): String {
        val path = uri.path ?: ""
        return if (path.endsWith(".webp")) {
            "image/webp"
        } else {
            // Default or query-based type can be handled here if needed
            "application/octet-stream"
        }
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
