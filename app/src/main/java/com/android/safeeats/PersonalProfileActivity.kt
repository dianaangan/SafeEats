package com.android.safeeats

import android.Manifest
import android.app.Activity
import android.app.Dialog
import android.content.*
import android.content.pm.PackageManager
import android.graphics.*
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.*
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.*
import com.android.safeeats.utils.LoadingDialog
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.*
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class PersonalProfileActivity : Activity() {
    private val TAG = "PersonalProfileActivity"

    private lateinit var preferencesContainer: LinearLayout
    private lateinit var allergensContainer: LinearLayout
    private lateinit var addPreferenceButton: ImageButton
    private lateinit var addAllergenButton: ImageButton
    private lateinit var preferenceName: TextView
    private lateinit var allergenName: TextView
    private lateinit var allergenLevel: TextView
    private lateinit var confirmPreferenceButton: ImageButton
    private lateinit var confirmAllergenButton: ImageButton
    private lateinit var cancelPreferenceButton: ImageButton
    private var dietaryPreferences = mutableListOf<String>()
    private var allergens = mutableListOf<Pair<String, String>>()

    // User data
    private lateinit var email: String
    private lateinit var firstName: String
    private var middleName: String? = null
    private lateinit var lastName: String
    private var isGoogleAccount: Boolean = false

    // UI Elements
    private lateinit var tvEmail: TextView
    private lateinit var etFirstName: EditText
    private lateinit var etMiddleName: EditText
    private lateinit var etLastName: EditText

    private lateinit var btnEditFirstName: ImageButton
    private lateinit var btnEditMiddleName: ImageButton
    private lateinit var btnEditLastName: ImageButton

    private lateinit var btnCancelFirstName: ImageButton
    private lateinit var btnCancelMiddleName: ImageButton
    private lateinit var btnCancelLastName: ImageButton

    private lateinit var btnBack: ImageButton

    // Profile Picture UI Elements
    private lateinit var profileImageView: ImageView
    private lateinit var changePhotoButton: ImageButton
    private val STORAGE_PERMISSION_CODE = 101
    private val CAMERA_PERMISSION_CODE = 102
    private val GALLERY_REQUEST_CODE = 201
    private val CAMERA_REQUEST_CODE = 202

    // Camera photo URI
    private var currentPhotoUri: Uri? = null

    companion object {
        const val TAB_PERSONAL = 0
        const val TAB_DIETARY = 1
        const val TAB_ALLERGEN = 2
        const val SELECTED_TAB = "selected_tab"
    }

    // HTTP Client
    private val okHttpClient = OkHttpClient.Builder()
        .callTimeout(30, TimeUnit.SECONDS)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)  // Add write timeout
        .retryOnConnectionFailure(true)
        .connectionPool(ConnectionPool(5, 30, TimeUnit.SECONDS))  // Optimize connection pool
        .build()

    private lateinit var loadingDialog: LoadingDialog

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_personal_profile)

        // Initialize the loading dialog
        loadingDialog = LoadingDialog(this)

        // Get user info from intent
        email = intent.getStringExtra("email") ?: ""
        firstName = intent.getStringExtra("firstName") ?: ""
        lastName = intent.getStringExtra("lastName") ?: ""

        val profileName = findViewById<TextView>(R.id.profileName)
        profileName.text = firstName

        val profiledesc = findViewById<TextView>(R.id.profiledesc)

        val boldFont = androidx.core.content.res.ResourcesCompat.getFont(this, R.font.roboto_serif_bold)
        val regularFont = androidx.core.content.res.ResourcesCompat.getFont(this, R.font.roboto_serif_regular)

        profiledesc.typeface = boldFont
        profileName.typeface = regularFont

        // Get Google account flag from intent if available
        isGoogleAccount = intent.getBooleanExtra("isGoogleAccount", false)

        // Initialize UI elements first
        initializeUIElements()
        initializeDietaryUI()
        initializeAllergenUI()

        // Fetch user data - with explicit callbacks to ensure UI updates
        fetchUserProfile()

        // Fetch dietary preferences and allergens
        fetchDietaryPreferences()
        fetchAllergens()

        // Load profile picture if available
        loadProfilePicture()

        // Initialize profile picture UI elements
        profileImageView = findViewById(R.id.profileImage)
        changePhotoButton = findViewById(R.id.changePhotoButton)

        // Set click listener for change photo button
        changePhotoButton.setOnClickListener {
            showImagePickerDialog()
        }

        btnBack = findViewById(R.id.backButton)

        btnBack.setOnClickListener {
            prepareResultIntent()
            finish()
        }


        // Initialize tab views and indicators
        val personalTab = findViewById<TextView>(R.id.profileTab)
        val dietaryTab = findViewById<TextView>(R.id.dietaryTab)
        val allergenTab = findViewById<TextView>(R.id.allergenTab)

        val personalScrollView = findViewById<ScrollView>(R.id.personalScrollView)
        val dietaryScrollView = findViewById<ScrollView>(R.id.dietaryScrollView)
        val allergenScrollView = findViewById<ScrollView>(R.id.allergenScrollView)

        val personalTabIndicator = findViewById<View>(R.id.personalTabIndicator)
        val dietaryTabIndicator = findViewById<View>(R.id.dietaryTabIndicator)
        val allergenTabIndicator = findViewById<View>(R.id.allergenTabIndicator)

        fun switchToTab(tabIndex: Int) {
            when (tabIndex) {
                TAB_PERSONAL -> {
                    // Show personal tab
                    personalScrollView.visibility = View.VISIBLE
                    dietaryScrollView.visibility = View.GONE
                    allergenScrollView.visibility = View.GONE

                    // Update tab styles
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        personalTab.typeface = resources.getFont(R.font.roboto_serif_bold)
                        dietaryTab.typeface = resources.getFont(R.font.roboto_serif_regular)
                        allergenTab.typeface = resources.getFont(R.font.roboto_serif_regular)
                    }

                    // Update indicators
                    personalTabIndicator.setBackgroundColor(ContextCompat.getColor(this, R.color.orange))
                    dietaryTabIndicator.setBackgroundColor(ContextCompat.getColor(this, R.color.light_gray))
                    allergenTabIndicator.setBackgroundColor(ContextCompat.getColor(this, R.color.light_gray))
                }
                TAB_DIETARY -> {
                    // Show dietary tab
                    personalScrollView.visibility = View.GONE
                    dietaryScrollView.visibility = View.VISIBLE
                    allergenScrollView.visibility = View.GONE

                    // Update tab styles
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        personalTab.typeface = resources.getFont(R.font.roboto_serif_regular)
                        dietaryTab.typeface = resources.getFont(R.font.roboto_serif_bold)
                        allergenTab.typeface = resources.getFont(R.font.roboto_serif_regular)
                    }

                    // Update indicators
                    personalTabIndicator.setBackgroundColor(ContextCompat.getColor(this, R.color.light_gray))
                    dietaryTabIndicator.setBackgroundColor(ContextCompat.getColor(this, R.color.orange))
                    allergenTabIndicator.setBackgroundColor(ContextCompat.getColor(this, R.color.light_gray))
                }
                TAB_ALLERGEN -> {
                    // Show allergen tab
                    personalScrollView.visibility = View.GONE
                    dietaryScrollView.visibility = View.GONE
                    allergenScrollView.visibility = View.VISIBLE

                    // Update tab styles
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        personalTab.typeface = resources.getFont(R.font.roboto_serif_regular)
                        dietaryTab.typeface = resources.getFont(R.font.roboto_serif_regular)
                        allergenTab.typeface = resources.getFont(R.font.roboto_serif_bold)
                    }

                    // Update indicators
                    personalTabIndicator.setBackgroundColor(ContextCompat.getColor(this, R.color.light_gray))
                    dietaryTabIndicator.setBackgroundColor(ContextCompat.getColor(this, R.color.light_gray))
                    allergenTabIndicator.setBackgroundColor(ContextCompat.getColor(this, R.color.orange))
                }
            }
        }

        // Set up tab click listeners using the switchToTab function
        personalTab.setOnClickListener {
            switchToTab(TAB_PERSONAL)
        }

        dietaryTab.setOnClickListener {
            switchToTab(TAB_DIETARY)
        }

        allergenTab.setOnClickListener {
            switchToTab(TAB_ALLERGEN)
        }

        // Check if a specific tab was requested
        val selectedTab = intent.getIntExtra(SELECTED_TAB, TAB_PERSONAL)
        switchToTab(selectedTab)

        val addPreferenceButton = findViewById<ImageButton>(R.id.addPreference)
        val addNewPreferenceLayout = findViewById<LinearLayout>(R.id.addNewPreferenceLayout)

        addPreferenceButton.setOnClickListener {
            addNewPreferenceLayout.visibility = View.VISIBLE
        }

        val cancelPreferenceButton = findViewById<ImageButton>(R.id.cancelPreferenceButton)
        cancelPreferenceButton.setOnClickListener {
            addNewPreferenceLayout.visibility = View.GONE
        }

        confirmPreferenceButton.setOnClickListener {
            val preference = preferenceName.text.toString()
            if (preference != "Select Preference") {
                if (!dietaryPreferences.contains(preference)) {
                    addDietaryPreference(preference)
                    Toast.makeText(this, "Preference successfully added", Toast.LENGTH_SHORT).show()
                    preferenceName.text = "Select Preference"
                    findViewById<LinearLayout>(R.id.addNewPreferenceLayout).visibility = View.GONE
                } else {
                    Toast.makeText(this, "This preference is already added", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Please select a preference", Toast.LENGTH_SHORT).show()
            }
        }

        val addAllergenButton = findViewById<ImageButton>(R.id.addAllergen)
        val addNewAllergenLayout = findViewById<LinearLayout>(R.id.addNewAllergenLayout)

        addAllergenButton.setOnClickListener {
            addNewAllergenLayout.visibility = View.VISIBLE
        }

        val cancelAllergenButton = findViewById<ImageButton>(R.id.cancelAllergenButton)
        cancelAllergenButton.setOnClickListener {
            addNewAllergenLayout.visibility = View.GONE
        }

        // Update in initializeAllergenUI() method:
        confirmAllergenButton.setOnClickListener {
            val allergen = allergenName.text.toString()
            val level = allergenLevel.text.toString()

            if (allergen != "Select Allergen" && level != "Select Level") {
                // Check if already exists before showing toast
                if (!allergens.any { it.first == allergen }) {
                    addAllergen(allergen, level)
                    Toast.makeText(this, "Allergen successfully added", Toast.LENGTH_SHORT).show()
                    allergenName.text = "Select Allergen"
                    allergenLevel.text = "Select Level"
                    findViewById<LinearLayout>(R.id.addNewAllergenLayout).visibility = View.GONE
                } else {
                    Toast.makeText(this, "This allergen is already added", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Please select both allergen and severity level", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun initializeUIElements() {
        // Text views and edit texts
        tvEmail = findViewById(R.id.tv_email)
        etFirstName = findViewById(R.id.et_first_name)
        etMiddleName = findViewById(R.id.et_middle_name)
        etLastName = findViewById(R.id.et_last_name)

        // Edit buttons
        btnEditFirstName = findViewById(R.id.btn_edit_first_name)
        btnEditMiddleName = findViewById(R.id.btn_edit_middle_name)
        btnEditLastName = findViewById(R.id.btn_edit_last_name)

        // Cancel buttons
        btnCancelFirstName = findViewById(R.id.btn_cancel_first_name)
        btnCancelMiddleName = findViewById(R.id.btn_cancel_middle_name)
        btnCancelLastName = findViewById(R.id.btn_cancel_last_name)

        // Set up initial state - all fields non-editable
        setAllFieldsNonEditable()

        // Set up edit button click listeners
        setupEditButtonListeners()

        // Set initial data if available
        tvEmail.text = email
        etFirstName.setText(firstName)
        etLastName.setText(lastName)
    }

    private fun initializeDietaryUI() {
        preferencesContainer = findViewById(R.id.preferencesContainer)
        addPreferenceButton = findViewById(R.id.addPreference)
        preferenceName = findViewById(R.id.preferenceName)
        confirmPreferenceButton = findViewById(R.id.confirmPreferenceButton)
        cancelPreferenceButton = findViewById(R.id.cancelPreferenceButton)

        // Set up click listeners
        addPreferenceButton.setOnClickListener {
            preferenceName.text = "Select Preference"
            cancelPreferenceButton.visibility = View.VISIBLE
        }

        preferenceName.setOnClickListener {
            showPreferenceOptions()
        }

        findViewById<View>(R.id.preferenceDropdown).setOnClickListener {
            showPreferenceOptions()
        }

        confirmPreferenceButton.setOnClickListener {
            val preference = preferenceName.text.toString()
            if (preference != "Select Preference") {
                addDietaryPreference(preference)
                Toast.makeText(this, "Dietary added successfully", Toast.LENGTH_SHORT).show()
                preferenceName.text = "Select Preference"
                cancelPreferenceButton.visibility = View.INVISIBLE
            } else {
                Toast.makeText(this, "Please select a preference", Toast.LENGTH_SHORT).show()
            }
        }

        cancelPreferenceButton.setOnClickListener {
            preferenceName.text = "Select Preference"
            cancelPreferenceButton.visibility = View.INVISIBLE
        }
    }

    private fun initializeAllergenUI() {
        allergensContainer = findViewById(R.id.allergensContainer)
        addAllergenButton = findViewById(R.id.addAllergen)
        allergenName = findViewById(R.id.allergenName)
        allergenLevel = findViewById(R.id.allergenLevel)
        confirmAllergenButton = findViewById(R.id.confirmAllergenButton)

        // Set up click listeners
        addAllergenButton.setOnClickListener {
            allergenName.text = "Select Allergen"
            allergenLevel.text = "Select Level"
        }

        allergenName.setOnClickListener {
            showAllergenOptions()
        }

        allergenLevel.setOnClickListener {
            showAllergenLevelOptions()
        }

        findViewById<View>(R.id.allergenDropdown).setOnClickListener {
            showAllergenOptions()
        }

        findViewById<View>(R.id.levelDropdown).setOnClickListener {
            showAllergenLevelOptions()
        }

        confirmAllergenButton.setOnClickListener {
            val allergen = allergenName.text.toString()
            val level = allergenLevel.text.toString()

            if (allergen != "Select Allergen" && level != "Select Level") {
                addAllergen(allergen, level)
                allergenName.text = "Select Allergen"
                allergenLevel.text = "Select Level"
            } else {
                Toast.makeText(this, "Please select both allergen and severity level", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun fetchUserProfile() {
        loadingDialog.executeWithLoading(
            thresholdMs = 500, // Show loading if operation takes more than 500ms
            operation = {
                try {
                    val request = Request.Builder()
                        .url("https://swamp-brief-brake.glitch.me/api/customer/profile?email=$email")
                        .get()
                        .build()

                    val response = okHttpClient.newCall(request).execute()
                    val responseBody = response.body?.string()

                    if (response.isSuccessful && responseBody != null) {
                        val jsonResponse = JSONObject(responseBody)

                        if (jsonResponse.optBoolean("success", false)) {
                            val userData = jsonResponse.getJSONObject("user")

                            // Extract user data
                            val userEmail = userData.getString("email")
                            val userFirstName = userData.getString("firstName")
                            val userMiddleName = userData.optString("middleName", "")
                            val userLastName = userData.getString("lastName")

                            // Check if account is Google account from API response if available
                            val accountType = userData.optString("registrationType", "")
                            val isGoogleAccountFromApi = accountType.equals("google", ignoreCase = true)

                            // Update isGoogleAccount flag if API provides the information
                            if (accountType.isNotEmpty()) {
                                isGoogleAccount = isGoogleAccountFromApi
                            }

                            // Return profile fetch result
                            return@executeWithLoading ProfileResult(
                                isSuccess = true,
                                firstName = userFirstName,
                                middleName = userMiddleName,
                                lastName = userLastName,
                                email = userEmail
                            )
                        } else {
                            val errorMessage = jsonResponse.optString("message", "Unknown error")
                            return@executeWithLoading ProfileResult(isSuccess = false, errorMessage = errorMessage)
                        }
                    } else {
                        return@executeWithLoading ProfileResult(isSuccess = false)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error fetching user profile: ${e.message}", e)
                    return@executeWithLoading ProfileResult(isSuccess = false, errorMessage = e.message ?: "Unknown error")
                }
            },
            callback = { result ->
                if (result.isSuccess) {
                    // Update member variables
                    firstName = result.firstName
                    middleName = if (result.middleName.isNotEmpty()) result.middleName else null
                    lastName = result.lastName

                    // Update UI
                    tvEmail.text = result.email
                    etFirstName.setText(result.firstName)
                    etMiddleName.setText(result.middleName)
                    etLastName.setText(result.lastName)
                } else {

                }
            }
        )
    }

    private fun fetchDietaryPreferences() {
        loadingDialog.executeWithLoading(
            thresholdMs = 500,
            operation = {
                try {
                    val request = Request.Builder()
                        .url("https://swamp-brief-brake.glitch.me/api/customer/dietary-preferences?email=$email")
                        .get()
                        .build()

                    val response = okHttpClient.newCall(request).execute()
                    val responseBody = response.body?.string()

                    if (response.isSuccessful && responseBody != null) {
                        val jsonResponse = JSONObject(responseBody)

                        if (jsonResponse.optBoolean("success", false)) {
                            val preferencesArray = jsonResponse.getJSONArray("preferences")
                            val fetchedPreferences = mutableListOf<String>()

                            for (i in 0 until preferencesArray.length()) {
                                val preference = preferencesArray.getJSONObject(i).getString("name")
                                fetchedPreferences.add(preference)
                            }

                            return@executeWithLoading FetchDietaryResult(
                                isSuccess = true,
                                preferences = fetchedPreferences
                            )
                        } else {
                            val message = jsonResponse.optString("message", "Unknown error")
                            return@executeWithLoading FetchDietaryResult(
                                isSuccess = false,
                                errorMessage = message
                            )
                        }
                    } else {
                        return@executeWithLoading FetchDietaryResult(
                            isSuccess = false,
                            errorMessage = "API request failed with status code: ${response.code}"
                        )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error fetching dietary preferences: ${e.message}", e)
                    return@executeWithLoading FetchDietaryResult(
                        isSuccess = false,
                        errorMessage = e.message ?: "Unknown error"
                    )
                }
            },
            callback = { result ->
                if (result.isSuccess) {
                    dietaryPreferences = result.preferences
                    updateDietaryPreferencesUI()

                    if (dietaryPreferences.isEmpty()) {
                        Log.d(TAG, "No dietary preferences found for user")
                    } else {
                        Log.d(TAG, "Loaded ${dietaryPreferences.size} dietary preferences")
                    }
                } else {
                    Log.e(TAG, "Failed to fetch dietary preferences: ${result.errorMessage}")
                    Toast.makeText(
                        this@PersonalProfileActivity,
                        "Could not retrieve your dietary preferences. Please try again.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )
    }

    private fun fetchAllergens() {
        loadingDialog.executeWithLoading(
            thresholdMs = 500,
            operation = {
                try {
                    val request = Request.Builder()
                        .url("https://swamp-brief-brake.glitch.me/api/customer/allergens?email=$email")
                        .get()
                        .build()

                    val response = okHttpClient.newCall(request).execute()
                    val responseBody = response.body?.string()

                    if (response.isSuccessful && responseBody != null) {
                        val jsonResponse = JSONObject(responseBody)

                        if (jsonResponse.optBoolean("success", false)) {
                            val allergensArray = jsonResponse.getJSONArray("allergens")
                            val fetchedAllergens = mutableListOf<Pair<String, String>>()

                            for (i in 0 until allergensArray.length()) {
                                val allergenObj = allergensArray.getJSONObject(i)
                                val allergen = allergenObj.getString("name")
                                val level = allergenObj.getString("level")
                                fetchedAllergens.add(Pair(allergen, level))
                            }

                            return@executeWithLoading FetchAllergensResult(
                                isSuccess = true,
                                allergens = fetchedAllergens
                            )
                        } else {
                            val message = jsonResponse.optString("message", "Unknown error")
                            return@executeWithLoading FetchAllergensResult(
                                isSuccess = false,
                                errorMessage = message
                            )
                        }
                    } else {
                        return@executeWithLoading FetchAllergensResult(
                            isSuccess = false,
                            errorMessage = "API request failed with status code: ${response.code}"
                        )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error fetching allergens: ${e.message}", e)
                    return@executeWithLoading FetchAllergensResult(
                        isSuccess = false,
                        errorMessage = e.message ?: "Unknown error"
                    )
                }
            },
            callback = { result ->
                if (result.isSuccess) {
                    allergens = result.allergens.toMutableList()
                    updateAllergensUI()

                    if (allergens.isEmpty()) {
                        Log.d(TAG, "No allergens found for user")
                    } else {
                        Log.d(TAG, "Loaded ${allergens.size} allergens")
                    }
                } else {
                    Log.e(TAG, "Failed to fetch allergens: ${result.errorMessage}")
                    Toast.makeText(
                        this@PersonalProfileActivity,
                        "Could not retrieve your allergen information. Please try again.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )
    }

    // Add these result classes for fetching operations
    data class FetchDietaryResult(
        val isSuccess: Boolean,
        val preferences: MutableList<String> = mutableListOf(),
        val errorMessage: String = ""
    )

    data class FetchAllergensResult(
        val isSuccess: Boolean,
        val allergens: List<Pair<String, String>> = listOf(),
        val errorMessage: String = ""
    )

    // Don't forget to add this helper method if it's not already included
    private fun showError(message: String) {
        CoroutineScope(Dispatchers.Main).launch {
            Toast.makeText(this@PersonalProfileActivity, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadProfilePicture() {
        loadingDialog.executeWithLoading(
            thresholdMs = 500,
            operation = {
                try {
                    // URL encode the email to handle special characters in email addresses
                    val encodedEmail = URLEncoder.encode(email, "UTF-8")

                    val request = Request.Builder()
                        .url("https://swamp-brief-brake.glitch.me/api/customer/profile-picture?email=$encodedEmail")
                        .get()
                        .build()

                    Log.d(TAG, "Requesting profile picture for email: $email")

                    val response = okHttpClient.newCall(request).execute()

                    if (response.isSuccessful) {
                        val responseBody = response.body
                        if (responseBody != null) {
                            val inputStream = responseBody.byteStream()
                            val bitmap = BitmapFactory.decodeStream(inputStream)
                            return@executeWithLoading ProfilePictureResult(isSuccess = true, bitmap = bitmap)
                        } else {
                            Log.e(TAG, "Response body is null for $email")
                            return@executeWithLoading ProfilePictureResult(isSuccess = false, errorMessage = "Empty response")
                        }
                    } else {
                        Log.e(TAG, "Failed to load profile picture. Response code: ${response.code}")
                        if (response.code == 404) {
                            Log.i(TAG, "No profile picture found for $email")
                            return@executeWithLoading ProfilePictureResult(isSuccess = false, errorMessage = "No profile picture found", notFound = true)
                        }
                        return@executeWithLoading ProfilePictureResult(isSuccess = false, errorMessage = "Failed to load profile picture")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error loading profile picture: ${e.message}")
                    return@executeWithLoading ProfilePictureResult(isSuccess = false, errorMessage = e.message ?: "Unknown error")
                }
            },
            callback = { result ->
                if (result.isSuccess && result.bitmap != null) {
                    profileImageView.setImageBitmap(result.bitmap)
                    profileImageView.background = getDrawable(R.drawable.profile_avatar_background)
                    profileImageView.clipToOutline = true
                    Log.d(TAG, "Successfully loaded profile picture for $email")
                } else if (result.notFound) {
                    Log.i(TAG, "No profile picture found for $email")
                } else {
                    Log.e(TAG, "Failed to load profile picture: ${result.errorMessage}")
                }
            }
        )
    }

    override fun onResume() {
        super.onResume()
        // Reload profile picture when returning to this activity (e.g., after updating it in profile screen)
        loadProfilePicture()
    }

    // Define result classes for the loading operations
    data class ProfileResult(
        val isSuccess: Boolean,
        val firstName: String = "",
        val middleName: String = "",
        val lastName: String = "",
        val email: String = "",
        val errorMessage: String = ""
    )

    data class ProfilePictureResult(
        val isSuccess: Boolean,
        val bitmap: Bitmap? = null,
        val errorMessage: String = "",
        val notFound: Boolean = false
    )

    // Add onDestroy to clean up resources
    override fun onDestroy() {
        super.onDestroy()
        // Clean up loading dialog resources
        loadingDialog.dispose()
    }

    private fun showPreferenceOptions() {
        val options = arrayOf("Halal", "Kosher", "Vegan", "Vegetarian", "Gluten-Free", "Pescatarian", "Lactose-Free")
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Select Dietary Preference")
        builder.setItems(options) { _, which ->
            preferenceName.text = options[which]
        }
        builder.show()
    }

    private fun showAllergenOptions() {
        val options = arrayOf("Peanuts", "Dairy", "Eggs", "Wheat", "Soy", "Fish", "Shellfish", "Tree Nuts", "Sesame")
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Select Allergen")
        builder.setItems(options) { _, which ->
            allergenName.text = options[which]
        }
        builder.show()
    }

    private fun showAllergenLevelOptions() {
        val options = arrayOf("Mild", "Moderate", "Severe")
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Select Severity Level")
        builder.setItems(options) { _, which ->
            allergenLevel.text = options[which]
        }
        builder.show()
    }

    private fun addDietaryPreference(preference: String) {
        if (dietaryPreferences.contains(preference)) {
            Toast.makeText(this, "This preference is already added", Toast.LENGTH_SHORT).show()
            return
        }

        // Add to UI temporarily (will be refreshed after server response)
        val preferenceView = layoutInflater.inflate(R.layout.item_preference, null)
        preferenceView.findViewById<TextView>(R.id.preferenceName).text = preference

        // Add bottom margin programmatically
        val layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        layoutParams.bottomMargin = resources.getDimensionPixelSize(R.dimen.item_margin_bottom)
        preferenceView.layoutParams = layoutParams

        // Set delete button functionality - now with confirmation
        preferenceView.findViewById<ImageButton>(R.id.deleteButton).setOnClickListener {
            // Check if this is the last preference before showing delete confirmation
            if (dietaryPreferences.size <= 1) {
                Toast.makeText(
                    this@PersonalProfileActivity,
                    "Cannot delete. At least one dietary preference must exist.",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                showDeleteConfirmationDialog("preference", preference) {
                    removeDietaryPreference(preference)
                    preferencesContainer.removeView(preferenceView)
                }
            }
        }

        preferencesContainer.addView(preferenceView)

        // Save to server - success toast will be shown only after server confirmation
        saveDietaryPreference(preference)
    }

    private fun addAllergen(allergen: String, level: String) {
        // Check if allergen already exists
        for (pair in allergens) {
            if (pair.first == allergen) {
                Toast.makeText(this, "This allergen is already added", Toast.LENGTH_SHORT).show()
                return
            }
        }

        // Add to UI temporarily (will be refreshed after server response)
        val allergenView = layoutInflater.inflate(R.layout.item_allergen, null)
        allergenView.findViewById<TextView>(R.id.allergenName).text = allergen
        allergenView.findViewById<TextView>(R.id.allergenLevel).text = level

        // Add bottom margin programmatically
        val layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        layoutParams.bottomMargin = resources.getDimensionPixelSize(R.dimen.item_margin_bottom)
        allergenView.layoutParams = layoutParams

        // Set delete button functionality - now with confirmation
        allergenView.findViewById<ImageButton>(R.id.deleteButton).setOnClickListener {
            showDeleteConfirmationDialog("allergen", allergen) {
                removeAllergen(allergen)
                allergensContainer.removeView(allergenView)
            }
        }

        allergensContainer.addView(allergenView)

        // Save to server - success toast will be shown only after server confirmation
        saveAllergen(allergen, level)
    }

    private fun showDeleteConfirmationDialog(itemType: String, itemName: String, deleteAction: () -> Unit) {

        // Inflate the custom dialog layout
        val dialogView = layoutInflater.inflate(R.layout.delete_conformation_alert_dialog, null)

        // Create dialog that uses our custom layout
        val dialog = Dialog(this)
        dialog.setContentView(dialogView)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        // Set up the cancel button
        val cancelButton = dialogView.findViewById<TextView>(R.id.cancelButton)
        cancelButton.setOnClickListener {
            dialog.dismiss()
        }

        // Set up the delete button
        val deleteButton = dialogView.findViewById<TextView>(R.id.deleteButton)
        deleteButton.setOnClickListener {
            deleteAction.invoke()
            dialog.dismiss()
        }

        dialog.show()
    }

    // Modified method
    private fun removeDietaryPreference(preference: String) {
        dietaryPreferences.remove(preference)
        deleteDietaryPreference(preference)
    }

    // Modified method
    private fun removeAllergen(allergen: String) {
        allergens.removeIf { it.first == allergen }
        deleteAllergen(allergen)
    }

    private fun updateDietaryPreferencesUI() {
        preferencesContainer.removeAllViews()

        for (preference in dietaryPreferences) {
            val preferenceView = layoutInflater.inflate(R.layout.item_preference, null)
            preferenceView.findViewById<TextView>(R.id.preferenceName).text = preference

            // Add bottom margin programmatically
            val layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            layoutParams.bottomMargin = resources.getDimensionPixelSize(R.dimen.item_margin_bottom)
            preferenceView.layoutParams = layoutParams

            // Set delete button functionality - now with confirmation
            preferenceView.findViewById<ImageButton>(R.id.deleteButton).setOnClickListener {
                // Check if this is the last preference before showing delete confirmation
                if (dietaryPreferences.size <= 1) {
                    Toast.makeText(
                        this@PersonalProfileActivity,
                        "Cannot delete. At least one dietary preference must exist.",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    showDeleteConfirmationDialog("preference", preference) {
                        removeDietaryPreference(preference)
                        preferencesContainer.removeView(preferenceView)
                    }
                }
            }

            preferencesContainer.addView(preferenceView)
        }
    }

    private fun updateAllergensUI() {
        allergensContainer.removeAllViews()

        for (allergen in allergens) {
            val allergenView = layoutInflater.inflate(R.layout.item_allergen, null)
            allergenView.findViewById<TextView>(R.id.allergenName).text = allergen.first
            allergenView.findViewById<TextView>(R.id.allergenLevel).text = allergen.second

            // Add bottom margin programmatically
            val layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            layoutParams.bottomMargin = resources.getDimensionPixelSize(R.dimen.item_margin_bottom)
            allergenView.layoutParams = layoutParams

            // Set delete button functionality - now with confirmation
            allergenView.findViewById<ImageButton>(R.id.deleteButton).setOnClickListener {
                showDeleteConfirmationDialog("allergen", allergen.first) {
                    removeAllergen(allergen.first)
                    allergensContainer.removeView(allergenView)
                }
            }

            allergensContainer.addView(allergenView)
        }
    }

    private fun saveDietaryPreference(preference: String) {
        // First add to local state to show immediate feedback
        if (!dietaryPreferences.contains(preference)) {
            dietaryPreferences.add(preference)
            // Update UI immediately for responsive experience
            updateDietaryPreferencesUI()
        }

        // Make the API call without loading dialog or toasts
        try {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val jsonObject = JSONObject().apply {
                        put("email", email)
                        put("preference", preference)
                    }

                    val mediaType = "application/json; charset=utf-8".toMediaType()
                    val body = RequestBody.create(mediaType, jsonObject.toString())

                    val request = Request.Builder()
                        .url("https://swamp-brief-brake.glitch.me/api/customer/add-dietary-preference")
                        .post(body)
                        .addHeader("Content-Type", "application/json")
                        .build()

                    // Execute the request and log errors, but don't show any toasts
                    val response = okHttpClient.newCall(request).execute()
                    if (!response.isSuccessful) {
                        Log.e(TAG, "Failed to save dietary preference: ${response.code}")
                    }
                } catch (e: Exception) {
                    // Just log the error without showing a toast
                    Log.e(TAG, "Error saving dietary preference: ${e.message}", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error launching coroutine: ${e.message}", e)
        }
    }

    private fun saveAllergen(allergen: String, level: String) {
        // First add to local state to show immediate feedback
        if (!allergens.any { it.first == allergen }) {
            allergens.add(Pair(allergen, level))
            // Update UI immediately for responsive experience
            updateAllergensUI()
        }

        // Make the API call without loading dialog or toasts
        try {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val jsonObject = JSONObject().apply {
                        put("email", email)
                        put("allergen", allergen)
                        put("level", level)
                    }

                    val mediaType = "application/json; charset=utf-8".toMediaType()
                    val body = RequestBody.create(mediaType, jsonObject.toString())

                    val request = Request.Builder()
                        .url("https://swamp-brief-brake.glitch.me/api/customer/add-allergen")
                        .post(body)
                        .addHeader("Content-Type", "application/json")
                        .build()

                    // Execute the request and log errors, but don't show any toasts
                    val response = okHttpClient.newCall(request).execute()
                    if (!response.isSuccessful) {
                        Log.e(TAG, "Failed to save allergen: ${response.code}")
                    }
                } catch (e: Exception) {
                    // Just log the error without showing a toast
                    Log.e(TAG, "Error saving allergen: ${e.message}", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error launching coroutine: ${e.message}", e)
        }
    }

    private fun deleteDietaryPreference(preference: String) {
        // First remove from local state for immediate feedback
        dietaryPreferences.remove(preference)
        updateDietaryPreferencesUI()

        // Make the API call without loading dialog or toasts
        try {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val jsonObject = JSONObject().apply {
                        put("email", email)
                        put("preference", preference)
                    }

                    val mediaType = "application/json; charset=utf-8".toMediaType()
                    val body = RequestBody.create(mediaType, jsonObject.toString())

                    val request = Request.Builder()
                        .url("https://swamp-brief-brake.glitch.me/api/customer/remove-dietary-preference")
                        .delete(body)
                        .addHeader("Content-Type", "application/json")
                        .build()

                    // Execute the request and log errors, but don't show any toasts
                    val response = okHttpClient.newCall(request).execute()
                    if (!response.isSuccessful) {
                        Log.e(TAG, "Failed to delete dietary preference: ${response.code}")
                    }
                } catch (e: Exception) {
                    // Just log the error without showing a toast
                    Log.e(TAG, "Error deleting dietary preference: ${e.message}", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error launching coroutine: ${e.message}", e)
        }
    }

    private fun deleteAllergen(allergen: String) {
        // First remove from local state for immediate feedback
        allergens.removeIf { it.first == allergen }
        updateAllergensUI()

        // Make the API call without loading dialog or toasts
        try {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val jsonObject = JSONObject().apply {
                        put("email", email)
                        put("allergen", allergen)
                    }

                    val mediaType = "application/json; charset=utf-8".toMediaType()
                    val body = RequestBody.create(mediaType, jsonObject.toString())

                    val request = Request.Builder()
                        .url("https://swamp-brief-brake.glitch.me/api/customer/remove-allergen")
                        .delete(body)
                        .addHeader("Content-Type", "application/json")
                        .build()

                    // Execute the request and log errors, but don't show any toasts
                    val response = okHttpClient.newCall(request).execute()
                    if (!response.isSuccessful) {
                        Log.e(TAG, "Failed to delete allergen: ${response.code}")
                    }
                } catch (e: Exception) {
                    // Just log the error without showing a toast
                    Log.e(TAG, "Error deleting allergen: ${e.message}", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error launching coroutine: ${e.message}", e)
        }
    }

    private fun prepareResultIntent() {
        val resultIntent = Intent()
        resultIntent.putExtra("firstName", firstName)
        resultIntent.putExtra("lastName", lastName)
        resultIntent.putExtra("middleName", middleName)
        resultIntent.putExtra("email", email)
        setResult(RESULT_OK, resultIntent)
    }

    override fun onBackPressed() {
        prepareResultIntent()
        super.onBackPressed()
    }

    private fun updateProfile(field: String, value: String) {
        if (value.isEmpty() && field != "middleName") {
            Toast.makeText(this, "$field cannot be empty", Toast.LENGTH_SHORT).show()
            return
        }

        // Check if the value has actually changed
        val currentValue = when (field) {
            "firstName" -> firstName
            "middleName" -> middleName ?: ""
            "lastName" -> lastName
            else -> ""
        }

        // If no change, just return without showing toast or making API call
        if (currentValue == value) {
            // Toggle back to non-edit mode without showing toast
            when (field) {
                "firstName" -> toggleEditState(etFirstName, btnEditFirstName, btnCancelFirstName, false)
                "middleName" -> toggleEditState(etMiddleName, btnEditMiddleName, btnCancelMiddleName, false)
                "lastName" -> toggleEditState(etLastName, btnEditLastName, btnCancelLastName, false)
            }
            return
        }

        // Update local values right away for UI feedback
        when (field) {
            "firstName" -> {
                firstName = value
                // Update the profile name TextView
                val profileName = findViewById<TextView>(R.id.profileName)
                profileName.text = firstName
            }
            "middleName" -> middleName = value
            "lastName" -> lastName = value
        }

        loadingDialog.executeWithLoading(
            thresholdMs = 500,
            operation = {
                try {
                    // Create request body
                    val jsonObject = JSONObject().apply {
                        put("email", email)

                        when (field) {
                            "firstName" -> put("firstName", value)
                            "middleName" -> put("middleName", value)
                            "lastName" -> put("lastName", value)
                        }
                    }

                    val mediaType = "application/json; charset=utf-8".toMediaType()
                    val body = RequestBody.create(mediaType, jsonObject.toString())

                    val request = Request.Builder()
                        .url("https://swamp-brief-brake.glitch.me/api/customer/update-profile")
                        .put(body)
                        .addHeader("Content-Type", "application/json")
                        .build()

                    val response = okHttpClient.newCall(request).execute()
                    val responseBody = response.body?.string()

                    if (response.isSuccessful && responseBody != null) {
                        val jsonResponse = JSONObject(responseBody)

                        if (jsonResponse.optBoolean("success", false)) {
                            prepareResultIntent()
                            return@executeWithLoading true
                        } else {
                            val errorMessage = jsonResponse.optString("message", "Unknown error")
                            return@executeWithLoading Pair(false, errorMessage)
                        }
                    } else {
                        return@executeWithLoading Pair(false, "API request failed")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error updating profile: ${e.message}", e)
                    return@executeWithLoading Pair(false, e.message ?: "Unknown error")
                }
            },
            callback = { result ->
                when (result) {
                    is Boolean -> {
                        if (result) {
                            // Update UI state to non-edit mode
                            when (field) {
                                "firstName" -> toggleEditState(etFirstName, btnEditFirstName, btnCancelFirstName, false)
                                "middleName" -> toggleEditState(etMiddleName, btnEditMiddleName, btnCancelMiddleName, false)
                                "lastName" -> toggleEditState(etLastName, btnEditLastName, btnCancelLastName, false)
                            }
                            Toast.makeText(this@PersonalProfileActivity, "Information successfully updated", Toast.LENGTH_SHORT).show()
                        }
                    }
                    is Pair<*, *> -> {
                        val (success, message) = result as Pair<Boolean, String>
                        if (!success) {
                            Toast.makeText(this@PersonalProfileActivity, "Update failed: $message", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        )
    }

    private fun saveImageToCache(bitmap: Bitmap): Uri? {
        try {
            val file = File(cacheDir, "profile_${System.currentTimeMillis()}.jpg")
            val outputStream = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
            outputStream.flush()
            outputStream.close()
            return Uri.fromFile(file)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving image to cache: ${e.message}")
            return null
        }
    }

    private fun uploadProfilePicture(uri: Uri) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Show uploading message
                withContext(Dispatchers.Main) {
                    // Profile Picture uploading
                }

                // Create a temp file from the Uri
                val inputStream = contentResolver.openInputStream(uri)
                val file = File(cacheDir, "temp_profile_pic.jpg")
                val outputStream = FileOutputStream(file)
                inputStream?.copyTo(outputStream)
                inputStream?.close()
                outputStream.close()

                // Create multipart request
                val requestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart(
                        "picture",
                        file.name,
                        file.asRequestBody("image/jpeg".toMediaTypeOrNull())
                    )
                    .addFormDataPart("email", email)
                    .build()

                // Use the update endpoint instead of upload
                val request = Request.Builder()
                    .url("https://swamp-brief-brake.glitch.me/api/customer/update-profile-picture")
                    .put(requestBody)  // Use PUT instead of POST for updates
                    .build()

                val response = okHttpClient.newCall(request).execute()
                val responseBody = response.body?.string()

                // Delete the temp file
                file.delete()

                withContext(Dispatchers.Main) {
                    if (response.isSuccessful && responseBody != null) {
                        val jsonResponse = JSONObject(responseBody)
                        if (jsonResponse.optBoolean("success", false)) {
                            Toast.makeText(
                                this@PersonalProfileActivity,
                                "Profile picture updated successfully",
                                Toast.LENGTH_SHORT
                            ).show()

                            // Update profileImageView with the new image
                            try {
                                val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                    val source = ImageDecoder.createSource(contentResolver, uri)
                                    ImageDecoder.decodeBitmap(source)
                                } else {
                                    MediaStore.Images.Media.getBitmap(contentResolver, uri)
                                }

                                profileImageView.setImageBitmap(bitmap)
                                profileImageView.background = getDrawable(R.drawable.profile_avatar_background)
                                profileImageView.clipToOutline = true
                            } catch (e: Exception) {
                                Log.e(TAG, "Error updating profile imageView: ${e.message}", e)
                            }
                        } else {
                            val errorMessage = jsonResponse.optString("message", "Unknown error")
                            Toast.makeText(
                                this@PersonalProfileActivity,
                                "Failed to update profile picture: $errorMessage",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    } else {
                        Toast.makeText(
                            this@PersonalProfileActivity,
                            "Failed to update profile picture. Server responded with code: ${response.code}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Upload error: ${e.message}")
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@PersonalProfileActivity,
                        "Error updating profile picture: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun setupEditButtonListeners() {
        // First Name
        btnEditFirstName.setOnClickListener {
            if (etFirstName.isEnabled) {
                // Save changes
                updateProfile("firstName", etFirstName.text.toString())
                toggleEditState(etFirstName, btnEditFirstName, btnCancelFirstName, false)
            } else {
                // Enable editing
                toggleEditState(etFirstName, btnEditFirstName, btnCancelFirstName, true)
            }
        }

        btnCancelFirstName.setOnClickListener {
            etFirstName.setText(firstName)
            toggleEditState(etFirstName, btnEditFirstName, btnCancelFirstName, false)
        }

        // Middle Name
        btnEditMiddleName.setOnClickListener {
            if (etMiddleName.isEnabled) {
                // Save changes
                updateProfile("middleName", etMiddleName.text.toString())
                toggleEditState(etMiddleName, btnEditMiddleName, btnCancelMiddleName, false)
            } else {
                // Enable editing
                toggleEditState(etMiddleName, btnEditMiddleName, btnCancelMiddleName, true)
            }
        }

        btnCancelMiddleName.setOnClickListener {
            etMiddleName.setText(middleName)
            toggleEditState(etMiddleName, btnEditMiddleName, btnCancelMiddleName, false)
        }

        // Last Name
        btnEditLastName.setOnClickListener {
            if (etLastName.isEnabled) {
                // Save changes
                updateProfile("lastName", etLastName.text.toString())
                toggleEditState(etLastName, btnEditLastName, btnCancelLastName, false)
            } else {
                // Enable editing
                toggleEditState(etLastName, btnEditLastName, btnCancelLastName, true)
            }
        }

        btnCancelLastName.setOnClickListener {
            etLastName.setText(lastName)
            toggleEditState(etLastName, btnEditLastName, btnCancelLastName, false)
        }
    }

    private fun toggleEditState(editText: EditText, editButton: ImageButton, cancelButton: ImageButton, isEditable: Boolean) {
        editText.isEnabled = isEditable

        if (isEditable) {
            // Switch to edit mode
            editButton.setImageResource(R.drawable.ic_check)
            cancelButton.visibility = View.VISIBLE
            editText.requestFocus()
        } else {
            // Switch to view mode
            editButton.setImageResource(R.drawable.ic_edit)
            cancelButton.visibility = View.GONE
        }
    }

    private fun setAllFieldsNonEditable() {
        // Set all fields as non-editable initially
        etFirstName.isEnabled = false
        etMiddleName.isEnabled = false
        etLastName.isEnabled = false

        // Hide all cancel buttons
        btnCancelFirstName.visibility = View.GONE
        btnCancelMiddleName.visibility = View.GONE
        btnCancelLastName.visibility = View.GONE
    }

    // Helper function to convert dp to pixels
    private fun dpToPx(dp: Int): Int {
        val density = resources.displayMetrics.density
        return (dp * density).toInt()
    }

    private fun showImagePickerDialog() {
        val options = arrayOf("Take Photo", "Choose from Gallery", "Cancel")
        val builder = android.app.AlertDialog.Builder(this)
        builder.setTitle("Choose Profile Picture")
        builder.setItems(options) { dialog, item ->
            when (item) {
                0 -> checkCameraPermission()
                1 -> checkStoragePermission()
                2 -> dialog.dismiss()
            }
        }
        builder.show()
    }

    private fun checkStoragePermission() {
        when {
            // Check if we have permission
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_MEDIA_IMAGES
            ) == PackageManager.PERMISSION_GRANTED) ||
                    (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.READ_EXTERNAL_STORAGE
                    ) == PackageManager.PERMISSION_GRANTED) -> {
                // We have permission, proceed with gallery
                openGallery()
            }

            // Should show rationale
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ActivityCompat.shouldShowRequestPermissionRationale(
                this,
                Manifest.permission.READ_MEDIA_IMAGES
            )) ||
                    (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU && ActivityCompat.shouldShowRequestPermissionRationale(
                        this,
                        Manifest.permission.READ_EXTERNAL_STORAGE
                    )) -> {
                // Show explanation dialog
                AlertDialog.Builder(this)
                    .setTitle("Storage Permission Required")
                    .setMessage("This app needs storage access to select profile pictures from your gallery")
                    .setPositiveButton("Grant") { _, _ ->
                        requestStoragePermission()
                    }
                    .setNegativeButton("Cancel") { dialog, _ ->
                        dialog.dismiss()
                    }
                    .show()
            }

            // Request directly
            else -> {
                requestStoragePermission()
            }
        }
    }

    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_MEDIA_IMAGES),
                STORAGE_PERMISSION_CODE
            )
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                STORAGE_PERMISSION_CODE
            )
        }
    }

    private fun checkCameraPermission() {
        when {
            // Check if we have permission
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                // We have permission, proceed with camera
                openCamera()
            }

            // Should show rationale
            ActivityCompat.shouldShowRequestPermissionRationale(
                this,
                Manifest.permission.CAMERA
            ) -> {
                // Show explanation dialog
                AlertDialog.Builder(this)
                    .setTitle("Camera Permission Required")
                    .setMessage("This app needs camera access to take profile pictures")
                    .setPositiveButton("Grant") { _, _ ->
                        ActivityCompat.requestPermissions(
                            this,
                            arrayOf(Manifest.permission.CAMERA),
                            CAMERA_PERMISSION_CODE
                        )
                    }
                    .setNegativeButton("Cancel") { dialog, _ ->
                        dialog.dismiss()
                    }
                    .show()
            }

            // Request directly
            else -> {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.CAMERA),
                    CAMERA_PERMISSION_CODE
                )
            }
        }
    }

    private fun openGallery() {
        // Try alternative gallery access method
        try {
            val intent = Intent(Intent.ACTION_GET_CONTENT)
            intent.type = "image/*"
            startActivityForResult(Intent.createChooser(intent, "Select Picture"), GALLERY_REQUEST_CODE)
        } catch (e: Exception) {
            // Fall back to traditional method
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            startActivityForResult(intent, GALLERY_REQUEST_CODE)
        }
    }

    private fun openCamera() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)

        // Create a file to save the image
        val photoFile: File? = try {
            createImageFile()
        } catch (ex: IOException) {
            // Error occurred while creating the File
            Log.e(TAG, "Error creating image file", ex)
            null
        }

        // Continue only if the File was successfully created
        photoFile?.also {
            currentPhotoUri = FileProvider.getUriForFile(
                this,
                "com.android.safeeats.fileprovider",  // Make sure this matches your manifest
                it
            )
            intent.putExtra(MediaStore.EXTRA_OUTPUT, currentPhotoUri)
            startActivityForResult(intent, CAMERA_REQUEST_CODE)
        } ?: run {
            Toast.makeText(this, "Could not create image file", Toast.LENGTH_SHORT).show()
        }
    }

    @Throws(IOException::class)
    private fun createImageFile(): File {

        val timeStamp = System.currentTimeMillis()
        val imageFileName = "JPEG_${timeStamp}_"
        val storageDir = getExternalFilesDir(null)
        return File.createTempFile(
            imageFileName,
            ".jpg",
            storageDir
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            when (requestCode) {
                STORAGE_PERMISSION_CODE -> openGallery()
                CAMERA_PERMISSION_CODE -> openCamera()
            }
        } else {
            val permissionName = when (requestCode) {
                STORAGE_PERMISSION_CODE -> "storage"
                CAMERA_PERMISSION_CODE -> "camera"
                else -> "requested"
            }

            // Check if user has permanently denied permission
            if ((requestCode == STORAGE_PERMISSION_CODE && !ActivityCompat.shouldShowRequestPermissionRationale(
                    this,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                        Manifest.permission.READ_MEDIA_IMAGES
                    else
                        Manifest.permission.READ_EXTERNAL_STORAGE
                )) ||
                (requestCode == CAMERA_PERMISSION_CODE && !ActivityCompat.shouldShowRequestPermissionRationale(
                    this,
                    Manifest.permission.CAMERA
                ))
            ) {
                // User has permanently denied, show settings dialog
                showSettingsDialog(permissionName)
            } else {
                // User denied but didn't check "Don't ask again"
                Toast.makeText(
                    this,
                    "$permissionName permission denied. This feature requires permission to work properly.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun showSettingsDialog(permissionName: String) {
        AlertDialog.Builder(this)
            .setTitle("Permission Required")
            .setMessage("$permissionName permission is required to use this feature. Please enable it in app settings.")
            .setPositiveButton("Go to Settings") { _, _ ->
                // Direct user to app settings
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                intent.data = Uri.fromParts("package", packageName, null)
                startActivity(intent)
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode == RESULT_OK) {
            when (requestCode) {
                GALLERY_REQUEST_CODE -> {
                    data?.data?.let { uri ->
                        try {
                            val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                val source = ImageDecoder.createSource(contentResolver, uri)
                                ImageDecoder.decodeBitmap(source)
                            } else {
                                MediaStore.Images.Media.getBitmap(contentResolver, uri)
                            }

                            // Apply square profile image
                            profileImageView.setImageBitmap(bitmap)
                            profileImageView.background = getDrawable(R.drawable.profile_avatar_background)
                            profileImageView.clipToOutline = true

                            uploadProfilePicture(uri)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error loading image: ${e.message}", e)
                            Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                CAMERA_REQUEST_CODE -> {
                    try {
                        currentPhotoUri?.let { uri ->
                            val bitmap = BitmapFactory.decodeStream(contentResolver.openInputStream(uri))

                            // Apply square profile image
                            profileImageView.setImageBitmap(bitmap)
                            profileImageView.background = getDrawable(R.drawable.profile_avatar_background)
                            profileImageView.clipToOutline = true

                            uploadProfilePicture(uri)
                        } ?: run {
                            val bitmap = data?.extras?.get("data") as? Bitmap
                            bitmap?.let {
                                // Apply square profile image
                                profileImageView.setImageBitmap(it)
                                profileImageView.background = getDrawable(R.drawable.profile_avatar_background)
                                profileImageView.clipToOutline = true

                                val uri = saveImageToCache(it)
                                uri?.let { imageUri -> uploadProfilePicture(imageUri) }
                            }
                        }
                    } catch (e: Exception) {
                        Toast.makeText(this, "Failed to load camera image", Toast.LENGTH_SHORT).show()
                        Log.e(TAG, "Error loading camera image: ${e.message}", e)
                    }
                }
            }
        }
    }

}