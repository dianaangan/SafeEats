package com.android.safeeats.models

data class NotificationItem(
    val notificationId: String,
    val email: String,
    val itemId: String,
    val dishName: String,
    val dishDescription: String?,
    val dishSafetyScore: Int,
    val analyzedTime: String
)