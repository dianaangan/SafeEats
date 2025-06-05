package com.android.safeeats

import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.safeeats.models.MenuItem
import com.android.safeeats.utils.LoadingDialog
import com.android.safeeats.utils.ImageLoadingDialog
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class MenuActivity : Activity() {

    private lateinit var restaurantId: String
    private lateinit var restaurantName: String
    private lateinit var restaurantDescription: String
    private var restaurantSafetyScore: Int = 0
    private var restaurantImageUrl: String? = null
    private lateinit var email: String
    private lateinit var loadingDialog: LoadingDialog
    private lateinit var imageLoadingDialog: ImageLoadingDialog

    // RecyclerViews for different menu categories
    private lateinit var startersDishRecycler: RecyclerView
    private lateinit var mainsDishRecycler: RecyclerView
    private lateinit var dessertsDishRecycler: RecyclerView
    private lateinit var drinksDishRecycler: RecyclerView

    // Category headers
    private lateinit var menuItemsStarters: LinearLayout
    private lateinit var menuItemsMains: LinearLayout
    private lateinit var menuItemsDesserts: LinearLayout
    private lateinit var menuItemsDrinks: LinearLayout

    // Filter tabs
    private lateinit var tabAll: TextView
    private lateinit var tabStarters: TextView
    private lateinit var tabMains: TextView
    private lateinit var tabDesserts: TextView
    private lateinit var tabDrinks: TextView

    // Store menu items data
    private var allMenuItems = mutableListOf<MenuItem>()
    private var startersMenuItems = mutableListOf<MenuItem>()
    private var mainsMenuItems = mutableListOf<MenuItem>()
    private var dessertsMenuItems = mutableListOf<MenuItem>()
    private var drinksMenuItems = mutableListOf<MenuItem>()

    // Sorting options
    private var isStartersSortedBySafety = true
    private var isMainsSortedBySafety = true
    private var isDessertsSortedBySafety = true
    private var isDrinksSortedBySafety = true

    // Search functionality
    private lateinit var searchEditText: EditText
    private lateinit var searchMenuItemsLayout: LinearLayout
    private lateinit var searchMenuItemsTitle: TextView
    private lateinit var searchDishRecycler: RecyclerView
    private var isSearchActive: Boolean = false
    private var filteredMenuItems = mutableListOf<MenuItem>()


    // HTTP Client
    private lateinit var okHttpClient: OkHttpClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_menu)
        // Initialize loading dialogs
        loadingDialog = LoadingDialog(this)
        imageLoadingDialog = ImageLoadingDialog(this)

        // Initialize OkHttpClient
        okHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        // Get restaurant data from intent
        getIntentData()

        // Set up UI
        setupUI()

        // Load data
        loadMenuItem()

        // Load restaurant image if available
        if (!restaurantImageUrl.isNullOrEmpty()) {
            loadRestaurantImage()
        }
    }

    private fun initializeViews() {
        // Initialize RecyclerViews
        startersDishRecycler = findViewById(R.id.starters_dish_recycler)
        mainsDishRecycler = findViewById(R.id.mains_dish_recycler)
        dessertsDishRecycler = findViewById(R.id.desserts_dish_recycler)
        drinksDishRecycler = findViewById(R.id.drinks_dish_recycler)
        searchDishRecycler = findViewById(R.id.search_dish_recycler)

        // Initialize filter tabs
        tabAll = findViewById(R.id.tab_all)
        tabStarters = findViewById(R.id.tab_starters)
        tabMains = findViewById(R.id.tab_mains)
        tabDesserts = findViewById(R.id.tab_desserts)
        tabDrinks = findViewById(R.id.tab_drinks)

        // Initialize category headers
        menuItemsStarters = findViewById(R.id.menu_items)
        menuItemsMains = findViewById(R.id.mains_menu_items)
        menuItemsDesserts = findViewById(R.id.desserts_menu_items)
        menuItemsDrinks = findViewById(R.id.drinks_menu_items)
        searchMenuItemsLayout = findViewById(R.id.search_menu_items)

        // Initialize search section title
        searchMenuItemsTitle = searchMenuItemsLayout.findViewById(R.id.search_title)

        // Hide search section initially
        searchMenuItemsLayout.visibility = View.GONE
        searchDishRecycler.visibility = View.GONE

        // Initialize search
        searchEditText = findViewById(R.id.searchEditText)

        // Set up RecyclerViews with vertical layout
        startersDishRecycler.layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
        mainsDishRecycler.layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
        dessertsDishRecycler.layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
        drinksDishRecycler.layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
        searchDishRecycler.layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)

        // Set up search icon click
        findViewById<ImageView>(R.id.searchIcon).setOnClickListener {
            searchEditText.requestFocus()
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
                .showSoftInput(searchEditText, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun hideSearchResults() {
        searchMenuItemsLayout.visibility = View.GONE
        searchDishRecycler.visibility = View.GONE
    }

    private fun hideAllCategories() {
        // Hide all category sections
        menuItemsStarters.visibility = View.GONE
        menuItemsMains.visibility = View.GONE
        menuItemsDesserts.visibility = View.GONE
        menuItemsDrinks.visibility = View.GONE

        startersDishRecycler.visibility = View.GONE
        mainsDishRecycler.visibility = View.GONE
        dessertsDishRecycler.visibility = View.GONE
        drinksDishRecycler.visibility = View.GONE
    }

    private fun getIntentData() {
        restaurantId = intent.getStringExtra("restaurant_id") ?: ""
        restaurantName = intent.getStringExtra("restaurant_name") ?: "Restaurant"
        restaurantDescription = intent.getStringExtra("restaurant_description") ?: ""
        restaurantSafetyScore = intent.getIntExtra("restaurant_safety_score", 0)
        restaurantImageUrl = intent.getStringExtra("restaurant_image_url")
        email = intent.getStringExtra("email") ?: ""
    }

    private fun setupFilterTabs() {
        // Set default state - "All" tab is selected
        updateTabSelection(tabAll)

        // Set click listeners for tabs
        tabAll.setOnClickListener {
            updateTabSelection(tabAll)
            showAllCategories()
        }

        tabStarters.setOnClickListener {
            updateTabSelection(tabStarters)
            showOnlyCategory("starters")
        }

        tabMains.setOnClickListener {
            updateTabSelection(tabMains)
            showOnlyCategory("mains")
        }

        tabDesserts.setOnClickListener {
            updateTabSelection(tabDesserts)
            showOnlyCategory("desserts")
        }

        tabDrinks.setOnClickListener {
            updateTabSelection(tabDrinks)
            showOnlyCategory("drinks")
        }
    }


    private fun updateTabSelection(selectedTab: TextView) {
        // Reset all tabs to unselected state
        for (tab in listOf(tabAll, tabStarters, tabMains, tabDesserts, tabDrinks)) {
            tab.setBackgroundResource(R.drawable.search_background)
            tab.setTextColor(getColor(R.color.tab_unselected_text))
            tab.isSelected = false
        }

        // Set selected tab style
        selectedTab.setBackgroundResource(R.drawable.selected_tab_background)
        selectedTab.setTextColor(getColor(R.color.tab_selected_text))
        selectedTab.isSelected = true
    }

    private fun showOnlyCategory(category: String) {
        if (isSearchActive) {
            // If search is active, only show search results
            hideAllCategories()
            searchMenuItemsLayout.visibility = View.VISIBLE
            searchDishRecycler.visibility = View.VISIBLE
            return
        }

        // Hide search results
        hideSearchResults()

        // Hide all categories first
        hideAllCategories()

        // Show only the selected category
        when (category) {
            "starters" -> {
                menuItemsStarters.visibility = View.VISIBLE
                startersDishRecycler.visibility = View.VISIBLE
            }
            "mains" -> {
                menuItemsMains.visibility = View.VISIBLE
                mainsDishRecycler.visibility = View.VISIBLE
            }
            "desserts" -> {
                menuItemsDesserts.visibility = View.VISIBLE
                dessertsDishRecycler.visibility = View.VISIBLE
            }
            "drinks" -> {
                menuItemsDrinks.visibility = View.VISIBLE
                drinksDishRecycler.visibility = View.VISIBLE
            }
        }
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
                    filterMenuItems(searchText)
                } else if (wasSearchActive) {
                    // User cleared search, revert to tab state
                    hideSearchResults()
                    when {
                        tabAll.isSelected -> showAllCategories()
                        tabStarters.isSelected -> showOnlyCategory("starters")
                        tabMains.isSelected -> showOnlyCategory("mains")
                        tabDesserts.isSelected -> showOnlyCategory("desserts")
                        tabDrinks.isSelected -> showOnlyCategory("drinks")
                    }
                }
            }
        })
    }

    private fun showAllCategories() {
        if (isSearchActive) {
            // If search is active, only show search results
            hideAllCategories()
            searchMenuItemsLayout.visibility = View.VISIBLE
            searchDishRecycler.visibility = View.VISIBLE
            return
        }

        // Hide search results
        hideSearchResults()

        // Show all categories
        menuItemsStarters.visibility = if (startersMenuItems.isNotEmpty()) View.VISIBLE else View.GONE
        menuItemsMains.visibility = if (mainsMenuItems.isNotEmpty()) View.VISIBLE else View.GONE
        menuItemsDesserts.visibility = if (dessertsMenuItems.isNotEmpty()) View.VISIBLE else View.GONE
        menuItemsDrinks.visibility = if (drinksMenuItems.isNotEmpty()) View.VISIBLE else View.GONE

        startersDishRecycler.visibility = if (startersMenuItems.isNotEmpty()) View.VISIBLE else View.GONE
        mainsDishRecycler.visibility = if (mainsMenuItems.isNotEmpty()) View.VISIBLE else View.GONE
        dessertsDishRecycler.visibility = if (dessertsMenuItems.isNotEmpty()) View.VISIBLE else View.GONE
        drinksDishRecycler.visibility = if (drinksMenuItems.isNotEmpty()) View.VISIBLE else View.GONE

        // Update adapters with current sorting
        updateAdapters()
    }

    private fun filterMenuItems(searchText: String) {
        // Filter menu items based on search text
        filteredMenuItems = allMenuItems.filter { menuItem ->
            menuItem.name.lowercase().contains(searchText) ||
                    menuItem.description.lowercase().contains(searchText) ||
                    menuItem.allergens.lowercase().contains(searchText)
        }.toMutableList()

        // Hide all category sections
        hideAllCategories()

        // Show search section
        searchMenuItemsLayout.visibility = View.VISIBLE
        searchDishRecycler.visibility = View.VISIBLE

        // Update search results title with count
        searchMenuItemsTitle.text = "Search Results (${filteredMenuItems.size})"

        // Update search recycler with filtered items
        searchDishRecycler.adapter = MenuItemAdapter(filteredMenuItems)
    }

    private fun setupBackButton() {
        findViewById<ImageButton>(R.id.btn_back).setOnClickListener {
            finish()
        }
    }

    private fun setupSortOptions() {
        // Set up click listeners for sort options
        val sortStarter = findViewById<TextView>(R.id.sort_starter)
        val sortMains = findViewById<TextView>(R.id.sort_mains)
        val sortDesserts = findViewById<TextView>(R.id.sort_desserts)
        val sortDrinks = findViewById<TextView>(R.id.sort_drinks)

        // Initialize text to "Sort by Safety" for all sort options
        sortStarter.text = "Sort by Safety"
        sortMains.text = "Sort by Safety"
        sortDesserts.text = "Sort by Safety"
        sortDrinks.text = "Sort by Safety"

        sortStarter.setOnClickListener {
            isStartersSortedBySafety = !isStartersSortedBySafety
            sortMenuItems(startersMenuItems, isStartersSortedBySafety)
            startersDishRecycler.adapter = MenuItemAdapter(startersMenuItems)
            updateSortIndicator(sortStarter, isStartersSortedBySafety)
            // Update text based on current sort mode
            sortStarter.text = if (isStartersSortedBySafety) "Sort by Safety" else "Sort by Risk"
        }

        sortMains.setOnClickListener {
            isMainsSortedBySafety = !isMainsSortedBySafety
            sortMenuItems(mainsMenuItems, isMainsSortedBySafety)
            mainsDishRecycler.adapter = MenuItemAdapter(mainsMenuItems)
            updateSortIndicator(sortMains, isMainsSortedBySafety)
            // Update text based on current sort mode
            sortMains.text = if (isMainsSortedBySafety) "Sort by Safety" else "Sort by Risk"
        }

        sortDesserts.setOnClickListener {
            isDessertsSortedBySafety = !isDessertsSortedBySafety
            sortMenuItems(dessertsMenuItems, isDessertsSortedBySafety)
            dessertsDishRecycler.adapter = MenuItemAdapter(dessertsMenuItems)
            updateSortIndicator(sortDesserts, isDessertsSortedBySafety)
            // Update text based on current sort mode
            sortDesserts.text = if (isDessertsSortedBySafety) "Sort by Safety" else "Sort by Risk"
        }

        sortDrinks.setOnClickListener {
            isDrinksSortedBySafety = !isDrinksSortedBySafety
            sortMenuItems(drinksMenuItems, isDrinksSortedBySafety)
            drinksDishRecycler.adapter = MenuItemAdapter(drinksMenuItems)
            updateSortIndicator(sortDrinks, isDrinksSortedBySafety)
            // Update text based on current sort mode
            sortDrinks.text = if (isDrinksSortedBySafety) "Sort by Safety" else "Sort by Risk"
        }
    }

    private fun updateSortIndicator(textView: TextView, isSortedBySafety: Boolean) {
        val icon = if (isSortedBySafety) R.drawable.ic_chevron_upward else R.drawable.ic_chevron_downward
        textView.setCompoundDrawablesWithIntrinsicBounds(0, 0, icon, 0)
    }

    private fun sortMenuItems(items: MutableList<MenuItem>, sortBySafety: Boolean) {
        if (sortBySafety) {
            items.sortByDescending { it.safetyScore }
        } else {
            items.sortBy { it.safetyScore }
        }
    }

    private fun updateAdapters() {
        // Update adapters with current data and sorting
        startersDishRecycler.adapter = MenuItemAdapter(startersMenuItems)
        mainsDishRecycler.adapter = MenuItemAdapter(mainsMenuItems)
        dessertsDishRecycler.adapter = MenuItemAdapter(dessertsMenuItems)
        drinksDishRecycler.adapter = MenuItemAdapter(drinksMenuItems)
    }

    private fun updateRestaurantInfo() {
        // Update restaurant information in the UI
        findViewById<TextView>(R.id.restaurant_name).text = restaurantName
        findViewById<TextView>(R.id.restaurant_description).text = restaurantDescription

        val safetyScoreText = findViewById<TextView>(R.id.restaurant_safety_score)
        safetyScoreText.text = "Safety Score: $restaurantSafetyScore%"

        // Set safety score color and background based on the score
        when {
            restaurantSafetyScore >= 85 -> {
                safetyScoreText.setTextColor(getColor(R.color.safety_high))
                safetyScoreText.background = getDrawable(R.drawable.safety_badge_high)
            }
            restaurantSafetyScore >= 75 -> {
                safetyScoreText.setTextColor(getColor(R.color.safety_medium))
                safetyScoreText.background = getDrawable(R.drawable.safety_badge_medium)
            }
            else -> {
                safetyScoreText.setTextColor(getColor(R.color.safety_low))
                safetyScoreText.background = getDrawable(R.drawable.safety_badge_low)
            }
        }

        // Load restaurant image if available
        if (!restaurantImageUrl.isNullOrEmpty()) {
            loadRestaurantImage()
        }
    }

    private fun loadRestaurantImage() {
        if (restaurantImageUrl.isNullOrEmpty()) return

        val baseUrl = "https://swamp-brief-brake.glitch.me"
        val fullUrl = if (restaurantImageUrl!!.startsWith("http"))
                        restaurantImageUrl!!
                      else
                        baseUrl + restaurantImageUrl!!

        // Use the image loading dialog
        imageLoadingDialog.loadImage(
            imageUrl = fullUrl,
            imageView = findViewById(R.id.restaurant_image),
            showPlaceholder = true
        )
    }

    private fun loadMenuItems() {
        loadingDialog.executeWithLoading(
            thresholdMs = 500,
            operation = {
                try {
                    val encodedEmail = URLEncoder.encode(email, "UTF-8")
                    val request = Request.Builder()
                        .url("https://swamp-brief-brake.glitch.me/api/menu?restaurant_id=$restaurantId&email=$encodedEmail")
                        .get()
                        .build()

                    val response = okHttpClient.newCall(request).execute()
                    if (!response.isSuccessful) return@executeWithLoading false

                    // Parse the raw JSON response
                    val jsonText = response.body?.string() ?: "{}"
                    Log.d("SafeEats", "RAW /menu response → $jsonText")
                    val jsonObject = JSONObject(jsonText)

                    // Process menu_items array from API response
                    if (jsonObject.has("menu_items")) {
                        parseMenuItems(jsonObject.getJSONArray("menu_items"))
                        return@executeWithLoading true
                    } else {
                        Log.e("SafeEats", "No 'menu_items' field found in API response")
                        return@executeWithLoading false
                    }
                } catch (e: Exception) {
                    Log.e("SafeEats", "Error loading menu items: ${e.message}", e)
                    return@executeWithLoading false
                }
            },
            callback = { success ->
                if (success) {
                    // Log the counts to debug
                    Log.d("SafeEats", "Loaded menu items - All: ${allMenuItems.size}, " +
                            "Starters: ${startersMenuItems.size}, " +
                            "Mains: ${mainsMenuItems.size}, " +
                            "Desserts: ${dessertsMenuItems.size}, " +
                            "Drinks: ${drinksMenuItems.size}")

                    // Categorize menu items
                    categorizeMenuItems()

                    // Apply initial sorting
                    sortMenuItems(startersMenuItems, isStartersSortedBySafety)
                    sortMenuItems(mainsMenuItems, isMainsSortedBySafety)
                    sortMenuItems(dessertsMenuItems, isDessertsSortedBySafety)
                    sortMenuItems(drinksMenuItems, isDrinksSortedBySafety)

                    // Update UI with menu items
                    updateAdapters()

                    // Update sort indicators
                    updateSortIndicator(findViewById(R.id.sort_starter), isStartersSortedBySafety)
                    updateSortIndicator(findViewById(R.id.sort_mains), isMainsSortedBySafety)
                    updateSortIndicator(findViewById(R.id.sort_desserts), isDessertsSortedBySafety)
                    updateSortIndicator(findViewById(R.id.sort_drinks), isDrinksSortedBySafety)

                    // Show/hide sections based on availability
                    menuItemsStarters.visibility = if (startersMenuItems.isNotEmpty()) View.VISIBLE else View.GONE
                    menuItemsMains.visibility = if (mainsMenuItems.isNotEmpty()) View.VISIBLE else View.GONE
                    menuItemsDesserts.visibility = if (dessertsMenuItems.isNotEmpty()) View.VISIBLE else View.GONE
                    menuItemsDrinks.visibility = if (drinksMenuItems.isNotEmpty()) View.VISIBLE else View.GONE
                } else {
                    Toast.makeText(this, "Failed to load menu items", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    // Fix for parsing menu items in MenuActivity.kt
    private fun parseMenuItems(jsonArray: JSONArray): Boolean {
        // Add this at the beginning of parseMenuItems
        Log.d("SafeEats", "Complete menu API response: ${jsonArray.toString()}")
        allMenuItems.clear()

        try {
            for (i in 0 until jsonArray.length()) {
                val json = jsonArray.getJSONObject(i)

                // Log the raw JSON for debugging
                Log.d("SafeEats", "Menu item JSON: ${json.toString()}")

                // Extract and parse allergens string
                val allergensStr = when {
                    json.has("allergens") && !json.isNull("allergens") -> {
                        try {
                            // Try to parse allergens as a JSON array if it's a string representation
                            val allergensObj = if (json.get("allergens") is String) {
                                JSONArray(json.getString("allergens"))
                            } else {
                                json.getJSONArray("allergens")
                            }
                            formatAllergens(allergensObj)
                        } catch (e: Exception) {
                            "No allergen information available"
                        }
                    }
                    else -> "No allergen information available"
                }

                // Always construct image URL from item_id - this will always use the endpoint
                // that serves the menu item picture
                val itemId = json.getString("item_id")
                val imageUrl = "https://swamp-brief-brake.glitch.me/api/menuitem/$itemId/picture"

                // Log that we're using the picture endpoint
                Log.d("SafeEats", "Using menu item picture endpoint for item $itemId: $imageUrl")

                val menuItem = MenuItem(
                    id = itemId,
                    name = json.optString("name", "Unknown Item"),
                    description = json.optString("description", "No description available"),
                    category = mapCategory(json.optString("category", "")),
                    allergens = allergensStr,
                    safetyScore = json.optInt("safety_score", 0),
                    imageUrl = imageUrl
                )

                Log.d("SafeEats", "Parsed menu item: ${menuItem.name}, Safety: ${menuItem.safetyScore}, Image URL: ${menuItem.imageUrl}")
                allMenuItems.add(menuItem)
            }
            return true
        } catch (e: Exception) {
            Log.e("SafeEats", "Error parsing menu items: ${e.message}", e)
            return false
        }
    }

    // Helper function to format allergens from JSONArray to readable string
    private fun formatAllergens(allergensArray: JSONArray?): String {
        if (allergensArray == null || allergensArray.length() == 0) {
            return "No allergen information available"
        }

        val allergensList = mutableListOf<String>()
        for (i in 0 until allergensArray.length()) {
            allergensList.add(allergensArray.getString(i))
        }

        return allergensList.joinToString(", ")
    }

    private fun mapCategory(serverCategory: String): String {
        // Map server categories to client categories
        return when (serverCategory.lowercase()) {
            "starter", "appetizer", "starters", "appetizers" -> "Starters"
            "main", "entree", "mains", "entrees" -> "Mains"
            "dessert", "desserts" -> "Desserts"
            "drink", "drinks", "beverage", "beverages" -> "Drinks"
            else -> {
                // Default mapping based on common terms
                when {
                    serverCategory.contains("starter") || serverCategory.contains("appetizer") -> "Starters"
                    serverCategory.contains("main") || serverCategory.contains("entree") -> "Mains"
                    serverCategory.contains("dessert") -> "Desserts"
                    serverCategory.contains("drink") || serverCategory.contains("beverage") -> "Drinks"
                    else -> "Mains" // Default category if mapping fails
                }
            }
        }
    }

    private fun categorizeMenuItems() {
        // Clear existing category lists
        startersMenuItems.clear()
        mainsMenuItems.clear()
        dessertsMenuItems.clear()
        drinksMenuItems.clear()

        // Categorize items
        for (item in allMenuItems) {
            when (item.category) {
                "Starters" -> startersMenuItems.add(item)
                "Mains" -> mainsMenuItems.add(item)
                "Desserts" -> dessertsMenuItems.add(item)
                "Drinks" -> drinksMenuItems.add(item)
                else -> {
                    // If category is unknown, add to mains as default
                    Log.d("SafeEats", "Unknown category: ${item.category} for item: ${item.name}")
                    mainsMenuItems.add(item)
                }
            }
        }

        // Log the categorized counts
        Log.d("SafeEats", "After categorization - " +
                "Starters: ${startersMenuItems.size}, " +
                "Mains: ${mainsMenuItems.size}, " +
                "Desserts: ${dessertsMenuItems.size}, " +
                "Drinks: ${drinksMenuItems.size}")
    }

    private inner class MenuItemAdapter(private val menuItems: List<MenuItem>) :
        RecyclerView.Adapter<MenuItemAdapter.MenuItemViewHolder>() {

        inner class MenuItemViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val dishName: TextView = view.findViewById(R.id.dish_name)
            val dishDescription: TextView = view.findViewById(R.id.dish_description)
            val dishSafetyScore: TextView = view.findViewById(R.id.dish_safety_score)
            val analyzeButton: androidx.appcompat.widget.AppCompatButton = view.findViewById(R.id.analyze_button)
            val dishPicture: ImageView = view.findViewById(R.id.dish_picture)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MenuItemViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.menu_card_item, parent, false)
            return MenuItemViewHolder(view)
        }

        override fun onBindViewHolder(holder: MenuItemViewHolder, position: Int) {
            val menuItem = menuItems[position]

            holder.dishName.text = menuItem.name
            holder.dishDescription.text = menuItem.description
            holder.dishSafetyScore.text = "${menuItem.safetyScore}% Safe"

            // Set safety score color and background based on the score
            when {
                menuItem.safetyScore >= 85 -> {
                    holder.dishSafetyScore.setTextColor(getColor(R.color.safety_high))
                    holder.dishSafetyScore.background = getDrawable(R.drawable.safety_badge_high)
                }
                menuItem.safetyScore >= 75 -> {
                    holder.dishSafetyScore.setTextColor(getColor(R.color.safety_medium))
                    holder.dishSafetyScore.background = getDrawable(R.drawable.safety_badge_medium)
                }
                else -> {
                    holder.dishSafetyScore.setTextColor(getColor(R.color.safety_low))
                    holder.dishSafetyScore.background = getDrawable(R.drawable.safety_badge_low)
                }
            }

            // Inside MenuActivity - launching AnalyzeActivity with dish info
            holder.analyzeButton.setOnClickListener {
                val currentTimestamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                    .format(java.util.Date())

                startActivity(Intent(this@MenuActivity, AnalyzeActivity::class.java).apply {
                    // Include dish details
                    putExtra("item_id", menuItem.id)
                    putExtra("dish_safety_score", menuItem.safetyScore)
                    putExtra("dish_name", menuItem.name)
                    putExtra("dish_description", menuItem.description)
                    putExtra("email", email)

                    // Simple string timestamp
                    putExtra("analyze_timestamp", currentTimestamp)

                    // Dish picture URL
                    if (!menuItem.imageUrl.isNullOrEmpty()) {
                        putExtra("dish_picture", menuItem.imageUrl)
                        Log.d("SafeEats", "Setting dish_picture in intent to: ${menuItem.imageUrl}")
                    }

                    // Additional parameters
                    putExtra("allergens", menuItem.allergens)
                })
            }

            // Load dish image if available
            if (!menuItem.imageUrl.isNullOrEmpty()) {
                imageLoadingDialog.loadImage(
                    imageUrl = menuItem.imageUrl,
                    imageView = holder.dishPicture,
                    showPlaceholder = true
                )
            } else {
                // Set placeholder if no image URL
                holder.dishPicture.setImageResource(R.drawable.placeholder_dish)
                holder.dishPicture.visibility = View.VISIBLE
            }
        }

        override fun getItemCount() = menuItems.size
    }

    private fun setupUI() {
        initializeViews()
        setupFilterTabs()
        setupSearchFunctionality()
        setupBackButton()
        setupSortOptions()
        updateRestaurantInfo()
    }

    private fun loadMenuItem() {
        loadMenuItems()
    }

    override fun onDestroy() {
        super.onDestroy()
        loadingDialog.dispose()
        imageLoadingDialog.dispose()
    }
}