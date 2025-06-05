package com.android.safeeats

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast

class AccountSetupThreeActivity : Activity() {
    private lateinit var email: String
    private lateinit var firstName: String
    private lateinit var lastName: String
    private lateinit var selectedAllergens: ArrayList<String>
    private val allergenSeverityMap = HashMap<String, String>()
    private val radioGroupMap = HashMap<String, RadioGroup>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_account_setup_three)

        // Get user info and selected allergens from intent
        email = intent.getStringExtra("email") ?: ""
        firstName = intent.getStringExtra("firstName") ?: ""
        lastName = intent.getStringExtra("lastName") ?: ""
        selectedAllergens = intent.getStringArrayListExtra("SELECTED_ALLERGENS") ?: ArrayList()

        val btnNext = findViewById<Button>(R.id.btn_next)
        val btnBack = findViewById<ImageButton>(R.id.btn_back)
        val allergensContainer = findViewById<LinearLayout>(R.id.allergens_container)

        val allergensseverity = findViewById<TextView>(R.id.allergensseverity)
        val selectyour  = findViewById<TextView>(R.id.selectyour)

        val boldFont = androidx.core.content.res.ResourcesCompat.getFont(this, R.font.roboto_serif_bold)

        allergensseverity.typeface = boldFont
        selectyour.typeface = boldFont

        // Back button simply goes back to previous screen
        btnBack.setOnClickListener {
            finish()
        }

        // If no allergens selected, skip to dietary preferences
        if (selectedAllergens.isEmpty()) {
            navigateToNextScreen()
            return
        }

        // Create RadioGroups for each allergen
        for (allergen in selectedAllergens) {
            createAllergenRadioGroup(allergensContainer, allergen)
        }

        // Next button validates and moves to next screen
        btnNext.setOnClickListener {
            if (validateSelections()) {
                navigateToNextScreen()
            }
        }
    }

    private fun createAllergenRadioGroup(container: LinearLayout, allergen: String) {
        // Inflate each RadioGroup from XML
        val inflater = LayoutInflater.from(this)
        val radioGroupLayout = inflater.inflate(R.layout.allergen_radio_group, container, false)

        // Get the views from the inflated layout
        val radioGroup = radioGroupLayout.findViewById<RadioGroup>(R.id.allergen)
        val textAllergen = radioGroupLayout.findViewById<TextView>(R.id.text_allergen)

        // Set the allergen name
        textAllergen.text = allergen

        // Store the RadioGroup for validation later
        radioGroupMap[allergen] = radioGroup

        // Add the layout to the container
        container.addView(radioGroupLayout)

        // Add spacing between allergen groups
        if (allergen != selectedAllergens.last()) {
            val spacer = View(this)
            spacer.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                24 // 24dp spacing
            )
            container.addView(spacer)
        }
    }

    private fun validateSelections(): Boolean {
        var allSelected = true

        for (allergen in selectedAllergens) {
            val radioGroup = radioGroupMap[allergen]
            if (radioGroup != null && radioGroup.checkedRadioButtonId == -1) {
                allSelected = false
                break
            }
        }

        if (!allSelected) {
            Toast.makeText(this, "Please select severity level for all allergens", Toast.LENGTH_SHORT).show()
            return false
        }

        // Store all selections in the map
        for (allergen in selectedAllergens) {
            val radioGroup = radioGroupMap[allergen]
            if (radioGroup != null) {
                val selectedId = radioGroup.checkedRadioButtonId
                if (selectedId != -1) {
                    val radioButton = findViewById<RadioButton>(selectedId)
                    allergenSeverityMap[allergen] = radioButton.text.toString()
                }
            }
        }

        return true
    }

    private fun navigateToNextScreen() {
        // Convert allergenSeverityMap to a format that can be passed in an intent
        val severityList = ArrayList<String>()
        for (allergen in selectedAllergens) {
            val allergenSeverity = allergenSeverityMap[allergen] ?: "Mild" // Default to Mild if not specified
            severityList.add("$allergen:$allergenSeverity")
        }

        val intent = Intent(this, AccountSetupFourActivity::class.java).apply {
            putExtra("email", email)
            putExtra("firstName", firstName)
            putExtra("lastName", lastName)
            putStringArrayListExtra("SELECTED_ALLERGENS", selectedAllergens)
            putStringArrayListExtra("ALLERGEN_SEVERITY", severityList)
        }
        startActivity(intent)
        finish()
    }
}