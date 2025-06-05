package com.android.safeeats

import android.app.Activity
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.android.safeeats.models.Notification
import com.android.safeeats.utils.ImageLoadingDialog
import com.android.safeeats.utils.LoadingAnalyzingDialog
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

// Renamed Request import to avoid conflict
import okhttp3.Request as OkHttpRequest

class AnalyzeActivity : Activity() {

    private val TAG = "AnalyzeActivity" // Tag for logging
    private lateinit var loadingDialog: LoadingAnalyzingDialog
    private lateinit var imageLoadingDialog: ImageLoadingDialog
    private val BASE_URL = "https://swamp-brief-brake.glitch.me/api"
    private lateinit var okHttpClient: OkHttpClient

    private lateinit var dishNameTextView: TextView
    private lateinit var dishSafetyScoreTextView: TextView
    private lateinit var dishDescriptionTextView: TextView
    private lateinit var dishImage: ImageView
    private lateinit var dietaryCompatibilitySection: LinearLayout
    private lateinit var allergenCheckSection: LinearLayout
    private lateinit var analysisSummary: TextView
    private lateinit var overallRecommendation: TextView
    private lateinit var backButton: ImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Show loading dialog immediately
        loadingDialog = LoadingAnalyzingDialog(this)
        loadingDialog.show()

        // Initialize image loading dialog
        imageLoadingDialog = ImageLoadingDialog(this)

        // Initialize OkHttpClient
        okHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        setContentView(R.layout.activity_analyze)
        Log.d(TAG, "Activity created")

