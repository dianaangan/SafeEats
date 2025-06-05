    package com.android.safeeats

    import android.app.Activity
    import android.content.Intent
    import android.graphics.BitmapFactory
    import android.os.Bundle
    import android.text.Editable
    import android.text.TextWatcher
    import android.util.Log
    import android.view.LayoutInflater
    import android.view.View
    import android.view.ViewGroup
    import android.view.inputmethod.InputMethodManager
    import android.widget.*
    import androidx.drawerlayout.widget.DrawerLayout
    import androidx.recyclerview.widget.LinearLayoutManager
    import androidx.recyclerview.widget.RecyclerView
    import com.android.safeeats.models.Restaurant
    import com.android.safeeats.utils.LoadingDialog
    import okhttp3.*
    import org.json.JSONArray
    import org.json.JSONObject
    import java.io.IOException
    import java.net.URLEncoder
    import java.util.*
    import java.util.concurrent.TimeUnit

    class HomeActivity : Activity() {

        private val TAG = "HomeActivity"
        private var hasSeenNotifications = false
        private var notificationCount = 0

        private lateinit var email: String
        private lateinit var firstName: String
        private lateinit var lastName: String
        private lateinit var profileImage: ImageView
        private lateinit var loadingDialog: LoadingDialog

        // RecyclerViews
        private lateinit var compatibleRestaurantsRecycler: RecyclerView
        private lateinit var allergenSafeRestaurantsRecycler: RecyclerView
        private lateinit var allRestaurantsRecycler: RecyclerView
        private lateinit var searchRestaurantsRecycler: RecyclerView

        // Section containers and titles
        private lateinit var compatibleRestaurantsSection: LinearLayout
        private lateinit var topRatedRestaurantsSection: LinearLayout
        private lateinit var allRestaurantsSection: LinearLayout
        private lateinit var allRestaurantsTitle: TextView
        private lateinit var searchRestaurantsSection: LinearLayout
        private lateinit var searchRestaurantsTitle: TextView

        // Filter tabs
        private lateinit var tabAll: TextView
        private lateinit var tabTopRated: TextView
        private lateinit var tabCompatible: TextView

        // Store all restaurants data
        private var allRestaurantsData: List<Restaurant> = emptyList()
        private var compatibleRestaurantsData: List<Restaurant> = emptyList()
        private var allergenSafeRestaurantsData: List<Restaurant> = emptyList()

        // For search functionality
        private lateinit var searchEditText: EditText
        private var filteredRestaurantsData: List<Restaurant> = emptyList()
        private var isSearchActive: Boolean = false

        // Track expanded state for "All" tab only
        private var isAllRestaurantsExpanded = false
        private var isCompatibleRestaurantsExpanded = false
        private var isTopRatedRestaurantsExpanded = false

        companion object {
            private const val PROFILE_REQUEST_CODE = 1001
            private const val NOTIFICATION_REQUEST_CODE = 1002
        }

        // HTTP Client
        private val okHttpClient = OkHttpClient.Builder()
            .callTimeout(30, TimeUnit.SECONDS)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .connectionPool(ConnectionPool(5, 30, TimeUnit.SECONDS))
            .build()

        private lateinit var drawerLayout: DrawerLayout

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            setContentView(R.layout.activity_home)

            try {
                loadingDialog = LoadingDialog(this)
                initializeViews()
                setupNavigationDrawer()
                setupProfileActions()
                setupFilterTabs()
                setupSearchFunctionality()
                updateGreetingText()
                loadProfilePicture()
                loadRestaurants()
                updateSafeRestaurantCount()
                updateNotificationBadge()

                // Set up notification click listener
                findViewById<LinearLayout>(R.id.notification_button).setOnClickListener {
                    markNotificationsAsSeen()  // Mark notifications as seen when clicked
                    val intent = Intent(this, NotificationActivity::class.java).apply {
                        putExtra("email", email)
                    }
                    startActivityForResult(intent, NOTIFICATION_REQUEST_CODE)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during activity initialization: ${e.message}", e)
                Toast.makeText(this, "Error initializing app: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }

        private fun initializeViews() {
            // Initialize basic UI elements
            profileImage = findViewById(R.id.profileImage)
            val name = findViewById<TextView>(R.id.user_name)
            searchEditText = findViewById(R.id.searchEditText)
            allRestaurantsTitle = findViewById(R.id.restaurants_title)

            // Initialize RecyclerViews
            compatibleRestaurantsRecycler = findViewById(R.id.compatible_restaurants_recycler)
            allergenSafeRestaurantsRecycler = findViewById(R.id.top_rated_restaurants_recycler)
            allRestaurantsRecycler = findViewById(R.id.all_restaurants_recycler)

            // Initialize section containers
            compatibleRestaurantsSection = findViewById(R.id.compatible_restaurants)
            topRatedRestaurantsSection = findViewById(R.id.section_top_rated_restaurants)
            allRestaurantsSection = findViewById(R.id.restaurants)

            findViewById<TextView>(R.id.see_all_restaurants).text = "See All"
            findViewById<TextView>(R.id.see_all_compatible_restaurants).text = "See All"
            findViewById<TextView>(R.id.see_all_top_rated_restaurants).text = "See All"

            // Initialize filter tabs
            tabAll = findViewById(R.id.tab_all)
            tabTopRated = findViewById(R.id.tab_top_rated)
            tabCompatible = findViewById(R.id.tab_compatible)

            // Initialize search
            searchRestaurantsSection = findViewById(R.id.search_restaurants)
            searchRestaurantsTitle = findViewById(R.id.search_restaurant_titles)
            searchRestaurantsRecycler = findViewById(R.id.search_restaurant_recycler)

            // Set up the search recycler view
            searchRestaurantsRecycler.layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)

            // Initially hide the search section
            searchRestaurantsSection.visibility = View.GONE
            searchRestaurantsRecycler.visibility = View.GONE

            // Set up RecyclerViews with vertical layout
            setUpRecyclerViews()

            // Get user info from intent
            email = intent.getStringExtra("email") ?: ""
            firstName = intent.getStringExtra("firstName") ?: ""
            lastName = intent.getStringExtra("lastName") ?: ""

            // Set user name
            name.text = "$firstName $lastName"

            // Set up search icon click
            findViewById<ImageView>(R.id.searchIcon).setOnClickListener {
                searchEditText.requestFocus()
                (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
                    .showSoftInput(searchEditText, InputMethodManager.SHOW_IMPLICIT)
            }
        }

        private fun setUpRecyclerViews() {
            compatibleRestaurantsRecycler.layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
            allergenSafeRestaurantsRecycler.layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
            allRestaurantsRecycler.layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
        }

        private fun setupSearchFunctionality() {
            searchEditText.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

                override fun afterTextChanged(s: Editable?) {
                    val searchText = s.toString().trim().lowercase()
                    val wasSearchActive = isSearchActive
                    isSearchActive = searchText.isNotEmpty()

                    if (isSearchActive) {
                        filterRestaurants(searchText)
                    } else if (wasSearchActive) {
                        // User cleared search
                        searchRestaurantsSection.visibility = View.GONE
                        searchRestaurantsRecycler.visibility = View.GONE

                        when {
                            tabCompatible.isSelected -> showOnlyCompatibleSection()
                            tabTopRated.isSelected -> showOnlyTopRatedSection()
                            else -> showAllSections()
                        }
                    }
                }
            })
        }

        private fun filterRestaurants(searchText: String) {
            filteredRestaurantsData = allRestaurantsData.filter { restaurant ->
                restaurant.name.lowercase().contains(searchText) ||
                        restaurant.category.lowercase().contains(searchText) ||
                        restaurant.allergenInfo.lowercase().contains(searchText)
            }

            allRestaurantsSection.visibility = View.GONE
            topRatedRestaurantsSection.visibility = View.GONE
            compatibleRestaurantsSection.visibility = View.GONE

            allRestaurantsRecycler.visibility = View.GONE
            allergenSafeRestaurantsRecycler.visibility = View.GONE
            compatibleRestaurantsRecycler.visibility = View.GONE

            searchRestaurantsTitle.text = "Search Results (${filteredRestaurantsData.size})"
            searchRestaurantsSection.visibility = View.VISIBLE
            searchRestaurantsRecycler.visibility = View.VISIBLE
            searchRestaurantsRecycler.adapter = RestaurantAdapter(filteredRestaurantsData)

            // Hide all sections during search
            compatibleRestaurantsSection.visibility = View.GONE
            topRatedRestaurantsSection.visibility = View.GONE
            allRestaurantsSection.visibility = View.GONE

            updateActionButtonVisibility()
        }

        private fun setupFilterTabs() {
            // Set default state - "All" tab is selected
            updateTabSelection(tabAll)

            // Make sure expanded states are reset initially
            isAllRestaurantsExpanded = false
            isCompatibleRestaurantsExpanded = false
            isTopRatedRestaurantsExpanded = false

            // Set click listeners for tabs
            tabAll.setOnClickListener {
                updateTabSelection(tabAll)
                searchEditText.setText("") // Clear search
                isSearchActive = false
                showAllSections()
            }

            tabTopRated.setOnClickListener {
                updateTabSelection(tabTopRated)
                searchEditText.setText("") // Clear search
                isSearchActive = false
                showOnlyTopRatedSection()
            }

            tabCompatible.setOnClickListener {
                updateTabSelection(tabCompatible)
                searchEditText.setText("") // Clear search
                isSearchActive = false
                showOnlyCompatibleSection()
            }
        }

        private fun updateTabSelection(selectedTab: TextView) {
            // Reset all tabs to unselected state
            for (tab in listOf(tabAll, tabTopRated, tabCompatible)) {
                tab.setBackgroundResource(R.drawable.search_background)
                tab.setTextColor(getColor(R.color.tab_unselected_text))
                tab.isSelected = false
            }

            // Set selected tab style
            selectedTab.setBackgroundResource(R.drawable.selected_tab_background)
            selectedTab.setTextColor(getColor(R.color.tab_selected_text))
            selectedTab.isSelected = true
        }

        private fun showAllSections() {
            if (isSearchActive) {
                // Hide all regular sections and show search section
                hideAllMainSections()
                searchRestaurantsSection.visibility = View.VISIBLE
                searchRestaurantsRecycler.visibility = View.VISIBLE
                return
            }

            Log.d("SafeEats", "----------- SHOW ALL SECTIONS ------------")
            Log.d("SafeEats", "Restaurant counts - compatible: ${compatibleRestaurantsData.size}, allergenSafe: ${allergenSafeRestaurantsData.size}, all: ${allRestaurantsData.size}")

            if (allRestaurantsData.isNotEmpty()) {
                Log.d("SafeEats", "First restaurant details: id=${allRestaurantsData[0].id}, name=${allRestaurantsData[0].name}, category=${allRestaurantsData[0].category}")
            } else {
                Log.d("SafeEats", "No restaurants in allRestaurantsData")
            }

            // Hide search section first
            searchRestaurantsSection.visibility = View.GONE
            searchRestaurantsRecycler.visibility = View.GONE

            // Show all three main sections based on data availability
            val hasCompatible = compatibleRestaurantsData.isNotEmpty()
            val hasAllergenSafe = allergenSafeRestaurantsData.isNotEmpty()
            val hasAll = allRestaurantsData.isNotEmpty()

            // Set section visibility
            compatibleRestaurantsSection.visibility = if (hasCompatible) View.VISIBLE else View.GONE
            topRatedRestaurantsSection.visibility = if (hasAllergenSafe) View.VISIBLE else View.GONE
            allRestaurantsSection.visibility = if (hasAll) View.VISIBLE else View.GONE

            // Force set RecyclerView visibility to match their sections
            compatibleRestaurantsRecycler.visibility = compatibleRestaurantsSection.visibility
            allergenSafeRestaurantsRecycler.visibility = topRatedRestaurantsSection.visibility
            allRestaurantsRecycler.visibility = allRestaurantsSection.visibility

            Log.d("SafeEats", "Section visibility - compatible: ${compatibleRestaurantsSection.visibility == View.VISIBLE}, topRated: ${topRatedRestaurantsSection.visibility == View.VISIBLE}, all: ${allRestaurantsSection.visibility == View.VISIBLE}")

            // Set the title
            allRestaurantsTitle.text = "Restaurants"

            // Setup adapters for each RecyclerView
            Log.d("SafeEats", "Setting up adapters for recycler views...")

            // IMPORTANT: Always reset expanded states when showing all sections
            // This ensures "See All" is the default state when switching back to All tab
            isCompatibleRestaurantsExpanded = false
            isTopRatedRestaurantsExpanded = false
            isAllRestaurantsExpanded = false

            if (hasCompatible) {
                // Limit compatible restaurants list based on expanded state
                val compatibleList = if (isCompatibleRestaurantsExpanded)
                    compatibleRestaurantsData
                else
                    compatibleRestaurantsData.take(1)

                val compatibleAdapter = RestaurantAdapter(compatibleList)
                compatibleRestaurantsRecycler.adapter = compatibleAdapter
                Log.d("SafeEats", "Compatible adapter item count: ${compatibleAdapter.itemCount}")
            }

            if (hasAllergenSafe) {
                // Limit allergen safe restaurants list based on expanded state
                val allergenList = if (isTopRatedRestaurantsExpanded)
                    allergenSafeRestaurantsData
                else
                    allergenSafeRestaurantsData.take(1)

                val allergenAdapter = RestaurantAdapter(allergenList)
                allergenSafeRestaurantsRecycler.adapter = allergenAdapter
                Log.d("SafeEats", "Allergen adapter item count: ${allergenAdapter.itemCount}")
            }

            if (hasAll) {
                // Limit all restaurants list based on expanded state
                val allList = if (isAllRestaurantsExpanded)
                    allRestaurantsData
                else
                    allRestaurantsData.take(1)

                val allAdapter = RestaurantAdapter(allList)
                allRestaurantsRecycler.adapter = allAdapter
                Log.d("SafeEats", "All restaurants adapter item count: ${allAdapter.itemCount}")
            }

            updateActionButtonVisibility()
        }

        private fun showOnlyCompatibleSection() {
            // Hide other sections
            hideAllMainSections()
            allRestaurantsRecycler.visibility = View.GONE
            allergenSafeRestaurantsRecycler.visibility = View.GONE
            searchRestaurantsSection.visibility = View.GONE

            // Show only compatible section with ALL restaurants (not limited)
            if (compatibleRestaurantsData.isNotEmpty()) {
                compatibleRestaurantsSection.visibility = View.VISIBLE
                compatibleRestaurantsRecycler.visibility = View.VISIBLE

                // Always show all compatible restaurants when tab is selected
                isCompatibleRestaurantsExpanded = true
                compatibleRestaurantsRecycler.adapter = RestaurantAdapter(compatibleRestaurantsData)
                Log.d("SafeEats", "Showing Compatible section with ${compatibleRestaurantsData.size} restaurants")
            } else {
                Log.d("SafeEats", "No compatible restaurants to show")
                // Show an empty state or message
                compatibleRestaurantsSection.visibility = View.VISIBLE
            }

            // Update action button visibility
            updateActionButtonVisibility()
        }

        private fun showOnlyTopRatedSection() {
            // Hide other sections
            hideAllMainSections()
            allRestaurantsRecycler.visibility = View.GONE
            compatibleRestaurantsRecycler.visibility = View.GONE
            searchRestaurantsSection.visibility = View.GONE

            // Show only top rated section with ALL restaurants (not limited)
            if (allergenSafeRestaurantsData.isNotEmpty()) {
                topRatedRestaurantsSection.visibility = View.VISIBLE
                allergenSafeRestaurantsRecycler.visibility = View.VISIBLE

                // Always show all top rated restaurants when tab is selected
                isTopRatedRestaurantsExpanded = true
                allergenSafeRestaurantsRecycler.adapter = RestaurantAdapter(allergenSafeRestaurantsData)
                Log.d("SafeEats", "Showing Top Rated section with ${allergenSafeRestaurantsData.size} restaurants")
            } else {
                Log.d("SafeEats", "No top rated restaurants to show")
                // Show an empty state or message
                topRatedRestaurantsSection.visibility = View.VISIBLE
            }

            // Update action button visibility
            updateActionButtonVisibility()
        }

        private fun hideAllMainSections() {
            compatibleRestaurantsSection.visibility = View.GONE
            topRatedRestaurantsSection.visibility = View.GONE
            allRestaurantsSection.visibility = View.GONE
        }

        private fun updateActionButtonVisibility() {
            val seeAllCompatible = findViewById<TextView>(R.id.see_all_compatible_restaurants)
            val seeAllTopRated = findViewById<TextView>(R.id.see_all_top_rated_restaurants)
            val seeAllRestaurants = findViewById<TextView>(R.id.see_all_restaurants)

            // Hide ALL action buttons during search
            if (isSearchActive) {
                seeAllCompatible.visibility = View.GONE
                seeAllTopRated.visibility = View.GONE
                seeAllRestaurants.visibility = View.GONE
                return
            }

            // Handle "All" tab - show all action buttons
            if (tabAll.isSelected) {
                // Update "See All" text for each section based on expanded state
                seeAllCompatible.apply {
                    visibility = if (compatibleRestaurantsSection.visibility == View.VISIBLE && compatibleRestaurantsData.size > 1)
                        View.VISIBLE else View.GONE
                    text = if (isCompatibleRestaurantsExpanded) "See Less" else "See All"
                }

                seeAllTopRated.apply {
                    visibility = if (topRatedRestaurantsSection.visibility == View.VISIBLE && allergenSafeRestaurantsData.size > 1)
                        View.VISIBLE else View.GONE
                    text = if (isTopRatedRestaurantsExpanded) "See Less" else "See All"
                }

                seeAllRestaurants.apply {
                    visibility = if (allRestaurantsSection.visibility == View.VISIBLE && allRestaurantsData.size > 1)
                        View.VISIBLE else View.GONE
                    text = if (isAllRestaurantsExpanded) "See Less" else "See All"
                }
            } else if (tabCompatible.isSelected) {
                // Hide the compatible section button when in compatible tab
                // We always show all items in this tab
                seeAllCompatible.visibility = View.GONE
                seeAllTopRated.visibility = View.GONE
                seeAllRestaurants.visibility = View.GONE
            } else if (tabTopRated.isSelected) {
                // Hide the top rated section button when in top rated tab
                // We always show all items in this tab
                seeAllTopRated.visibility = View.GONE
                seeAllCompatible.visibility = View.GONE
                seeAllRestaurants.visibility = View.GONE
            }
        }

        private fun setupProfileActions() {
            val profileButton = findViewById<LinearLayout>(R.id.profile_button)

            // Navigate to profile screen
            val profileClickListener = View.OnClickListener {
                val intent = Intent(this, PersonalProfileActivity::class.java).apply {
                    putExtra("email", email)
                    putExtra("firstName", firstName)
                    putExtra("lastName", lastName)
                }
                startActivityForResult(intent, PROFILE_REQUEST_CODE)
            }

            profileButton.setOnClickListener(profileClickListener)
            profileImage.setOnClickListener(profileClickListener)
        }

        private fun setupNavigationDrawer() {
            drawerLayout = findViewById(R.id.drawer_layout)

            // Setup open/close buttons
            findViewById<ImageView>(R.id.settings_icon).setOnClickListener {
                drawerLayout.openDrawer(findViewById(R.id.settings_drawer))
            }

            findViewById<ImageView>(R.id.close_menu_button).setOnClickListener {
                drawerLayout.closeDrawer(findViewById(R.id.settings_drawer))
            }

            // Set up menu items
            setupMenuItemActions()
        }

        private fun updateNotificationBadge() {
            val notificationButton = findViewById<LinearLayout>(R.id.notification_button)
            val notificationBadge = findViewById<TextView>(R.id.notification_badge)

            val url = "https://swamp-brief-brake.glitch.me/api/notifications/count?email=${URLEncoder.encode(email, "UTF-8")}"
            Log.d(TAG, "Fetching notification count from: $url")

            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            okHttpClient.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.e(TAG, "Failed to get notification count", e)
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        if (response.isSuccessful) {
                            val responseBody = response.body?.string()
                            Log.d(TAG, "Notification count response: $responseBody")
                            val jsonObject = JSONObject(responseBody)
                            val count = jsonObject.getInt("count")
                            notificationCount = count // Store the current count

                            runOnUiThread {
                                if (count > 0) {
                                    notificationBadge.visibility = View.VISIBLE
                                    notificationBadge.text = if (count > 9) "9+" else count.toString()
                                    Log.d(TAG, "Showing badge with count: ${notificationBadge.text}")
                                } else {
                                    notificationBadge.visibility = View.GONE
                                    Log.d(TAG, "Hiding notification badge")
                                }
                            }
                        }
                    }
                }
            })
        }

        private fun markNotificationsAsSeen() {
            val url = "https://swamp-brief-brake.glitch.me/api/notifications/seen?email=${URLEncoder.encode(email, "UTF-8")}"
            Log.d(TAG, "Marking notifications as seen: $url")

            val request = Request.Builder()
                .url(url)
                .put(RequestBody.create(null, ByteArray(0)))
                .build()

            okHttpClient.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.e(TAG, "Failed to mark notifications as seen", e)
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        if (response.isSuccessful) {
                            Log.d(TAG, "Successfully marked notifications as seen")
                            runOnUiThread {
                                updateNotificationBadge()
                            }
                        }
                    }
                }
            })
        }

        override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
            super.onActivityResult(requestCode, resultCode, data)

            when (requestCode) {
                PROFILE_REQUEST_CODE -> {
                    if (resultCode == RESULT_OK && data != null) {
                        // Update user information with data returned from PersonalProfileActivity
                        firstName = data.getStringExtra("firstName") ?: firstName
                        lastName = data.getStringExtra("lastName") ?: lastName
                        email = data.getStringExtra("email") ?: email

                        // Update UI with new name
                        findViewById<TextView>(R.id.user_name).text = "$firstName $lastName"

                        // Refresh profile data
                        loadProfilePicture()
                        loadRestaurants()
                        updateSafeRestaurantCount()
                    }
                }
                NOTIFICATION_REQUEST_CODE -> {
                    // Reset notification count when returning from notifications
                    updateNotificationBadge()
                }
            }
        }

        private fun setupMenuItemActions() {
            // Helper function to navigate to profile tabs
            fun navigateToProfileTab(tabIndex: Int) {
                drawerLayout.closeDrawer(findViewById(R.id.settings_drawer))
                val intent = Intent(this, PersonalProfileActivity::class.java).apply {
                    putExtra("email", email)
                    putExtra("firstName", firstName)
                    putExtra("lastName", lastName)
                    putExtra(PersonalProfileActivity.SELECTED_TAB, tabIndex)
                }
                startActivityForResult(intent, PROFILE_REQUEST_CODE)
            }

            // Set click listeners for menu items
            findViewById<LinearLayout>(R.id.menu_account).setOnClickListener {
                navigateToProfileTab(PersonalProfileActivity.TAB_PERSONAL)
            }

            findViewById<LinearLayout>(R.id.menu_dietary_preference).setOnClickListener {
                navigateToProfileTab(PersonalProfileActivity.TAB_DIETARY)
            }

            findViewById<LinearLayout>(R.id.menu_allergen).setOnClickListener {
                navigateToProfileTab(PersonalProfileActivity.TAB_ALLERGEN)
            }

            findViewById<LinearLayout>(R.id.menu_about_us).setOnClickListener {
                drawerLayout.closeDrawer(findViewById(R.id.settings_drawer))
                startActivity(Intent(this, AboutUsActivity::class.java))
            }

            findViewById<LinearLayout>(R.id.menu_logout).setOnClickListener {
                drawerLayout.closeDrawer(findViewById(R.id.settings_drawer))
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
            }
        }

        private fun updateSafeRestaurantCount() {
            val safeRestaurantCountTextView = findViewById<TextView>(R.id.safe_restaurant_count)

            loadingDialog.executeWithLoading<Int>(
                thresholdMs = 500,
                operation = {
                    try {
                        val encodedEmail = URLEncoder.encode(email, "UTF-8")
                        val request = Request.Builder()
                            .url("https://swamp-brief-brake.glitch.me/api/customer/safety-score?email=$encodedEmail")
                            .get()
                            .build()

                        val response = okHttpClient.newCall(request).execute()

                        if (response.isSuccessful) {
                            JSONObject(response.body?.string()).getInt("safeCount")
                        } else {
                            Log.e("SafeEats", "Failed to get safe restaurant count. Response code: ${response.code}")
                            0
                        }
                    } catch (e: Exception) {
                        Log.e("SafeEats", "Error getting safe restaurant count: ${e.message}")
                        0
                    }
                },
                callback = { safeCount ->
                    safeRestaurantCountTextView.text = "Based on your profile, we found $safeCount restaurants with safe menu options for you."
                }
            )
        }

        private fun loadRestaurants() {
            Log.d("SafeEats", "=============================================")
            Log.d("SafeEats", "STARTING TO LOAD RESTAURANTS")
            Log.d("SafeEats", "=============================================")

            try {
                loadingDialog.executeWithLoading<Triple<List<Restaurant>, List<Restaurant>, List<Restaurant>>>(
                    thresholdMs = 500,
                    operation = {
                        try {
                            val encodedEmail = URLEncoder.encode(email, "UTF-8")
                            val url = "https://swamp-brief-brake.glitch.me/api/restaurants?email=$encodedEmail"
                            Log.d("SafeEats", "Loading restaurants from: $url")

                            val request = Request.Builder()
                                .url(url)
                                .get()
                                .build()

                            Log.d("SafeEats", "Executing network request...")
                            val response = okHttpClient.newCall(request).execute()
                            Log.d("SafeEats", "Network request completed. Response code: ${response.code}")

                            if (response.isSuccessful) {
                                val responseBody = response.body?.string()
                                Log.d("SafeEats", "Restaurant API response: $responseBody")

                                if (responseBody == null || responseBody.isEmpty()) {
                                    Log.e("SafeEats", "Empty response body from server")
                                    return@executeWithLoading Triple(emptyList(), emptyList(), emptyList())
                                }

                                val jsonObject = JSONObject(responseBody)
                                if (!jsonObject.has("restaurants")) {
                                    Log.e("SafeEats", "Response JSON doesn't contain 'restaurants' field: $jsonObject")
                                    return@executeWithLoading Triple(emptyList(), emptyList(), emptyList())
                                }

                                val restaurantsArray = jsonObject.getJSONArray("restaurants")
                                Log.d("SafeEats", "Restaurants array length: ${restaurantsArray.length()}")

                                val allRestaurants = parseRestaurants(restaurantsArray)
                                    .distinctBy { it.id }  // Ensure no duplicate restaurants by ID

                                Log.d("SafeEats", "Parsed restaurants count: ${allRestaurants.size}")
                                if (allRestaurants.isNotEmpty()) {
                                    Log.d("SafeEats", "First restaurant: ${allRestaurants[0]}")
                                }

                                // Sort restaurants by scores
                                val compatibleRestaurants = allRestaurants.sortedByDescending { it.dietaryMatchScore }
                                val allergenSafeRestaurants = allRestaurants.sortedByDescending { it.safetyScore }

                                // Ensure all restaurants list is also sorted for consistent presentation
                                val sortedAllRestaurants = allRestaurants.sortedBy { it.name }

                                // Log any found restaurants
                                Log.d("SafeEats", "Total Restaurant Counts - compatible: ${compatibleRestaurants.size}, " +
                                    "allergenSafe: ${allergenSafeRestaurants.size}, all: ${sortedAllRestaurants.size}")

                                Triple(compatibleRestaurants, allergenSafeRestaurants, sortedAllRestaurants)
                            } else {
                                Log.e("SafeEats", "Failed to load restaurants. Response code: ${response.code}")
                                Log.e("SafeEats", "Error response body: ${response.body?.string()}")
                                Triple(emptyList(), emptyList(), emptyList())
                            }
                        } catch (e: Exception) {
                            Log.e("SafeEats", "Error loading restaurants: ${e.message}", e)
                            e.printStackTrace()
                            Triple(emptyList(), emptyList(), emptyList())
                        }
                    },
                    callback = { (compatibleRestaurants, allergenSafeRestaurants, allRestaurants) ->
                        try {
                            Log.d("SafeEats", "Callback received with restaurant counts - compatible: ${compatibleRestaurants.size}, " +
                                    "allergenSafe: ${allergenSafeRestaurants.size}, all: ${allRestaurants.size}")

                            this.compatibleRestaurantsData = compatibleRestaurants
                            this.allergenSafeRestaurantsData = allergenSafeRestaurants
                            this.allRestaurantsData = allRestaurants

                            // Reset expanded states when loading restaurants
                            isAllRestaurantsExpanded = false
                            isCompatibleRestaurantsExpanded = false
                            isTopRatedRestaurantsExpanded = false

                            Log.d("SafeEats", "Updated local data with - compatible: ${this.compatibleRestaurantsData.size}, " +
                                    "allergenSafe: ${this.allergenSafeRestaurantsData.size}, all: ${this.allRestaurantsData.size}")

                            if (isSearchActive) {
                                // Stay in search mode after restaurants load
                                Log.d("SafeEats", "Search is active, applying filter")
                                val currentSearchText = searchEditText.text.toString().trim().lowercase()
                                filterRestaurants(currentSearchText)
                            } else {
                                // Default behavior if not searching
                                Log.d("SafeEats", "Search not active, showing selected tab view")
                                when {
                                    tabCompatible.isSelected -> {
                                        Log.d("SafeEats", "Compatible tab selected, showing compatible section")
                                        showOnlyCompatibleSection()
                                    }
                                    tabTopRated.isSelected -> {
                                        Log.d("SafeEats", "Top Rated tab selected, showing top rated section")
                                        showOnlyTopRatedSection()
                                    }
                                    else -> {
                                        Log.d("SafeEats", "All tab selected, showing all sections")
                                        showAllSections()
                                    }
                                }
                            }
                            setupActionButtons()
                            Log.d("SafeEats", "Restaurant loading and UI update complete")

                            // Display a message if no restaurants are found at all
                            if (allRestaurantsData.isEmpty()) {
                                Toast.makeText(this, "No restaurants found. Please check your connection or try again later.", Toast.LENGTH_LONG).show()
                            }
                        } catch (e: Exception) {
                            Log.e("SafeEats", "Error in callback processing: ${e.message}", e)
                            Toast.makeText(this, "Error displaying restaurants: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e("SafeEats", "Failed to start restaurant loading process: ${e.message}", e)
                Toast.makeText(this, "Error loading restaurants: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }

        private fun setupActionButtons() {
            val seeAllCompatible = findViewById<TextView>(R.id.see_all_compatible_restaurants)
            val seeAllTopRated = findViewById<TextView>(R.id.see_all_top_rated_restaurants)
            val seeAllRestaurants = findViewById<TextView>(R.id.see_all_restaurants)

            // Setup click listener for Compatible Restaurants section
            seeAllCompatible.setOnClickListener {
                if (isCompatibleRestaurantsExpanded) {
                    collapseCompatibleRestaurantsSection()
                } else {
                    expandCompatibleRestaurantsSection()
                }
            }

            // Setup click listener for Top Rated Restaurants section
            seeAllTopRated.setOnClickListener {
                if (isTopRatedRestaurantsExpanded) {
                    collapseTopRatedRestaurantsSection()
                } else {
                    expandTopRatedRestaurantsSection()
                }
            }

            // Setup click listener for All Restaurants section
            seeAllRestaurants.setOnClickListener {
                if (isAllRestaurantsExpanded) {
                    collapseAllRestaurantsSection()
                } else {
                    expandAllRestaurantsSection()
                }
            }
        }

        // 5. Update expandAllRestaurantsSection() to show all restaurants
        private fun expandAllRestaurantsSection() {
            isAllRestaurantsExpanded = true
            allRestaurantsRecycler.adapter = RestaurantAdapter(allRestaurantsData)
            updateActionButtonVisibility()
        }

        // 6. Update collapseAllRestaurantsSection() to limit to 1 restaurant
        private fun collapseAllRestaurantsSection() {
            isAllRestaurantsExpanded = false
            val limitedList = allRestaurantsData.take(1)
            allRestaurantsRecycler.adapter = RestaurantAdapter(limitedList)
            updateActionButtonVisibility()
        }

        // Add these new methods for expanding/collapsing compatible restaurants section
        private fun expandCompatibleRestaurantsSection() {
            isCompatibleRestaurantsExpanded = true
            compatibleRestaurantsRecycler.adapter = RestaurantAdapter(compatibleRestaurantsData)
            updateActionButtonVisibility()
        }

        private fun collapseCompatibleRestaurantsSection() {
            isCompatibleRestaurantsExpanded = false
            val limitedList = compatibleRestaurantsData.take(1)
            compatibleRestaurantsRecycler.adapter = RestaurantAdapter(limitedList)
            updateActionButtonVisibility()
        }

        // Add these new methods for expanding/collapsing top rated restaurants section
        private fun expandTopRatedRestaurantsSection() {
            isTopRatedRestaurantsExpanded = true
            allergenSafeRestaurantsRecycler.adapter = RestaurantAdapter(allergenSafeRestaurantsData)
            updateActionButtonVisibility()
        }

        private fun collapseTopRatedRestaurantsSection() {
            isTopRatedRestaurantsExpanded = false
            val limitedList = allergenSafeRestaurantsData.take(1)
            allergenSafeRestaurantsRecycler.adapter = RestaurantAdapter(limitedList)
            updateActionButtonVisibility()
        }

        private fun parseRestaurants(jsonArray: JSONArray): List<Restaurant> {
            val restaurants = mutableListOf<Restaurant>()
            Log.d("SafeEats", "Starting to parse ${jsonArray.length()} restaurants from JSON array")

            for (i in 0 until jsonArray.length()) {
                try {
                    val json = jsonArray.getJSONObject(i)
                    Log.d("SafeEats", "Parsing restaurant JSON: ${json.toString()}")

                    // Get all available fields from the JSON
                    val keys = json.keys()
                    val keyList = mutableListOf<String>()
                    while (keys.hasNext()) {
                        keyList.add(keys.next())
                    }

                    // Check if we have at least a restaurant_id or id field
                    if (!json.has("id") && !json.has("restaurant_id")) {
                        Log.e("SafeEats", "Restaurant JSON missing ID field, skipping")
                        continue
                    }

                    // Get ID using either the new 'id' format or legacy 'restaurant_id' format
                    val restaurantId = if (json.has("id")) json.getString("id") else json.getString("restaurant_id")

                    // Get other fields with different possible field names
                    val restaurantName = if (json.has("name"))
                        json.optString("name")
                    else
                        json.optString("restaurant_name", "Restaurant #${i+1}")

                    val restaurantCategory = if (json.has("category"))
                        json.optString("category")
                    else
                        json.optString("restaurant_category", "General")

                    val restaurantAllergenInfo = if (json.has("allergenInfo"))
                        json.optString("allergenInfo")
                    else
                        json.optString("restaurant_allergen_info", "No allergen information available")

                    val restaurantRating = json.optDouble("rating", 0.0)

                    // Get safety score from either format
                    val safetyScore = if (json.has("safetyScore"))
                        json.optInt("safetyScore", 0)
                    else
                        json.optInt("safety_score", 0)

                    // Get dietary match score from either format
                    val dietaryMatchScore = if (json.has("dietaryMatchScore"))
                        json.optInt("dietaryMatchScore", 0)
                    else
                        json.optInt("dietary_match_score", 0)

                    // Get image URL from either format
                    val pictureUrl = if (json.has("imageUrl"))
                        json.optString("imageUrl", null)
                    else
                        json.optString("picture_url", null)

                    val restaurant = Restaurant(
                        id = restaurantId,
                        name = restaurantName,
                        category = restaurantCategory,
                        allergenInfo = restaurantAllergenInfo,
                        rating = restaurantRating,
                        safetyScore = safetyScore,
                        dietaryMatchScore = dietaryMatchScore,
                        imageUrl = pictureUrl
                    )
                    Log.d("SafeEats", "Successfully parsed restaurant: id=${restaurant.id}, name=${restaurant.name}")
                    restaurants.add(restaurant)
                } catch (e: Exception) {
                    Log.e("SafeEats", "Error parsing restaurant at index $i: ${e.message}", e)
                    // Print full stack trace for more details
                    e.printStackTrace()
                }
            }

            Log.d("SafeEats", "Finished parsing restaurants, total: ${restaurants.size}")
            return restaurants
        }

        private inner class RestaurantAdapter(private val restaurants: List<Restaurant>) :
            RecyclerView.Adapter<RestaurantAdapter.RestaurantViewHolder>() {

            init {
                Log.d("SafeEats", "Initializing RestaurantAdapter with ${restaurants.size} restaurants")
                restaurants.forEachIndexed { index, restaurant ->
                    Log.d("SafeEats", "Restaurant $index: ${restaurant.name}")
                }
            }

            inner class RestaurantViewHolder(view: View) : RecyclerView.ViewHolder(view) {
                val restaurantName: TextView = view.findViewById(R.id.restaurant_name)
                val restaurantCategory: TextView = view.findViewById(R.id.restaurant_category)
                val restaurantRating: TextView = view.findViewById(R.id.restaurant_rating)
                val restaurantSafetyScore: TextView = view.findViewById(R.id.restaurant_safety_score)
                val restaurantAllergenInfo: TextView = view.findViewById(R.id.restaurant_allergen_info)
                val restaurantPicture: ImageView = view.findViewById(R.id.restaurant_picture)
                val viewMenuButton: androidx.appcompat.widget.AppCompatButton = view.findViewById(R.id.view_menu_button)
            }

            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RestaurantViewHolder {
                Log.d("SafeEats", "Creating view holder for restaurant item")
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.restaurant_card_item, parent, false)
                return RestaurantViewHolder(view)
            }

            override fun onBindViewHolder(holder: RestaurantViewHolder, position: Int) {
                val restaurant = restaurants[position]
                Log.d("SafeEats", "Binding restaurant at position $position: ${restaurant.name}")

                // Set default values if empty
                holder.restaurantName.text = if (restaurant.name.isNotEmpty()) restaurant.name else "Restaurant #${position+1}"
                holder.restaurantCategory.text = if (restaurant.category.isNotEmpty()) restaurant.category else "General"
                holder.restaurantRating.text = restaurant.rating.toString()
                holder.restaurantSafetyScore.text = "Safety Score: ${restaurant.safetyScore}%"

                // Handle empty allergen info
                val allergenInfo = if (restaurant.allergenInfo.isNotEmpty()) restaurant.allergenInfo else "No allergen information available"

                // Trim allergen info if too long
                val maxLength = 100
                holder.restaurantAllergenInfo.text = if (allergenInfo.length > maxLength) {
                    "${allergenInfo.substring(0, maxLength)}..."
                } else {
                    allergenInfo
                }

                // Load restaurant image if available
                if (!restaurant.imageUrl.isNullOrEmpty()) {
                    Log.d("SafeEats", "Loading image for restaurant: ${restaurant.name}, URL: ${restaurant.imageUrl}")
                    loadImageIntoView(restaurant.imageUrl, holder.restaurantPicture)
                } else {
                    Log.d("SafeEats", "No image URL for restaurant: ${restaurant.name}")
                    // Set a default placeholder image
                    holder.restaurantPicture.setImageResource(R.drawable.ic_restaurant_placeholder)
                }

                // Set safety score color and background based on the score
                when {
                    restaurant.safetyScore >= 85 -> {
                        holder.restaurantSafetyScore.setTextColor(getColor(R.color.safety_high))
                        holder.restaurantSafetyScore.background = getDrawable(R.drawable.safety_badge_high)
                    }
                    restaurant.safetyScore >= 75 -> {
                        holder.restaurantSafetyScore.setTextColor(getColor(R.color.safety_medium))
                        holder.restaurantSafetyScore.background = getDrawable(R.drawable.safety_badge_medium)
                    }
                    else -> {
                        holder.restaurantSafetyScore.setTextColor(getColor(R.color.safety_low))
                        holder.restaurantSafetyScore.background = getDrawable(R.drawable.safety_badge_low)
                    }
                }

                // Inside RestaurantAdapter.onBindViewHolder (in HomeActivity.kt)
                holder.viewMenuButton.setOnClickListener {
                    Log.d("SafeEats", "View menu button clicked for restaurant: ${restaurant.name}")
                    startActivity(Intent(this@HomeActivity, MenuActivity::class.java).apply {
                        // Existing parameters
                        putExtra("restaurant_id", restaurant.id)
                        putExtra("restaurant_name", restaurant.name)
                        putExtra("email", email)

                        // Additional parameters you requested
                        putExtra("restaurant_description", restaurant.allergenInfo)
                        putExtra("restaurant_safety_score", restaurant.safetyScore)
                        putExtra("restaurant_image_url", restaurant.imageUrl)
                    })
                }
            }

            override fun getItemCount(): Int {
                Log.d("SafeEats", "getItemCount() called, returning ${restaurants.size}")
                return restaurants.size
            }
        }

        private fun loadProfilePicture() {
            loadingDialog.executeWithLoading<android.graphics.Bitmap?>(
                thresholdMs = 500,
                operation = {
                    try {
                        val encodedEmail = URLEncoder.encode(email, "UTF-8")
                        val request = Request.Builder()
                            .url("https://swamp-brief-brake.glitch.me/api/customer/profile-picture?email=$encodedEmail")
                            .get()
                            .build()

                        val response = okHttpClient.newCall(request).execute()

                        if (response.isSuccessful) {
                            val responseBody = response.body
                            if (responseBody != null) {
                                BitmapFactory.decodeStream(responseBody.byteStream())
                            } else null
                        } else null
                    } catch (e: Exception) {
                        Log.e("SafeEats", "Error loading profile picture: ${e.message}")
                        null
                    }
                },
                callback = { bitmap ->
                    if (bitmap != null) {
                        profileImage.setImageBitmap(bitmap)
                        profileImage.background = getDrawable(R.drawable.profile_avatar_background)
                        profileImage.clipToOutline = true
                    }
                }
            )
        }

        private fun loadImageIntoView(imageUrl: String, imageView: ImageView) {
            Thread {
                try {
                    val baseUrl = "https://swamp-brief-brake.glitch.me"
                    val fullUrl = if (imageUrl.startsWith("http")) imageUrl else baseUrl + imageUrl

                    Log.d("SafeEats", "Loading image from URL: $fullUrl")

                    val request = Request.Builder()
                        .url(fullUrl)
                        .get()
                        .build()

                    val response = okHttpClient.newCall(request).execute()

                    if (response.isSuccessful) {
                        val responseBody = response.body
                        if (responseBody != null) {
                            val bitmap = BitmapFactory.decodeStream(responseBody.byteStream())
                            runOnUiThread {
                                if (bitmap != null) {
                                    imageView.setImageBitmap(bitmap)
                                } else {
                                    Log.e("SafeEats", "Failed to decode image from URL: $fullUrl")
                                    imageView.setImageResource(R.drawable.ic_restaurant_placeholder)
                                }
                            }
                        } else {
                            Log.e("SafeEats", "Empty response body for image URL: $fullUrl")
                            runOnUiThread {
                                imageView.setImageResource(R.drawable.ic_restaurant_placeholder)
                            }
                        }
                    } else {
                        Log.e("SafeEats", "Failed to load image. Response code: ${response.code} for URL: $fullUrl")
                        runOnUiThread {
                            imageView.setImageResource(R.drawable.ic_restaurant_placeholder)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("SafeEats", "Error loading image: ${e.message}", e)
                    runOnUiThread {
                        imageView.setImageResource(R.drawable.ic_restaurant_placeholder)
                    }
                }
            }.start()
        }

        private fun updateGreetingText() {
            val greetingsTextView = findViewById<TextView>(R.id.grettings_text)

            // Get current hour and set appropriate greeting
            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            greetingsTextView.text = when {
                hour in 5..11 -> "Good Morning!"
                hour in 12..16 -> "Good Afternoon!"
                hour in 17..21 -> "Good Evening!"
                else -> "Good Night!"
            }
        }

        override fun onResume() {
            super.onResume()
            updateNotificationBadge()
            updateGreetingText()
            loadProfilePicture()
            loadRestaurants()
            updateSafeRestaurantCount()
        }

        override fun onDestroy() {
            super.onDestroy()
            loadingDialog.dispose()
        }

        // Called when a new dish is analyzed
        fun onDishAnalyzed() {
            updateNotificationBadge() // This will fetch the new count from server
        }
    }