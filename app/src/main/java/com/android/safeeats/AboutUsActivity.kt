package com.android.safeeats

import android.app.Activity
import android.os.Bundle
import android.widget.ImageView

class AboutUsActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about_us)

        val backButton = findViewById<ImageView>(R.id.btn_back)

        backButton.setOnClickListener {
            finish()
        }
    }
}