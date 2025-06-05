package com.android.safeeats

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import com.android.safeeats.utils.LoadingDialog

class LandingActivity : Activity() {
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var loadingDialog: LoadingDialog

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_landing)

        // Initialize the loading dialog
        loadingDialog = LoadingDialog(this)

        sharedPreferences = getSharedPreferences("SafeEatsPrefs", MODE_PRIVATE)
        val hasVisitedBefore = sharedPreferences.getBoolean("has_visited_landing", false)

        val logoImage: ImageView = findViewById(R.id.iv_logo)
        val appNameText: TextView = findViewById(R.id.tv_description_title)
        val descriptionText: TextView = findViewById(R.id.tv_description)
        val getStartedButton = findViewById<Button>(R.id.btn_get_started)
        val loginButton = findViewById<Button>(R.id.btn_get_started_login)

        val boldFont = androidx.core.content.res.ResourcesCompat.getFont(this, R.font.roboto_serif_bold)
        val regularFont = androidx.core.content.res.ResourcesCompat.getFont(this, R.font.roboto_serif_regular)

        appNameText.typeface = boldFont
        descriptionText.typeface = regularFont

        if (!hasVisitedBefore) {
            // First-time visitor - show smoother animations
            runSmoothEntryAnimations(logoImage, appNameText, descriptionText, getStartedButton, loginButton)

            // Mark that user has visited the landing page
            sharedPreferences.edit().putBoolean("has_visited_landing", true).apply()
        } else {
            // Returning visitor - show everything immediately without animations
            showElementsImmediately(logoImage, appNameText, descriptionText, getStartedButton, loginButton)
        }

        getStartedButton.setOnClickListener {
            loadingDialog.executeWithLoading(
                thresholdMs = 5000,
                operation = {
                    prepareForRegistration()
                },
                callback = { success ->
                    if (success) {
                        val intent = Intent(this, RegisterActivity::class.java)
                        startActivity(intent)
                    } else {
                        // Handle any preparation errors
                        // showErrorMessage("Failed to prepare registration")
                    }
                }
            )
        }

        loginButton.setOnClickListener {
            loadingDialog.executeWithLoading(
                thresholdMs = 5000,
                operation = {
                    prepareForLogin()
                },
                callback = { success ->
                    if (success) {
                        val intent = Intent(this, LoginActivity::class.java)
                        startActivity(intent)
                    } else {
                        // Handle any preparation errors
                        // showErrorMessage("Failed to prepare login")
                    }
                }
            )
        }
    }
    private fun prepareForRegistration(): Boolean {
        try {
            return true
        } catch (e: Exception) {
            return false
        }
    }

    private fun prepareForLogin(): Boolean {
        try {
            return true
        } catch (e: Exception) {
            return false
        }
    }

    private fun showElementsImmediately(
        logoImage: ImageView,
        appNameText: TextView,
        descriptionText: TextView,
        getStartedButton: Button,
        loginButton: Button
    ) {
        // Set all elements to be fully visible immediately
        logoImage.alpha = 1f
        appNameText.alpha = 1f
        descriptionText.alpha = 1f
        getStartedButton.alpha = 1f
        loginButton.alpha = 1f

        // Make sure scale and translation are at their final values
        logoImage.scaleX = 1f
        logoImage.scaleY = 1f
        appNameText.translationY = 0f
        descriptionText.translationY = 0f
        getStartedButton.translationY = 0f
        loginButton.translationY = 0f
    }

    private fun runSmoothEntryAnimations(
        logoImage: ImageView,
        appNameText: TextView,
        descriptionText: TextView,
        getStartedButton: Button,
        loginButton: Button
    ) {
        // Set initial state for animation
        logoImage.alpha = 0f
        appNameText.alpha = 0f
        descriptionText.alpha = 0f
        getStartedButton.alpha = 0f
        loginButton.alpha = 0f

        logoImage.scaleX = 0.5f
        logoImage.scaleY = 0.5f
        appNameText.translationY = 50f
        descriptionText.translationY = 30f
        getStartedButton.translationY = 20f
        loginButton.translationY = 20f

        // Logo Animation - smoother with longer duration
        val logoFadeIn = ObjectAnimator.ofFloat(logoImage, View.ALPHA, 0f, 1f).apply {
            duration = 1200
            interpolator = DecelerateInterpolator()
        }
        val logoScaleX = ObjectAnimator.ofFloat(logoImage, View.SCALE_X, 0.5f, 1f).apply {
            duration = 1200
            interpolator = DecelerateInterpolator()
        }
        val logoScaleY = ObjectAnimator.ofFloat(logoImage, View.SCALE_Y, 0.5f, 1f).apply {
            duration = 1200
            interpolator = DecelerateInterpolator()
        }

        // App Name Animation - with smoother timing
        val nameFadeIn = ObjectAnimator.ofFloat(appNameText, View.ALPHA, 0f, 1f).apply {
            duration = 1000
            startDelay = 600
            interpolator = DecelerateInterpolator()
        }
        val nameSlideUp = ObjectAnimator.ofFloat(appNameText, View.TRANSLATION_Y, 50f, 0f).apply {
            duration = 1000
            startDelay = 600
            interpolator = AccelerateDecelerateInterpolator()
        }

        // Description Animation - smoother and better timed
        val descFadeIn = ObjectAnimator.ofFloat(descriptionText, View.ALPHA, 0f, 1f).apply {
            duration = 1000
            startDelay = 1300
            interpolator = DecelerateInterpolator()
        }
        val descSlideUp = ObjectAnimator.ofFloat(descriptionText, View.TRANSLATION_Y, 30f, 0f).apply {
            duration = 1000
            startDelay = 1300
            interpolator = AccelerateDecelerateInterpolator()
        }

        // Buttons Animation - more gradual appearance
        val buttonFadeIn = ObjectAnimator.ofFloat(getStartedButton, View.ALPHA, 0f, 1f).apply {
            duration = 1000
            startDelay = 2000
            interpolator = DecelerateInterpolator()
        }
        val buttonSlideUp = ObjectAnimator.ofFloat(getStartedButton, View.TRANSLATION_Y, 20f, 0f).apply {
            duration = 1000
            startDelay = 2000
            interpolator = AccelerateDecelerateInterpolator()
        }

        val loginButtonFadeIn = ObjectAnimator.ofFloat(loginButton, View.ALPHA, 0f, 1f).apply {
            duration = 1000
            startDelay = 2300
            interpolator = DecelerateInterpolator()
        }
        val loginButtonSlideUp = ObjectAnimator.ofFloat(loginButton, View.TRANSLATION_Y, 20f, 0f).apply {
            duration = 1000
            startDelay = 2300
            interpolator = AccelerateDecelerateInterpolator()
        }

        // Combine animations
        val animatorSet = AnimatorSet().apply {
            playTogether(
                logoFadeIn, logoScaleX, logoScaleY,
                nameFadeIn, nameSlideUp,
                descFadeIn, descSlideUp,
                buttonFadeIn, buttonSlideUp,
                loginButtonFadeIn, loginButtonSlideUp
            )
        }

        // Start animations
        animatorSet.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        loadingDialog.dispose()
    }
}