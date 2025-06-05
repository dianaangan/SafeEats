package com.android.safeeats.utils

import android.app.Activity
import android.app.Dialog
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Window
import android.view.WindowManager
import android.widget.ImageView
import com.android.safeeats.R
import okhttp3.*
import java.io.IOException

/**
 * A utility class for loading images with a loading dialog.
 * This shows a small progress indicator while loading images.
 */
class ImageLoadingDialog(private val activity: Activity) {
    private var okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    
    /**
     * Loads an image from the given URL and displays it in the ImageView
     * Shows a loading indicator during the process
     */
    fun loadImage(
        imageUrl: String,
        imageView: ImageView,
        timeoutMs: Long = 15000,
        showPlaceholder: Boolean = true
    ) {
        val handler = Handler(Looper.getMainLooper())
        val timeoutRunnable = Runnable {
            Log.e("ImageLoadingDialog", "Image loading timed out for URL: $imageUrl")
            // Show placeholder on timeout
            if (showPlaceholder) {
                imageView.setImageResource(R.drawable.placeholder_dish)
                imageView.visibility = android.view.View.VISIBLE
            }
        }
        handler.postDelayed(timeoutRunnable, timeoutMs)
        
        Log.d("ImageLoadingDialog", "Loading image from URL: $imageUrl")

        val request = Request.Builder()
            .url(imageUrl)
            .get()
            .build()

        okHttpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("ImageLoadingDialog", "Error loading image: ${e.message}")
                handler.removeCallbacks(timeoutRunnable)
                if (showPlaceholder) {
                    activity.runOnUiThread {
                        imageView.setImageResource(R.drawable.placeholder_dish)
                        imageView.visibility = android.view.View.VISIBLE
                    }
                }
            }

            override fun onResponse(call: Call, response: Response) {
                handler.removeCallbacks(timeoutRunnable)
                if (response.isSuccessful) {
                    val responseBody = response.body
                    if (responseBody != null) {
                        try {
                            val bitmap = BitmapFactory.decodeStream(responseBody.byteStream())
                            activity.runOnUiThread {
                                if (bitmap != null) {
                                    imageView.setImageBitmap(bitmap)
                                    imageView.visibility = android.view.View.VISIBLE
                                } else {
                                    Log.e("ImageLoadingDialog", "Failed to decode bitmap from response")
                                    if (showPlaceholder) {
                                        imageView.setImageResource(R.drawable.placeholder_dish)
                                        imageView.visibility = android.view.View.VISIBLE
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("ImageLoadingDialog", "Error processing image: ${e.message}")
                            if (showPlaceholder) {
                                activity.runOnUiThread {
                                    imageView.setImageResource(R.drawable.placeholder_dish)
                                    imageView.visibility = android.view.View.VISIBLE
                                }
                            }
                        }
                    }
                } else {
                    Log.e("ImageLoadingDialog", "Error loading image. Status code: ${response.code}")
                    if (showPlaceholder) {
                        activity.runOnUiThread {
                            imageView.setImageResource(R.drawable.placeholder_dish)
                            imageView.visibility = android.view.View.VISIBLE
                        }
                    }
                }
            }
        })
    }
    
    /**
     * Clean up resources when no longer needed
     */
    fun dispose() {
        // Cancel any pending requests
        okHttpClient.dispatcher.cancelAll()
    }
} 