package com.android.safeeats

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.RadioButton
import android.widget.TextView
import android.widget.Toast

class AccountSetupOneActivity : Activity() {
    private lateinit var email: String
    private lateinit var firstName: String
    private lateinit var lastName: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_account_setup_one)

        // Get user info from intent
        email = intent.getStringExtra("email") ?: ""
        firstName = intent.getStringExtra("firstName") ?: ""
        lastName = intent.getStringExtra("lastName") ?: ""

        val btnNext = findViewById<Button>(R.id.btn_next)
        val radioYes = findViewById<RadioButton>(R.id.yes_allergen)
        val radioNo = findViewById<RadioButton>(R.id.no_allergen)

        val question = findViewById<TextView>(R.id.question)
        val foodallergy = findViewById<TextView>(R.id.foodallergy)

        val boldFont = androidx.core.content.res.ResourcesCompat.getFont(this, R.font.roboto_serif_bold)

        foodallergy.typeface = boldFont
        question.typeface = boldFont


        btnNext.setOnClickListener {
            if (!radioYes.isChecked && !radioNo.isChecked) {
                Toast.makeText(this, "Please select an option", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (radioYes.isChecked) {
                // If user has allergies, go to allergen selection
                val intent = Intent(this, AccountSetupTwoActivity::class.java).apply {
                    putExtra("email", email)
                    putExtra("firstName", firstName)
                    putExtra("lastName", lastName)
                }
                startActivity(intent)
            } else {
                // If user has no allergies, skip to dietary preferences
                val intent = Intent(this, AccountSetupThreeActivity::class.java).apply {
                    putExtra("email", email)
                    putExtra("firstName", firstName)
                    putExtra("lastName", lastName)
                    putStringArrayListExtra("SELECTED_ALLERGENS", arrayListOf())
                    putExtra("ALLERGEN_LEVELS", HashMap<String, String>())
                }
                startActivity(intent)
            }
        }
    }
}