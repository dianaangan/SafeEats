package com.android.safeeats

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView

class TermsAndPrivacyActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_terms_and_privacy)

        val read_button = findViewById<Button>(R.id.btn_read)
        val back_button = findViewById<ImageButton>(R.id.btn_back)
        val desc_safeeats = findViewById<TextView>(R.id.safeeats)
        val desc_privacy = findViewById<TextView>(R.id.privacy)
        val desc_termscondition = findViewById<TextView>(R.id.termscondition)

        val boldFont = androidx.core.content.res.ResourcesCompat.getFont(this, R.font.roboto_serif_bold)
        desc_safeeats.typeface = boldFont
        desc_privacy.typeface = boldFont
        desc_termscondition.typeface = boldFont

        back_button.setOnClickListener {
            val intent = Intent(this, RegisterActivity::class.java)
            startActivity(intent)
        }

        read_button.setOnClickListener {
            val intent = Intent(this, RegisterActivity::class.java)
            startActivity(intent)
        }

    }
}