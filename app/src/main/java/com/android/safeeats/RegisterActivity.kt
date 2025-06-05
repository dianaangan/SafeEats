package com.android.safeeats

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import com.android.safeeats.utils.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class RegisterActivity : Activity() {
    private val TAG = "RegisterActivity"
    private lateinit var loadingDialog: LoadingDialog

    private val okHttpClient = OkHttpClient.Builder()
        .callTimeout(30, TimeUnit.SECONDS)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        // Initialize loading dialog
        loadingDialog = LoadingDialog(this)


        val desc_letsget = findViewById<TextView>(R.id.letsget)
        val desc_started = findViewById<TextView>(R.id.started)

        val boldFont = androidx.core.content.res.ResourcesCompat.getFont(this, R.font.roboto_serif_bold)

        desc_letsget.typeface = boldFont
        desc_started.typeface = boldFont

        val edittext_firstname = findViewById<EditText>(R.id.et_firstname)
        val edittext_middlename = findViewById<EditText>(R.id.et_middlename)
        val edittext_lastname = findViewById<EditText>(R.id.et_lastname)
        val edittext_email = findViewById<EditText>(R.id.et_email)
        val edittext_password = findViewById<EditText>(R.id.et_password)
        val edittext_confirm_password = findViewById<EditText>(R.id.et_confirm_password)

        val button_register = findViewById<Button>(R.id.btn_register)
        val textview_termsAndPrivacy = findViewById<TextView>(R.id.tv_termsAndPrivacy)
        val togglePasswordButton = findViewById<ImageButton>(R.id.btn_toggle_password)
        val toggleConfirmPasswordButton = findViewById<ImageButton>(R.id.btn_toggle_confirm_password)
        val passwordEditText = findViewById<EditText>(R.id.et_password)
        val confirmPasswordEditText = findViewById<EditText>(R.id.et_confirm_password)
        val back_button = findViewById<ImageButton>(R.id.btn_back)

        back_button.setOnClickListener {
            val intent = Intent(this, LandingActivity::class.java)
            startActivity(intent)
        }

        // Password toggle functionality (unchanged)
        togglePasswordButton.setOnClickListener {
            if (passwordEditText.transformationMethod is PasswordTransformationMethod) {
                // Show password
                passwordEditText.transformationMethod = HideReturnsTransformationMethod.getInstance()
                togglePasswordButton.setImageResource(R.drawable.ic_view)
            } else {
                // Hide password
                passwordEditText.transformationMethod = PasswordTransformationMethod.getInstance()
                togglePasswordButton.setImageResource(R.drawable.ic_not_view)
            }
            // Move cursor to end of text
            passwordEditText.setSelection(passwordEditText.text.length)
        }

        toggleConfirmPasswordButton.setOnClickListener {
            if (confirmPasswordEditText.transformationMethod is PasswordTransformationMethod) {
                // Show password
                confirmPasswordEditText.transformationMethod = HideReturnsTransformationMethod.getInstance()
                toggleConfirmPasswordButton.setImageResource(R.drawable.ic_view)
            } else {
                // Hide password
                confirmPasswordEditText.transformationMethod = PasswordTransformationMethod.getInstance()
                toggleConfirmPasswordButton.setImageResource(R.drawable.ic_not_view)
            }
            // Move cursor to end of text
            confirmPasswordEditText.setSelection(confirmPasswordEditText.text.length)
        }

        // Regular Register Button
        button_register.setOnClickListener {
            // Trim whitespace from all inputs
            val email = edittext_email.text.toString().trim()
            val password = edittext_password.text.toString().trim()
            val confirmPassword = edittext_confirm_password.text.toString().trim()
            val firstName = edittext_firstname.text.toString().trim()
            val middleName = edittext_middlename.text.toString().trim()
            val lastName = edittext_lastname.text.toString().trim()

            // Validate email
            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                toast("Invalid email format!")
                return@setOnClickListener
            }

            // Validate password match
            if (password != confirmPassword) {
                toast("Password and confirm password must be the same!")
                edittext_password.text.clear()
                edittext_confirm_password.text.clear()
                return@setOnClickListener
            }

            // Validate required fields
            when {
                email.isEmpty() -> {
                    toast("Email is required")
                    return@setOnClickListener
                }
                password.isEmpty() -> {
                    toast("Password is required")
                    return@setOnClickListener
                }
                firstName.isEmpty() -> {
                    toast("First Name is required")
                    return@setOnClickListener
                }
                lastName.isEmpty() -> {
                    toast("Last Name is required")
                    return@setOnClickListener
                }
                else -> {
                    // Proceed with registration using loading dialog
                    loadingDialog.executeWithLoading(
                        thresholdMs = 500, // Show loading dialog if it takes more than 500ms
                        operation = {
                            // Return a boolean to indicate success/failure
                            registerUserWithResult(
                                email,
                                password,
                                firstName,
                                middleName.takeIf { it.isNotEmpty() },
                                lastName
                            )
                        },
                        callback = { registerResult ->
                            // Handle registration result in UI thread
                            handleRegisterResult(registerResult, email)
                        }
                    )
                }
            }
        }

        textview_termsAndPrivacy.setOnClickListener {
            val intent = Intent(this, TermsAndPrivacyActivity::class.java)
            startActivity(intent)
        }
    }

    // Data class for registration result
    data class RegisterResult(
        val isSuccess: Boolean,
        val message: String
    )

    private fun registerUserWithResult(
        email: String,
        password: String,
        firstName: String,
        middleName: String?,
        lastName: String
    ): RegisterResult {
        Log.d(TAG, "Starting registration for: $email")
        try {
            val jsonBody = JSONObject().apply {
                put("email", email)
                put("password", password)
                put("firstname", firstName)
                put("lastname", lastName)
                middleName?.let { put("middlename", it) }
            }

            val mediaType = "application/json".toMediaType()
            val body = jsonBody.toString().toRequestBody(mediaType)

            // Log the exact JSON being sent
            Log.d(TAG, "Registration Request JSON: $jsonBody")

            val request = Request.Builder()
                .url("https://swamp-brief-brake.glitch.me/api/customer/register")
                .post(body)
                .addHeader("Content-Type", "application/json")
                .build()

            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string()

            // Log full response details
            Log.d(TAG, "Response Code: ${response.code}")
            Log.d(TAG, "Response Body: $responseBody")

            if (response.isSuccessful) {
                println("Response: $responseBody")
                return RegisterResult(
                    isSuccess = true,
                    message = "Registration successful!"
                )
            } else {
                // Parse error response if possible
                val errorMessage = try {
                    val errorJson = JSONObject(responseBody ?: "{}")
                    errorJson.optString("message", "Unknown error occurred")
                } catch (e: Exception) {
                    "Registration failed: ${response.message}"
                }

                Log.e(TAG, "Registration Error: $errorMessage")
                return RegisterResult(isSuccess = false, message = errorMessage)
            }
        } catch (e: IOException) {
            Log.e(TAG, "Network Error: ${e.message}", e)
            return RegisterResult(isSuccess = false, message = "Network error: Please check your connection")
        } catch (e: Exception) {
            Log.e(TAG, "Error: ${e.message}", e)
            return RegisterResult(isSuccess = false, message = "Registration error: ${e.message}")
        }
    }

    // Handle registration result on UI thread
    private fun handleRegisterResult(result: RegisterResult, email: String) {
        if (result.isSuccess) {
            toast(result.message)
            startActivity(
                Intent(this, LoginActivity::class.java).apply {
                    putExtra("email", email)
                }
            )
        } else {
            toast(result.message)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clean up loading dialog resources
        loadingDialog.dispose()
    }
}