package com.android.safeeats

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.safeeats.models.NotificationItem
import com.android.volley.Request as VolleyRequest
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.android.safeeats.utils.LoadingDialog
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.net.URLEncoder

class NotificationActivity : Activity() {
    private val TAG = "NotificationActivity"
    private val BASE_URL = "https://swamp-brief-brake.glitch.me/api"
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: NotificationAdapter
    private var userEmail: String = ""
    private lateinit var noNotificationsText: TextView
    private lateinit var clearAllButton: TextView
    private lateinit var loadingDialog: LoadingDialog

    private val okHttpClient = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notification)

        // Initialize loading dialog
        loadingDialog = LoadingDialog(this)

        // Get user email from intent
        userEmail = intent.getStringExtra("email") ?: ""
        if (userEmail.isEmpty()) {
            Toast.makeText(this, "User email is required", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Initialize views
        recyclerView = findViewById(R.id.notification_recycler_view)
        noNotificationsText = findViewById(R.id.no_notifications_text)
        clearAllButton = findViewById(R.id.clear_all_button)

        // Set up RecyclerView
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = NotificationAdapter(mutableListOf()) { notification ->
            deleteNotification(notification.notificationId)
        }
        recyclerView.adapter = adapter

        // Set up back button
        findViewById<ImageButton>(R.id.btn_back).setOnClickListener {
            markNotificationsAsSeen { success ->
                if (success) {
                    setResult(RESULT_OK)
                }
                finish()
            }
        }

        // Set up clear all button
        clearAllButton.setOnClickListener {
            Log.d(TAG, "Clear All button clicked")
            clearAllNotifications()
        }

        // Load notifications with loading dialog
        loadNotificationsWithLoading()
    }

    override fun onBackPressed() {
        markNotificationsAsSeen { success ->
            if (success) {
                setResult(RESULT_OK)
            }
            super.onBackPressed()
        }
    }

    private fun loadNotificationsWithLoading() {
        // Show loading dialog with threshold
        loadingDialog.executeWithLoading(
            thresholdMs = 300,
            operation = {
                // This runs in a background thread
                try {
                    val url = "$BASE_URL/notifications?email=${URLEncoder.encode(userEmail, "UTF-8")}"
                    Log.d(TAG, "Fetching notifications from: $url")

                    val request = Request.Builder()
                        .url(url)
                        .get()
                        .build()

                    // Execute synchronously for background thread
                    val response = okHttpClient.newCall(request).execute()

                    if (response.isSuccessful) {
                        val responseBody = response.body?.string()
                        val jsonObject = JSONObject(responseBody)
                        val notifications = jsonObject.getJSONArray("notifications")

                        val notificationList = mutableListOf<NotificationItem>()

                        for (i in 0 until notifications.length()) {
                            val notification = notifications.getJSONObject(i)
                            val timestamp = notification.optLong("analyzedTime", System.currentTimeMillis())
                            val date = Date(timestamp)
                            val formattedDate = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(date)

                            notificationList.add(
                                NotificationItem(
                                    notificationId = notification.getString("notification_id"),
                                    email = notification.getString("email"),
                                    itemId = notification.getString("item_id"),
                                    dishName = notification.getString("dish_name"),
                                    dishDescription = notification.optString("dish_description"),
                                    dishSafetyScore = notification.getInt("dish_safety_score"),
                                    analyzedTime = formattedDate
                                )
                            )
                        }

                        return@executeWithLoading Pair(true, notificationList)
                    } else {
                        Log.e(TAG, "Error: ${response.code}")
                        return@executeWithLoading Pair(false, mutableListOf<NotificationItem>())
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Exception loading notifications", e)
                    return@executeWithLoading Pair(false, mutableListOf<NotificationItem>())
                }
            },
            callback = { result ->
                // This runs on the main thread
                val (success, notificationList) = result

                if (success && notificationList.isNotEmpty()) {
                    noNotificationsText.visibility = View.GONE
                    recyclerView.visibility = View.VISIBLE
                    clearAllButton.visibility = View.VISIBLE
                    adapter.updateNotifications(notificationList)
                } else {
                    showEmptyState()
                    if (!success) {
                        Toast.makeText(this, "Failed to load notifications", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    private fun markNotificationsAsSeen(callback: (Boolean) -> Unit) {
        val url = "$BASE_URL/notifications/seen?email=${URLEncoder.encode(userEmail, "UTF-8")}"
        Log.d(TAG, "Marking notifications as seen: $url")

        val requestQueue = Volley.newRequestQueue(this)

        val jsonObjectRequest = JsonObjectRequest(
            VolleyRequest.Method.PUT,
            url,
            null,
            { response ->
                Log.d(TAG, "Mark seen response: $response")
                val success = response.optBoolean("success", false)
                if (!success) {
                    Log.e(TAG, "Failed to mark notifications as seen: ${response.optString("message")}")
                }
                callback(success)
            },
            { error ->
                Log.e(TAG, "Error marking notifications as seen: ${error.message}")
                callback(false)
            }
        ).apply {
            retryPolicy = com.android.volley.DefaultRetryPolicy(
                15000, // 15 seconds timeout
                1, // Max retries
                1f // No backoff multiplier
            )
        }

        requestQueue.add(jsonObjectRequest)
    }

    private fun showEmptyState() {
        // Additional logging
        Log.d(TAG, "Showing empty state")

        noNotificationsText.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
        clearAllButton.visibility = View.GONE
    }

    private fun showError() {
        Toast.makeText(this, "Failed to load notifications", Toast.LENGTH_SHORT).show()
        showEmptyState()
    }

    private fun deleteNotification(notificationId: String) {
        // Show loading dialog
        loadingDialog.showLoading()

        val url = "$BASE_URL/notifications/$notificationId?email=${URLEncoder.encode(userEmail, "UTF-8")}"
        Log.d(TAG, "Deleting notification: $url")

        val requestQueue = Volley.newRequestQueue(this)

        val jsonObjectRequest = JsonObjectRequest(
            VolleyRequest.Method.DELETE,
            url,
            null,
            { response ->
                loadingDialog.hideLoading()
                Log.d(TAG, "Delete response: $response")
                try {
                    if (response.getBoolean("success")) {
                        adapter.removeNotification(notificationId)
                        Toast.makeText(this, "Notification deleted", Toast.LENGTH_SHORT).show()

                        // Check if we need to show empty state
                        if (adapter.itemCount == 0) {
                            showEmptyState()
                        }
                    } else {
                        val message = response.optString("message", "Failed to delete notification")
                        Log.e(TAG, "Server returned error: $message")
                        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing delete response: ${e.message}")
                    Toast.makeText(this, "Error deleting notification", Toast.LENGTH_SHORT).show()
                }
            },
            { error ->
                loadingDialog.hideLoading()
                val errorMessage = when (error) {
                    is com.android.volley.NoConnectionError -> "No internet connection"
                    is com.android.volley.TimeoutError -> "Request timed out"
                    is com.android.volley.ServerError -> "Server error"
                    else -> "Error deleting notification"
                }
                Log.e(TAG, "Error deleting notification: ${error.message}")
                Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show()
            }
        ).apply {
            retryPolicy = com.android.volley.DefaultRetryPolicy(
                15000, // 15 seconds timeout
                1, // Max retries
                1f // No backoff multiplier
            )
        }

        requestQueue.add(jsonObjectRequest)
    }

    private fun clearAllNotifications() {
        // Show loading dialog
        loadingDialog.showLoading()

        // Use the alternate endpoint that's simpler
        val url = "$BASE_URL/notifications/erase-all?email=${URLEncoder.encode(userEmail, "UTF-8")}"
        Log.d(TAG, "Clearing all notifications: $url")

        val requestQueue = Volley.newRequestQueue(this)

        // Add more detailed logging
        Log.d(TAG, "User email: $userEmail")
        Log.d(TAG, "Full URL being called: $url")

        val jsonObjectRequest = JsonObjectRequest(
            VolleyRequest.Method.DELETE,
            url,
            null,
            { response ->
                loadingDialog.hideLoading()
                Log.d(TAG, "Delete response: $response")
                try {
                    if (response.getBoolean("success")) {
                        adapter.updateNotifications(mutableListOf()) // Clear adapter immediately
                        showEmptyState() // Show empty state directly
                        Toast.makeText(this, "All notifications cleared", Toast.LENGTH_SHORT).show()
                        setResult(RESULT_OK) // Notify HomeActivity to update badge
                    } else {
                        val message = response.optString("message", "Failed to clear notifications")
                        Log.e(TAG, "Server returned error: $message")
                        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing delete response: ${e.message}")
                    Toast.makeText(this, "Error clearing notifications", Toast.LENGTH_SHORT).show()
                }
            },
            { error ->
                loadingDialog.hideLoading()
                // Enhanced error logging
                val statusCode = error.networkResponse?.statusCode
                val responseData = error.networkResponse?.data?.let { String(it) } ?: "No response data"
                Log.e(TAG, "Network error: Status code=$statusCode, Response=$responseData")

                val errorMessage = when (error) {
                    is com.android.volley.NoConnectionError -> "No internet connection"
                    is com.android.volley.TimeoutError -> "Request timed out"
                    is com.android.volley.ServerError -> "Server error (${statusCode}): $responseData"
                    else -> "Error clearing notifications: ${error.message}"
                }
                Log.e(TAG, "Error clearing notifications: $errorMessage")
                Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show()
            }
        ).apply {
            retryPolicy = com.android.volley.DefaultRetryPolicy(
                15000, // 15 seconds timeout
                1, // Max retries
                1f // No backoff multiplier
            )
        }

        requestQueue.add(jsonObjectRequest)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clean up the loading dialog
        loadingDialog.dispose()
    }

    // Inner adapter class
    private inner class NotificationAdapter(
        private var notifications: MutableList<NotificationItem>,
        private val onDeleteClick: (NotificationItem) -> Unit
    ) : RecyclerView.Adapter<NotificationAdapter.NotificationViewHolder>() {

        inner class NotificationViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val dishName: TextView = view.findViewById(R.id.dish_name)
            val dishDescription: TextView = view.findViewById(R.id.dish_description)
            val safetyScore: TextView = view.findViewById(R.id.restaurant_safety_score)
            val analyzedTime: TextView = view.findViewById(R.id.dish_analyze_time)
            val deleteButton: TextView = view.findViewById(R.id.delete_notification)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotificationViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_notification, parent, false)
            return NotificationViewHolder(view)
        }

        override fun onBindViewHolder(holder: NotificationViewHolder, position: Int) {
            val notification = notifications[position]

            holder.dishName.text = notification.dishName
            holder.dishDescription.text = notification.dishDescription ?: "No description available"
            holder.safetyScore.text = "Safety Score: ${notification.dishSafetyScore}%"
            holder.analyzedTime.text = "Time Analyzed: ${notification.analyzedTime}"

            // Set safety score badge color based on the score
            val safetyScoreColor = when {
                notification.dishSafetyScore >= 85 -> R.color.safety_high
                notification.dishSafetyScore >= 70 -> R.color.safety_medium
                else -> R.color.safety_low
            }
            holder.safetyScore.setTextColor(holder.itemView.context.getColor(safetyScoreColor))

            // Set safety score badge background
            val safetyScoreBadge = when {
                notification.dishSafetyScore >= 85 -> R.drawable.safety_badge_high
                notification.dishSafetyScore >= 70 -> R.drawable.safety_badge_medium
                else -> R.drawable.safety_badge_low
            }
            holder.safetyScore.setBackgroundResource(safetyScoreBadge)

            // Set delete click listener
            holder.deleteButton.setOnClickListener {
                onDeleteClick(notification)
            }
        }

        override fun getItemCount() = notifications.size

        fun updateNotifications(newNotifications: List<NotificationItem>) {
            notifications.clear()
            notifications.addAll(newNotifications)
            notifyDataSetChanged()
        }

        fun removeNotification(notificationId: String) {
            val position = notifications.indexOfFirst { it.notificationId == notificationId }
            if (position != -1) {
                notifications.removeAt(position)
                notifyItemRemoved(position)

                // Also notify that the list may have changed more broadly to update spacing
                notifyItemRangeChanged(position, notifications.size)
            }
        }
    }
} 