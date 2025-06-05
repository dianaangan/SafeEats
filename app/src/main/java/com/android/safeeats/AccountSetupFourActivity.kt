package com.android.safeeats

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import com.android.safeeats.utils.LoadingDialog
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class AccountSetupFourActivity : Activity() {
    private val TAG = "AccountSetupFourActivity"
    private lateinit var email: String
    private lateinit var firstName: String
    private lateinit var lastName: String
    private lateinit var selectedAllergens: ArrayList<String>
    private lateinit var allergenSeverity: ArrayList<String>
    private lateinit var loadingDialog: LoadingDialog

    private val okHttpClient = OkHttpClient.Builder()
        .callTimeout(30, TimeUnit.SECONDS)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_account_step_four)

        // Initialize loading dialog
        loadingDialog = LoadingDialog(this)

        // Get user info and selected allergens from intent
        email = intent.getStringExtra("email") ?: ""
        firstName = intent.getStringExtra("firstName") ?: ""
        lastName = intent.getStringExtra("lastName") ?: ""
        selectedAllergens = intent.getStringArrayListExtra("SELECTED_ALLERGENS") ?: ArrayList()
        allergenSeverity = intent.getStringArrayListExtra("ALLERGEN_SEVERITY") ?: ArrayList()

        // Initialize UI elements
        val btnSave = findViewById<Button>(R.id.btn_save)
        val btnBack = findViewById<ImageButton>(R.id.btn_back)
        val dietary = findViewById<TextView>(R.id.dietary)
        val preference = findViewById<TextView>(R.id.preference)

        val boldFont = androidx.core.content.res.ResourcesCompat.getFont(this, R.font.roboto_serif_bold)

        dietary.typeface = boldFont
        preference.typeface = boldFont

        // Back button simply goes back to previous screen
        btnBack.setOnClickListener {
            finish()
        }

        // Next button collects selected dietary preferences and saves all data
        btnSave.setOnClickListener {
            val dietaryPreferences = ArrayList<String>()

            // Add selected dietary preferences to list
            findViewById<CheckBox>(R.id.vegan)?.let { if (it.isChecked) dietaryPreferences.add("Vegan") }
            findViewById<CheckBox>(R.id.vegetarian)?.let { if (it.isChecked) dietaryPreferences.add("Vegetarian") }
            findViewById<CheckBox>(R.id.gluten_free)?.let { if (it.isChecked) dietaryPreferences.add("Gluten-Free") }
            findViewById<CheckBox>(R.id.lactose_free)?.let { if (it.isChecked) dietaryPreferences.add("Lactose-Free") }
            findViewById<CheckBox>(R.id.halal)?.let { if (it.isChecked) dietaryPreferences.add("Halal") }
            findViewById<CheckBox>(R.id.kosher)?.let { if (it.isChecked) dietaryPreferences.add("Kosher") }
            findViewById<CheckBox>(R.id.nut_free)?.let { if (it.isChecked) dietaryPreferences.add("Nut-Free") }

            // Check if at least one dietary preference is selected
            if (dietaryPreferences.isEmpty()) {
                Toast.makeText(
                    this@AccountSetupFourActivity,
                    "Please select at least one dietary preference",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            // Save all collected data using loading dialog
            loadingDialog.executeWithLoading(
                thresholdMs = 500, // Show loading dialog if it takes more than 500ms
                operation = {
                    // Return a boolean to indicate success/failure
                    saveUserPreferencesWithResult(dietaryPreferences)
                },
                callback = { result ->
                    // Handle save result in UI thread
                    handleSaveResult(result)
                }
            )
        }
    }

    // Data class for save operation result
    data class SaveResult(
        val isSuccess: Boolean,
        val message: String
    )

    private fun saveUserPreferencesWithResult(dietaryPreferences: ArrayList<String>): SaveResult {
        try {
            // 1. Parse allergen severity data
            val allergenData = ArrayList<Map<String, String>>()
            for (item in allergenSeverity) {
                val parts = item.split(":")
                if (parts.size == 2) {
                    allergenData.add(mapOf(
                        "name" to parts[0],
                        "level" to parts[1]
                    ))
                }
            }

            // 2. Send dietary preferences to server
            val success1 = saveDietaryPreferencesToServer(email, dietaryPreferences)

            // 3. Send allergens to server
            val success2 = saveAllergensToServer(email, allergenData)

            return if (success1 && success2) {
                SaveResult(isSuccess = true, message = "Account setup complete!")
            } else {
                SaveResult(isSuccess = false, message = "Failed to save preferences. Please try again.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving preferences: ${e.message}", e)
            return SaveResult(isSuccess = false, message = "Error: ${e.message}")
        }
    }

    // Handle save result on UI thread
    private fun handleSaveResult(result: SaveResult) {
        Toast.makeText(this@AccountSetupFourActivity, result.message, Toast.LENGTH_SHORT).show()

        if (result.isSuccess) {
            // Navigate to the main profile/home screen
            val intent = Intent(this@AccountSetupFourActivity, HomeActivity::class.java).apply {
                putExtra("email", email)
                putExtra("firstName", firstName)
                putExtra("lastName", lastName)
                // Clear back stack
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
            finish()
        }
    }

    private fun saveDietaryPreferencesToServer(email: String, preferences: ArrayList<String>): Boolean {
        return try {
            val jsonObject = JSONObject().apply {
                put("email", email)

                // Add dietary preferences array
                val preferencesArray = JSONArray()
                for (preference in preferences) {
                    preferencesArray.put(preference)
                }
                put("preferences", preferencesArray)
            }

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val body = RequestBody.create(mediaType, jsonObject.toString())

            val request = Request.Builder()
                .url("https://swamp-brief-brake.glitch.me/api/customer/dietary-preferences")
                .post(body)
                .addHeader("Content-Type", "application/json")
                .build()

            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string()

            Log.d(TAG, "Save preferences response code: ${response.code}")
            Log.d(TAG, "Save preferences response: $responseBody")

            if (response.isSuccessful) {
                val jsonResponse = responseBody?.let { JSONObject(it) }
                jsonResponse?.optBoolean("success", true) ?: false
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving dietary preferences: ${e.message}", e)
            false
        }
    }

    private fun saveAllergensToServer(email: String, allergenData: ArrayList<Map<String, String>>): Boolean {
        return try {
            val jsonObject = JSONObject().apply {
                put("email", email)

                // Add allergens array
                val allergensArray = JSONArray()
                for (allergen in allergenData) {
                    val allergenObj = JSONObject().apply {
                        put("name", allergen["name"])
                        put("level", allergen["level"])
                    }
                    allergensArray.put(allergenObj)
                }
                put("allergens", allergensArray)
            }

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val body = RequestBody.create(mediaType, jsonObject.toString())

            val request = Request.Builder()
                .url("https://swamp-brief-brake.glitch.me/api/customer/allergens")
                .post(body)
                .addHeader("Content-Type", "application/json")
                .build()

            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string()

            Log.d(TAG, "Save allergens response code: ${response.code}")
            Log.d(TAG, "Save allergens response: $responseBody")

            if (response.isSuccessful) {
                val jsonResponse = responseBody?.let { JSONObject(it) }
                jsonResponse?.optBoolean("success", true) ?: false
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving allergens: ${e.message}", e)
            false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        loadingDialog.dispose()
    }
}