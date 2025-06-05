package com.android.safeeats

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.*
import com.android.safeeats.utils.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.IOException
import java.util.concurrent.TimeUnit

class ForgotPasswordActivity : Activity() {
    private val TAG = "ForgotPasswordActivity"
    private lateinit var loadingDialog: LoadingDialog

    private val okHttpClient = OkHttpClient.Builder()
        .callTimeout(30, TimeUnit.SECONDS)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_forgot_password)

        // Initialize loading dialog
        loadingDialog = LoadingDialog(this)

        val userEmail = findViewById<EditText>(R.id.et_forgotpassword_email)
        val text_register = findViewById<TextView>(R.id.tv_create_account)
        val sendCodeButton = findViewById<Button>(R.id.btn_sendcode)
        val backButton = findViewById<ImageButton>(R.id.btn_back)

        val desc_forgotpassword = findViewById<TextView>(R.id.password)

        val boldFont = androidx.core.content.res.ResourcesCompat.getFont(this, R.font.roboto_serif_bold)

        desc_forgotpassword.typeface = boldFont

        text_register.setOnClickListener{
            val intent = Intent(this, RegisterActivity::class.java)
            startActivity(intent)
        }

        backButton.setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
            finish()
        }

        sendCodeButton.setOnClickListener {
            if (userEmail.text.toString().isNotEmpty()) {
                if (!isValidEmail(userEmail.text.toString())) {
                    toast("Please enter a valid email address")
                    return@setOnClickListener
                }

                // Use loading dialog for sending reset code
                loadingDialog.executeWithLoading(
                    thresholdMs = 500, // Show loading dialog if it takes more than 500ms
                    operation = {
                        // Return a result object
                        sendResetCodeWithResult(userEmail.text.toString())
                    },
                    callback = { result ->
                        // Handle result in UI thread
                        handleResetCodeResult(result)
                    }
                )
            } else {
                toast("Please enter your email address")
            }
        }
    }

    private fun isValidEmail(email: String): Boolean {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }

    // Data class for reset code result
    data class ResetCodeResult(
        val isSuccess: Boolean,
        val statusCode: Int,
        val email: String,
        val message: String
    )

    private fun sendResetCodeWithResult(email: String): ResetCodeResult {
        Log.d(TAG, "Sending reset code for: $email")
        try {
            val mediaType = "application/x-www-form-urlencoded".toMediaType()
            val body = RequestBody.create(
                mediaType,
                "email=$email"
            )
            val request = Request.Builder()
                .url("https://swamp-brief-brake.glitch.me/api/forgot-password")
                .post(body)
                .addHeader("Content-Type", "application/x-www-form-urlencoded")
                .build()

            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: "{}"

            Log.d(TAG, "Reset code response code: ${response.code}")
            Log.d(TAG, "Reset code response: $responseBody")

            return when (response.code) {
                200 -> {
                    ResetCodeResult(
                        isSuccess = true,
                        statusCode = 200,
                        email = email,
                        message = "Reset code sent to your email"
                    )
                }
                404 -> {
                    ResetCodeResult(
                        isSuccess = false,
                        statusCode = 404,
                        email = email,
                        message = "Email not found in our records"
                    )
                }
                else -> {
                    ResetCodeResult(
                        isSuccess = false,
                        statusCode = response.code,
                        email = email,
                        message = "Failed to send reset code. Please try again."
                    )
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Network Error: ${e.message}", e)
            return ResetCodeResult(
                isSuccess = false,
                statusCode = -1,
                email = email,
                message = "Network error: Please check your connection"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error: ${e.message}", e)
            return ResetCodeResult(
                isSuccess = false,
                statusCode = -1,
                email = email,
                message = "Error: ${e.message}"
            )
        }
    }

    // Handle reset code result on UI thread
    private fun handleResetCodeResult(result: ResetCodeResult) {
        toast(result.message)

        if (result.isSuccess) {
            val intent = Intent(this, CodeVerificationActivity::class.java)
            intent.putExtra("email", result.email)
            startActivity(intent)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clean up loading dialog resources
        loadingDialog.dispose()
    }
}