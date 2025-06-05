package com.android.safeeats.models

data class Notification(
    val notificationId: String? = null,
    val email: String,
    val itemId: String,
    val dishSafetyScore: Int,
    val dishName: String,
    val dishDescription: String,
    val analyzedTime: Long = System.currentTimeMillis()
)