        try {
            // Initialize views
            initViews()
            Log.d(TAG, "Views initialized")

            // Set back button click listener
            backButton.setOnClickListener {
                finish()
            }

            // Get data from intent
            val itemId = intent.getStringExtra("item_id") ?: ""
            if (itemId.isEmpty()) {
                throw IllegalArgumentException("Item ID is required")
            }
            val email = intent.getStringExtra("email") ?: ""
            val dishName = intent.getStringExtra("dish_name") ?: ""
            val dishDescription = intent.getStringExtra("dish_description") ?: ""
            val dishSafetyScore = intent.getIntExtra("dish_safety_score", 0)
            
            // Instead of relying on the passed URL, use the item_id directly to construct the URL
            // This ensures we're always using the latest image from the server
            val dishPictureUrl = "https://swamp-brief-brake.glitch.me/api/menuitem/$itemId/picture"
            
            // Load dish image using the image loading dialog
            imageLoadingDialog.loadImage(
                imageUrl = dishPictureUrl,
                imageView = dishImage,
                showPlaceholder = true
            )
            
            Log.d(TAG, "Loading dish image from endpoint for item $itemId")

            // Create notification object and store it
            val notification = Notification(
                email = email,
                itemId = itemId,
                dishName = dishName,
                dishDescription = dishDescription,
                dishSafetyScore = dishSafetyScore
            )
            storeAnalysisNotification(notification)

            Log.d(TAG, "Retrieved from intent - itemId: $itemId, dishName: $dishName, email: $email")

            // Hide all content initially
            findViewById<LinearLayout>(R.id.content_container).visibility = View.INVISIBLE

            // Set initial dish details
            dishNameTextView.text = dishName
            dishSafetyScoreTextView.text = "$dishSafetyScore% Safe"
            dishDescriptionTextView.text = dishDescription

            // Set safety score badge background
            setSafetyScoreBadge(dishSafetyScore)

            // Show loading state or placeholder content
            analysisSummary.text = "Loading analysis..."
            overallRecommendation.text = "Please wait..."

            // Fetch dish analysis data
            fetchDishAnalysis(itemId, email)
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate: ${e.message}", e)
            Toast.makeText(this, "Error initializing screen: ${e.message}", Toast.LENGTH_LONG).show()

            // Show error in UI
            analysisSummary.text = "Error: ${e.message}"
            overallRecommendation.text = "Please try again"

            // Dismiss loading dialog and finish activity after delay
            loadingDialog.dismiss {
                Handler(Looper.getMainLooper()).postDelayed({
                    if (!isFinishing) {
                        finish()
                    }
                }, 3000)
            }
        }
    }

    private fun initViews() {
        try {
            dishNameTextView = findViewById(R.id.dish_name)
            dishSafetyScoreTextView = findViewById(R.id.dish_safety_score)
            dishDescriptionTextView = findViewById(R.id.dish_description)
            dishImage = findViewById(R.id.dish_image)
            dietaryCompatibilitySection = findViewById(R.id.dietary_compatibility_section)
            allergenCheckSection = findViewById(R.id.allergen_check_section)
            analysisSummary = findViewById(R.id.analysis_summary)
            overallRecommendation = findViewById(R.id.overall_recommendation)
            backButton = findViewById(R.id.btn_back)

            // Verify views are initialized
            if (dietaryCompatibilitySection == null) {
                Log.e(TAG, "dietaryCompatibilitySection is null")
            }
            if (allergenCheckSection == null) {
                Log.e(TAG, "allergenCheckSection is null")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in initViews: ${e.message}", e)
            throw e
        }
    }

    private fun setSafetyScoreBadge(score: Int) {
        try {
            val background = when {
                score >= 85 -> R.drawable.safety_badge_high
                score >= 70 -> R.drawable.safety_badge_medium
                else -> R.drawable.safety_badge_low
            }

            val textColor = when {
                score >= 85 -> getColor(R.color.safety_high)
                score >= 70 -> getColor(R.color.safety_medium)
                else -> getColor(R.color.safety_low)
            }

            dishSafetyScoreTextView.setBackgroundResource(background)
            dishSafetyScoreTextView.setTextColor(textColor)
        } catch (e: Exception) {
            Log.e(TAG, "Error in setSafetyScoreBadge: ${e.message}", e)
        }
    }

    private fun fetchDishAnalysis(itemId: String, email: String) {
        try {
            val url = "https://swamp-brief-brake.glitch.me/api/dish-analysis?item_id=${itemId}&email=${URLEncoder.encode(email, "UTF-8")}"
            Log.d(TAG, "Fetching from URL: $url")

            // Create a new request queue for this specific request
            val requestQueue = Volley.newRequestQueue(this)

            val jsonObjectRequest = JsonObjectRequest(
                com.android.volley.Request.Method.GET, url, null,
                { response ->
                    Log.d(TAG, "API Response received: $response")
                    try {
                        if (response.getBoolean("success")) {
                            val analysis = response.getJSONObject("analysis")
                            loadingDialog.dismiss {
                                updateUI(analysis)
                            }
                        } else {
                            val message = response.getString("message")
                            Log.e(TAG, "API returned error: $message")
                            loadingDialog.dismiss {
                                Toast.makeText(this, "Error: $message", Toast.LENGTH_LONG).show()
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing response: ${e.message}", e)
                        loadingDialog.dismiss {
                            Toast.makeText(this, "Error processing response: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                },
                { error ->
                    Log.e(TAG, "Error fetching dish analysis", error)
                    loadingDialog.dismiss {
                        Toast.makeText(this, "Error fetching analysis", Toast.LENGTH_SHORT).show()
                    }
                }
            )

            // Add the request to the RequestQueue
            requestQueue.add(jsonObjectRequest)
        } catch (e: Exception) {
            Log.e(TAG, "Error in fetchDishAnalysis", e)
            loadingDialog.dismiss {
                Toast.makeText(this, "Error analyzing dish", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateUI(analysis: JSONObject) {
        try {
            Log.d(TAG, "Updating UI with analysis data")

            // Update safety score if available
            val safetyScore = analysis.getInt("safety_score")
            dishSafetyScoreTextView.text = "$safetyScore% Safe"
            setSafetyScoreBadge(safetyScore)

            // Process dietary compatibility
            val dietaryCompatibility = analysis.getJSONArray("dietary_compatibility")
            Log.d(TAG, "Dietary compatibility items: ${dietaryCompatibility.length()}")
            updateDietaryCompatibilitySection(dietaryCompatibility)

            // Process allergen check
            val allergenCheck = analysis.getJSONArray("allergen_check")
            Log.d(TAG, "Allergen check items: ${allergenCheck.length()}")
            updateAllergenCheckSection(allergenCheck)

            // Update summary and recommendation
            val summaryText = analysis.getString("analysis_summary")
            val recommendationText = analysis.getString("overall_recommendation")

            Log.d(TAG, "Summary: $summaryText")
            Log.d(TAG, "Recommendation: $recommendationText")

            analysisSummary.text = summaryText
            overallRecommendation.text = recommendationText

            // Verify UI was updated
            if (analysisSummary.text.isEmpty()) {
                Log.e(TAG, "Analysis summary is empty after setting text")
            }

            // Show content
            findViewById<LinearLayout>(R.id.content_container).visibility = View.VISIBLE
        } catch (e: Exception) {
            Log.e(TAG, "Error in updateUI: ${e.message}", e)
            Toast.makeText(this, "Error updating UI: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun updateDietaryCompatibilitySection(dietaryCompatibility: JSONArray) {
        try {
            dietaryCompatibilitySection.removeAllViews()

            if (dietaryCompatibility.length() == 0) {
                // Add a default message if no dietary preferences
                val defaultView = LayoutInflater.from(this)
                    .inflate(R.layout.compatibility_item, dietaryCompatibilitySection, false)

                val icon = defaultView.findViewById<ImageView>(R.id.compatibility_icon)
                val text = defaultView.findViewById<TextView>(R.id.compatibility_text)

                icon.setImageResource(R.drawable.ic_warning)
                text.text = "No dietary preferences specified"

                dietaryCompatibilitySection.addView(defaultView)
                return
            }

            for (i in 0 until dietaryCompatibility.length()) {
                val item = dietaryCompatibility.getJSONObject(i)
                val preference = item.getString("preference")
                val isCompatible = item.getBoolean("is_compatible")

                val compatibilityView = LayoutInflater.from(this)
                    .inflate(R.layout.compatibility_item, dietaryCompatibilitySection, false)

                val icon = compatibilityView.findViewById<ImageView>(R.id.compatibility_icon)
                val text = compatibilityView.findViewById<TextView>(R.id.compatibility_text)

                if (isCompatible) {
                    icon.setImageResource(R.drawable.ic_check_circle)
                    icon.setColorFilter(getColor(R.color.safety_high))
                    text.text = "$preference - Suitable"
                } else {
                    icon.setImageResource(R.drawable.ic_cancel)
                    icon.setColorFilter(getColor(R.color.safety_low))
                    text.text = "$preference - Not suitable"
                }

                dietaryCompatibilitySection.addView(compatibilityView)
                Log.d(TAG, "Added dietary item: $preference, compatible: $isCompatible")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in updateDietaryCompatibilitySection: ${e.message}", e)

            // Add error view
            val errorView = LayoutInflater.from(this)
                .inflate(R.layout.compatibility_item, dietaryCompatibilitySection, false)

            val icon = errorView.findViewById<ImageView>(R.id.compatibility_icon)
            val text = errorView.findViewById<TextView>(R.id.compatibility_text)

            icon.setImageResource(R.drawable.ic_warning)
            text.text = "Error loading dietary information"

            dietaryCompatibilitySection.addView(errorView)
        }
    }

    private fun updateAllergenCheckSection(allergenCheck: JSONArray) {
        try {
            allergenCheckSection.removeAllViews()

            if (allergenCheck.length() == 0) {
                // Add a default message if no allergens
                val defaultView = LayoutInflater.from(this)
                    .inflate(R.layout.compatibility_item, allergenCheckSection, false)

                val icon = defaultView.findViewById<ImageView>(R.id.compatibility_icon)
                val text = defaultView.findViewById<TextView>(R.id.compatibility_text)

                icon.setImageResource(R.drawable.ic_check_circle)
                icon.setColorFilter(getColor(R.color.safety_high))
                text.text = "No allergens detected"

                allergenCheckSection.addView(defaultView)
                return
            }

            for (i in 0 until allergenCheck.length()) {
                val item = allergenCheck.getJSONObject(i)
                val allergen = item.getString("allergen")
                val isSafe = item.getBoolean("is_safe")

                val allergenView = LayoutInflater.from(this)
                    .inflate(R.layout.compatibility_item, allergenCheckSection, false)

                val icon = allergenView.findViewById<ImageView>(R.id.compatibility_icon)
                val text = allergenView.findViewById<TextView>(R.id.compatibility_text)

                if (isSafe) {
                    icon.setImageResource(R.drawable.ic_check_circle)
                    icon.setColorFilter(getColor(R.color.safety_high))

                    // Display message from API if available, otherwise use default
                    val message = if (item.has("message")) item.getString("message") else "Allergy-safe and approved"
                    text.text = "$allergen - $message"
                } else {
                    icon.setImageResource(R.drawable.ic_cancel)
                    icon.setColorFilter(getColor(R.color.safety_low))

                    // If severity is provided, use it
                    val displayText = if (item.has("severity")) {
                        val severity = item.getString("severity")
                        "$allergen - Contains this allergen (${severity.capitalize()} sensitivity)"
                    } else {
                        "$allergen - Contains this allergen"
                    }

                    text.text = displayText
                }

                allergenCheckSection.addView(allergenView)
                Log.d(TAG, "Added allergen item: $allergen, safe: $isSafe")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in updateAllergenCheckSection: ${e.message}", e)

            // Add error view
            val errorView = LayoutInflater.from(this)
                .inflate(R.layout.compatibility_item, allergenCheckSection, false)

            val icon = errorView.findViewById<ImageView>(R.id.compatibility_icon)
            val text = errorView.findViewById<TextView>(R.id.compatibility_text)

            icon.setImageResource(R.drawable.ic_warning)
            text.text = "Error loading allergen information"

            allergenCheckSection.addView(errorView)
        }
    }

    private fun storeAnalysisNotification(notification: Notification) {
        try {
            Log.d(TAG, "Storing notification for dish: ${notification.dishName}")
            val url = "$BASE_URL/notifications"

            val jsonBody = JSONObject().apply {
                put("email", notification.email)
                put("item_id", notification.itemId)
                put("dish_safety_score", notification.dishSafetyScore)
                put("dish_name", notification.dishName)
                put("dish_description", notification.dishDescription)
            }

            Log.d(TAG, "Sending notification payload: $jsonBody")

            // Create a request queue
            val requestQueue = Volley.newRequestQueue(this)

            val jsonObjectRequest = JsonObjectRequest(
                com.android.volley.Request.Method.POST,
                url,
                jsonBody,
                { response ->
                    try {
                        val success = response.getBoolean("success")
                        val message = response.getString("message")
                        if (success) {
                            val notificationId = response.getInt("notification_id")
                            Log.d(TAG, "Notification stored successfully with ID: $notificationId")
                        } else {
                            Log.e(TAG, "Server returned error: $message")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing server response: ${e.message}")
                    }
                },
                { error ->
                    Log.e(TAG, "Error storing notification: ${error.message}")
                    when (error) {
                        is com.android.volley.TimeoutError -> Log.e(TAG, "Request timed out")
                        is com.android.volley.NetworkError -> Log.e(TAG, "Network error")
                        is com.android.volley.ServerError -> Log.e(TAG, "Server error")
                        else -> Log.e(TAG, "Unknown error occurred")
                    }
                }
            )

            // Add timeout to request
            jsonObjectRequest.retryPolicy = com.android.volley.DefaultRetryPolicy(
                15000, // 15 seconds timeout
                1, // Max retries
                1f // No backoff multiplier
            )

            // Add the request to the RequestQueue
            requestQueue.add(jsonObjectRequest)
        } catch (e: Exception) {
            Log.e(TAG, "Error creating notification request: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        loadingDialog.dispose()
        imageLoadingDialog.dispose()
    }
}