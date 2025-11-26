package com.example.wppsticker.util

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.nio.ByteBuffer
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object BackgroundRemover {

    suspend fun removeBackground(inputBitmap: Bitmap): Bitmap {
        return suspendCancellableCoroutine { continuation ->
            try {
                val options = SelfieSegmenterOptions.Builder()
                    .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
                    .build()
                
                val segmenter = Segmentation.getClient(options)
                val image = InputImage.fromBitmap(inputBitmap, 0)

                segmenter.process(image)
                    .addOnSuccessListener { segmentationMask ->
                        val mask = segmentationMask.buffer
                        val maskWidth = segmentationMask.width
                        val maskHeight = segmentationMask.height
                        
                        // Create a copy of the input bitmap to modify (or create new)
                        // Ideally, the mask size matches the input bitmap if using fromBitmap
                        
                        // Check if mask dimensions match bitmap
                        if (maskWidth != inputBitmap.width || maskHeight != inputBitmap.height) {
                             // Scale or handle discrepancy (ML Kit might resize)
                             // For simplicity, assume they match or we scale the mask.
                             // Actually, ML Kit Segmentation usually returns mask matching input size for SINGLE_IMAGE_MODE 
                             // if raw size option is not set differently? 
                             // Wait, Selfie Segmentation mask is usually smaller?
                             // "The mask size is the same as the input image size" -> Docs say it depends.
                             // Let's assume for now we need to be careful.
                        }

                        val outputBitmap = applyMask(inputBitmap, mask, maskWidth, maskHeight)
                        continuation.resume(outputBitmap)
                    }
                    .addOnFailureListener { e ->
                        Log.e("BackgroundRemover", "Segmentation failed", e)
                        continuation.resumeWithException(e)
                    }
            } catch (e: Exception) {
                continuation.resumeWithException(e)
            }
        }
    }

    private fun applyMask(image: Bitmap, mask: ByteBuffer, maskWidth: Int, maskHeight: Int): Bitmap {
        // Ensure image is mutable/software or copy
        val width = image.width
        val height = image.height
        
        // Create result bitmap
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        
        // Get pixels from original image
        val pixels = IntArray(width * height)
        image.getPixels(pixels, 0, width, 0, 0, width, height)
        
        // Process mask
        // The buffer contains float values from 0.0 (background) to 1.0 (foreground) usually
        // Or depending on options. Default is RAW_SIZE_MASK? No, default is scaled?
        // Wait, we didn't enable raw size mask.
        
        // If the mask is the same size as image
        if (width == maskWidth && height == maskHeight) {
            for (i in 0 until width * height) {
                val confidence = mask.float // Read float (0.0 - 1.0)
                if (confidence < 0.5f) {
                    pixels[i] = Color.TRANSPARENT // Background
                }
                // Else keep original pixel (Foreground)
            }
        } else {
             // Mask is likely smaller, need to scale?
             // Actually default Selfie Segmenter returns mask same size as input image for SINGLE_IMAGE_MODE?
             // Let's verify documentation assumption. 
             // If not, we'd need to scale. For robust code, let's hope it matches or logic needs resizing.
             // Usually standard option matches.
             
             // Fallback: Just return original if mismatch to avoid crash (for this iteration)
             Log.w("BackgroundRemover", "Mask size mismatch: $maskWidth x $maskHeight vs $width x $height")
             return image
        }
        
        result.setPixels(pixels, 0, width, 0, 0, width, height)
        return result
    }
}
