package com.android.safeeats.utils

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Window
import android.view.WindowManager
import com.android.safeeats.R

/**
 * A reusable loading dialog utility class that shows a loading screen only when
 * operations actually take time to complete.
 *
 * @param activity The activity context where the loading dialog will be displayed
 */
class LoadingDialog(private val activity: Activity) {
    private var dialog: Dialog? = null
    private var isLoading = false

    fun showLoading() {
        // If dialog is already showing, don't create a new one
        if (dialog?.isShowing == true) return

        isLoading = true

        dialog = Dialog(activity).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setContentView(R.layout.loading_screen)
            setCancelable(false)
            window?.apply {
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                // Make the dialog fill the screen
                setLayout(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT
                )
                // Add necessary flags to make it work correctly
                clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                setFlags(
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                )
            }
        }

        // Only show if activity is not finishing
        if (!activity.isFinishing) {
            dialog?.show()
        }
    }

    /**
     * Hides the loading dialog if it's showing
     */
    fun hideLoading() {
        isLoading = false
        if (dialog?.isShowing == true && !activity.isFinishing) {
            dialog?.dismiss()
        }
    }

    /**
     * Checks if the loading dialog is currently showing
     * @return true if loading dialog is visible
     */
    fun isShowing(): Boolean {
        return dialog?.isShowing == true
    }

    /**
     * Use this method to show loading dialog only for operations that
     * take longer than the specified threshold
     *
     * @param thresholdMs Time in milliseconds before showing loading dialog
     * @param operation The operation to execute
     * @param callback Callback to execute when operation completes
     */
    fun <T> executeWithLoading(
        thresholdMs: Long = 300,
        operation: () -> T,
        callback: (T) -> Unit
    ) {
        val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
        android.util.Log.d("LoadingDialog", "Starting executeWithLoading with threshold: $thresholdMs ms")

        // Create a runnable that will show the loading dialog after threshold
        val loadingRunnable = Runnable {
            android.util.Log.d("LoadingDialog", "Showing loading dialog after threshold")
            showLoading()
        }

        // Schedule showing the loading after threshold
        mainHandler.postDelayed(loadingRunnable, thresholdMs)

        // Execute operation in background thread
        Thread {
            android.util.Log.d("LoadingDialog", "Starting background operation")
            try {
                val result = operation()
                android.util.Log.d("LoadingDialog", "Operation completed successfully, result type: ${if (result == null) "null" else result.javaClass.simpleName}")

                // Back on main thread to update UI
                mainHandler.post {
                    // Cancel showing loading if operation completed before threshold
                    mainHandler.removeCallbacks(loadingRunnable)
                    android.util.Log.d("LoadingDialog", "Back on main thread, about to hide loading and execute callback")

                    // Hide loading if it was shown
                    hideLoading()

                    // Execute callback with result
                    callback(result)
                    android.util.Log.d("LoadingDialog", "Callback executed")
                }
            } catch (e: Exception) {
                android.util.Log.e("LoadingDialog", "Error in background operation: ${e.message}", e)
                mainHandler.post {
                    mainHandler.removeCallbacks(loadingRunnable)
                    hideLoading()
                    android.util.Log.e("LoadingDialog", "Operation failed, hiding loading dialog")
                }
            }
        }.start()
    }

    /**
     * Clean up resources when no longer needed
     */
    fun dispose() {
        hideLoading()
        dialog = null
    }
}