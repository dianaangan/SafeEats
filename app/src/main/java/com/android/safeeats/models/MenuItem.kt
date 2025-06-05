package com.android.safeeats.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class MenuItem(
    val id: String,
    val name: String,
    val description: String,
    val category: String,
    val allergens: String,
    val safetyScore: Int,
    val imageUrl: String?
) : Parcelable