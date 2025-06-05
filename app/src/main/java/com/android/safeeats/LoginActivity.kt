package com.android.safeeats

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.util.Log
import android.widget.*
import com.android.safeeats.utils.*
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.IOException
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class LoginActivity : Activity() {
    private val TAG = "LoginActivity"
    private lateinit var googleSignInClient: GoogleSignInClient
    private val RC_SIGN_IN = 9001
    private lateinit var loadingDialog: LoadingDialog

    private val okHttpClient = OkHttpClient.Builder()
        .callTimeout(30, TimeUnit.SECONDS)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        // Initialize loading dialog
        loadingDialog = LoadingDialog(this)

        val desc_hey = findViewById<TextView>(R.id.hey)
        val desc_welcometo = findViewById<TextView>(R.id.welcometo)
        val desc_safeeats = findViewById<TextView>(R.id.safeeats)

        val boldFont = androidx.core.content.res.ResourcesCompat.getFont(this, R.font.roboto_serif_bold)

        desc_hey.typeface = boldFont
        desc_welcometo.typeface = boldFont
        desc_safeeats.typeface = boldFont

        val et_username = findViewById<EditText>(R.id.et_email)
        val et_password = findViewById<EditText>(R.id.et_password)
        val back_button = findViewById<ImageButton>(R.id.btn_back)
        val button_login = findViewById<Button>(R.id.btn_login)
        val button_google_login = findViewById<Button>(R.id.btn_google_login)

        val togglePasswordButton = findViewById<ImageButton>(R.id.btn_toggle_password)
        val passwordEditText = findViewById<EditText>(R.id.et_password)
        val textview_forgotpassword = findViewById<TextView>(R.id.tv_forgot_password)


        googleSignInClient = GoogleSignIn.getClient(
            this, GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build()
        )

        // Set click listener for Google login button
        button_google_login.setOnClickListener {
            loadingDialog.showLoading()
            val signInIntent = googleSignInClient.signInIntent
            startActivityForResult(signInIntent, RC_SIGN_IN)
        }

        textview_forgotpassword.setOnClickListener{
            val intent = Intent(this, ForgotPasswordActivity::class.java)
            startActivity(intent)
        }

        back_button.setOnClickListener{
            val intent = Intent(this, LandingActivity::class.java)
            startActivity(intent)
        }

        // Check if we have data from registration
        val email = intent.getStringExtra("email")
        val password = intent.getStringExtra("password")

        if (email != null) {
            et_username.setText(email)
        }
        if (password != null) {
            et_password.setText(password)
        }

        button_login.setOnClickListener {
            if (et_username.text.toString().isNotEmpty() && et_password.text.toString().isNotEmpty()) {
                // Use loading dialog for login operation
                loadingDialog.executeWithLoading(
                    thresholdMs = 500, // Show loading dialog if it takes more than 500ms
                    operation = {
                        // Return a boolean to indicate success/failure
                        loginUserWithResult(et_username.text.toString(), et_password.text.toString())
                    },
                    callback = { loginResult ->
                        // Handle login result in UI thread
                        handleLoginResult(loginResult)
                    }
                )
            } else {
                toast("Please enter email and password")
                return@setOnClickListener
            }
        }

        togglePasswordButton.setOnClickListener {
            if (passwordEditText.transformationMethod is PasswordTransformationMethod) {
                passwordEditText.transformationMethod = HideReturnsTransformationMethod.getInstance()
                togglePasswordButton.setImageResource(R.drawable.ic_view)
            } else {
                passwordEditText.transformationMethod = PasswordTransformationMethod.getInstance()
                togglePasswordButton.setImageResource(R.drawable.ic_not_view)
            }
            passwordEditText.setSelection(passwordEditText.text.length)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == RC_SIGN_IN) {
            loadingDialog.hideLoading() // Hide loading dialog
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            handleGoogleSignInResult(task)
        }
    }

    // Handle Google Sign In result
    private fun handleGoogleSignInResult(completedTask: Task<GoogleSignInAccount>) {
        try {
            val account = completedTask.getResult(ApiException::class.java)
            
            // Google Sign In was successful, authenticate with backend
            val idToken = account.idToken
            if (idToken != null) {
                // Show loading while authenticating with backend
                loadingDialog.showLoading()
                
                // Run authentication in background thread
                Thread {
                    val success = authenticateWithGoogleToken(idToken)
                    runOnUiThread {
                        loadingDialog.hideLoading()
                        if (!success) {
                            // If Google authentication fails, clear the Google Sign-In state
                            googleSignInClient.signOut().addOnCompleteListener {
                                Toast.makeText(
                                    this@LoginActivity,
                                    "Google Sign-In failed. Please try email login instead.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                }.start()
            } else {
                Toast.makeText(this, "Google Sign-In failed: No ID token", Toast.LENGTH_SHORT).show()
            }
        } catch (e: ApiException) {
            Log.e(TAG, "Google sign in failed: ${e.statusCode}, ${e.message}")
            val errorMessage = when (e.statusCode) {
                GoogleSignInStatusCodes.SIGN_IN_CANCELLED -> "Sign in cancelled"
                GoogleSignInStatusCodes.NETWORK_ERROR -> "Network error. Please check your connection"
                else -> "Google Sign-In failed: ${e.message}"
            }
            Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during Google sign-in: ${e.message}", e)
            Toast.makeText(this, "Sign-in error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // Authenticate with Google token on server
    private fun authenticateWithGoogleToken(idToken: String): Boolean {
        Log.d(TAG, "Sending Google token to server for authentication")
        try {
            val jsonObject = JSONObject().apply {
                put("idToken", idToken)
            }

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val body = RequestBody.create(mediaType, jsonObject.toString())

            val request = Request.Builder()
                .url("https://swamp-brief-brake.glitch.me/api/customer/google-login")
                .post(body)
                .addHeader("Content-Type", "application/json")
                .build()

            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string()

            Log.d(TAG, "Google login response code: ${response.code}")
            Log.d(TAG, "Google login response: $responseBody")

            if (response.isSuccessful) {
                val jsonResponse = JSONObject(responseBody ?: "{}")
                val success = jsonResponse.optBoolean("success", false)

                if (success) {
                    val userEmail = jsonResponse.optString("email")
                    val firstName = jsonResponse.optString("firstname", "")
                    val lastName = jsonResponse.optString("lastname", "")
                    val isNewAccount = jsonResponse.optBoolean("isNewAccount", false)

                    Log.d(TAG, "Google authentication successful for $userEmail, new account: $isNewAccount")

                    runOnUiThread {
                        toast("Login successful")
                    }

                    // If it's a new account, go directly to account setup
                    if (isNewAccount) {
                        runOnUiThread {
                            val intent = Intent(this@LoginActivity, AccountSetupActivity::class.java).apply {
                                putExtra("email", userEmail)
                                putExtra("firstName", firstName) 
                                putExtra("lastName", lastName)
                            }
                            startActivity(intent)
                            finish()
                        }
                    } else {
                        // If it's an existing account, check setup status as normal
                        checkSetupStatusWithLoading(userEmail, firstName, lastName)
                    }
                    return true
                } else {
                    val message = jsonResponse.optString("message", "Google login failed")
                    val errorDetails = jsonResponse.optString("error", "")
                    Log.e(TAG, "Google authentication failed: $message, details: $errorDetails")
                    runOnUiThread { toast(message) }
                    return false
                }
            } else {
                val errorMessage = try {
                    val errorJson = JSONObject(responseBody ?: "{}")
                    errorJson.optString("message", "Google login failed")
                } catch (e: Exception) {
                    "Google login failed with status: ${response.code}"
                }
                Log.e(TAG, errorMessage)
                runOnUiThread { toast(errorMessage) }
                return false
            }
        } catch (e: IOException) {
            Log.e(TAG, "Network Error during Google authentication: ${e.message}", e)
            runOnUiThread { toast("Network error: Please check your connection") }
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Error during Google authentication: ${e.message}", e)
            runOnUiThread { toast("Error: ${e.message}") }
            return false
        }
    }

    // Function to send Google sign-in data to the server with result
    private fun sendGoogleSignInDataToServerWithResult(idToken: String?): Boolean {
        try {
            val jsonObject = JSONObject().apply {
                put("idToken", idToken)
            }

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val body = RequestBody.create(mediaType, jsonObject.toString())

            val request = Request.Builder()
                .url("https://swamp-brief-brake.glitch.me/api/customer/google-login")
                .post(body)
                .addHeader("Content-Type", "application/json")
                .build()

            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string()

            Log.d(TAG, "Google login response code: ${response.code}")
            Log.d(TAG, "Google login response: $responseBody")

            if (response.isSuccessful) {
                val jsonResponse = JSONObject(responseBody ?: "{}")
                val success = jsonResponse.optBoolean("success", false)

                if (success) {
                    val userEmail = jsonResponse.optString("email")
                    val firstName = jsonResponse.optString("firstname")
                    val lastName = jsonResponse.optString("lastname")
                    val isNewAccount = jsonResponse.optBoolean("isNewAccount", false)

                    // Run on main thread
                    runOnUiThread {

                        if (isNewAccount) {
                            // If it's a new account, go directly to the account setup
                            val intent = Intent(this@LoginActivity, AccountSetupActivity::class.java).apply {
                                putExtra("email", userEmail)
                                putExtra("firstName", firstName)
                                putExtra("lastName", lastName)
                            }
                            startActivity(intent)
                            finish()
                        } else {
                            // If it's an existing account, check setup status as normal
                            checkSetupStatusWithLoading(userEmail, firstName, lastName)
                        }
                    }
                    return true
                } else {
                    val message = jsonResponse.optString("message", "Google login failed")
                    runOnUiThread { toast(message) }
                    return false
                }
            } else {
                runOnUiThread { toast("Google login failed with status: ${response.code}") }
                return false
            }
        } catch (e: IOException) {
            Log.e(TAG, "Network Error: ${e.message}", e)
            runOnUiThread { toast("Network error: Please check your connection") }
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Error: ${e.message}", e)
            runOnUiThread { toast("Error: ${e.message}") }
            return false
        }
    }

    // New method that returns a result
    private fun loginUserWithResult(email: String, password: String): LoginResult {
        Log.d(TAG, "Starting email login for: $email")
        try {
            val jsonObject = JSONObject().apply {
                put("email", email)
                put("password", password)
            }

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val body = RequestBody.create(mediaType, jsonObject.toString())

            val request = Request.Builder()
                .url("https://swamp-brief-brake.glitch.me/api/customer/login")
                .post(body)
                .addHeader("Content-Type", "application/json")
                .build()

            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string()

            Log.d(TAG, "Login response code: ${response.code}")
            Log.d(TAG, "Login response: $responseBody")

            if (response.isSuccessful) {
                try {
                    val jsonResponse = JSONObject(responseBody ?: "{}")
                    val success = jsonResponse.optBoolean("success", false)

                    if (success) {
                        val userEmail = jsonResponse.optString("email", email)
                        val firstName = jsonResponse.optString("firstname", "User")
                        val lastName = jsonResponse.optString("lastname", "")

                        return LoginResult(
                            isSuccess = true,
                            email = userEmail,
                            firstName = firstName,
                            lastName = lastName,
                            message = "Login successful!"
                        )
                    } else {
                        val message = jsonResponse.optString("message", "Login failed")
                        return LoginResult(isSuccess = false, message = message)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing JSON: ${e.message}", e)
                    return LoginResult(isSuccess = false, message = "Error processing login response")
                }
            } else {
                val errorMessage = try {
                    val errorJson = JSONObject(responseBody ?: "{}")
                    errorJson.optString("message", "Invalid email or password")
                } catch (e: Exception) {
                    "Login failed: Invalid email or password"
                }

                Log.e(TAG, "Error: ${response.code} - $errorMessage")
                return LoginResult(isSuccess = false, message = errorMessage)
            }
        } catch (e: IOException) {
            Log.e(TAG, "Network Error: ${e.message}", e)
            return LoginResult(isSuccess = false, message = "Network error: Please check your connection")
        } catch (e: Exception) {
            Log.e(TAG, "Error: ${e.message}", e)
            return LoginResult(isSuccess = false, message = "Login error: ${e.message}")
        }
    }

    // Handle login result on UI thread
    private fun handleLoginResult(result: LoginResult) {
        if (result.isSuccess) {
            toast(result.message)
            checkSetupStatusWithLoading(result.email, result.firstName, result.lastName)
        } else {
            toast(result.message)
        }
    }

    // Check setup status with loading dialog
    private fun checkSetupStatusWithLoading(email: String, firstName: String, lastName: String) {
        loadingDialog.executeWithLoading(
            thresholdMs = 500,
            operation = {
                checkSetupStatusWithResult(email, firstName, lastName)
            },
            callback = { setupResult ->
                if (setupResult.isSuccess) {
                    if (setupResult.hasCompletedSetup) {
                        val intent = Intent(this@LoginActivity, HomeActivity::class.java).apply {
                            putExtra("email", email)
                            putExtra("firstName", firstName)
                            putExtra("lastName", lastName)
                        }
                        startActivity(intent)
                        finish()
                    } else {
                        // If setup is not complete, go to account setup
                        val intent = Intent(this@LoginActivity, AccountSetupActivity::class.java).apply {
                            putExtra("email", email)
                            putExtra("firstName", firstName)
                            putExtra("lastName", lastName)
                        }
                        startActivity(intent)
                        finish()
                    }
                } else {
                    val intent = Intent(this@LoginActivity, HomeActivity::class.java).apply {
                        putExtra("email", email)
                        putExtra("firstName", firstName)
                        putExtra("lastName", lastName)
                    }
                    startActivity(intent)
                    finish()
                }
            }
        )
    }

    // Check setup status with result
    private fun checkSetupStatusWithResult(email: String, firstName: String, lastName: String): SetupStatusResult {
        try {
            val request = Request.Builder()
                .url("https://swamp-brief-brake.glitch.me/api/customer/setup-status?email=$email")
                .get()
                .build()

            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string()

            if (response.isSuccessful) {
                val jsonResponse = JSONObject(responseBody ?: "{}")
                val success = jsonResponse.optBoolean("success", false)

                if (success) {
                    val hasCompletedSetup = jsonResponse.optBoolean("hasCompletedSetup", false)
                    return SetupStatusResult(isSuccess = true, hasCompletedSetup = hasCompletedSetup)
                } else {
                    return SetupStatusResult(isSuccess = false, hasCompletedSetup = false)
                }
            } else {
                return SetupStatusResult(isSuccess = false, hasCompletedSetup = false)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking setup status: ${e.message}", e)
            return SetupStatusResult(isSuccess = false, hasCompletedSetup = false)
        }
    }

    // Data class for login result
    data class LoginResult(
        val isSuccess: Boolean,
        val email: String = "",
        val firstName: String = "",
        val lastName: String = "",
        val message: String
    )

    // Data class for setup status result
    data class SetupStatusResult(
        val isSuccess: Boolean,
        val hasCompletedSetup: Boolean
    )

    override fun onDestroy() {
        super.onDestroy()
        // Clean up loading dialog resources
        loadingDialog.dispose()
    }
}