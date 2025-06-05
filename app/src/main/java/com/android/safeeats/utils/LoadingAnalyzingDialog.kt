package com.android.safeeats.utils

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Handler
import android.os.Looper
import android.view.Window
import com.android.safeeats.R

class LoadingAnalyzingDialog(context: Context) {
    private var dialog: Dialog = Dialog(context)
    private var handler: Handler = Handler(Looper.getMainLooper())
    private var minimumShowTime: Long = 2000 // 2 seconds minimum show time
    private var startTime: Long = 0

    init {
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setCancelable(false)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.setContentView(R.layout.loading_analyzing_dish)
    }

    fun show() {
        startTime = System.currentTimeMillis()
        if (!dialog.isShowing) {
            dialog.show()
        }
    }

    fun dismiss(onDismissed: () -> Unit = {}) {
        val currentTime = System.currentTimeMillis()
        val elapsedTime = currentTime - startTime
        
        if (elapsedTime >= minimumShowTime) {
            dismissDialog(onDismissed)
        } else {
            // Wait for the remaining time before dismissing
            handler.postDelayed({
                dismissDialog(onDismissed)
            }, minimumShowTime - elapsedTime)
        }
    }

    private fun dismissDialog(onDismissed: () -> Unit) {
        if (dialog.isShowing) {
            dialog.dismiss()
            onDismissed()
        }
    }

    fun dispose() {
        handler.removeCallbacksAndMessages(null)
        if (dialog.isShowing) {
            dialog.dismiss()
        }
    }
} 