package com.android.safeeats

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.util.Log
import android.widget.*
import com.android.safeeats.utils.*
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.IOException
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class NewPasswordSetupActivity : Activity() {
    private val TAG = "NewPasswordSetupActivity"
    private lateinit var loadingDialog: LoadingDialog

    private val okHttpClient = OkHttpClient.Builder()
        .callTimeout(30, TimeUnit.SECONDS)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_new_password_setup)

        // Initialize loading dialog
        loadingDialog = LoadingDialog(this)

        val toggleNewPasswordButton = findViewById<ImageButton>(R.id.btn_toggle_new_password)
        val toggleConfirmNewPasswordButton = findViewById<ImageButton>(R.id.btn_toggle_confirm_new_password)
        val newPassword = findViewById<EditText>(R.id.et_newpassword)
        val confirmNewPassword = findViewById<EditText>(R.id.et_confirm_new_password)
        val saveButton = findViewById<Button>(R.id.btn_save)

        val settingupyour = findViewById<TextView>(R.id.settingupyour)
        val descnewpassword = findViewById<TextView>(R.id.descnewpassword)

        val boldFont = androidx.core.content.res.ResourcesCompat.getFont(this, R.font.roboto_serif_bold)

        descnewpassword.typeface = boldFont
        settingupyour.typeface = boldFont

        val email = intent.getStringExtra("email") ?: ""
        val resetToken = intent.getStringExtra("resetToken") ?: ""
        val currentPassword = intent.getStringExtra("currentPassword") ?: ""

        toggleNewPasswordButton.setOnClickListener {
            if (newPassword.transformationMethod is PasswordTransformationMethod) {
                // Show password
                newPassword.transformationMethod = HideReturnsTransformationMethod.getInstance()
                toggleNewPasswordButton.setImageResource(R.drawable.ic_view)
            } else {
                // Hide password
                newPassword.transformationMethod = PasswordTransformationMethod.getInstance()
                toggleNewPasswordButton.setImageResource(R.drawable.ic_not_view)
            }
            newPassword.setSelection(newPassword.text.length)
        }

        toggleConfirmNewPasswordButton.setOnClickListener {
            if (confirmNewPassword.transformationMethod is PasswordTransformationMethod) {
                // Show password
                confirmNewPassword.transformationMethod = HideReturnsTransformationMethod.getInstance()
                toggleConfirmNewPasswordButton.setImageResource(R.drawable.ic_view)
            } else {
                // Hide password
                confirmNewPassword.transformationMethod = PasswordTransformationMethod.getInstance()
                toggleConfirmNewPasswordButton.setImageResource(R.drawable.ic_not_view)
            }
            confirmNewPassword.setSelection(confirmNewPassword.text.length)
        }

        saveButton.setOnClickListener {
            if (newPassword.text.toString().isEmpty() || confirmNewPassword.text.toString().isEmpty()) {
                toast("Please enter and confirm your new password")
                return@setOnClickListener
            }

            if (newPassword.text.toString() != confirmNewPassword.text.toString()) {
                toast("Passwords don't match!")
                return@setOnClickListener
            }

            if (newPassword.text.toString().length < 6) {
                toast("Password must be at least 6 characters")
                return@setOnClickListener
            }

            // Check if the new password is the same as the current password
            if (checkIfSameAsCurrentPassword(email, newPassword.text.toString())) {
                toast("Your new password cannot be the same as your current password")
                return@setOnClickListener
            }

            // Use loading dialog for password reset operation
            loadingDialog.executeWithLoading(
                thresholdMs = 500, // Show loading dialog if it takes more than 500ms
                operation = {
                    // Return a boolean to indicate success/failure
                    resetPasswordWithResult(email, newPassword.text.toString(), resetToken)
                },
                callback = { result ->
                    // Handle reset password result in UI thread
                    handleResetPasswordResult(result, email)
                }
            )
        }
    }

    // Check if the new password is the same as the current password
    private fun checkIfSameAsCurrentPassword(email: String, newPassword: String): Boolean {
        Log.d(TAG, "Checking if new password is the same as current for: $email")
        try {
            val jsonObject = JSONObject().apply {
                put("email", email)
                put("password", newPassword)
            }

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val body = RequestBody.create(mediaType, jsonObject.toString())

            val request = Request.Builder()
                .url("https://swamp-brief-brake.glitch.me/api/check-password")
                .post(body)
                .addHeader("Content-Type", "application/json")
                .build()

            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string()

            if (response.isSuccessful) {
                val jsonResponse = JSONObject(responseBody ?: "{}")
                return jsonResponse.optBoolean("isSameAsCurrentPassword", false)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking current password: ${e.message}", e)
        }

        // If there's an error in the check, return false to allow the process to continue
        return false
    }

    // Data class for reset password result
    data class ResetPasswordResult(
        val isSuccess: Boolean,
        val message: String,
        val tokenExpired: Boolean = false,
        val passwordReused: Boolean = false
    )

    // New method that returns a result
    private fun resetPasswordWithResult(email: String, newPassword: String, resetToken: String): ResetPasswordResult {
        Log.d(TAG, "Starting password reset for: $email")
        try {
            val jsonObject = JSONObject().apply {
                put("email", email)
                put("newPassword", newPassword)
                put("resetToken", resetToken)
            }

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val body = RequestBody.create(mediaType, jsonObject.toString())

            val request = Request.Builder()
                .url("https://swamp-brief-brake.glitch.me/api/reset-password")
                .post(body)
                .addHeader("Content-Type", "application/json")
                .build()

            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string()

            Log.d(TAG, "Reset password response code: ${response.code}")
            Log.d(TAG, "Reset password response: $responseBody")

            if (response.isSuccessful) {
                return ResetPasswordResult(
                    isSuccess = true,
                    message = "Password reset successfully!"
                )
            } else {
                val errorJson = try {
                    JSONObject(responseBody ?: "{}")
                } catch (e: Exception) {
                    JSONObject()
                }

                val errorMessage = errorJson.optString("message", "Failed to reset password")
                val tokenExpired = response.code == 400 && errorMessage.contains("expired")
                val passwordReused = response.code == 400 && errorMessage.contains("same as current")

                Log.e(TAG, "Error: ${response.code} - $errorMessage")
                return ResetPasswordResult(
                    isSuccess = false,
                    message = "$errorMessage. Please try again.",
                    tokenExpired = tokenExpired,
                    passwordReused = passwordReused
                )
            }
        } catch (e: IOException) {
            Log.e(TAG, "Network Error: ${e.message}", e)
            return ResetPasswordResult(
                isSuccess = false,
                message = "Network error: Please check your connection"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error: ${e.message}", e)
            return ResetPasswordResult(
                isSuccess = false,
                message = "Error: ${e.message}"
            )
        }
    }

    // Handle reset password result on UI thread
    private fun handleResetPasswordResult(result: ResetPasswordResult, email: String) {
        toast(result.message)

        if (result.isSuccess) {
            // Go back to login screen with the email prefilled
            val intent = Intent(this, LoginActivity::class.java)
            intent.putExtra("email", email)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            startActivity(intent)
            finish()
        } else if (result.tokenExpired) {
            // Token expired, go back to ForgotPasswordActivity
            val intent = Intent(this, ForgotPasswordActivity::class.java)
            intent.putExtra("email", email)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            startActivity(intent)
            finish()
        } else if (result.passwordReused) {
            // Password is the same as current, clear the password fields
            findViewById<EditText>(R.id.et_newpassword).text.clear()
            findViewById<EditText>(R.id.et_confirm_new_password).text.clear()
            findViewById<EditText>(R.id.et_newpassword).requestFocus()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clean up loading dialog resources
        loadingDialog.dispose()
    }
}