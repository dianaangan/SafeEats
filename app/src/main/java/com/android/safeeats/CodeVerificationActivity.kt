package com.android.safeeats

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import com.android.safeeats.utils.*
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.IOException
import org.json.JSONObject

class CodeVerificationActivity : Activity() {
    private val TAG = "CodeVerificationActivity"
    private var attemptCount = 0
    private val MAX_ATTEMPTS = 5
    private lateinit var verifyButton: Button
    private lateinit var resendCodeButton: Button
    private lateinit var loadingDialog: LoadingDialog

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_code_verification)

        // Initialize loading dialog
        loadingDialog = LoadingDialog(this)

        val digit1 = findViewById<EditText>(R.id.et_codeDigit1)
        val digit2 = findViewById<EditText>(R.id.et_codeDigit2)
        val digit3 = findViewById<EditText>(R.id.et_codeDigit3)
        val digit4 = findViewById<EditText>(R.id.et_codeDigit4)

        val desc_verification = findViewById<TextView>(R.id.verificationdesc)

        val boldFont = androidx.core.content.res.ResourcesCompat.getFont(this, R.font.roboto_serif_bold)
        desc_verification.typeface = boldFont

        verifyButton = findViewById(R.id.btn_verify)
        resendCodeButton = findViewById(R.id.tv_resend_code) // This is actually a Button in your XML

        val email = intent.getStringExtra("email") ?: ""

        // Show the email being used
        val emailInfoText = findViewById<TextView>(R.id.tv_email_info)
        emailInfoText.text = "Code sent to: $email"

        // Set up digit input with auto-focus and auto-verify
        setupDigitInputs(digit1, digit2, digit3, digit4)

        resendCodeButton.setOnClickListener {
            resendResetCode(email)
        }

        verifyButton.setOnClickListener {
            performCodeVerification(email, digit1, digit2, digit3, digit4)
        }
    }

    private fun setupDigitInputs(vararg digitInputs: EditText) {
        for (i in 0 until digitInputs.size - 1) {
            digitInputs[i].addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    if (s?.length == 1) {
                        digitInputs[i + 1].requestFocus()
                    }
                }
            })
        }

        // Auto-verify when all digits are filled
        digitInputs[digitInputs.size - 1].addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (areAllDigitsFilled(digitInputs)) {
                    performCodeVerification(
                        intent.getStringExtra("email") ?: "",
                        *digitInputs
                    )
                }
            }
        })
    }

    private fun areAllDigitsFilled(digitInputs: Array<out EditText>): Boolean {
        return digitInputs.all { it.text.toString().isNotEmpty() }
    }

    private fun clearDigitInputs(vararg digitInputs: EditText) {
        digitInputs.forEach { it.text.clear() }
        digitInputs[0].requestFocus()
    }

    private fun setButtonLoadingState(isLoading: Boolean) {
        verifyButton.isEnabled = !isLoading

        // Since there's no progress bar in the XML, just change the button text
        if (isLoading) {
            verifyButton.text = "Verifying..."
        } else {
            verifyButton.text = "Verify"
        }
    }

    private fun performCodeVerification(email: String, vararg digitInputs: EditText) {
        if (attemptCount >= MAX_ATTEMPTS) {
            toast("Too many attempts. Please request a new code.")
            return
        }

        val userCode = digitInputs.joinToString("") { it.text.toString() }

        // Set button to loading state
        setButtonLoadingState(true)

        CoroutineScope(Dispatchers.IO).launch {
            val client = OkHttpClient()
            val mediaType = "application/x-www-form-urlencoded".toMediaType()
            val body = RequestBody.create(
                mediaType,
                "email=$email&code=$userCode"
            )
            val request = Request.Builder()
                .url("https://swamp-brief-brake.glitch.me/api/verify-reset-code")
                .post(body)
                .addHeader("Content-Type", "application/x-www-form-urlencoded")
                .build()

            try {
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: "{}"

                // Increment attempt counter
                attemptCount++

                withContext(Dispatchers.Main) {
                    // Always disable loading state
                    setButtonLoadingState(false)

                    if (response.isSuccessful) {
                        Log.d(TAG, "Response: $responseBody")

                        val resetToken = try {
                            val jsonObject = JSONObject(responseBody)
                            jsonObject.optString("resetToken", "")
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing JSON: ${e.message}")
                            ""
                        }

                        if (resetToken.isNotEmpty()) {
                            toast("Code verified successfully")
                            val intent = Intent(this@CodeVerificationActivity, NewPasswordSetupActivity::class.java)
                            intent.putExtra("email", email)
                            intent.putExtra("resetToken", resetToken)
                            startActivity(intent)
                            finish()
                        } else {
                            toast("Error: Invalid server response")
                            clearDigitInputs(*digitInputs)
                        }
                    } else {
                        val errorMessage = try {
                            val jsonObject = JSONObject(responseBody)
                            jsonObject.optString("message", "Invalid verification code")
                        } catch (e: Exception) {
                            "Invalid verification code"
                        }

                        if (response.code == 400 && errorMessage.contains("Too many attempts")) {
                            attemptCount = MAX_ATTEMPTS // Force disable further attempts
                        }

                        toast(errorMessage)
                        clearDigitInputs(*digitInputs)
                    }
                }
            } catch (e: IOException) {
                withContext(Dispatchers.Main) {
                    // Disable loading state
                    setButtonLoadingState(false)

                    toast("Network error: Please check your connection")
                    clearDigitInputs(*digitInputs)
                }
            }
        }
    }

    private fun resendResetCode(email: String) {
        // Disable resend button during the operation
        resendCodeButton.isEnabled = false
        resendCodeButton.text = "Sending..."

        // Show loading dialog while resending code
        loadingDialog.executeWithLoading(
            thresholdMs = 500,
            operation = {
                try {
                    val client = OkHttpClient()
                    val mediaType = "application/x-www-form-urlencoded".toMediaType()
                    val body = RequestBody.create(
                        mediaType,
                        "email=$email"
                    )
                    val request = Request.Builder()
                        .url("https://swamp-brief-brake.glitch.me/api/send-reset-code")
                        .post(body)
                        .addHeader("Content-Type", "application/x-www-form-urlencoded")
                        .build()

                    val response = client.newCall(request).execute()
                    val responseBody = response.body?.string() ?: "{}"

                    Log.d(TAG, "Resend code response: ${response.code}, $responseBody")

                    Pair(response.isSuccessful, responseBody)
                } catch (e: IOException) {
                    Log.e(TAG, "Network Error during resend: ${e.message}", e)
                    Pair(false, "Network error")
                } catch (e: Exception) {
                    Log.e(TAG, "Error during resend: ${e.message}", e)
                    Pair(false, "Unexpected error: ${e.message}")
                }
            },
            callback = { result ->
                // Re-enable resend button
                resendCodeButton.isEnabled = true
                resendCodeButton.text = "Resend code"

                val (isSuccess, responseBody) = result
                if (isSuccess) {
                    toast("New code sent to your email")
                    // Reset attempt counter
                    attemptCount = 0
                } else {
                    toast("Failed to send new code. Please try again.")
                }
            }
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clean up loading dialog resources
        loadingDialog.dispose()
    }
}