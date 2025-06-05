package com.android.safeeats

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView

class AccountSetupActivity : Activity() {
    private lateinit var email: String
    private lateinit var firstName: String
    private lateinit var lastName: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_new_account_landing)

        // Get user info from intent
        email = intent.getStringExtra("email") ?: ""
        firstName = intent.getStringExtra("firstName") ?: ""
        lastName = intent.getStringExtra("lastName") ?: ""

        // Check if we have needed data
        if (email.isEmpty()) {
            finish()
            return
        }

        val continueButton = findViewById<Button>(R.id.btn_continue)
        val welcomeText = findViewById<TextView>(R.id.tv_description)

        val tv_app_name = findViewById<TextView>(R.id.tv_app_name)

        val boldFont = androidx.core.content.res.ResourcesCompat.getFont(this, R.font.roboto_serif_bold)

        tv_app_name.typeface = boldFont

        // Set welcome message
        welcomeText.text = "Welcome to SafeEats, $firstName!"

        // Set up continue button
        continueButton.setOnClickListener {
            // Start the account setup flow
            val intent = Intent(this, AccountSetupOneActivity::class.java).apply {
                putExtra("email", email)
                putExtra("firstName", firstName)
                putExtra("lastName", lastName)
            }
            startActivity(intent)
        }
    }
}