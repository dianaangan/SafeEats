package com.android.safeeats

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast

class AccountSetupTwoActivity : Activity() {
    private lateinit var email: String
    private lateinit var firstName: String
    private lateinit var lastName: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_account_setup_two)

        // Get user info from intent
        email = intent.getStringExtra("email") ?: ""
        firstName = intent.getStringExtra("firstName") ?: ""
        lastName = intent.getStringExtra("lastName") ?: ""

        val btnNext = findViewById<Button>(R.id.btn_next)
        val btnBack = findViewById<ImageButton>(R.id.btn_back)

        val selectyourdesc = findViewById<TextView>(R.id.selectyour)
        val allergensdesc = findViewById<TextView>(R.id.allergens)

        val boldFont = androidx.core.content.res.ResourcesCompat.getFont(this, R.font.roboto_serif_bold)
        selectyourdesc.typeface = boldFont
        allergensdesc.typeface = boldFont

        // Back button simply closes this screen
        btnBack.setOnClickListener {
            finish()
        }

        // Next button collects selected allergens and moves to next screen
        btnNext.setOnClickListener {
            val allergens = ArrayList<String>()

            // Add selected allergens to list
            if (findViewById<CheckBox>(R.id.peanuts_allergen).isChecked) allergens.add("Peanuts")
            if (findViewById<CheckBox>(R.id.soy_allergen).isChecked) allergens.add("Soy")
            if (findViewById<CheckBox>(R.id.dairy_allergen).isChecked) allergens.add("Dairy")
            if (findViewById<CheckBox>(R.id.treenuts_allergen).isChecked) allergens.add("Tree Nuts")
            if (findViewById<CheckBox>(R.id.gluten_allergen).isChecked) allergens.add("Gluten")
            if (findViewById<CheckBox>(R.id.fish_allergen).isChecked) allergens.add("Fish")
            if (findViewById<CheckBox>(R.id.shellfish_allergen).isChecked) allergens.add("Shellfish")
            if (findViewById<CheckBox>(R.id.sesame_allergen).isChecked) allergens.add("Sesame")
            if (findViewById<CheckBox>(R.id.egg_allergen).isChecked) allergens.add("Eggs")

            if (allergens.isEmpty()) {
                Toast.makeText(this, "Please select at least one allergen or go back if you have none", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Go to next screen and pass selected allergens
            val intent = Intent(this, AccountSetupThreeActivity::class.java).apply {
                putExtra("email", email)
                putExtra("firstName", firstName)
                putExtra("lastName", lastName)
                putStringArrayListExtra("SELECTED_ALLERGENS", allergens)
            }
            startActivity(intent)
        }
    }
}